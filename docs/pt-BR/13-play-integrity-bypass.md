# 13 — Bypass do Play Integrity: o que circula na natureza

> "Toda onda de hardening do Play Integrity dispara uma onda de módulos de bypass. A pergunta
> do defensor não é se o bypass existe — é como detectar a distribuição de veredito mudando
> na sua telemetria antes da fraude detectar." — *Fortress field notes*

**TL;DR** — Play Integrity foi hardening, depois bypass, depois re-hardening, em ciclos de
aproximadamente seis meses desde 2022. Hoje (início de 2026) o kit de bypass que funciona é
**Magisk + Zygisk + DenyList + módulo Play Integrity Fix (PIF)**. Derrota
`MEETS_BASIC_INTEGRITY` e muito frequentemente `MEETS_DEVICE_INTEGRITY`. **Não** derrota
`MEETS_STRONG_INTEGRITY`. A contra-jogada do defensor é exigir Strong para ações de alto
valor e observar a distribuição de veredito por versão do app / mercado para o sinal
característico quando um novo módulo de bypass é shipado.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Notar a distribuição de bypass antes que volumes de fraude subam | Convencer o veredito do Google a ler "device stock" no meu telefone rootado |
| **Ideia central** | Strong integrity usa attestation hardware-rooted; bypasses de módulo não conseguem forjar essa assinatura | Eu não preciso forjar — preciso fazer o veredito chegar a um nível que seu servidor ainda aceita |
| **Pior falha** | Aceitar `MEETS_BASIC_INTEGRITY` para ações de alto valor | Thresholds hardcoded que não sobrevivem a um shift de distribuição de veredito |

---

## 🛡️ Defensor — "Eu observo o histograma, exijo o piso"

### A escada de veredito, de novo

| Veredito | Bootloader | Magisk + DenyList? | Módulo PIF hoje? |
|---|---|---|---|
| `MEETS_STRONG_INTEGRITY` | Locked + verified-boot green + hardware-attested | Falha | Falha — precisa de RoT hardware |
| `MEETS_DEVICE_INTEGRITY` | Locked + imagem Android padrão | Passa se DenyList cobre seu app | Frequentemente passa (com PIF) |
| `MEETS_BASIC_INTEGRITY` | Pode estar desbloqueado | Passa se PIF spoofa o build fingerprint do Pixel | Passa de forma confiável |
| `MEETS_VIRTUAL_INTEGRITY` | Emulador | n/a | n/a |
| (nenhum) | Desbloqueado, sem módulo | — | Veredito honesto |

**Para Fortress, o portão é**: ações de alto valor exigem `MEETS_STRONG_INTEGRITY`. Esse é o
piso que um atacante não consegue bypassar com módulos commercial-grade atuais; chegar lá
exige um ataque hardware-level no secure element do device, que está fora do alcance do
toolkit Magisk gratuito.

### O shape da telemetria do defensor

O que logar por requisição:

- Distribuição de `verdict.deviceRecognitionVerdict` por (versão do app, país, modelo do
  device).
- Razão Strong / Device / Basic / None por slice.
- Latência percentil 95 das requisições de Play Integrity token.
- Códigos de falha do `decodeIntegrityToken` (assinatura inválida, expirado, app mismatch).

O sinal de um módulo de bypass circulando é **um shift repentino no mix de veredito** para
uma slice de usuários. Antes do shift: a distribuição de um país pode ser 70% Strong / 20%
Device / 9% Basic / 1% None. Duas semanas depois de um novo módulo PIF shipar e pegar
tração: 40% Strong / 35% Device / 22% Basic / 3% None. O bucket "Device" inflou às custas
de Strong — esse é o shape de "alguém publicou um novo bypass".

Resposta operacional:

1. **Alertar** quando o ratio por slice shifta > 2σ em 24 horas.
2. **Não** apertar thresholds automaticamente durante o spike — falsos positivos também
   sobem.
3. **Verificar manualmente** uma amostra de sessões "agora só Device": eram antes Strong?
4. Se um módulo conhecido é responsável, escalar para a **equipe Play Integrity** com os
   payloads de veredito — Google ship fixes server-side em semanas.

### Attestation hardware como a camada inenganável

`MEETS_STRONG_INTEGRITY` ultimamente depende da **cadeia de attestation hardware-rooted** do
device (coberta em [11-root-detection.md](11-root-detection.md)). O serviço Play Integrity lê
cadeias de cert de attestation do secure element do device; essas cadeias terminam numa
chave que **Google controla** no hardware root. Magisk roda em userspace Linux; o secure
element roda abaixo do Linux. Não há truque de software que faz o secure element atestar um
estado de boot diferente.

O que Magisk + PIF conseguem fazer é **manipular as entradas de userspace** que o Play
Integrity lê ao lado da attestation: build properties, device fingerprint, lista de pacotes.
Quando a lógica de veredito do Google pesa muito sinais de userspace (como ela faz para
`BASIC`/`DEVICE`), spoofing de userspace funciona. Quando ela pesa muito attestation
hardware (como faz para `STRONG`), spoofing não alcança.

### Defesa em profundidade, mesmo se você exige Strong

Mesmo com `STRONG` exigido, sobreponha:

- **Device binding** ([09-zero-trust.md](09-zero-trust.md)) para que um device atacante com
  Strong integrity ainda não bata com o binding enrolado do usuário.
- **Behaviour scoring** — Strong integrity é necessário mas não suficiente. Uma primeira
  transação de uma vida de um device Strong-integrity novinho deveria ainda disparar a risk
  engine.
- **Confirmação out-of-band** — push para um device known-trusted para ops de alto valor.

### Não confie no cliente para enviar "Strong"

O integrity token tem que ser **decoded server-side**. Um cliente que diz "eu tenho um
veredito Strong" é só texto no fio. O token é um JWT (bem, JWS) assinado pelo Google; o
servidor chama `playintegrity.v1.decodeIntegrityToken`, pega o veredito estruturado, e
**depois** decide. Veja [05-play-integrity.md](05-play-integrity.md).

---

## ⚔️ Atacante — "Eu shifto o veredito um tier e procuro portas destrancadas"

### Bypass 1 — Magisk + Zygisk + DenyList (o piso)

```
1. Instale Magisk via custom recovery (TWRP ou patch da boot.img).
2. Habilite Zygisk nas configurações do Magisk.
3. Instale o módulo "Play Integrity Fix" — fork canônico atual.
4. Abra Magisk → DenyList → adicione o app alvo (`com.umain.fortress`).
5. Reinicie.
6. Verifique: o app "Play Integrity API Checker" mostra MEETS_DEVICE_INTEGRITY (às vezes STRONG).
7. Abra o app alvo.
```

Se o alvo aceita `MEETS_DEVICE_INTEGRITY` para operações sensíveis, eu entro.

**Counter:**
- Exija `MEETS_STRONG_INTEGRITY` para ops que mudam estado. Reduz o que eu posso fazer mas
  não recusa minha sessão.
- Para *fazer login* e *ler estado*, um veredito Device é um piso razoável; o dano que eu
  posso fazer sem Strong é limitado pelo gating de step-up.

### Bypass 2 — Spoofing de fingerprint do módulo PIF

O módulo PIF funciona injetando um `Build.FINGERPRINT` + `ro.build.fingerprint` falso que bate
com um build de Pixel cujas chaves de assinatura Google **não** revogou. O serviço Play
Integrity vê um fingerprint Pixel "known good" e concede `MEETS_DEVICE_INTEGRITY` (às vezes
brevemente `MEETS_STRONG_INTEGRITY` até Google patchar aquele fingerprint específico).

A caçada: mantenedores do PIF rastreiam quais fingerprints Pixel Google ainda não revogou e
atualizam o módulo. Google revoga; o módulo atualiza de novo. Ciclo é ~semanas.

**Counter:**
- Observe a distribuição de **verdict.deviceIntegrity** por versão de app. Uma forma nova
  de "queda em Strong + subida em Device" correlaciona com uma nova release de PIF.
- Telemetria em `appIntegrity.certificateSha256Digest` — verifique que bate com *seu*
  release cert. PIF não consegue forjar isso sem re-assinar o APK.

### Bypass 3 — Servidores de decryption / proxy

Serviços online aceitam o nonce do app alvo, rodam o fluxo Play Integrity no device real,
attested **deles**, e devolvem o token resultante. O token é genuíno — é um veredito Strong
de um Pixel real — só não do device que está pedindo.

Efetivo contra qualquer defesa que não atrele o token ao cliente requisitante.

**Counter:**
- Atrele o integrity token a um `requestHash` derivado da public key de device-binding e da
  ação. O proxy não consegue gerar um token para o *meu* hash porque eles não têm minha
  chave.
- Cert pinning + DPoP (proof of possession) em toda requisição torna "trocar por um token
  roubado" difícil, porque a requisição ainda tem que ser assinada pelo device para o qual
  o token foi emitido.

### Bypass 4 — Chaves de assinatura do Google vazadas (raro)

Algumas vezes na história, chaves de assinatura OEM vazaram (Pixel 6, certo firmware
Samsung). Uma ROM custom assinada com as chaves vazadas sinaliza estado "Verified" de boot
para Play Integrity. Google revoga as chaves; até a revogação propagar, a ROM passa Strong.

**Counter:**
- A revogação é a resposta. Verifique contra o JWKS / trust roots de KeyAttestation atual,
  que exclui chaves revogadas.
- Se você tem visibilidade em fingerprints específicos de chave comprometida, deny-liste em
  nível de aplicação também.

### Bypass 5 — Downgrade de `attestationVersion`

Devices velhos reportam um `attestationVersion` menor na cadeia KeyAttestation. Se seu
servidor aceita versões baixas para ops sensíveis, um atacante com um device velho vulnerável
pega um veredito Strong de hardware cooperativo-mas-fraco.

**Counter:**
- Defina um piso mínimo de `attestationVersion` (ex: ≥ 200, KeyMaster 4.0+) para ops
  sensíveis.
- Devices mais velhos: recuse ops sensíveis polidamente, aponte o usuário para docs de
  "atualize seu telefone".

### Bypass 6 — Bootloader-locked mas ROM com chave custom

Alguns OEMs deixam usuários trancarem o bootloader com a própria chave de assinatura **deles**
(ex: via fastboot flash key). Play Integrity então vê "bootloader locked + verified boot" e
pode conceder Strong na primeira geração desses devices, antes da lógica do Google aprender
a distinguir "chave OEM" de "chave do usuário".

**Counter:**
- Validação de cadeia KeyAttestation: a root key instalada pelo usuário produz uma cadeia
  que não valida contra os roots hardware do Google. A checagem server-side pega.
- Telemetria: um Pixel afirmando "Locked + bootkey não-Google" é anômalo e deveria ser
  flagado.

### Bypass 7 — Cachear um veredito Strong e replayar

Se seu servidor cacheia vereditos por tempo demais (digamos 24 horas), eu pego um veredito
Strong limpo à meia-noite, depois rodo meus ataques o resto do dia sob a benção cacheada.

**Counter:**
- Janela de cache ≤ 5 min para ops sensíveis, binding de uso único para transferências.
- Para ops read-only: cache mais longo está OK mas sempre atrele ao device.

### Bypass 8 — Race da janela de freshness com requisições paralelas

Se a checagem de integridade é por sessão (não por ação), eu disparo 1 000 requisições de
transferência nos 60 segundos que meu token está fresh, antes que a próxima checagem me
pegue.

**Counter:**
- Token binding por ação sensível via `requestHash`. O token emitido para "reveal account X"
  não verifica em "transfer €1000 to Y".
- Rate limit em endpoints sensíveis — na camada de aplicação, independente de integridade.

---

## Cross-reference

- **Os vereditos de Play Integrity e o fluxo server** → [05-play-integrity.md](05-play-integrity.md)
- **O que substitui detecção de root em 2026** → [11-root-detection.md](11-root-detection.md)
- **Por que device binding fecha lacunas residuais** → [09-zero-trust.md](09-zero-trust.md)
- **Camada Frida / RASP detectando in-process tamper** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **O ângulo de emulator-rooting** → [15-emulator-rooting.md](15-emulator-rooting.md)

## Referências

- [How Attackers Bypass Play Integrity API in the Wild](https://medium.com/@vaibhav.shakya786/how-attackers-bypass-play-integrity-api-in-the-wild-f1091aea36e9)
- [Google Play Integrity Verdicts reference](https://developer.android.com/google/play/integrity/verdicts)
- [topjohnwu/Magisk DenyList](https://topjohnwu.github.io/Magisk/denylist.html)
- [Play Integrity Fix module (community fork)](https://github.com/chiteroman/PlayIntegrityFix)
