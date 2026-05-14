# 11 — Detecção de root morreu: o que funciona de verdade em 2026

> "Checar `su` no `$PATH` é o equivalente de segurança a trancar a porta da frente mas
> deixar a chave embaixo do tapete — só que o tapete já foi movido cem vezes e ninguém te
> avisou." — *Fortress field notes*

**TL;DR** — Detecção de root em userspace (scan de binário `su`, checagem de paths RW, lookup
de busybox, probes em package manager) é **derrotada por padrão** em qualquer device com
Magisk + os módulos certos. O que ainda funciona em 2026: **Play Integrity** (para o veredito
do device), **KeyAttestation** (para geração de chave hardware-attested), e **blending por
camadas** (RASP + Play Integrity + risk engine). Este arquivo passa por por que os truques
velhos morreram, o que substituiu, e o inventário do atacante.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Pegar um veredito hardware-attested que sobrevive aos truques de userspace | Esconder root de qualquer coisa que o app consiga observar |
| **Ideia central** | Mover o portão de "o que o app vê" para "o que a TEE / Google atesta" | Se o portão é qualquer coisa que o app vê, eu mostro o que ele quer ver |
| **Pior falha** | Shipar qualquer boolean "está rootado?" em que o app confia | Checagem do defender que fail open em erro de parse |

---

## 🛡️ Defensor — "Eu terceirizo a pergunta para o silício"

### Por que as checagens clássicas morreram

Bibliotecas velhas de root detection (`RootBeer`, `SafetyNetHelper`, hand-rolled)
tipicamente faziam algumas de:

```kotlin
// 1. Procura binários su
File("/system/bin/su").exists()
File("/system/xbin/su").exists()
File("/sbin/su").exists()

// 2. Procura apps de root
packageManager.getPackageInfo("com.topjohnwu.magisk", 0)

// 3. Procura Xposed / hooks
File("/system/framework/XposedBridge.jar").exists()

// 4. Checa selinux mode
Os.uname().release.contains("magisk", ignoreCase = true)

// 5. Tenta executar su
Runtime.getRuntime().exec("su").exitValue() != 0
```

Cada um desses é mitigado por **Magisk DenyList** (desde Magisk 24.0+) ou **módulos Zygisk**:

| Checagem | Como DenyList mitiga |
|---|---|
| Existência de arquivo | Magisk monta uma view limpa do filesystem para o app alvo — os binários su não existem *da perspectiva do app* |
| Lookup de pacote | Hide-from-target hookeia `PackageManager.getPackageInfo` para jogar `NameNotFoundException` |
| Leituras de property | `getprop ro.boot.verifiedbootstate` devolve `green` em vez de `orange` |
| `Runtime.exec("su")` | O shim su é gateado pela DenyList; do UID do app não está disponível |

Essas checagens nem precisam de um atacante sofisticado. Módulos Magisk gratuitos
("Universal Safetynet Fix", "Play Integrity Fix") chaveiam os switches certos e sua detecção
some.

### O que ainda funciona: KeyAttestation hardware-attested

Quando você gera uma chave no Android Keystore, você pode pedir um **cert de attestation** que
vem do hardware root of trust do Google. A cadeia de cert codifica:

- `attestationVersion` (qual versão do KeyMaster do Android)
- `attestationSecurityLevel` — `Software`, `TrustedEnvironment`, ou `StrongBox`
- `verifiedBootKey` — fingerprint criptográfico do bootloader
- `verifiedBootState` — `Verified`, `SelfSigned`, `Unverified`, `Failed`
- `verifiedBootHash`

A cadeia de cert termina numa CA Google-controlled. **Um device com bootloader desbloqueado
não consegue emitir uma cadeia de attestation que afirme `Verified` + o boot hash original**
— o RoT de hardware recusa.

No Fortress, lemos isso durante enrolment de device-binding:

```kotlin
fun deviceAttestation(alias: String): AttestationVerdict {
    val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    val certChain = ks.getCertificateChain(alias) ?: return AttestationVerdict.Unknown

    val factory = CertificateFactory.getInstance("X.509")
    val attestation = factory.generateCertificate(
        certChain[0].encoded.inputStream()
    ) as X509Certificate

    // A extensão de attestation é OID 1.3.6.1.4.1.11129.2.1.17.
    // Parseie a ASN.1 para extrair verifiedBootState, attestationSecurityLevel, etc.
    val ext = attestation.getExtensionValue("1.3.6.1.4.1.11129.2.1.17")
        ?: return AttestationVerdict.Unknown
    // ...parseia ASN.1, valida cadeia contra root pinhado do Google...
}
```

O **servidor** também recebe a cadeia de cert (ou um hash dela) e valida contra as chaves
root publicadas pelo Google. Fazer isso *só* no cliente é sem sentido — veja seção do atacante.

Um atalho via `KeyInfo` (sem parsing de cadeia de cert) também está disponível para um sinal
mais grosso:

```kotlin
val factory = KeyFactory.getInstance(privateKey.algorithm, "AndroidKeyStore")
val info = factory.getKeySpec(privateKey, KeyInfo::class.java)
when (info.securityLevel) {
    KeyProperties.SECURITY_LEVEL_STRONGBOX  -> { /* trust alto */ }
    KeyProperties.SECURITY_LEVEL_TRUSTED_ENVIRONMENT -> { /* trust normal */ }
    KeyProperties.SECURITY_LEVEL_SOFTWARE   -> { /* recusa */ }
    KeyProperties.SECURITY_LEVEL_UNKNOWN    -> { /* recusa */ }
}
```

Se um Keystore alega `TRUSTED_ENVIRONMENT` mas o bootloader está desbloqueado, a validação
de cadeia no servidor pega a mentira. A query local de `securityLevel` serve para fast-fail
de UX; a checagem server-side da cadeia é a autoridade.

### O que ainda funciona: Play Integrity

Coberto em detalhe em [05-play-integrity.md](05-play-integrity.md). Resumo neste contexto:

- **`MEETS_STRONG_INTEGRITY`** → boot hardware-attested, sem root conhecido
- **`MEETS_DEVICE_INTEGRITY`** → bootloader locked, Android padrão
- **`MEETS_BASIC_INTEGRITY`** só → usuário rootou o device (bootloader desbloqueado, ou root
  systemless que *Google flaggou*), ou roda ROM custom
- **`MEETS_VIRTUAL_INTEGRITY`** → emulador

Combine com KeyAttestation: `MEETS_STRONG_INTEGRITY` + nível de security `StrongBox` é
mais ou menos "não modificado, hardware-rooted, vault de hardware completo". Esse é o piso
para ações de alto valor.

### O que ainda funciona: heurísticas RASP (em camadas com o acima)

Checagens em runtime que o *atacante* pode hookar, *se* ele já injetou algo no processo. Útil
como tripwire — se eles disparam, você assume que o processo está comprometido:

| Tripwire | Detecta |
|---|---|
| `Debug.isDebuggerConnected()` | Debugger JDWP ativo |
| `/proc/self/status` → `TracerPid` ≠ 0 | ptrace acoplado (Frida, etc.) |
| `/proc/self/maps` contém `frida-agent.so`, `xposed`, `riru` | Injeção de biblioteca |
| `Build.TAGS` contém `test-keys` | ROM custom |
| `Build.FINGERPRINT.startsWith("generic")` | Emulador (às vezes — emuladores modernos mentem) |
| `pm list packages` contém apps Magisk conhecidos (best-effort) | Root manager instalado pelo usuário |

Nenhum desses é confiável sozinho. Como **última linha** antes de uma operação sensível —
rodando e combinando o resultado com Play Integrity + KeyAttestation dá um quadro mais
honesto que qualquer sinal sozinho.

### O veredito blended

```
verdict = combine(
  playIntegrity = standardRequest(),     // visão do Google
  keyAttestation = attestationChain(),   // visão do hardware (cadeia CA do Google)
  raspSignals    = runtimeProbes(),      // visão interna do processo
  riskEngine     = serverDecide(...)     // agregação server-side
)
```

Mapeado para UX:

- Todos os quatro "limpos" → funcionalidade total
- `keyAttestation` diz StrongBox mas `playIntegrity` é `BASIC` → confie no hardware,
  suspeite que Play Integrity está sendo suprimido; degrade para read-only
- `playIntegrity` diz `STRONG` mas RASP detecta processo hookado → confie nos seus olhos;
  recuse
- Qualquer sinal fail hard → recuse todas as ops sensíveis, mostre uma mensagem clara

### A implementação do Fortress, hoje

[`IntegrityCheck`](../../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt) é
um stub devolvendo `Trusted`. A wire-up para Play Integrity e extração de cadeia de
KeyAttestation está staged para o próximo pass. A arquitetura está pronta — o lado consumer
([`SecurityChip`](../../app/src/main/java/com/umain/fortress/ui/components/SecurityChip.kt)
e [`DashboardViewModel`](../../app/src/main/java/com/umain/fortress/ui/screens/dashboard/DashboardViewModel.kt))
já lida com o veredito de três estados. Plugar sinais reais substitui o stub sem mexer no
resto do app.

---

## ⚔️ Atacante — "Eu escondo o que o app pode ver, e leio o vento"

### Bypass 1 — Magisk DenyList + Play Integrity Fix

Kit padrão, gratuito, leva cinco minutos. Instala Magisk, habilita Zygisk, instala módulo Play
Integrity Fix, adiciona Fortress à DenyList. Toda checagem de root em userspace passa. Play
Integrity também pode devolver `MEETS_DEVICE_INTEGRITY` se o módulo de fix estiver atual —
Google patcha em ondas e os módulos de fix shipam updates na mesma semana.

**Counter:**
- Exija `MEETS_STRONG_INTEGRITY` para ops de alto valor. Strong integrity exige bootloader
  locked e verified-boot `green` — Magisk não consegue bypass disso porque o veredito é
  assinado pela attestation hardware-rooted do device, não por algo que Magisk pode tocar.
- Validação server-side de cadeia de KeyAttestation. O `verifiedBootState` na extensão de
  attestation é assinado pelo root hardware do Google — não-spoofável em userspace.

### Bypass 2 — ROM custom bootloader-locked com chaves do Google vazadas

Algumas vezes na história, as chaves de assinatura do Google para famílias específicas de
device vazaram (chaves DAR do Pixel 6, etc.). Uma ROM custom assinada com as chaves vazadas
pode se apresentar como estado "Verified" de boot nesses devices, e `MEETS_DEVICE_INTEGRITY`
passa.

**Counter:**
- Google revoga as chaves vazadas. Validação server-side da cadeia checa contra os trust
  roots de KeyAttestation / JWKS atual do Google, e certs revogados são rejeitados.
- Mantenha uma denylist de famílias de device known-compromised se a timeline de revogação
  for lenta.

### Bypass 3 — Hooking de memória depois que KeyAttestation já teve sucesso

Mesmo se a cadeia de attestation no enrolment é genuína, depois que o processo roda eu posso
hookar as chamadas de API *depois* da attestation via Frida. A query local
`KeyInfo.securityLevel` devolve o que o hook disser.

**Counter:**
- Validação server-side é a verdade — checagens locais são diagnóstico.
- A private key TEE-bound ainda não pode ser exportada, então mesmo com metadata hookada eu
  não consigo assinar em outro device.
- Probes RASP detectam Frida no processo e recusam continuar.

### Bypass 4 — Rodar o app num device real, attested, sob meu controle

Comprar um Pixel 8, deixar stock, enrolar Fortress como vítima. Agora eu tenho um device
real attested. Use-o para emitir e completar fluxos de step-up em nome da sessão da vítima.

É a mesma forma do Bypass 3 de [05-play-integrity.md](05-play-integrity.md). É caro
(device real, conta real, footprint de rede real) e de alta fricção (manual ou throughput
limitado).

**Counter:**
- Device binding ([09-zero-trust.md](09-zero-trust.md)) — o device com meu bootloader real
  não é o device enrolado da vítima. Um novo binding (userId, deviceId) dispara confirmação
  OOB.
- Risk engine: um padrão "usuário previamente dormante, device novo, transferência imediata"
  é um sinal forte.

### Bypass 5 — Patchar o APK para pular a checagem inteira

Eu decompilo o APK ([12-decompiling.md](12-decompiling.md)), acho a chamada da checagem de
integridade, no-op, recompilo, re-assino com meu próprio cert, instalo.

**Counter:**
- Play Integrity vai detectar o APK modificado (`appIntegrity.appRecognitionVerdict !=
  PLAY_RECOGNIZED`) e o mismatch de digest de cert.
- Ações server-side verificam o veredito de integridade antes de ops de alto valor —
  patchar o cliente não ajuda quando o servidor é o portão.

### Bypass 6 — Race da checagem local vs checagem server

Se o app gateia a UI num veredito de integridade *local* (o stub) e o servidor não é
consultado de fato, eu só preciso derrotar a checagem local (Frida hooka o resultado). A
checagem server-side é a única que importa.

**Counter:**
- Nunca gateie operações sensíveis em integridade só do cliente. O cliente renderiza o
  veredito para *informação ao usuário*; o servidor aplica para *autorização*.

### Bypass 7 — Emulador com `MEETS_VIRTUAL_INTEGRITY`

Emuladores modernos (Genymotion com Play Services, emulador AOSP com Google APIs) passam em
virtual integrity. Se o servidor aceita isso para ops sensíveis, eu pego meu playground de
fuzzing.

**Counter:**
- `MEETS_VIRTUAL_INTEGRITY` sozinho autoriza operações read-only e fluxos de dev. Para
  transferências / IBAN reveal, exija `MEETS_DEVICE_INTEGRITY` ou mais forte.
- Uso de emulador está OK para QA — flag com banner developer-mode, telemetria distingue
  emuladores "QA interno" (IPs/projects conhecidos) de tráfego de emulador externo.

### Bypass 8 — KernelSU em emuladores Android Studio (Apple Silicon)

KernelSU traz root para emuladores AOSP em Macs M1/M2/M3/M4 sem o overhead do userspace do
Magisk. O device parece de outra forma stock para userspace.

Veja [15-emulator-rooting.md](15-emulator-rooting.md) para o procedimento completo. KernelSU
ainda falha em vereditos strong/device de Play Integrity porque o bootloader num emulador
KernelSU não é verificado — mas uma checagem rápida de root em userspace não vê nada errado.

**Counter:**
- Igual ao Bypass 7. O caminho de emulador precisa `MEETS_DEVICE_INTEGRITY` para ops
  sensíveis, que emuladores KernelSU não atingem.

---

## Cross-reference

- **A attestation do lado do Google** → [05-play-integrity.md](05-play-integrity.md)
- **Onde a cadeia de attestation é gerada e consumida** → [02-hardware-vault.md](02-hardware-vault.md), [09-zero-trust.md](09-zero-trust.md)
- **Anti-Frida / detecção interna ao processo** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **O que KernelSU faz em emuladores** → [15-emulator-rooting.md](15-emulator-rooting.md)
- **Patching de APK que faz bypass de checagens locais** → [12-decompiling.md](12-decompiling.md)
- **O panorama de bypass contra o próprio Play Integrity** → [13-play-integrity-bypass.md](13-play-integrity-bypass.md)

## Referências

- [Root Detection Is Dead: What Actually Works in Android 2026](https://levelup.gitconnected.com/root-detection-is-dead-what-actually-works-in-android-2026-b7f801e50531)
- [Android Developers — Verifying hardware-backed key pairs with KeyAttestation](https://developer.android.com/privacy-and-security/security-key-attestation)
- [Android KeyAttestation ASN.1 schema](https://developer.android.com/privacy-and-security/security-key-attestation#schema)
- [AOSP — Verified Boot](https://source.android.com/docs/security/features/verifiedboot)
- [Magisk DenyList docs](https://topjohnwu.github.io/Magisk/denylist.html)
