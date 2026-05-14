# 02 — O Vault do Android

> "Criptografia software-only de credenciais é só ofuscação com passos extras. A TEE
> transforma teatro em perímetro." — *Fortress field notes*

**TL;DR** — O par de tokens da sessão (access + refresh) é encriptado at rest com uma chave
**AES-256-GCM** que vive dentro do Android Keystore — idealmente lastreada em StrongBox. Os
bytes da chave nunca deixam o secure element. Mesmo com root, `adb backup`, ou uma imagem
forense, um atacante pega ciphertext opaco. Este arquivo passa pelo threat model, pela spec da
chave, pelo trade-off StrongBox-vs-TEE, e por como a mesma chave morre limpa quando o usuário
limpa biometria.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Tornar a cópia at rest dos tokens irrecuperável sem a TEE | Tirar os tokens do dispositivo por qualquer meio |
| **Ideia central** | A TEE embrulha a chave em hardware; userspace só orquestra encrypt/decrypt | Se eu consigo subir outro processo e chamar o mesmo alias do Keystore, o SO faz o trabalho por mim |
| **Pior falha** | Armazenar tokens em `SharedPreferences` plain | Comprometimento do sandbox do app me deixa chamar seu `Cipher` a partir de processo hostil sob seu UID |

---

## 🛡️ Defensor — "Trato o disco como adversário"

### Onde os tokens vivem neste repositório

[`TokenStore`](../../app/src/main/java/com/umain/fortress/security/TokenStore.kt) é a única
classe que toca no blob encriptado:

```
JSON Session plaintext  ──►  AES-256-GCM (alias do Keystore "fortress.vault.tokens")  ──►  VaultBlob(iv, ct)
                                                                                              │
                                                                                              └──►  Base64 no DataStore
```

DataStore é tratado como um arquivo glorificado — opaco sem a chave. Nada no app lê ou escreve
tokens de sessão por outro caminho.

### A spec da chave, linha a linha

[`KeystoreVault`](../../app/src/main/java/com/umain/fortress/security/KeystoreVault.kt)
constrói a chave com esta spec:

```kotlin
KeyGenParameterSpec.Builder(
    alias,
    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
)
    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
    .setKeySize(256)
    .setRandomizedEncryptionRequired(true)
    .setIsStrongBoxBacked(true)   // best-effort
```

| Flag | Por quê |
|---|---|
| `PURPOSE_ENCRYPT or PURPOSE_DECRYPT` | Esta chave não pode assinar, derivar outras chaves, ou ser exportada. |
| `BLOCK_MODE_GCM` | Criptografia autenticada — adulteração do ciphertext falha na descriptografia (vs CBC, onde padding-oracle é coisa real). |
| `ENCRYPTION_PADDING_NONE` | GCM não precisa de padding; se você setar um, Keystore rejeita a chave. |
| `setKeySize(256)` | Crypto simétrica é barata; pegue o maior dos dois tamanhos padrão. |
| `setRandomizedEncryptionRequired(true)` | Força o Keystore a recusar IVs estáticos. O IV do GCM é gerado pelo provider, então isso é cinto + suspensório. |
| `setIsStrongBoxBacked(true)` | Pede para a chave viver num secure element discreto (Titan M / equivalente). Faz fallback silencioso em dispositivos mais antigos via o try/catch em [`KeystoreVault`](../../app/src/main/java/com/umain/fortress/security/KeystoreVault.kt). |

O que esta chave **não** é:

- Não é biometric-gated — forçaria uma impressão digital em toda leitura do access token, o
  que quebraria a camada de rede. Biometric gating é reservado para chaves de
  **autorização-de-ação** (veja [`BiometricKeyStore`](../../app/src/main/java/com/umain/fortress/security/BiometricKeyStore.kt)
  e [07-biometric-hardening.md](07-biometric-hardening.md)).
- Não exige user-authentication pela mesma razão.

### TEE vs StrongBox

| | TEE (TrustZone) | StrongBox (Titan M / Pixel etc.) |
|---|---|---|
| Implementação | Um ambiente de execução separado, confiável, dentro do SoC principal | Um secure element fisicamente discreto, no próprio barramento de hardware |
| Threat model | Kernel Linux comprometido não consegue extrair a chave | TEE comprometida (ex: via exploits no nível do SoC) não consegue extrair a chave |
| Performance | Rápido — silício compartilhado | Mais lento — IPC pelo barramento de hardware |
| Disponibilidade | Efetivamente universal em dispositivos modernos | Pixel 3+, Samsung S20+, alguns outros |

Este repositório pede StrongBox e cai para TEE silenciosamente. Em produção você adicionaria
telemetria para saber quais dispositivos caíram — o perfil de ameaça é diferente e a risk
engine pode querer pesar isso ([09-zero-trust.md](09-zero-trust.md)).

### Ciclo de vida da chave

A chave é criada lazy no primeiro encrypt. Ela é destruída por:

- `KeystoreVault.invalidate(alias)` — explícito, chamado no logout para tornar ciphertext
  remanescente irrecuperável.
- Limpar dados do app / desinstalar — o SO recolhe a chave.
- Factory reset.

O que **não** invalida:

- Adicionar ou remover impressões digitais (intencional — chaves at rest devem sobreviver a
  re-enrolment biométrico). Chaves de autorização-de-ação usam
  `setInvalidatedByBiometricEnrollment(true)` em vez disso.

### Por que IV ao lado do ciphertext está OK

IVs do GCM só precisam ser **únicos** por par (chave, IV) — não secretos. Armazenamos eles
base64 codificado junto com o ciphertext: `<iv-b64>.<ciphertext-b64>`. O provider gera o IV;
nunca reusamos um. Se *reusássemos*, um atacante observando dois ciphertexts conseguiria
cancelar via XOR e recuperar diferenças de plaintext — mas o cipher fornecido pelo SO é o
único caminho que inicializa um IV fresco por chamada.

### Hardening de manifest que sustenta isso

[`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml):

```xml
android:allowBackup="false"
android:dataExtractionRules="@xml/data_extraction_rules"
android:networkSecurityConfig="@xml/network_security_config"
```

- `allowBackup="false"` — `adb backup -shared` não consegue exfiltrar `data/data/.../files`,
  incluindo o blob do DataStore.
- `dataExtractionRules` — opt-out de cópias de device-transfer para paths sensíveis.
- `networkSecurityConfig` — cleartext desabilitado fora do dev loopback. Veja
  [08-network-warfare.md](08-network-warfare.md).

---

## ⚔️ Atacante — "Eu nunca vejo a chave, mas talvez não precise"

### Bypass 1 — Pegar os bytes da chave

Resposta padrão para chaves desprotegidas, falha aqui. Keystore não exporta nada para chaves
simétricas criadas com `PURPOSE_ENCRYPT/DECRYPT`. `keystore-encryption-key` de um store não
hardware-backed me deixaria copiar bytes; o hardware-backed não.

**Counter:** mantenha `setIsStrongBoxBacked` (ou aceite fallback para TEE) e nunca setar
`setUserAuthenticationRequired(false)` + `setUserPresenceRequired(false)` enquanto também
expõe a chave para export. Chaves simétricas do Keystore não têm caminho via
`KeyStore.Entry.getKey()` que devolva bytes raw.

### Bypass 2 — Chamar seu Cipher de outro processo sob seu UID

Se eu pego execução de código dentro do sandbox do app (SDK malicioso puxado pelo build,
Frida acoplado no processo, código dinamicamente carregado), o SO me deixa felizmente chamar
`Cipher.getInstance(...)` e descriptografar o blob com o seu alias do Keystore. Sem precisar
de chave raw.

**Counter:**
- Execução de código dentro do processo é a barra alta. Reduza superfície de ataque:
  - Higiene de SDK (sem blobs opacos de vendor random).
  - `setReleaseDebuggable` off em release.
  - Detecção de hooking em runtime — veja [14-rasp-strategies.md](14-rasp-strategies.md).
  - Veredito de Play Integrity exigido para operações sensíveis — veja [05-play-integrity.md](05-play-integrity.md).
- Para operações ultra-sensíveis, sobreponha uma chave biometric-gated para que um processo
  hookado ainda não consiga emitir assinaturas sem mostrar o prompt — veja
  [07-biometric-hardening.md](07-biometric-hardening.md).

### Bypass 3 — `adb backup` do dispositivo

Objetivo: puxar `data/data/com.umain.fortress/files/datastore/fortress_tokens.preferences_pb`
e parsear. Sem a chave do Keystore é ciphertext.

**Counter:**
- `allowBackup="false"` — e verifique que vale: o manifest mergeado é o que o SO vê, não seu
  source.
- Mesmo com o backup, o blob resultante é inútil sem a chave on-device.

### Bypass 4 — Imagem forense após factory reset / root

Igual ao anterior — sem a chave da TEE, o blob é opaco. Em factory reset a chave é destruída;
num root-after-the-fact a chave nunca saiu da TEE.

### Bypass 5 — Timing side-channel

Operações repetidas de GCM têm características de tempo. Ataques práticos contra chaves AES
lastradas em hardware exigem acesso físico e equipamento de laboratório bem além do threat
model de um app financeiro em telefones de consumo.

**Counter:** fora de escopo para o threat model deste app. Se você está defendendo contra um
estado-nação com acesso a laboratório, você tem uma conversa maior que esse doc.

### Bypass 6 — Substituir o alias da chave

Se eu pego acesso de escrita ao Keystore (ex: via um backup do Smart Lock mal configurado que
importou minha chave), posso substituir seu alias. Seu decrypt agora produz lixo; você falha
ruidosamente e força re-login. Não é roubo de token, mas é vetor de denial-of-service.

**Counter:**
- Substituição de chave no Keystore exige o mesmo UID — praticamente comprometimento no
  mesmo processo — e colisão de alias. Altamente improvável em apps publicados.
- Detecte: em falha de decrypt, superfície como `SignedOut` limpo e re-emita. Não tente
  "recuperar" os dados velhos; recusar é o comportamento correto.

---

## Cross-reference

- **Que tokens vão para dentro daqui** → [01-stateless-auth.md](01-stateless-auth.md)
- **Uma spec de chave diferente para autorização de ação** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Backups, manifest, e exposição lateral** → [16-content-providers.md](16-content-providers.md)

## Referências

- [Part 2 — The Android Vault: Hardware-Backed Token Storage](https://blog.stackademic.com/part-2-the-android-vault-hardware-backed-token-storage-a8beec566d81)
- [Android Developers — Use the Android Keystore](https://developer.android.com/privacy-and-security/keystore)
- [AOSP — Hardware-backed Keystore](https://source.android.com/docs/security/features/keystore)
- [Android Developers — StrongBox](https://developer.android.com/privacy-and-security/keystore#StrongBox)
