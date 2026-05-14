# 04 — Para além das senhas: a revolução das passkeys

> "Phishing morre no dia em que sua credencial se recusa a ser apresentada para um domínio que
> ela não reconhece. Senhas nunca recusaram nada." — *Fortress field notes*

**TL;DR** — Uma **passkey** é um par de chave pública/privada bound ao hardware, criado via
WebAuthn / FIDO2. A metade privada vive na TEE (ou num password manager sincronizado); a
metade pública vive no servidor. Autenticação é um challenge assinado — não há secret
compartilhada na rede e não há senha para phishing. O Android expõe tudo isso via
`androidx.credentials`. Este arquivo passa pelo fluxo de registro + sign-in que o Fortress
está montando, pelo que o servidor precisa fazer, e pelo toolkit de atacante mirando as
bordas do modelo.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Substituir senhas por credenciais bound à origem, resistentes a phishing | Enganar o usuário a autenticar num site que ele não percebe ser meu |
| **Ideia central** | O browser/SO se recusa a assinar para um `rp.id` diferente daquele a que a credencial foi atrelada | O fluxo de recuperação, o fluxo de QR cross-device, e o fallback de senha são alvos mais moles |
| **Pior falha** | Tratar passkey sign-in como "mais um método de auth" em vez de como substituto | Deixar um loop de recuperação de senha que pula a passkey inteira |

---

## 🛡️ Defensor — "Eu nunca vejo uma senha, nem o atacante"

### Modelo mental em 60 segundos

```
Enrolment:
  client → server: "quero criar uma passkey para alice@fortress.bank"
  server → client: challenge + rp.id="fortress.bank" + user info + pubKeyCredParams
  client (browser/SO): gera um par P-256 dentro da TEE
  client → server: { credentialId, publicKey, attestation, signature(challenge) }
  server: armazena { credentialId, publicKey, signCounter=0, userId, aaguid }

Sign-in:
  client → server: "quero entrar como alice@fortress.bank"
  server → client: challenge + rp.id + allowedCredentials[credentialId]
  client (browser/SO): user verification (biometric/PIN) → sign(challenge)
  client → server: { credentialId, signature, clientDataJSON, authenticatorData }
  server: lookup credential by credentialId → verifica assinatura com a publicKey armazenada → checa o counter
```

Duas coisas que o usuário digita: zero senhas, zero códigos.

### Por que isso é resistente a phishing

O `clientDataJSON` assinado inclui a **`origin`** que o browser viu quando o usuário
autenticou. O servidor verifica `origin === expected_origin`. Se um site de phishing em
`frortress.bank` (com dois r's) tenta relayar um challenge que pescou do servidor real, o
browser assina `origin = "frortress.bank"`, o servidor real vê o mismatch e rejeita. O usuário
não conseguiria, digitando ou clicando em qualquer lugar, autenticar no site errado — porque
o *browser*, não o usuário, recusa.

Essa é a propriedade mais importante do WebAuthn. Todo o resto é elenco coadjuvante.

### Onde o Fortress vive na spec

| Peça da spec | Valor Fortress | Por quê |
|---|---|---|
| `rp.id` | `fortress.bank` | O sufixo DNS contra o qual o app autentica |
| `rp.name` | `Fortress` | Mostrado no picker do SO |
| `user.id` | opaco, ≤64 bytes, estável por usuário | Spec proíbe usar o email |
| `user.name` | `alice@fortress.bank` | Mostrado no picker do SO |
| `pubKeyCredParams` | `[{ type: "public-key", alg: -7 }]` (ES256) | Curva moderna; suporte amplo |
| `attestation` | `"none"` para consumer; `"direct"` só para enterprise hardware-attested | Maioria dos fluxos consumer não precisa de attestation, e "direct" expõe AAGUID com preocupações de privacidade |
| `authenticatorSelection.userVerification` | `"required"` para sign-in, `"preferred"` para enrol | Verificar biometric/PIN é a diferença entre "eu tenho sua chave" e "eu tenho você" |
| `authenticatorSelection.residentKey` | `"required"` | Credenciais discoverable habilitam fluxos usernameless |
| `authenticatorSelection.authenticatorAttachment` | `"platform"` para device-bound; omita para cross-device | O SO lista as duas opções |

### Cliente Android — `androidx.credentials`

O Android moderno usa **Credential Manager** (`androidx.credentials`) para falar com o
authenticator da plataforma. Três chamadas cobrem toda a superfície:

```kotlin
// Enrolment (depois do usuário já estar autenticado por senha / passkey / link).
val rpJson = """{
    "challenge": "$base64UrlChallenge",
    "rp": { "id": "fortress.bank", "name": "Fortress" },
    "user": {
        "id": "$base64UrlUserId",
        "name": "alice@fortress.bank",
        "displayName": "Alice Hartman"
    },
    "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
    "authenticatorSelection": {
        "authenticatorAttachment": "platform",
        "residentKey": "required",
        "userVerification": "preferred"
    },
    "attestation": "none",
    "timeout": 60000
}"""

val request = CreatePublicKeyCredentialRequest(requestJson = rpJson)
val response = CredentialManager.create(context).createCredential(activity, request)
val publicKeyCred = response as CreatePublicKeyCredentialResponse
val registrationJson = publicKeyCred.registrationResponseJson
// POST para o servidor: /auth/passkey/register/finish com registrationJson
```

```kotlin
// Sign-in — servidor já emitiu um challenge e a lista de allowed credentials.
val signInJson = """{
    "challenge": "$base64UrlChallenge",
    "rpId": "fortress.bank",
    "allowCredentials": ${allowList.toJsonArray()},
    "userVerification": "required",
    "timeout": 60000
}"""

val request = GetCredentialRequest(
    credentialOptions = listOf(GetPublicKeyCredentialOption(signInJson))
)
val result = CredentialManager.create(context).getCredential(activity, request)
val cred = result.credential as PublicKeyCredential
val assertionJson = cred.authenticationResponseJson
// POST para o servidor: /auth/passkey/signin/finish com assertionJson
```

O SO mostra o próprio picker — o mesmo que o Chrome usa na web. Passkeys sincronizadas
(Google Password Manager) e passkeys device-bound (TEE) aparecem juntas; o usuário escolhe.

### Responsabilidades server-side

[`backend/src/routes/passkey.ts`](../../backend/src/routes/passkey.ts) (staged para o próximo
pass) precisa:

1. **Emitir um challenge random por tentativa** — 32 bytes random, opaco, bound ao usuário
   (ou anônimo para sign-in por discovery). TTL curto (60 s). Uso único.
2. **Verificar origin + RP-ID** — rejeitar qualquer coisa em que `clientDataJSON.origin` não
   bate com o esperado, ou em que `clientDataJSON.challenge` não é igual ao emitido, ou em que
   `authenticatorData.rpIdHash` não é igual a `SHA-256(rp.id)`.
3. **Verificar a assinatura** — usando a public key armazenada do enrolment.
4. **Checagens do sign-counter** — `authenticatorData.signCount` deve ser ≥ ao valor
   anteriormente armazenado. Se regredir, a credencial pode estar clonada. (Passkeys
   sincronizadas frequentemente sempre reportam 0, então seja permissivo nessa direção — veja
   abaixo.)
5. **Inspeção de AAGUID** — `attestationObject.authData.attestedCredentialData.aaguid`
   identifica o modelo do authenticator. Usado para lookups no FIDO Metadata Service em
   enterprise. Fluxos consumer tipicamente ignoram.

### Passkeys device-bound vs sincronizadas

| | Device-bound (TEE) | Sincronizadas (Google Password Manager / iCloud Keychain) |
|---|---|---|
| Storage | TEE / StrongBox no dispositivo | Encriptado na conta cloud do usuário |
| Resistência a phishing | Idêntica | Idêntica |
| Modelo de roubo | Pega o dispositivo + biometria → acesso | Comprometa a conta cloud → acesso em um novo dispositivo |
| Sign counter | Incrementa por uso, útil pra detecção de clone | Frequentemente sempre 0 (cloud não confia em incrementos por device) |
| Recuperação | Dispositivo perdido → enrol de novo a partir de outro | Sync de cloud continua entre devices |
| Adequado para | Confirmação de alto valor, step-up | Sign-in do dia a dia |

Estratégia Fortress:
- **Sign-in do dia a dia** aceita qualquer um dos dois tipos.
- **Step-up authentication** (transferências, reveal de IBAN) exige uma credencial
  device-bound ou um challenge assinado pela chave TEE-bound de autorização-de-ação — veja
  [07-biometric-hardening.md](07-biometric-hardening.md). Credenciais cloud-sync sozinhas não
  autorizam movimentação de dinheiro.

### Substituindo o endpoint de senha

O Fortress vai manter `POST /auth/login` (email + senha) como caminho **bootstrap**: é como
um usuário entra a partir de um device novinho. Depois disso, o dispositivo faz enrol de uma
passkey, e a senha vira vestígio — usável só via um fluxo explícito de recuperação que exige
re-verificação.

O objetivo eventual é deletar a coluna de senha por completo. Um usuário que nunca tipou uma
senha no seu serviço nunca pode ter a senha dele phishada de você.

### Logout, revogação, multi-device

| Evento | Ação do servidor |
|---|---|
| Usuário desloga | Revogar sessão (refresh token); deixar passkey viva |
| Usuário remove uma passkey de um dispositivo | Servidor deleta o record da credencial; futuras assertions com aquele `credentialId` falham |
| Usuário perde o dispositivo | Fluxo de recuperação → re-verifica identidade → invalida todas as credenciais bound a esse dispositivo → enrol uma nova em outro lugar |
| Atividade suspeita | Opcionalmente invalida todas as credenciais, força re-enrol completo |

### Tamanho da allow-list

`allowCredentials` é enviado **toda** sign-in. Se um usuário tem 17 passkeys entre devices, o
SO mostra 17 opções. Cap: armazena credenciais por recência, mostra top 5–10 mais recentes,
oferece um hook "mais opções" para a long tail.

Para fluxo usernameless / discoverable: omita `allowCredentials` inteiro, deixa o SO mostrar
todas as credenciais que ele tem para `rp.id`.

---

## ⚔️ Atacante — "Phishing morreu, vida longa a tudo o mais"

### Bypass 1 — Só phisha o fallback de senha

O fluxo de passkey é unphishable. O fluxo de recuperação de senha provavelmente não. Se o
usuário pode dizer "esqueci minha passkey" e re-enrolar via um link de email, eu phisho o
link de email.

**Counter:**
- Trate enrolment de passkey de um device novo com a mesma suspeita de uma mudança de senha.
- Exija que a recuperação envolva um segundo fator que não seja email (passkey existente em
  outro device; verificação de identidade via upload de documento; KYC presencial para
  contas de valor alto).
- Throttle recuperação agressivamente; alerte em recuperações rápidas sequenciais.

### Bypass 2 — Configuração ruim de RP-ID

Se seu `rp.id` é `*.fortress.bank` e você aceita assertions de `attacker.fortress.bank`
(subdomain takeover, subdomínio controlado por cliente via uma config de CDN), eu hospedo uma
página maliciosa lá e colho assertions de passkey.

**Counter:**
- Strict `rp.id` (`fortress.bank` exato, sem wildcards).
- Higiene de DNS; subdomain takeover é um problema separado mas relacionado e contínuo.
- Allow-list de valores aceitos de `origin`, não só a checagem de sufixo do `rp.id`.

### Bypass 3 — Origin via interceptação HTTPS local

Se eu consigo fazer o usuário instalar uma CA do sistema (device rootado, comprometimento de
MDM) e proxiar o dispositivo por mim, *e* o app está rodando sem cert pinning, eu adoraria
injetar um RP-ID malicioso na resposta de challenge. Exceto: o *browser/SO* aplica `rp.id`,
não o código do seu app. Então mesmo com controle total da rede, o SO se recusa a assinar
para o RP-ID errado.

**Counter:** a spec é o counter. Não subverta. Cert pinning ([08-network-warfare.md](08-network-warfare.md))
fecha o canal de rede mas o fluxo de assertion tem o próprio guard.

### Bypass 4 — Regressão do sign-counter como pista (e como ignorar)

Se a mesma credential ID dá assert com counter 47, depois 48, depois 12, a credencial foi
clonada: meu dispositivo tem counter 47 + 1 = 12 (eu comecei do zero) enquanto o do usuário
tem 49. O defender deveria detectar isso e invalidar a credencial.

**Minha jogada:** mirar usuários com passkeys sincronizadas (cloud), onde o counter é sempre
0 e regressão é invisível.

**Counter:**
- Trate credenciais counter=0-sempre como "sincronizadas" — relaxe a checagem de regressão,
  suba a barra em outros sinais (geolocalização, fingerprint do device, behaviour analytics).
- Para contas de alto valor, exija credenciais **device-bound** para step-up, não as
  sincronizadas.

### Bypass 5 — Phishing por QR cross-device (CTAP 2.2 hybrid)

WebAuthn permite o usuário scannear um QR com o telefone para autenticar num browser de
laptop em que não tem passkey. Se eu enganar o usuário a scannear um QR na *minha* página de
phishing, o browser no telefone assina uma assertion bound à minha origin de phishing — e o
telefone do usuário não tem ideia que isso está errado, porque a origin mostrada é a que está
no challenge codificado no QR.

Espera — a spec acerta isso também: o authenticator do telefone assina a *origin que a
página geradora do QR apresenta para o próprio user agent*. Se o QR foi gerado por
`attacker.com`, o telefone assina `origin=attacker.com`. O servidor real do Fortress rejeita.

**Minha contra-jogada:** engenharia social. Eu mostro uma UI convincente no meu site de
phishing dizendo "logamos você com sucesso". O usuário acha que entrou no Fortress. Eu não
capturei as credenciais dele mas tenho a atenção dele e ele está confuso.

**Counter:** confirmação out-of-band (push pro telefone do usuário mostrando "Você acabou de
logar em $LOCAL em $DISPOSITIVO?"). Usuário clica "não" → invalida a sessão.

### Bypass 6 — Roubar o blob da credencial

Passkeys do dia a dia vivem na TEE; não tem como roubar. Passkeys *sincronizadas* estão na
conta cloud. Comprometa a conta cloud → restaure num novo device sob meu controle → eu sou
o usuário.

**Counter:**
- A conta cloud é agora a conta mais importante do usuário. A segurança do provedor cloud é
  o teto da sua.
- Para step-up, exija credenciais device-bound (acima).
- Enrolment de um novo device a frio — mesmo com creds cloud válidas — deve exigir o segundo
  fator do fluxo de recuperação.

### Bypass 7 — Bypass de filtro por AAGUID

Se sua política enterprise só permite credenciais de authenticators aprovados (allow-list por
AAGUID), e a política é aplicada *server-side no enrolment*, eu crio um authenticator
malicioso que mente sobre o AAGUID dele. Sem attestation você não consegue saber.

**Counter:**
- Para enterprise / alta garantia: exija `attestation: "direct"` e valide contra o FIDO
  Metadata Service. Aí eu não consigo mentir sobre o AAGUID sem um cert.
- Fluxos consumer não precisam disso; inspeção de AAGUID é preocupação de privacidade em
  escala.

### Bypass 8 — Enumeração de credential ID

Se seu endpoint de sign-in se comporta diferente para "credentialId não encontrado" vs
"credentialId encontrado mas falhou verificação", eu enumero contas de usuário.

**Counter:**
- Forma de resposta constant-time (sempre parece "verificando", depois devolve um 401
  genérico em qualquer falha).
- Para fluxo usernameless, o servidor nem sabe quem está logando até a assertion verificar —
  sem superfície de enumeração.

### Bypass 9 — Abuso de resident key em dispositivos compartilhados

Credenciais discoverable (resident keys) num dispositivo compartilhado → o picker do SO
mostra minha passkey sem eu digitar nada. Um colega de trabalho pegando meu telefone
desbloqueado tem acesso.

**Counter:**
- `userVerification: "required"` — o SO pede biometric/PIN antes de assinar. O colega tem meu
  telefone mas não minha digital.
- Para kiosks compartilhados do escritório: não enrole passkeys; use o fluxo QR cross-device
  que exige o telefone pessoal do usuário.

---

## Cross-reference

- **A TEE que segura a private key device-bound** → [02-hardware-vault.md](02-hardware-vault.md)
- **Chave TEE de autorização-de-ação separada da passkey de sign-in** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Por que isso ainda precisa de cert pinning** → [08-network-warfare.md](08-network-warfare.md)
- **Como o claim `cnf` device-binding fica nos tokens** → [09-zero-trust.md](09-zero-trust.md)

## Referências

- [Part 4 — Beyond Passwords: The Passkey Revolution](https://blog.stackademic.com/part-4-beyond-passwords-the-passkey-revolution-793ea2f291fe)
- [WebAuthn — Level 3 spec](https://www.w3.org/TR/webauthn-3/)
- [FIDO2 — Client to Authenticator Protocol (CTAP)](https://fidoalliance.org/specs/fido-v2.2-rd-20230321/fido-client-to-authenticator-protocol-v2.2-rd-20230321.html)
- [Android Developers — Credential Manager](https://developer.android.com/training/sign-in/credential-manager)
- [passkeys.dev](https://passkeys.dev/)
