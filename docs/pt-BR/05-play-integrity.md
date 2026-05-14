# 05 — Confiança no ambiente: attestation via Play Integrity

> "Seu app não consegue provar a própria integridade. Esse é justamente o ponto — o SO prova
> *para* o seu backend, e seu backend decide no que acreditar." — *Fortress field notes*

**TL;DR** — A **Play Integrity API** deixa o SO Android (assinado pelo Google) atestar *por*
seu app que o dispositivo, o binário do app e a licença são o que você espera. O veredito é
um token tipo JWT assinado que **seu backend** verifica — não seu cliente. Qualquer decisão
que o cliente toma sobre o veredito é teatro. Este arquivo passa pelo fluxo de standard
request, pelo que os quatro vereditos significam, por como ligar isso no Fortress, e pela
longa e motivada lista de tentativas de bypass.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Obter uma segunda opinião assinada pelo Google sobre o device e o app | Fazer o Google assinar uma mentira |
| **Ideia central** | O veredito é evangelho server-side; o cliente é só mensageiro | O serviço de Play Integrity confia na TEE do dispositivo — quebre a TEE ou imite ela |
| **Pior falha** | Deixar o *cliente* parsear e decidir baseado no veredito | Um bypass tipo "device-was-Trusted" que engana seu `if (verdict == TRUSTED)` |

---

## 🛡️ Defensor — "Eu pergunto pro Google, não pergunto pro cliente"

### Os quatro vereditos

Uma resposta de Play Integrity empacota quatro vereditos independentes:

| Field | O que te diz |
|---|---|
| **`deviceIntegrity`** | O device roda Android não modificado: bootloader locked, sem Magisk, sem partições rootadas. Reporta como um conjunto de "labels" — `MEETS_DEVICE_INTEGRITY`, `MEETS_BASIC_INTEGRITY`, `MEETS_STRONG_INTEGRITY`, `MEETS_VIRTUAL_INTEGRITY`. |
| **`appIntegrity`** | O APK exato que está chamando é o que você assinou. Reporta `PLAY_RECOGNIZED` (bate com o binário Play-signed), `UNRECOGNIZED_VERSION` (binário desconhecido), ou `UNEVALUATED`. |
| **`accountDetails`** | O usuário instalou via Play e a licença é válida. `LICENSED`, `UNLICENSED`, ou `UNEVALUATED`. |
| **`environmentDetails`** | (Só em standard requests.) Sinais de risco sobre o ambiente em runtime — ex: **`NO_ISSUES`**, `APP_ACCESS_RISK` (sideloaded XAPK companions, ataques de overlay). |

Cada um é uma lista. Um device pode reportar `MEETS_DEVICE_INTEGRITY` *e*
`MEETS_VIRTUAL_INTEGRITY` — significando device real, bootloader real, mas atualmente rodando
num emulador que passa na checagem de virtual integrity. (Sim, emuladores podem passar — veja
a seção do atacante.)

### Standard vs Classic requests

| | Standard | Classic |
|---|---|---|
| Melhor para | Chamadas de alta frequência (toda mudança de tela) | Decisões one-shot (sign-in, transferência grande) |
| Latência | Baixa — token é pre-warmed | Maior — round trip fresco toda vez |
| Janela de replay | Refresh + reuse possível | Uso único contra um nonce recém emitido |
| Custo (quota) | Alta frequência, menos info por chamada | Frequência menor, veredito mais completo |
| Recomendado | Trust checks contínuos | Step-up authorization |

Estratégia Fortress:
- **Launch do app + a cada 15 min**: standard request, atualiza o `SecurityChip` na app bar.
- **Momentos de step-up** (confirmação de transferência, reveal de IBAN, novo destinatário):
  classic request com nonce fresco bound à ação.

### Ligando o standard request (lado Android)

```kotlin
// Inicializa o integrity manager uma vez. Provider precisa de Google Play Services >= 24.x.
private val provider = IntegrityManagerFactory.createStandard(context)

suspend fun getStandardToken(requestHash: ByteArray): String =
    provider.requestIntegrityToken(
        StandardIntegrityTokenRequest.builder()
            .setRequestHash(Base64.encodeToString(requestHash, Base64.URL_SAFE or Base64.NO_WRAP))
            .build()
    ).token()
```

O `requestHash` são **quaisquer** bytes opacos que o servidor pode depois verificar que ele
mesmo emitiu — tipicamente `SHA-256(nonce || userId || actionContext)`. O serviço de Play
Integrity atrela o token a esse hash. Sem isso, o mesmo token pode ser replayado entre
requisições.

### Ligando o classic request (ação de alto valor)

```kotlin
private val provider = IntegrityManagerFactory.create(context)

suspend fun getClassicToken(nonce: String): String =
    provider.requestIntegrityToken(
        IntegrityTokenRequest.builder()
            .setNonce(nonce)             // 16–500 chars, URL-safe base64, uso único
            .setCloudProjectNumber(123)  // seu Google Cloud project para verificação
            .build()
    ).token()
```

O nonce vem do servidor, expira em 60s, é bound ao usuário e à ação, e é de uso único. Emita
para cada momento de step-up, descarte após verificação.

### Verificação server-side

```ts
import { google } from "googleapis";

const playintegrity = google.playintegrity({
  version: "v1",
  auth: await getCloudAuth(),
});

const decoded = await playintegrity.v1.decodeIntegrityToken({
  packageName: "com.umain.fortress",
  requestBody: { integrityToken: token },
});

const payload = decoded.data.tokenPayloadExternal;
//  payload.deviceIntegrity.deviceRecognitionVerdict: string[]
//  payload.appIntegrity.appRecognitionVerdict: string
//  payload.appIntegrity.packageName: string
//  payload.appIntegrity.certificateSha256Digest: string[]
//  payload.accountDetails.appLicensingVerdict: string
//  payload.requestDetails.requestHash: string  (== o hash que emitimos)
//  payload.requestDetails.timestampMillis: number  (perto de agora)
```

Checagens críticas **que o servidor** precisa fazer:

1. **`requestDetails.requestHash` bate com o que emiti para este user/ação.** Se não, descarta
   — é replay de um contexto diferente.
2. **`requestDetails.timestampMillis` está fresco** (≤60s para classic, ≤5 min para standard).
3. **`appIntegrity.packageName === "com.umain.fortress"`**.
4. **`appIntegrity.certificateSha256Digest` bate com o fingerprint do nosso release cert** (o
   SHA-256 da App Signing key no Play Console).
5. **`appIntegrity.appRecognitionVerdict === "PLAY_RECOGNIZED"`** para portões de alto valor;
   permita `UNRECOGNIZED_VERSION` só em janelas conhecidas de staged rollout.
6. **`deviceIntegrity.deviceRecognitionVerdict` contém** no mínimo `MEETS_BASIC_INTEGRITY`;
   ações de step-up exigem `MEETS_DEVICE_INTEGRITY` ou `MEETS_STRONG_INTEGRITY`.
7. **`accountDetails.appLicensingVerdict === "LICENSED"`** (pega instalações piratas).

A saída é seu sealed type [`IntegrityVerdict`](../../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt)
— `Trusted`, `Limited(reasons)`, `Untrusted(reasons)`. As reasons são porquê, não o que está
errado com o usuário — elas decidem se o SecurityChip mostra amber ou red.

### Cacheando o veredito server-side

Um standard token pode ser reemitido pelo Play por ~10 minutos. Confie **no timestamp**, não
no cliente. Política do servidor:

- `≤ 5 min de idade`: confiar, sem re-fetch.
- `5 – 10 min de idade`: confiar para operações read-only, forçar um classic request fresco
  para qualquer operação que muta estado.
- `> 10 min de idade`: descartar, forçar o cliente a pedir um token fresco.

### O que fazer quando o veredito vai para o vermelho

| Veredito | UX |
|---|---|
| `MEETS_STRONG_INTEGRITY` | Acesso total |
| `MEETS_DEVICE_INTEGRITY` | Acesso total |
| `MEETS_BASIC_INTEGRITY` só | Operações sensíveis exigem um step-up biométrico adicional bound a uma chave TEE |
| `MEETS_VIRTUAL_INTEGRITY` só | Read-only, sem movimentação de dinheiro, sem mudança de destinatário |
| Nenhum | Bloquear, mostrar uma tela "este device não consegue rodar Fortress com segurança" |

Nunca **silenciosamente** degrade — o usuário vê o SecurityChip mudar. Falhas ruidosas mantêm
sua carga de suporte baixa (pessoas entendem o que está acontecendo) e sinalizam ao atacante
que o portão existe.

### O problema de bootstrap

Standard requests precisam do `cloudProjectNumber` e de um Google Play Services funcional. Em
devices sem GMS (alguns OEMs chineses, certas distribuições enterprise), a standard API falha.

Opções:
- **Recusar rodar** em devices sem GMS — apropriado para apps de banking de alta garantia.
- **Degradar** para uma postura mais conservadora de trust (sem transferências, read-only).
- **Substituir** por outra attestation (Samsung Knox em Samsung; KeyAttestation via cadeia de
  certificados do Keystore — veja [11-root-detection.md](11-root-detection.md)).

O Fortress vai recusar para a demo. Ajuste para sua população real.

---

## ⚔️ Atacante — "Eu faço o Google assinar uma mentira, ou pulo o signing inteiro"

### Bypass 1 — Faça o cliente decidir

Se seu cliente checa o veredito em Kotlin e gateia a requisição com base num boolean, meu
hook do Frida substitui o boolean por `true`. O servidor nunca vê Play Integrity.

**Counter:** o cliente nunca decide. O token é opaco para o cliente; o **servidor** chama
`decodeIntegrityToken`. Não há nada no cliente para hookear que mude a decisão do servidor.

### Bypass 2 — Replayar um token known-good

Se o servidor não atrela o token a `requestHash` ou não checa o timestamp, eu capturo um
token bom de um device real e reuso para toda requisição subsequente do meu device rootado.

**Counter:**
- Atrele o token a um hash fresco por requisição.
- Rejeite qualquer token cujo `timestampMillis` é mais velho que o threshold da política.
- Para classic: atrele a um nonce de uso único, queime após verificação.

### Bypass 3 — Pass-through por um device real

O ataque "device farm". Eu rodo o app num device real, não modificado, sob meu controle. Ele
produz um token bom. Eu extraio o token, envio via minha sessão de ataque atual (rodando num
device rootado ou PC). O servidor vê um token `MEETS_STRONG_INTEGRITY` perfeitamente válido.

**Counter:**
- Atrelar com `requestHash` torna o token válido só para a requisição específica em que foi
  emitido. Eu precisaria manter o device legítimo fazendo cada requisição subsequente do
  ataque, o que escala mal.
- Chaves públicas bound ao device (`cnf` em tokens) — veja [09-zero-trust.md](09-zero-trust.md)
  — tornam o integrity token *necessário mas não suficiente*. Minha chave de device-binding
  no meu device atacante não bate com o que o servidor espera.

### Bypass 4 — Magisk Hide / Zygisk + DenyList

Magisk tem um cat-and-mouse contínuo com Play Integrity. O estado da arte atual (2026) é
mais ou menos:

- Devices com bootloader desbloqueado **não conseguem** pegar `MEETS_DEVICE_INTEGRITY`
  independente de Magisk Hide — o estado do bootloader é assinado pelo hardware do device.
- Mas: alguns caminhos residuais existem onde Magisk + Zygisk + DenyList + módulos "Play
  Integrity Fix" spoofam o veredito em versões específicas do Android. Esses são patched em
  ondas.

**Minha jogada:** mirar devices mais velhos, versões mais velhas do Android, OEMs que não
integraram totalmente attestation hardware-backed.

**Counter:**
- Exija `MEETS_DEVICE_INTEGRITY` (não só `MEETS_BASIC_INTEGRITY`) para ops sensíveis.
- Mantenha um piso mínimo de versão de Android.
- Telemetria sobre distribuição de veredito: um spike repentino de usuários só
  `MEETS_BASIC_INTEGRITY` num mercado é sinal de um módulo de bypass circulando.

### Bypass 5 — Decryption servers (o caminho "private API")

Alguns serviços online recebem um token de Play Integrity e devolvem um "fixado" com vereditos
melhores. Tipicamente funcionam:

- Re-assinando o token com chaves roubadas de Play Integrity (raro; Google rotaciona e
  revoga),
- Ou proxando a requisição para um device real (veja Bypass 3),
- Ou explorando timing na API de verificação.

**Counter:**
- Revogação de token: Google ocasionalmente invalida chaves de assinatura comprometidas.
  Verifique contra o JWKS público atual.
- `tokenPayloadExternal.requestDetails.requestPackageName` deve bater com meu pacote. Tokens
  de um app diferente são pegos.

### Bypass 6 — ROMs custom alegando Play certification

Uma ROM custom com chave vazada de Play certification se assina como device reconhecido.
Esses leaks já aconteceram (chaves de Pixel 6 em 2023). Google revoga as chaves afetadas.

**Counter:**
- Verifique o token contra o JWKS público atual do Google — chaves revogadas não verificam.
- A checagem do lado do Google aplica isso; você só precisa chamar `decodeIntegrityToken`.

### Bypass 7 — Forçar `appRecognitionVerdict` para "UNRECOGNIZED_VERSION" e torcer pra você aceitar

Se a política do servidor é "aceitar qualquer um de `PLAY_RECOGNIZED`, `UNRECOGNIZED_VERSION`,
ou `UNEVALUATED`", eu posso sideloadar um APK modificado com um cert de assinatura conhecido
e pegar um veredito que seu servidor aceita.

**Counter:**
- Para operações que mudam estado: só `PLAY_RECOGNIZED`. Bloqueie o resto.
- Para operações read-only: `UNRECOGNIZED_VERSION` está OK durante staged rollouts (sua nova
  versão ainda não é reconhecida pelo Play). Documente a política.
- Sempre cheque `certificateSha256Digest` contra seu release cert esperado.

### Bypass 8 — Emulador com Play services completo

Emuladores modernos (Genymotion, o do Android Studio com GMS) podem atingir
`MEETS_VIRTUAL_INTEGRITY`. Se seu servidor aceita virtual-integrity para ops sensíveis, você
deu boas-vindas a um fuzzer baseado em emulador.

**Counter:**
- Ops sensíveis exigem `MEETS_DEVICE_INTEGRITY` ou `MEETS_STRONG_INTEGRITY`.
- `MEETS_VIRTUAL_INTEGRITY` está OK para fluxos de dev/QA mas deveria ser flagado em
  telemetria de produção — um spike de virtual integrity é padrão de dev/automação, não de
  usuários reais.

### Bypass 9 — Race da janela de freshness

Se a política de timestamp é "≤5 min de idade", tenho 5 minutos pra pegar um token bom e
queimar entre muitas requisições. Combinado com Bypass 3, isso é perigoso.

**Counter:**
- Para ops de alto valor: ≤60 segundos. O valor tático do token cai bruscamente.
- Binding por requisição via `requestHash` torna "queimar um token entre muitas" inviável
  independente da janela de freshness.

---

## Cross-reference

- **A struct de veredito no cliente** → [`IntegrityCheck`](../../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt), [`IntegrityVerdict`](../../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt)
- **A superfície de UI que reflete o veredito** → [`SecurityChip`](../../app/src/main/java/com/umain/fortress/ui/components/SecurityChip.kt)
- **Attestation pré-Play-Integrity (KeyAttestation)** → [11-root-detection.md](11-root-detection.md)
- **Técnicas de bypass conhecidas na natureza** → [13-play-integrity-bypass.md](13-play-integrity-bypass.md)
- **Hookar a chamada de fetch do integrity token** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **Por que chaves bound ao device fecham o resto da superfície de ataque** → [09-zero-trust.md](09-zero-trust.md)

## Referências

- [Part 5 — Environment Trust: Play Integrity Attestation](https://blog.stackademic.com/part-5-environment-trust-play-integrity-attestation-9c3409764e2e)
- [Google Play Integrity API — Overview](https://developer.android.com/google/play/integrity/overview)
- [Play Integrity — Verdicts](https://developer.android.com/google/play/integrity/verdicts)
- [Play Integrity — Server-side verification](https://developer.android.com/google/play/integrity/standard#decrypt_and_verify)
