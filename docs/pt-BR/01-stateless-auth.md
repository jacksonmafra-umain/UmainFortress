# 01 — O modelo stateless

> "Sessions são fáceis com um servidor, tratáveis com dez, e um pesadelo de coordenação com
> mil. Tokens stateless trocam um problema (coordenação entre servidores) por outro (revogação
> de token) — e o segundo problema é majoritariamente solucionável." — *Fortress field notes*

**TL;DR** — O Fortress Bank emite **JWTs HS256 de access token de vida curta** (15 min) mais
**refresh tokens opacos com rotação** de vida longa (30 dias, hashed at rest). O access token
é verificado por assinatura em cada serviço. O refresh token é o único objeto stateful;
rotação a cada uso limita o raio de explosão de um leak, e uma deny-list trata revogação
explícita. Este arquivo passa pela arquitetura, pelos trade-offs, pela implementação neste
repositório, e por como um atacante tenta armar cada decisão de design.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Verificar identidade em escala sem coordenar estado de sessão entre N servidores | Roubar, forjar, replayar, ou estender o tempo de vida de um token |
| **Ideia central** | Assinatura = autorização; tokens carregam só o necessário, nada mais | Se sua secret vaza, meu token forjado verifica para sempre |
| **Pior falha** | Access tokens de vida longa sem revogação, uma única HMAC secret compartilhada | Uma sym-key única compartilhada entre vários serviços e nunca rotacionada |

---

## 🛡️ Defensor — "Eu escalo emitindo, não lembrando"

### O modelo mental

Uma requisição chega em qualquer serviço da frota com `Authorization: Bearer <jwt>`. O
serviço:

1. Verifica a assinatura do JWT contra o material de verificação público (HMAC secret aqui;
   em produção: chave pública distribuída via JWKS).
2. Valida `iss`, `aud`, `exp`, `nbf`, `iat`.
3. Lê claims (`sub`, `email`, papéis opcionais).
4. Age na requisição.

Nenhum lookup de sessão. Nenhuma leitura de banco no hot path. O sistema escala
horizontalmente até onde o issuer subjacente conseguir.

### O par de tokens

| | Access token | Refresh token |
|---|---|---|
| Formato | JWT (header.payload.sig) | Random opaco base64url, 48 bytes |
| Algoritmo | HS256 (demo) / ES256 ou RS256 (prod) | n/a |
| Tempo de vida | 15 min | 30 dias |
| Storage server side | nenhum | hash (SHA-256) + metadata (deviceId, userId, expiresAt, revoked) |
| Storage client side | encriptado com Android Keystore AES-GCM ([`TokenStore`](../../app/src/main/java/com/umain/fortress/security/TokenStore.kt)) | igual |
| Enviado em toda chamada | sim, como `Authorization: Bearer …` | só em `/auth/refresh` |
| Revogação | implícita (ele expira) | explícita (deny-list / rotacionado a cada uso) |

### Por que access curto + refresh longo

- **Access curto** mantém o raio de explosão pequeno: um access token roubado é inútil
  passados 15 min.
- **Refresh longo** mantém a UX suave: o usuário não retipa credenciais a cada 15 min.
- **Rotação em todo refresh** torna um refresh roubado um objeto de uso único. Se o atacante
  refresha, o próximo refresh do cliente legítimo falha — e essa falha é o sinal que dispara
  invalidação de sessão / revisão de risco.

### Rotação de refresh neste repositório

[`backend/src/routes/auth.ts`](../../backend/src/routes/auth.ts) implementa a dança:

```ts
// 1. Procura o refresh token pelo SHA-256 dele (token raw nunca é armazenado).
// 2. Checa: não revogado, não expirado, deviceId bate com o que foi vinculado na emissão.
// 3. Revoga o record antigo.
// 4. Emite novo access token + novo refresh token.
// 5. Persiste o novo registro de refresh com o mesmo userId, mesmo deviceId.
// 6. Devolve o novo par para o cliente.
```

Detalhe crítico: **o refresh token é armazenado hashed**. Se o banco vaza, um atacante segura
bytes resistentes a preimage — não tokens utilizáveis. (Não usamos um hash lento porque a
secret é high-entropy por construção; SHA-256 é apropriado aqui. Veja *Por que SHA-256 e não
bcrypt para refresh tokens?* abaixo.)

### Por que HMAC (HS256) na demo, asimétrico (ES256/RS256) em produção

| | HS256 | ES256 / RS256 |
|---|---|---|
| Distribuição da secret | Todo serviço que verifica tem a mesma secret | Verifiers têm uma chave pública; só o issuer tem a privada |
| Raio de explosão do leak | Qualquer serviço pode emitir tokens | Só o issuer pode emitir; verifiers não |
| Rotação | Todos ao mesmo tempo na frota | Issuer rotaciona a privada; endpoint JWKS anuncia novas chaves públicas; verifiers pegam pelo key ID (`kid`) |
| Operacionalmente | Trivial de montar | Trivial *para verifiers*; o issuer precisa de HSM / KMS / custódia cuidadosa de chave |

A demo usa HS256 porque roda em um processo. Produção deve usar ES256: tokens menores que
RS256, curva moderna, suporte amplo de bibliotecas. A chave privada vive num KMS, o JWKS
público é servido em `/.well-known/jwks.json`, e clients/services rotacionam chaves pelo `kid`
delas.

### Higiene de claims

Só o mínimo necessário:

```json
{
  "iss": "fortress.demo",
  "aud": "fortress.client",
  "sub": "u_alice",
  "email": "alice@fortress.dev",
  "displayName": "Alice Hartman",
  "iat": 1741875600,
  "exp": 1741876500,
  "jti": "fdfa..."
}
```

Coisas para **não** colocar em um JWT:

- Qualquer coisa que você rotacionaria independentemente do token (ex: papéis que mudam
  diariamente). Coloque um mapa de papéis de vida curta keyed por `sub` no auth service e
  busque lazy — ou inclua fingerprints de papel + force refresh quando mudam.
- PII que você prefere não fazer passar por todo log. O token aparece em logs de HTTP, em
  storage do browser, em proxies intermediários se algum logar headers.
- Permissões para sistemas que não são a audience. Um token para `fortress.client` não deve
  também autorizar `fortress.admin`.

### Por que SHA-256 e não bcrypt para refresh tokens?

bcrypt / argon2 existem para frear brute force contra inputs de **baixa entropia** (senhas).
O refresh token é 48 bytes random — 384 bits de entropia. Um hash lento não compra nada aqui;
um banco roubado não fica brute-forceável. SHA-256 é rápido, determinístico, resistente a
colisão no sentido relevante, e mantém o caminho `/auth/refresh` em sub-milissegundo.

### Escalando o issuer

O issuer é o único nó stateful nesse desenho. Ele precisa escrever um record de refresh token
em cada login + cada refresh. Padrões dos artigos-fonte:

- **Verificações de authn read-heavy** (validar tokens) escalam horizontalmente — só precisam
  da chave pública.
- **Records de refresh token write-heavy** escalam via partitioning por `userId` (lookup na
  deny-list é rápido e limitado pelo fan-out por usuário).
- **Lista de revogação** — um Redis set pequeno de `jti`s revogados para kill switches de
  emergência; o resto fica por conta do TTL curto do access.

Para 5M de usuários você pode rodar o issuer como um serviço pequeno e particionado; a frota
de verifiers é a camada horizontal grande.

### Client side neste repositório

O cliente Android trata os dois tokens como bytes opacos:

- Eles fazem round-trip pelo [`TokenStore`](../../app/src/main/java/com/umain/fortress/security/TokenStore.kt)
  encriptado com AES-256-GCM via Android Keystore (TEE/StrongBox).
- [`SessionManager`](../../app/src/main/java/com/umain/fortress/auth/SessionManager.kt) trata
  o store como verdade e expõe `Active` / `Expired` / `SignedOut`.
- [`AuthInterceptor`](../../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt)
  anexa o access token em toda requisição autenticada e roda um **single-flight refresh em
  401** (o próximo arquivo da série).

---

## ⚔️ Atacante — "Trato seu desenho stateless como um passivo stateful"

### Bypass 1 — Forjar tokens com a HMAC secret vazada

Se você usou HS256 e a secret vazou (dump de env var, commit no GitHub, linha de log que
imprimiu `process.env`), eu emito tokens para qualquer subject com qualquer expiração. Sua
frota de verifiers vai verificar todos felizmente.

**Counter:**
- Use ES256/RS256 em produção. Aí um leak do material *do verifier* não me dá nada.
- Mantenha a chave de assinatura num KMS. Audite acesso; alerte em leituras diretas de
  material de chave.
- Mantenha secrets de vida curta: rotacione a HMAC semanalmente. Force `kid`s sobrepostos
  para rotação zero-downtime.

### Bypass 2 — Replayar um access token que eu roubei

Se eu peguei um access token (XSS, MITM em cliente sem pinning, debug log, screen recording,
FaceID + sleep), eu tenho ≤ 15 min. Essa janela é suficiente para um pipeline automatizado
de exfil.

**Counter:**
- 15 min é um teto deliberado, não uma meta. Combine com proof-of-possession atrelado ao
  device: o claim `cnf` do access token nomeia uma chave pública cuja metade privada está na
  TEE do dispositivo. Serviços que verificam exigem uma assinatura recente da requisição com
  essa chave privada. Veja [09-zero-trust.md](09-zero-trust.md) para o fluxo de device binding.
- Pin certificates para que eu não consiga MITM com um cert instalado pelo usuário. Veja
  [08-network-warfare.md](08-network-warfare.md).

### Bypass 3 — Replayar um refresh token que eu roubei

Sem rotação: todo refresh meu emite um novo par de vida longa. Eu nunca preciso da senha do
usuário de novo.

Com rotação: meu primeiro refresh tem sucesso; o próximo refresh do cliente legítimo falha.
Mas isso é visível pro servidor como **dois refreshes contra o mesmo parent** — o segundo
dentro de uma janela curta. Esse é o sinal canônico de "refresh-reuse": revogue a família
inteira de tokens e force re-auth.

**Counter:**
- Rotacione todo refresh, armazene a linhagem (`parent_id`), detecte reuso.
- Em reuso: revogue a linhagem + alerte o usuário + risk-score do device sobe por 24h.

### Bypass 4 — Emitir access tokens de vida longa fazendo skew do relógio

Se seu verifier confia no `exp` sem sanity-check contra o próprio relógio — ou se aceita skew
extremo de `iat` — eu fabrico tokens que parecem recém-emitidos pelo issuer.

**Counter:**
- Rejeite tokens com `iat` no futuro ou mais de uns minutos no passado.
- Rejeite `exp - iat` maior que o TTL máximo publicado pelo issuer.
- Sincronize relógios de servidor via NTP; recuse subir com sinais de clock ruim.

### Bypass 5 — Replay entre audiences

Você emitiu um token para `fortress.client`. O serviço interno `fortress.admin` compartilha a
config do verifier e esqueceu de checar `aud`. Agora um token de cliente roubado emite ações
de admin.

**Counter:**
- Todo serviço que verifica valida `aud` contra a própria identidade de serviço.
- Não compartilhe código de verificação que omite isso; coloque num middleware difícil de
  pular.

### Bypass 6 — Pescar tokens do dispositivo

Se o `TokenStore` gravasse tokens em plaintext em SharedPreferences, eu tiro um backup (`adb
backup`), exfiltro, e leio. Se `allowBackup="true"` no manifest, seus tokens deixam o
dispositivo.

**Counter:**
- Este repositório: `android:allowBackup="false"` (veja [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml))
- Tokens encriptados com uma chave Keystore AES-GCM não-exportável. Mesmo com root +
  filesystem access, eu pego ciphertext.
- Veja [02-hardware-vault.md](02-hardware-vault.md) para o threat model do storage.

### Bypass 7 — Ataque de confusão de algoritmo

Eu envio um JWT com `"alg": "none"` ou com `"alg": "HS256"` quando você esperava `RS256`. Uma
biblioteca ingênua aceita qualquer um, e agora meu token não-assinado verifica.

**Counter:**
- Pinhe o algoritmo explicitamente ao chamar `jwtVerify`. Neste repositório:
  `await jwtVerify(token, SECRET, { issuer, audience })` — `jose` infere `alg` pelo tipo da
  secret, não pelo header.
- Teste o verifier contra um conjunto curado de tokens ruins (`alg: none`, type mismatch,
  claims faltando).

---

## Cross-reference

- **O vault que protege os tokens at rest** → [02-hardware-vault.md](02-hardware-vault.md)
- **Single-flight refresh em 401** → [03-interceptor-pattern.md](03-interceptor-pattern.md)
- **Ciclo de vida do refresh, rotação, e o alerta de detecção de reuso** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Por que você ainda atrela step-up actions a uma biometria** → [07-biometric-hardening.md](07-biometric-hardening.md)
- **Device binding via claim `cnf`** → [09-zero-trust.md](09-zero-trust.md)

## Referências

- [Part 1 — The Stateless Blueprint: Scaling Android Auth for 5M Users](https://blog.stackademic.com/part-1-the-stateless-blueprint-scaling-android-auth-for-5m-users-56f10ed652a5)
- [RFC 7519 — JSON Web Token](https://www.rfc-editor.org/rfc/rfc7519)
- [RFC 8725 — JWT Best Current Practices](https://www.rfc-editor.org/rfc/rfc8725)
- [OWASP — JWT Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/JSON_Web_Token_for_Java_Cheat_Sheet.html)
