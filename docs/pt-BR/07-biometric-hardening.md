# 07 — Hardening biométrico e intenção do usuário

> "Uma impressão digital não é uma senha. É uma autorização que o SO entrega de volta para o
> seu processo — e uma autorização pode ser forjada, replayada, ou assinada pelo escritório
> errado." — *Fortress field notes*

**TL;DR** — Um `BiometricPrompt` retornando `SUCCESS` prova *que algum evento biométrico
aconteceu no dispositivo há pouco*. Não prova que o usuário autorizou **esta ação específica**
em **nome deste app**, a não ser que o prompt esteja ligado a um `CryptoObject` cuja chave é
gated por `setUserAuthenticationRequired(true)`. Tudo o que não seja isso é teatro.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Ligar criptograficamente a biometria do usuário a uma ação específica | Fazer o app *acreditar* que uma biometria aconteceu, sem uma |
| **Ideia central** | Se `signature.sign(challenge)` devolve bytes, uma biometria real *acabou* de acontecer dentro da StrongBox/TEE | Se o app só checa o boolean em `AuthenticationResult`, eu hookeio o boolean |
| **Pior falha** | Chamar `BiometricPrompt` sem `CryptoObject` e confiar no callback | Hook do Frida devolvendo `AuthenticationResult.SUCCESS` de userspace |

---

## 🛡️ Defensor — "Trato a biometria como uma chave, não como um interruptor de luz"

### O modelo mental

Autenticação biométrica no Android é uma **política aplicada a uma chave**, não uma checagem
do usuário. Quando você cria uma chave no Android Keystore com:

```kotlin
KeyGenParameterSpec.Builder(KEY_ALIAS, KeyProperties.PURPOSE_SIGN)
    .setAlgorithmParameterSpec(ECGenParameterSpec("secp256r1"))
    .setDigests(KeyProperties.DIGEST_SHA256)
    .setUserAuthenticationRequired(true)
    .setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
    .setInvalidatedByBiometricEnrollment(true)
    .setIsStrongBoxBacked(true) // best-effort
    .build()
```

…a **TEE/StrongBox em si** recusa executar `sign()` a menos que uma cerimônia biométrica
(biometric ceremony) tenha acontecido nos últimos *N* segundos. O SO, não o seu app, aplica o
portão. Seu app pode ser patched, hooked, ou replayed — mas a operação criptográfica não
consegue completar sem uma biometria real, recente, da classe strong.

### A dança da assinatura

Fluxo de step-up para "Transferir 1000 EUR para o IBAN XX":

```kotlin
// 1. O backend emite um challenge por ação (nonce + hash da ação + expiração)
val challenge: ByteArray = api.requestStepUpChallenge(action = TRANSFER, payload = transferDto)

// 2. Inicialize um Signature com a chave auth-gated — isso prepara a operação
//    mas AINDA NÃO assina. A TEE está esperando pela biometria.
val signature = Signature.getInstance("SHA256withECDSA").apply {
    initSign(keystore.getEntry(KEY_ALIAS, null).let { (it as PrivateKeyEntry).privateKey })
}

// 3. Embrulhe o Signature num CryptoObject e apresente o BiometricPrompt
val cryptoObject = BiometricPrompt.CryptoObject(signature)
BiometricPrompt(activity, executor, object : BiometricPrompt.AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
        // 4. O Signature dentro de result agora está AUTORIZADO para UMA chamada de sign().
        val sig = result.cryptoObject!!.signature!!
        sig.update(challenge)
        val signedChallenge = sig.sign()
        // 5. Envia o challenge assinado para o backend. Backend verifica com a public key
        //    que ele guardou no enrolment. Se a verificação passa, a ação é commitada.
        api.commitTransfer(transferDto, signedChallenge)
    }
}).authenticate(promptInfo, cryptoObject)
```

A ideia chave: **os bytes assinados são a prova**. Mesmo que toda linha de Kotlin seja
hostil, os bytes só podem existir se a TEE tiver visto uma biometria real.

### Cinco coisas inegociáveis

1. **Sempre use `CryptoObject`.** `BiometricPrompt.authenticate(promptInfo)` sem crypto object
   é um widget de UX, não uma fronteira de segurança. O callback pode ser forjado.
2. **Exija `BIOMETRIC_STRONG`.** Class 3 (Strong) é o único tier adequado para operações
   criptográficas. `BIOMETRIC_WEAK` (face em muitos devices que não passaram nos testes de
   spoof) não pode ser ligado a chaves.
3. **`setInvalidatedByBiometricEnrollment(true)`.** Quando o usuário adiciona uma nova
   impressão digital, a chave é destruída. Sem isso, um atacante que pega rapidamente o PIN do
   dispositivo pode cadastrar a própria digital e usar a chave existente para sempre.
4. **Challenges por ação.** Reusar uma única afirmação de "sou o usuário" entre várias ações =
   replayable. Ligue cada ação de risco alto a um nonce + hash de ação emitido pelo servidor.
5. **Registro server-side da public key.** O backend armazena a public key por (usuário,
   dispositivo, alias de chave). Ele verifica assinaturas. O telefone não prova nada sozinho —
   o servidor é a fonte da verdade.

### Cenários de step-up no Fortress Bank

| Ação | Portão |
|---|---|
| Abrir o app | Token de sessão em cache + verificação de integridade |
| Ver saldo | Token de sessão sozinho |
| Revelar IBAN / PAN completo | `BiometricPrompt` + `CryptoObject` (assina challenge: `reveal:<account_id>:<nonce>`) |
| Transferência ≤ €100 | `BiometricPrompt` + `CryptoObject` (assina challenge com hash do payload) |
| Transferência > €1000 ou novo destinatário | Acima + risk engine OK + idade do device trust ≥ 24h |
| Alterar configurações de segurança | Acima + reentrada de senha |

### Implementação: mapa dos arquivos neste repositório

- [`android/app/src/main/kotlin/com/umain/fortress/security/BiometricKeyStore.kt`](../../app/src/main/java/com/umain/fortress/security/BiometricKeyStore.kt) — geração/recuperação da chave com a spec auth-gated acima
- [`android/app/src/main/kotlin/com/umain/fortress/security/StepUpAuthenticator.kt`](../../app/src/main/java/com/umain/fortress/security/StepUpAuthenticator.kt) — a dança, embrulhada numa suspending function
- [`android/app/src/main/kotlin/com/umain/fortress/ui/components/StepUpSheet.kt`](../../app/src/main/java/com/umain/fortress/ui/components/StepUpSheet.kt) — bottom sheet em Compose
- [`backend/src/routes/stepup.ts`](../../backend/src/routes/stepup.ts) — emissão de challenge + verificação de assinatura

---

## ⚔️ Atacante — "Eu pulo o prompt inteiro"

### Como eu chegaria nisso

O prompt biométrico é só IPC do Android e um callback. Se você só checa o boolean do callback,
eu te entrego um boolean forjado.

### Bypass 1 — Hook do Frida no callback (sem crypto object)

Se você escreveu isto:

```kotlin
BiometricPrompt(activity, executor, object : AuthenticationCallback() {
    override fun onAuthenticationSucceeded(result: AuthenticationResult) {
        proceedWithTransfer() // ❌ confia no boolean
    }
}).authenticate(promptInfo)
```

…então num dispositivo rootado com Frida, meu script tem duas linhas:

```javascript
Java.perform(() => {
    const AC = Java.use("androidx.biometric.BiometricPrompt$AuthenticationCallback");
    AC.onAuthenticationSucceeded.implementation = function (result) {
        // Eu nunca toquei no sensor. O prompt pode nem ter aparecido.
        this.onAuthenticationSucceeded(result);
    };
});
```

**Counter:** use `CryptoObject`. Meu hook pode chamar seu callback o dia inteiro; sem os bytes
assinados pela TEE, seu servidor rejeita a requisição.

### Bypass 2 — Replay de um challenge assinado anterior

Se o servidor reusa challenges, ou não amarra o challenge ao payload da ação, eu capturo um
"aprovar transferência de €10 para o Bob" assinado e replayo como "aprovar transferência de
€10.000 para mim".

**Counter:** challenge = `HMAC(server_secret, user_id || action || canonical_payload || nonce
|| expiry)`. Servidor verifica que ele mesmo gerou *este* challenge exato para *este* payload
exato, depois queima o nonce.

### Bypass 3 — Sequestro de enrolment

Eu pego 30 segundos com o telefone desbloqueado. Adiciono minha impressão digital pelas
Configurações. Agora todas as chaves biometric-gated existentes no seu app aceitam minha
digital para sempre.

**Counter:** `setInvalidatedByBiometricEnrollment(true)` — adicionar uma nova biometria mata
a chave. Force re-enrolment da signing key do app, que exige a biometria *atual* do usuário.

### Bypass 4 — Face unlock Class 2 (Weak)

Em muitos dispositivos OEM, o face unlock é **Class 2 (Weak)**. Você não pode ligar uma chave
a ele — o Keystore vai dar throw. Se você fez fallback para "qualquer biometria serve", eu
imprimo um rosto 3D a partir de uma foto do LinkedIn e entro.

**Counter:** `setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`. Se só Weak estiver
disponível, force fallback para senha ou recuse a operação.

### Bypass 5 — Chaves baseadas em tempo via `setUserAuthenticationValidityDurationSeconds`

Se a spec da chave usa validade baseada em tempo
(`setUserAuthenticationParameters(30, AUTH_BIOMETRIC_STRONG)`), depois de uma biometria com
sucesso a chave fica utilizável por qualquer code path por 30 segundos — sem novo prompt.
Eu disparo seu fluxo biométrico legítimo (ex: "revelar saldo"), e logo em seguida disparo um
code path diferente que usa a mesma chave para uma transferência. A TEE diz "sim, biometria
foi recente" e assina.

**Counter:** use auth **por operação** (`setUserAuthenticationParameters(0, ...)`). A chave é
válida para *uma* operação, não para *N segundos de operações*.

### Bypass 6 — Snapshot do alias da chave e import em outro dispositivo

Tentei e falhei — a chave do Android Keystore é não-exportável, atrelada a hardware. Listo só
por completude; é a parte que o SO *de fato* acerta.

### O que eu procuro no seu APK

Decompila (ver [12-decompiling.md](12-decompiling.md)) e grep:

```bash
# Sinal ruim — confiança direta no callback
grep -r "onAuthenticationSucceeded" → verifica se algum branch não chega em signature.sign()

# Sinal ruim — sem CryptoObject
grep -r "BiometricPrompt" | grep -v "CryptoObject"

# Sinal ruim — janelas longas de validade
grep -r "setUserAuthenticationValidityDurationSeconds"
grep -r "setUserAuthenticationParameters" → o segundo argumento deve ser 0 para per-op
```

Se eu vir qualquer um deles, escrevo o hook do Frida e paro de ler.

---

## Cross-reference

- **Armazenamento da chave auth-gated** → [02-hardware-vault.md](02-hardware-vault.md)
- **Emissão server-side do challenge** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Risk engine que decide *quando* fazer step-up** → [09-zero-trust.md](09-zero-trust.md)
- **Contramedidas a Frida / hooking** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **O que acontece em dispositivos rootados** → [11-root-detection.md](11-root-detection.md)

## Referências

- [Part 7 — Identity Check: Biometric Hardening & User Intent](https://blog.stackademic.com/part-7-identity-check-biometric-hardening-user-intent-4eb927397e8b)
- [Android Developers — Use a cryptographic solution for sensitive information](https://developer.android.com/training/sign-in/biometric-auth#crypto)
- [AOSP — Biometric authentication classes (Strong/Weak/Convenience)](https://source.android.com/docs/security/features/biometric)
