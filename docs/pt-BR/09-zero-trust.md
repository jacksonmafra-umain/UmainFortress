# 09 — Zero trust: device binding e risk signals

> "A identidade do usuário é uma entrada. O dispositivo que faz a requisição é outra. Os
> padrões de como ele pergunta são uma terceira. Uma ação segura é a conjunção. Empilhe as
> entradas; recuse o single point of trust." — *Fortress field notes*

**TL;DR** — Bearer tokens provam *que alguém tem meu access token*. Device binding adiciona:
*e esse alguém está operando dessa TEE específica, segurando a private key cuja contraparte
pública registramos no enrolment*. Junto com risk signals (geolocalização, comportamento,
cadência de sign-in), o servidor faz uma decisão por ação: green-light, step-up, recusar.
Este arquivo passa pela implementação já existente no Fortress — o enrolment de
device-binding, o fluxo de step-up que assina IBAN reveals, e o panorama maior de risco que
está staged para o próximo pass.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Tornar um token roubado inútil sem o device atrelado | Roubar o binding também, ou pular por um caminho mais mole |
| **Ideia central** | Token + prova-de-posse assinada de uma chave bound ao device | Se a chave de binding é exportável, fracamente aplicada, ou substituída por confiança, eu entro |
| **Pior falha** | Armazenar a public key bound sem auth; confiar só no `deviceId` | Permitir re-registro de chave sem confirmação out-of-band |

---

## 🛡️ Defensor — "Eu atrelo o token ao silício"

### O quadro

Três coisas viajam juntas em toda requisição sensível:

```
Authorization: Bearer <JWT>           ← quem você é
X-Fortress-Device: <deviceId>         ← qual instalação você é
Body: { signatureB64, nonceB64 }      ← prova TEE-assinada de que o device é o mesmo do enrolment
```

O JWT sozinho te dá rotas read-only. A assinatura + nonce gateia as que mutam estado.

### Enrolment, uma vez por device

[`DeviceBindingEnroller`](../../app/src/main/java/com/umain/fortress/auth/DeviceBindingEnroller.kt)
faz o trabalho no primeiro login:

```kotlin
val publicKey = biometricKeyStore.getOrCreatePublicKey(ALIAS_DEVICE_BINDING)
val spki = Base64.encodeToString(publicKey.encoded, Base64.NO_WRAP)
deviceBindingApi.register(deviceIdProvider.current(), spki)
```

- O keypair é gerado dentro do Android Keystore — a metade privada **nunca** sai da TEE.
  `publicKey.encoded` devolve os bytes SPKI X.509; é isso que o servidor recebe.
- A chave usa `setUserAuthenticationRequired(true)` para que assinar exija uma biometria fresh.
- `setInvalidatedByBiometricEnrollment(true)` — se alguém adicionar uma digital, a chave
  morre.

Server side: [`/auth/device-binding/register`](../../backend/src/routes/devicebinding.ts)
armazena `{userId, deviceId, publicKeySpkiB64, createdAt, updatedAt}`. Re-registros
subsequentes do mesmo par `(userId, deviceId)` rotacionam a chave armazenada — útil quando a
chave local é invalidada por enrolment biométrico e o cliente regenera.

### Step-up, em cada ação sensível

Uma ação "step-up" é qualquer uma que toca em dinheiro ou revela identificadores de vida
longa (IBAN, PAN, email de recuperação de conta). O fluxo neste repositório está implementado
para IBAN reveal em [`AccountDetailViewModel.revealIban`](../../app/src/main/java/com/umain/fortress/ui/screens/accountdetail/AccountDetailViewModel.kt)
e a rota correspondente do backend em [`backend/src/routes/stepup.ts`](../../backend/src/routes/stepup.ts):

```
Cliente                                   Servidor
  │                                         │
  │  POST /stepup/reveal/account/:id/challenge
  │ ───────────────────────────────────────►│  gera nonce de 32 bytes
  │                                         │  atrela a (userId, action, payloadDigest, expiresAt)
  │                                         │  persiste como StepUpChallenge
  │  ◄─────────────────────────────────────  │  {nonceB64, expiresAtEpochMs}
  │                                         │
  │  BiometricPrompt + CryptoObject         │
  │  signature.sign(nonceBytes)             │
  │                                         │
  │  POST /stepup/reveal/account/:id/verify │
  │  body: {nonceB64, signatureB64, deviceId}
  │ ───────────────────────────────────────►│  lookup challenge por nonce
  │                                         │  lookup device-binding por (user, deviceId)
  │                                         │  crypto.verify('SHA256', nonce, pubKey, sig)
  │                                         │  em sucesso: marca consumed, devolve IBAN
  │  ◄─────────────────────────────────────  │  {ibanFull}
```

O que isso prova no servidor:

1. O usuário tem um access token válido (header Authorization).
2. O device bate com um binding que a gente conhece.
3. Uma cerimônia biométrica (biometric ceremony) aconteceu nesse device nos últimos ~60
   segundos (porque a TEE só assina depois de um `BiometricPrompt` com sucesso).
4. A biometria autorizou *esta ação específica* — o nonce foi emitido para ela.

Qualquer um sozinho é insuficiente. A conjunção é o portão.

### O claim `cnf` — para hardening de produção

O padrão acima prova o device por ação. Produção estende isso para **toda** verificação de
access token embutindo uma chave de confirmação no token:

```json
{
  "iss": "fortress.bank",
  "sub": "u_alice",
  "exp": 1717250400,
  "cnf": {
    "jkt": "SHA-256 thumbprint da public key de device-binding"
  }
}
```

Toda requisição autenticada então carrega ou:
- Um header **DPoP** (RFC 9449) — um JWS sobre o método HTTP, URL, e um nonce fresh,
  assinado pela chave de device-binding — verificado por todo serviço de backend, ou
- O mesmo padrão de nonce-assinado do step-up, para toda chamada.

Isso torna um access token roubado inutilizável de qualquer outro device. A implementação
atual do Fortress só faz step-up para ações de alto valor; o modelo `cnf`-em-toda-parte está
staged em [10-system-design.md](10-system-design.md) sob "session-bound tokens".

### Risk signals para misturar

O cálculo do defensor não deveria ser binário. Empilhe sinais:

| Sinal | Fonte | O que te diz |
|---|---|---|
| Device integrity | Veredito de Play Integrity ([05](05-play-integrity.md)) | É um device Android real, não modificado? |
| App integrity | Play Integrity | É o nosso APK, assinado por nós? |
| Cluster de geolocalização | IP geo + histórico | É uma localização normal para esse usuário? |
| Velocidade | Cadência de sign-in | O usuário acabou de logar de Estocolmo e agora de Pequim? |
| Modo biométrico | Classe que `setUserAuthenticationParameters` devolve | STRONG vs WEAK |
| Tempo desde o enrolment | `device_binding.createdAt` | Confie menos num binding novinho |
| Padrão de comportamento | Histórico de ações | É a primeira transferência desse usuário para um novo destinatário às 03:00? |
| Qualidade de rede | Tipo de conexão, RTT | TOR / VPN comercial / dados móveis |

Cada sinal pega um peso; a soma é um risk score; a tabela de política mapeia score → ação:

```
score < 20:  permite
20 ≤ score < 60:  exige step-up biometric
60 ≤ score < 90:  step-up + cooldown + confirmação por email
score ≥ 90:  recusa, alerta usuário, escala para fraude
```

Esses thresholds são tunados por mercado, por classe de ação, por cohort de usuário. A demo
do Fortress hardcoda uma política "step-up para IBAN reveal e qualquer transferência";
produção tem uma rules engine.

### O problema de "device novo"

Device binding de dia-1 não deveria ser a única proteção. Um device novo genuíno não tem
histórico com o usuário — risk score começa elevado, cai conforme o usuário demonstra
comportamento normal ao longo de dias. Padrões:

- **Cooldown**: um device novo só pode gastar até €X / dia nas primeiras 72 horas.
- **Confirmação out-of-band**: enrolar um device novo pela primeira vez dá ping num device
  trusted existente ("aprovar este novo device em telefone X?").
- **Re-verificação de identidade**: contas de alto valor exigem reupload de documento para
  devices novos.

Isso não vive em código neste repositório ainda, mas o modelo de dados suporta: cada row
`device_binding` carrega `createdAtEpochMs` e `updatedAtEpochMs`, prontos para lógica de
cooldown num commit futuro.

---

## ⚔️ Atacante — "Eu roubo o token mas o silício não acompanha"

### Bypass 1 — Usar o token roubado sem device binding

Ataque velho. Site de phishing, malware, MITM, backup vazado — eu tenho o bearer token.
Disparo em `/me/dashboard`: 200 OK, saldos da conta são meus. Disparo no challenge endpoint
do reveal: pego um nonce. Disparo `/verify` com assinatura vazia/random: 401.

Eu fico parado em read-only.

**Counter:** é exatamente o desenho. Bearer token sozinho é read-only, a chave bound é exigida
para ops que mutam estado. Aperte mais: exija prova de posse estilo DPoP nas rotas read-only
também para contas de alto valor.

### Bypass 2 — Gerar minha própria chave e registrar no usuário legítimo

Se o endpoint de registro de device-binding aceita qualquer tupla (userId, deviceId,
publicKey) de uma sessão autenticada, eu — tendo o token roubado — posso registrar minha
chave ao lado da legítima. Agora o usuário legítimo tem *dois* devices registrados: o dele e
o meu. Meus signed challenges verificam.

**Counter:**
- **Binding único por (userId, deviceId)**: a demo aplica isso — re-registro no mesmo
  `deviceId` rotaciona a chave, não adiciona uma nova. Então eu precisaria de um deviceId
  diferente.
- **Múltiplos devices exigem confirmação OOB**: uma nova tupla (userId, deviceId) além de
  N=2 emite um push para devices trusted existentes: "aprovar novo device $X?". Até
  confirmar, o binding novo fica em estado "pending" — útil para read-only mas não consegue
  completar step-up.
- Telemetria: spike em registros de device novo por usuário é sinal pra fraud engine.

### Bypass 3 — Race do primeiro enrolment do usuário legítimo

Eu roubo o token *antes* do usuário enrolar a chave de device-binding dele. Eu registro
minha chave primeiro (lembre — não tem binding ainda, o servidor aceita o primeiro). Quando
o usuário legítimo abre o app depois e enrola, meu binding fica sobreposto — mas eu já usei
minha janela.

**Counter:**
- Enrol device-binding **antes** da primeira chamada sensível, idealmente como parte do
  mesmo fluxo de `login` (a demo faz isso em [`AuthRepository.login`](../../app/src/main/java/com/umain/fortress/auth/AuthRepository.kt)).
- A janela entre "logado" e "enrolado" é a perigosa. Mantenha-a mínima.
- Endpoints de step-up rejeitam se não existe binding para o deviceId — então meu race não
  abre a superfície de step-up mesmo se eu vencer o usuário.

### Bypass 4 — Replayar um challenge assinado

Eu capturo um nonce + assinatura assinada de um usuário legítimo (rede vigiada, debug log,
exploit de screen capture). Eu replayo em `/verify`. Se o servidor não queima o nonce, eu
desbloqueio o IBAN.

**Counter:**
- Servidor marca o challenge `consumed: true` na primeira verificação. Segunda tentativa com
  o mesmo nonce: rejeitado porque `consumed === true`.
- TTL curto (60 s) limita a janela de replay mesmo se a detecção de reuso estiver bugada.

### Bypass 5 — Levantar a private key bound

O ataque dos sonhos: extrair a private key de device-binding da TEE. Aí eu posso assinar
challenges de qualquer lugar.

A TEE é o piso disso — chaves simétricas do Android Keystore são não-exportáveis; EC private
keys são não-exportáveis. As chaves do `BiometricKeyStore` usam `PURPOSE_SIGN` só, sem
caminho de export.

**Counter:** a TEE é o counter. Esse bypass é "comprometer o secure element" — fora do
escopo para defesa via software, no escopo para trabalho de certificação Pixel/Knox. Se você
está se preocupando com isso, você é um defensor soberano, não um app de banking.

### Bypass 6 — Patchar o cliente para pular o BiometricPrompt e chamar signature.sign() direto

Se eu tenho execução de código dentro do app, eu consigo chamar `signature.sign(nonceBytes)`
sem o prompt?

Não — a chave é criada com `setUserAuthenticationRequired(true)` e
`setUserAuthenticationParameters(0, AUTH_BIOMETRIC_STRONG)`. A TEE recusa `sign()` a menos
que uma cerimônia biométrica tenha acontecido na última operação. Chamar `signature.sign()`
sem o prompt joga `UserNotAuthenticatedException`. Meu código hookado não consegue forjar
isso de userspace — o portão está na TEE.

**Counter:** mantenha a spec de auth-gating exatamente como está. Nunca relaxe para validade
baseada em tempo em release builds. Veja [07-biometric-hardening.md](07-biometric-hardening.md).

### Bypass 7 — Compartilhamento cross-device de chave (abuso de credenciais sincronizadas)

Se o usuário logar em um segundo device malicioso via uma passkey sincronizada na cloud, esse
segundo device enrola a própria chave de device-binding (com o mesmo userId). Agora eu sou
um device "legítimo" da perspectiva do servidor — mas eu estou segurando o segundo device
malicioso.

**Counter:**
- Novo binding (userId, deviceId) exige confirmação OOB de um device trusted existente
  antes de poder completar step-up — veja Bypass 2.
- Risk engine fica de olho em "device novo começa a usar step-up imediatamente após o
  enrolment" como uma flag.
- Cap quanto dinheiro um device fresh pode mexer nas primeiras 72 horas.

### Bypass 8 — Replayar um challenge emitido para uma ação diferente

Se `/verify` para a ação A aceita um nonce que foi emitido para a ação B, eu emito um
challenge "reveal" de baixo risco com minha sessão, depois uso a assinatura desse nonce para
autorizar uma transferência.

**Counter:** a demo atrela cada nonce a `action: "reveal:account:<id>"` e o endpoint de
verify assenta `expectedAction === storedChallenge.action`. Mismatch → rejeita.
[`stepup.ts`](../../backend/src/routes/stepup.ts) mostra a checagem.

### Bypass 9 — Stripar `deviceId` do body do verify

Se o servidor faz fallback para "sem deviceId → usar qualquer um dos bindings do usuário", eu
omito `deviceId` na minha requisição e o servidor tenta todos eles em ordem até bater com
minha assinatura forjada. Combinado com Bypass 2 (registrei meu próprio binding pro
usuário), eu entro.

**Counter:** exija `deviceId`, recuse a requisição se faltar. O verify da demo faz isso:
type-check + 400 em ausência.

---

## Cross-reference

- **O que "TEE-bound" significa por baixo dos panos** → [02-hardware-vault.md](02-hardware-vault.md)
- **Como a cerimônia biométrica gateia a chamada de sign** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Ciclo de vida do token que esses sinais complementam** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Anti-Frida / anti-debug** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **O system design risk-aware em escala** → [10-system-design.md](10-system-design.md)

## Referências

- [Part 9 — Zero Trust: Device Binding, Risk Signals](https://blog.stackademic.com/part-9-zero-trust-device-binding-risk-signals-e2f2796ceefd)
- [RFC 9449 — OAuth 2.0 Demonstrating Proof of Possession (DPoP)](https://datatracker.ietf.org/doc/html/rfc9449)
- [Android Developers — Hardware-backed Keystore](https://developer.android.com/privacy-and-security/keystore)
- [NIST SP 800-63B — Digital Identity Guidelines, Authentication](https://pages.nist.gov/800-63-3/sp800-63b.html)
