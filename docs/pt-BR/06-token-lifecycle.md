# 06 — Ciclo de vida do token: rotação, revogação, detecção de reuso

> "Um token de vida longa é um contrato que você assinou com o você do futuro e que preferiria
> não ter que honrar. Um token rotativo é um contrato que você reescreve a cada interação." —
> *Fortress field notes*

**TL;DR** — Refresh tokens rotacionam a cada uso, são armazenados hashed (SHA-256) at rest, e
o servidor rastreia uma linhagem para que **reuso** de refresh token seja o sinal canônico de
que algo foi roubado. Reuso → revogar a família inteira → expulsar todos os devices daquela
linhagem → contar pra fraude. Os endpoints de servidor em
[`backend/src/routes/auth.ts`](../../backend/src/routes/auth.ts) ligam isso.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Um refresh, um uso; reuso significa comprometimento | Roubar uma vez, refreshar para sempre |
| **Ideia central** | Rotação é barata; o custo é pago pelo usuário legítimo uma vez por sessão | Se o servidor esquece a linhagem de rotação, um refresh roubado é eterno |
| **Pior falha** | Refresh tokens estáticos, sem detecção de reuso, sem rotação, sem revogação | Endpoint único de token sem rate limit, sem device binding |

---

## 🛡️ Defensor — "Todo refresh é uma renegociação de contrato"

### Estado por (usuário × device)

O mínimo de metadata para uma linhagem família-de-token:

```ts
interface RefreshTokenRecord {
  id: string;              // ID único para esse token individual
  userId: string;          // a quem esse token pertence
  tokenHash: string;       // SHA-256 dos bytes raw do token (nunca o token raw!)
  deviceId: string;        // atrelado na emissão, validado em todo refresh
  issuedAtEpochMs: number;
  expiresAtEpochMs: number;
  revoked: boolean;        // flag explícita de revogação
}
```

Produção deve adicionar:

- `parentId` — o token que essa rotação substitui. Deixa o servidor caminhar pela linhagem.
- `familyId` — igual entre todos os tokens descendentes de um login. Deixa você revogar a
  família em uma query.
- `userAgent` / `clientVersion` — sinais de risco.
- `ipFingerprint` — para detecção de outliers (não o IP raw, só um hash estável, GDPR-friendly).

### O fluxo de rotação

[`backend/src/routes/auth.ts:POST /auth/refresh`](../../backend/src/routes/auth.ts) faz:

```
1. hash(rawRefreshToken) → procura o record
2. se !record OU revoked OU expirado OU deviceId mismatch → 401
3. revoga o record (set revoked=true)
4. emite novo access + novo refresh token
5. persiste novo record de refresh (mesmo userId, mesmo deviceId)
6. devolve o novo par
```

O cliente legítimo recebe o par. O servidor agora revogou o token velho; qualquer tentativa
*futura* de usar o refresh velho falha. Essa é a propriedade em que detecção de reuso se apoia.

### Detecção de reuso

Dois refreshes em segundos de diferença, usando o *mesmo* refresh token: pelo menos um é
atacante. O servidor deveria:

1. Recusar os dois — o segundo claramente, mas o primeiro também se ainda estiver vivo
   quando o segundo chega.
2. Revogar a família inteira (todos os tokens descendentes desse login).
3. Incrementar risk score para esse usuário × device.
4. Notificar o usuário legítimo out-of-band (push, email): "Você foi deslogado porque algo
   incomum aconteceu. Se não foi você, mude sua senha e revise a atividade recente."

A implementação ingênua só revoga em uso e devolve 401 ao segundo caller. A robusta revoga a
*família*. A demo do backend rotaciona e revoga o record único; produção deveria adicionar
`familyId` e deny-list por família.

### Por que hashear o refresh token?

Se o banco vaza, um atacante com o dump de `refresh_tokens.json` não deveria conseguir nada
que dê para entregar a `/auth/refresh`. SHA-256 de um random com 384 bits de entropia é
resistente a preimage no sentido prático. O atacante pode verificar se um token que ele *já*
tem está na tabela, mas não consegue manufaturar um utilizável.

Exatamente por isso **não** usamos bcrypt/argon2 aqui: a entropia já é máxima, o hash lento
não compra nada, e a latência de refresh importa.

### Escolha de TTL do access token

| TTL | Pros | Cons |
|---|---|---|
| 1 min | Janela de replay minúscula | Tempestade de refresh; custo de bateria / data |
| 5 min | Janela pequena de replay | Refreshes frequentes |
| 15 min (este repositório) | Balanceado para mobile | Janela de replay razoável |
| 60 min | Refreshes baratos | Access token roubado funciona por 1h |
| 24 h | "Stateless" na prática | Access token roubado é uma credencial de vida longa |

15 min é o default fintech moderno. Para as ações de mais alto valor, sobreponha step-up
biométrico para que o access token sozinho nunca autorize movimentação de dinheiro
([07-biometric-hardening.md](07-biometric-hardening.md)).

### Estratégias de revogação

Verificação pura stateless não consegue revogar um access token não-expirado. Três padrões:

1. **TTL curto** — espera passar. Mecanismo primário deste repositório. 15 minutos no pior
   caso.
2. **Deny-list por `jti`** — serviços que verificam consultam um Redis set pequeno keyed por
   token ID. Barato de ler, escrito raramente (só em revogação explícita). Latência
   sub-milissegundo se o set estiver in-memory em cada serviço.
3. **Checagem online em ações sensíveis** — chamar o issuer para validar a vigência da sessão
   só quando executando a ação de movimentação de dinheiro. Adiciona um network hop onde
   importa mais.

O default é (1) + (2). Use (3) para transferências >€1000, mudanças de senha, adições de
destinatário.

### Fluxo de logout

[`POST /auth/logout`](../../backend/src/routes/auth.ts):

```
1. hash(refreshToken) → procura o record
2. marca revoked=true
3. devolve ok
```

Nenhum access-token blacklisting necessário se você usa TTL curto. O access token já em vôo
vai expirar em ≤ 15 min e o usuário não conseguirá refreshar.

Client side (este repositório): [`SessionManager.clear()`](../../app/src/main/java/com/umain/fortress/auth/SessionManager.kt)
remove o blob encriptado do DataStore. A chave do Keystore permanece (será reusada no próximo
login).

---

## ⚔️ Atacante — "Eu cobiço seus secrets de vida longa"

### Bypass 1 — Roubar um refresh, refreshar para sempre

Sem rotação: eu faço um refresh por expiração de access token, indefinidamente. O servidor não
tem como diferenciar meu refresh do refresh do usuário.

**Counter:** rotação. Meu refresh funciona uma vez; o próximo refresh do usuário falha
(porque revoguei a linhagem dele usando antes). O usuário é chutado; ele re-loga; meu refresh
roubado agora está revogado e inútil.

### Bypass 2 — Roubar um refresh, race contra o usuário

Rotação sozinha não é suficiente. Se eu refresho *antes* do usuário, eu pego o par novo e ele
fica trancado de fora. Aí eu derivo a linhagem com meus próprios refreshes, e enquanto eu for
o último a ter usado o refresh mais recente, o servidor me dá um par válido sob demanda. O
usuário está deslogado e não suspeita de nada técnico — ele acha que a senha expirou ou algo
assim.

**Counter:**
- Detecção de reuso por **família**. Se o app do usuário tenta refresh depois e apresenta um
  irmão da linhagem, o servidor diz "esse token pertence a uma família que vi bifurcar" e
  revoga a família.
- Notificação out-of-band na revogação da família.
- Risk engine flagga "padrão de device novo, bloco de IP novo, user-agent novo nos minutos
  após um refresh" como anômalo.

### Bypass 3 — Bind-jump

Eu roubo o refresh e de alguma forma também spoofo ou capturo o `deviceId`. Se o deviceId é a
única amarração, eu estou dentro.

**Counter:**
- Atrele também a uma chave pública lastrada em hardware: o refresh só é válido quando
  acompanhado de uma assinatura da chave de device-binding em [`BiometricKeyStore`](../../app/src/main/java/com/umain/fortress/security/BiometricKeyStore.kt).
- Aí eu precisaria da private key residente na TEE do device — impraticável sem acesso físico.

### Bypass 4 — Access TTL longo sem revogação

Se você setou access TTL para 24h e depende inteiramente de revogação de refresh, um access
token roubado é uma credencial de 24 horas. Quando o usuário desloga, o atacante já fez o
dano.

**Counter:**
- TTL curto de access.
- Para ações sensíveis: checagem online no issuer (padrão 3 acima) independente de TTL.

### Bypass 5 — Forjar via comprometimento da chave de assinatura

Se a HMAC secret vaza (env-var dump, linha de log, commit no GitHub), eu forjo qualquer
access token que quiser — sem refresh necessário. Veja [01-stateless-auth.md](01-stateless-auth.md)
para a mitigação com chave assimétrica.

### Bypass 6 — Furtar pela checagem de device-binding

Se o servidor não valida `deviceId` no refresh (só no login), eu posso roubar um refresh no
device A e usar do device B, sem perguntas.

**Counter:** a demo *checa* `record.deviceId !== deviceId`. Produção: também verifique um
nonce assinado da chave de device-binding em cada refresh, não só no login. (Veja
[09-zero-trust.md](09-zero-trust.md).)

### Bypass 7 — DoS de refresh-only

Se `/auth/refresh` não tem rate limit, eu martelo com tokens roubados para mantê-los "vivos"
indefinidamente, ou para brute-force um servidor mal configurado que devolva distinções úteis
entre 401-vs-403.

**Counter:**
- Rate limit por IP, por usuário, por device — apertado em `/auth/refresh`.
- Forma de resposta constant-time (sem vazar "device errado" vs "expirado" vs "não
  encontrado").

---

## Cross-reference

- **O modelo de tokens que estes implementam** → [01-stateless-auth.md](01-stateless-auth.md)
- **O interceptor que orquestra a rotação client-side** → [03-interceptor-pattern.md](03-interceptor-pattern.md)
- **O vault que protege o refresh token at rest no cliente** → [02-hardware-vault.md](02-hardware-vault.md)
- **Device binding para tornar um refresh roubado inútil** → [09-zero-trust.md](09-zero-trust.md)

## Referências

- [Part 6 — The Interceptor Pattern: Mastering the Token Lifecycle](https://blog.stackademic.com/part-6-the-interceptor-pattern-mastering-the-token-lifecycle-83afa94b8dd0)
- [OAuth 2.0 — Refresh Token Rotation](https://datatracker.ietf.org/doc/html/draft-ietf-oauth-security-topics-22#section-4.13.2)
- [Auth0 — Refresh Token Rotation and Reuse Detection](https://auth0.com/docs/secure/tokens/refresh-tokens/refresh-token-rotation)
