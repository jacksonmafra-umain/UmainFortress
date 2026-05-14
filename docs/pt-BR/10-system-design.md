# 10 — System design: arquitetura nível staff

> "Auth em mil usuários é uma biblioteca. Em um milhão de usuários é um banco de dados. Em
> dez milhões é um sistema distribuído. A pergunta da entrevista é se você percebeu quando o
> regime mudou." — *Fortress field notes*

**TL;DR** — Desenhar o Fortress para 5M de usuários exige traçar três linhas: o **issuer**
(emite tokens, dono da chave de assinatura, write-heavy), a **frota de verifiers** (read-only,
escala horizontal, dona do cache JWKS), e a **risk engine** (consome eventos, decide se uma
ação específica está OK *agora*). Este arquivo passa pela arquitetura, pelos data stores
atrás de cada linha, pelos modos de falha entre eles, e pela estratégia do atacante de
escolher costuras.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Tornar cada componente escalável e auditável de forma independente | Achar a assumption de trust cross-component que quebra primeiro |
| **Ideia central** | Verificação stateless na borda; enforcement stateful no centro | Consistência eventual = uma janela de race que eu posso explorar |
| **Pior falha** | Um serviço que precisa saber tudo | Um caminho de chamada "trusted internal" sem auth entre serviços |

---

## 🛡️ Defensor — "Eu traço três linhas e defendo cada uma"

### A forma de alto nível

```
                        ┌──────────────────────────────┐
       ┌──────────────► │   Risk Engine                │ ◄──── events bus
       │                │   (política por ação)         │
       │                └──────────────────────────────┘
       │                                ▲
       │                                │ "posso deixar a ação X acontecer?"
       │                                │
   ┌───┴────────────────────────────────┴──────┐
   │     API Gateway (terminação TLS,          │
   │     validação de shape de requisição,     │
   │     rate limit)                            │
   └───┬────────────────────────────┬──────────┘
       │                            │
       ▼                            ▼
  ┌─────────────────┐         ┌────────────────────────┐
  │ Frota verifier  │         │ Issuer service          │
  │ (JWT stateless) │         │ (mint, refresh, revoke, │
  │                 │         │  device-binding)        │
  └─────────────────┘         └────────────────────────┘
       │                            │
       │ eventos async              │ writes
       │                            ▼
       │                       ┌──────────────────┐
       │                       │ Refresh-token    │
       │                       │ store (sharded)  │
       │                       └──────────────────┘
       │
       ▼
  ┌─────────────────┐
  │ Event bus       │ ────► Audit log, fraud, telemetria
  └─────────────────┘
```

Três serviços escaláveis independentes + um fabric async.

### Racional da decomposição em serviços

**API Gateway**
- Terminação TLS, validação de shape de requisição, limites de tamanho de requisição,
  **rate limits por IP e por userId**.
- Dono da história de cert pinning da perspectiva do cliente (clients pinam o gateway, não
  o upstream).
- Stateless. Escala horizontal via load balancer.

**Frota verifier**
- Decoda o JWT bearer token, valida `iss/aud/exp/iat/nbf/cnf` contra JWKS in-memory.
- Para DPoP / proof-of-possession, valida a assinatura por requisição contra a public key
  nomeada por `cnf.jkt`.
- **Não lê banco no hot path.** Throughput máximo; é aqui que 99% das requisições vivem.
- Busca JWKS do `/.well-known/jwks.json` do issuer com TTL cache + refresh jitterado.

**Issuer service**
- Login, refresh, logout, enrol de device-binding, revogação.
- O único serviço com write access ao refresh-token store.
- Dono da chave de assinatura JWT — ela mesma armazenada num KMS / HSM, nunca em disco em
  plaintext.
- Throughput menor que a frota verifier (ordens de magnitude). Escala vertical + horizontal
  pequena.

**Risk engine**
- Consome eventos: tentativas de login, refreshes, transferências, registros de
  device-binding, mudanças de geo, sinais de RASP.
- Mantém um risk score por usuário, decai com o tempo.
- Expõe um endpoint síncrono `POST /risk/decide`: dado `{userId, deviceId, action, payload}`,
  devolve `{decision, requiredFactors, reasonCodes}` em ≤ 50 ms.
- Para leituras de alta frequência, cache write-through (Redis) lastreado por um columnar
  store para analytics.

**Event bus**
- Stream append-only de todo evento relevante para auth. Kafka em lojas grandes, Kinesis em
  AWS, Pub/Sub em GCP, Redis Streams em setups mais leves.
- Alimenta o audit log (compliance), o pipeline ML de fraude, e o estado da risk engine.

### Data stores

| Store | Workload | Padrão |
|---|---|---|
| Refresh tokens | Write-heavy (todo login + todo refresh), lookup por token-hash | Sharded por `userId` — Postgres + read replicas, ou DynamoDB com `userId` como partition key |
| Device bindings | Pouca escrita, lookup por `(userId, deviceId)` | Mesmo store que refresh tokens, tabela diferente |
| Estado de risk | Read-heavy via cache, escrita em todo evento | Redis (hot) + columnar store (analytics) |
| Audit log | Append-only, sem updates | Columnar / S3 + Athena |
| Estado de step-up challenge | Vida curta (≤60 s), lookup por nonce | Redis com TTL eviction |

### Custódia da chave JWT

- Chave privada para assinar: num **KMS / HSM**. O issuer service tem permissão IAM para
  *assinar* mas não consegue ler os bytes. Rotação é automatizada; operações de assinatura
  carregam `kid`.
- JWKS público: servido pelo issuer em `/.well-known/jwks.json`, cacheado por todo verifier
  com TTL ≤ 5 min. Rotação é dual-publish: a chave nova aparece no JWKS dias antes de ser
  usada para assinar, então verifiers já cachearam quando o primeiro token assinado chega.
- Revogação de um `kid`: remove do JWKS, mais um cache deny-list nos verifiers. Pior caso de
  tempo de recuperação: TTL de cache + delay de propagação do deny-list.

### Trade-offs de storage de refresh-token

| Store | Pros | Cons |
|---|---|---|
| **Postgres** | Rotação transacional, detecção de reuso fácil, maduro operacionalmente | Teto de escala vertical; sharding vira trabalho passado 50M tokens |
| **DynamoDB / Bigtable** | Horizontal pra sempre, latência previsível | Consistência eventual em índices secundários — fácil de usar errado para detecção de reuso |
| **Redis (volátil)** | Latência minúscula | Perde estado em failover → falhas em massa de refresh a menos que replicado |
| **Cassandra** | Writes multi-region | Modelo de query contra-intuitivo; lightweight transactions são pesadas |

Aposta do Fortress: Postgres com partitioning por `(user_id, hash)` até ~10M de usuários,
depois mover o hot path (lookup de token) para DynamoDB enquanto mantém o audit history em
Postgres.

### Risk engine, em 50 ms

Cada `POST /risk/decide` consulta:

- **Hot path** (Redis): score atual por usuário, última geolocalização vista, contagem
  recente de falhas.
- **Cold path** (só se os sinais hot estão faltando): batch lookup de idade do device
  binding, padrões históricos de ação.

Árvore de decisão (ilustrativa):

```
se score > 90:                       recusa + alerta
elif score > 60:                     exige step-up + cooldown
elif score > 30 e ação sensível:     exige step-up
elif ação sensível:                  exige step-up biométrico só
else:                                permite
```

A risk engine **nunca bloqueia** a requisição síncrono — ela devolve um veredito; o serviço
de ação aplica. Isso significa que a risk engine pode fail open ou closed dependendo da
política: o issuer pode decidir "se a risk engine não responde, recuse todos os step-ups"
(closed) ou "permita read-only, recuse mutações" (degradação graciosa).

### Multi-region

| Preocupação | Abordagem |
|---|---|
| Leituras (frota verifier) | Stateless — serving region-local de cache JWKS region-local |
| Escritas (refresh, device-binding) | Single-leader por shard, region-pinning por hash de `userId` |
| Risk engine | Instância por region lendo eventos do stream region-local; replicação cross-region do score é eventual (e reconhecida como tal — veja lado do atacante) |
| Audit log | Append-only, replicado eventualmente; SLAs de replicação compliance-grade |

A parte difícil é o usuário que muda de region. Estratégia do Fortress: uma `home_region` por
usuário, e requisições cross-region são proxadas para casa — adiciona latência mas preserva
consistência de escrita.

### Observability — os must-haves

- **Histograma de latência por usuário** em cada borda de serviço, p50/p95/p99.
- **Telemetria de veredito**: distribuição de vereditos de Play Integrity por versão de app
  por mercado.
- **Sinais de refresh-reuse**: contagem por minuto; é seu canário primário de fraude.
- **Latência cross-service**: API gateway → verifier → issuer → risk engine — um trace, um
  trace-id propagado.
- **Saúde de rotação**: quantos tokens são assinados por `kid` X, por `kid` Y? Usado para
  rotação segura.

### Shape de custo

Em 5M MAU com o mix fintech típico (um login por dia, dez chamadas de API por sessão, uma
transferência por semana):

- ~ 5M logins/dia → 60/s de pico → cabe num nó de issuer.
- ~ 500M chamadas de API/dia → 6 000/s de pico → 10-20 nós verifier.
- ~ 5M refreshes/dia → de novo no issuer.
- ~ 700K transferências/semana → infra de step-up segura ~1/s de pico.

O bottleneck **nunca é** compute. É o refresh-token store em recuperação de falha (quando todo
device re-loga depois de um outage). Capacity-plan para o thundering herd de recovery, não o
steady state.

---

## ⚔️ Atacante — "Eu procuro as costuras"

### Bypass 1 — Trust entre serviços

Chamadas internas entre API gateway e verifier, ou entre verifier e risk engine, frequentemente
pulam auth ("estamos dentro da VPC; tudo bem"). Eu pego RCE num serviço, agora tenho trust
implícito para chamar qualquer outro.

**Counter:**
- mTLS entre todo serviço interno.
- Tokens de identidade de serviço (SPIFFE / workload identity) em toda chamada.
- Networking zero-trust: sem trust implícito baseado em localização de rede.

### Bypass 2 — Cache poisoning de JWKS

Se um verifier cacheia JWKS via HTTP plain, eu MITM no endpoint JWKS e injeto minha chave.
Meus tokens agora verificam.

**Counter:**
- JWKS via HTTPS com mTLS para o issuer.
- Pin o cert do endpoint JWKS (equivalente verifier-side do cert pinning mobile).
- Assine o próprio JWKS com uma chave offline de vida longa, então mesmo um MITM bem
  sucedido do endpoint não me ajuda — verifier checa a assinatura externa.

### Bypass 3 — Race a risk engine na consistência eventual

Replicação cross-region do risk score é eventualmente consistente. Se a visão de uma region
do usuário é "score 20, tudo OK" enquanto a home region tem "score 95, fraude" porque os
eventos mais recentes não replicaram, eu posso explorar o gap momentaneamente roteando minhas
requisições pela region otimista.

**Counter:**
- **Decisão de hot path** é local a cada region — rápida. Mas consulta a home region para
  ações de alto valor (transferência ≥ €1000). Um pouco de latência, sem janela de race.
- Ou: degradação graciosa — se a home region é inalcançável dessa region, recuse a ação
  sensível em vez de fallback otimista.

### Bypass 4 — Costura de sharding do refresh store

Se shards são por hash de `userId`, e eu posso forçar o user-record a ser consultado por uma
chave diferente (ex: email), minha requisição bate num shard diferente com estado
potencialmente stale.

**Counter:**
- Caminho único canônico de lookup por record. Sem índice secundário que possa dessincronizar
  do primário.
- Todo routing de shard acontece por trás da API do issuer; clients nunca veem fronteiras de
  shard.

### Bypass 5 — Gap no audit-log para enganar fraude

Se o audit log atrasa em relação ao real-time, uma rajada de alta velocidade de ataque pode
acontecer antes do detector de fraude ver o primeiro evento. Quando o sistema reage, minha
sessão já mudou.

**Counter:**
- Proteções baseadas em velocidade no próprio issuer, não só fraude downstream.
- Rate limits em `/auth/login` por IP, por usuário, por device.
- "Cooldown" na cadência de rotação por token — refresh não pode acontecer mais de N vezes em
  M minutos.

### Bypass 6 — Skew de versão de serviço

Durante rollout, metade da frota verifier roda v1.4 (entende `cnf`), metade roda v1.3
(ignora). Eu roteio minhas requisições para nós v1.3 via manipulação de session-affinity.

**Counter:**
- Feature flags gateadas por política do issuer, não por versão do verifier. Se `cnf` é
  exigido num token, verifiers v1.3 recusam o token *inteiro* porque não entendem `cnf` —
  fail-closed.
- Deploys forçados fleet-wide para features security-critical; sem janelas de skew de versão
  opt-in.

### Bypass 7 — Store de step-up challenge sob pressão de TTL

O store de step-up challenge é Redis com TTL eviction (60 s). Sob pressão de memória, Redis
pode evictar antes do TTL. Um challenge pré-emitido "fica inválido" não porque foi consumed
mas porque sumiu — verify não consegue distinguir esse caso do "tentativa de replay
legítima do usuário", se o servidor fail open ("se não encontrado, recuse com 'tente de
novo'"), minhas tentativas de replay são indistinguíveis de retries legítimos.

**Counter:**
- Dimensione o store com folga (pico de challenges ativos × 10).
- Use storage persistente com expiry agressivo em vez de eviction baseada em TTL.
- Telemetria em "challenge não encontrado em verify" — um spike é ou pressão no Redis ou
  ataque.

### Bypass 8 — Exaustão de quota do KMS

Se o issuer assina com KMS (todo sign de token = uma chamada KMS), e KMS tem uma quota por
segundo, eu posso DoS o issuer fazendo o tráfego legítimo exaurir a quota.

**Counter:**
- Assine localmente com uma data key estilo CKM derivada da chave KMS, refrescada a cada N
  minutos. O KMS é consultado pra *data key*, não cada token. Smith chamou isso de "envelope
  signing".
- KMS multi-region para redundância.
- Coloque o issuer atrás de um rate limit por IP para que eu não consiga dirigir a carga
  KMS de fora.

### Bypass 9 — Movimento lateral interno via secrets compartilhadas

Se o issuer e o verifier compartilham uma HS256 secret em env vars (Bypass 1 de
[01-stateless-auth.md](01-stateless-auth.md)), e o verifier está exposto a uma rede mais
ampla que o issuer, comprometer um único nó verifier me dá a chave de assinatura.

**Counter:**
- Asimétrico (RS256/ES256). Verifiers nunca seguram material que pode emitir tokens.
  Comprometer um verifier dá ao atacante nada que eles não pudessem já fazer com o JWKS
  público.

---

## Cross-reference

- **A forma do token que flui por essa arquitetura** → [01-stateless-auth.md](01-stateless-auth.md)
- **Ciclo de vida do refresh-token que esses stores gerenciam** → [06-token-lifecycle.md](06-token-lifecycle.md)
- **Claim cnf de device-binding** → [09-zero-trust.md](09-zero-trust.md)
- **Fontes de risk signal** → [05-play-integrity.md](05-play-integrity.md), [09-zero-trust.md](09-zero-trust.md)
- **Cert pinning verifier-side** → [08-network-warfare.md](08-network-warfare.md) (o princípio é o mesmo)

## Referências

- [Part 10 — The Staff Interview: System Design Mastery](https://blog.stackademic.com/part-10-the-staff-interview-system-design-mastery-d758710ac7a4)
- [RFC 7517 — JSON Web Key (JWK) / JWKS](https://datatracker.ietf.org/doc/html/rfc7517)
- [RFC 9449 — DPoP](https://datatracker.ietf.org/doc/html/rfc9449)
- [Google SRE Workbook — Hierarchy of Service Reliability](https://sre.google/workbook/reliable-product-launches/)
- [SPIFFE — Secure Production Identity Framework for Everyone](https://spiffe.io/)
