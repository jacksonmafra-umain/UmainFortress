# 03 — O padrão de interceptor: dominando concorrência de transporte

> "Cinco abas do seu app disparam uma requisição no mesmo instante. Todos os cinco tokens
> estão expirados. Sem um mutex, você acabou de deslogar o usuário." — *Fortress field notes*

**TL;DR** — Um auth interceptor ingênuo dispara um refresh por 401. Sob concorrência ele se
auto-imola: cada refresh revoga os refresh tokens dos outros, a linhagem de rotação parece
reuso para o servidor, e o usuário é expulso. Este arquivo passa pelo single-flight refresh em
[`AuthInterceptor`](../../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt),
por que um Mutex (não um CountDownLatch, não `synchronized`) é a ferramenta certa, e como o
mesmo fluxo derrota — ou habilita — alguns ataques.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Servir todos os 401s concorrentes com um único refresh | Causar uma tempestade de refresh que revoga a sessão do usuário |
| **Ideia central** | Mutex em torno do refresh; replay das requisições originais com o novo token | Race conditions no seu interceptor são presentes |
| **Pior falha** | Cada 401 dispara o próprio refresh → revogação em massa → usuário deslogado | Access tokens de vida longa sem rotação, sem defesa contra replay |

---

## 🛡️ Defensor — "Refresh uma vez, replay várias"

### O modo de falha sem mutex

Três chamadas paralelas chegam no servidor, todas com o mesmo access token expirado. Cada uma
toma um 401. Um interceptor ingênuo faz:

```
401 received → refresh → save tokens → replay original
```

Em paralelo, três vezes. O primeiro refresh tem sucesso e rotaciona o refresh token. O segundo
dispara com o refresh token (agora revogado) e o servidor diz: esse token foi usado por
alguém, **revogue a família inteira**. O terceiro faz o mesmo. O servidor está fazendo
exatamente o que deveria — detecção de reuso. Mas o usuário agora está deslogado, e ele só
abriu o app uma vez.

### O fix: single-flight via Mutex

[`AuthInterceptor.refreshIfNeeded`](../../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt):

```kotlin
private suspend fun refreshIfNeeded(staleSession: Session): Session? = refreshMutex.withLock {
    val current = tokenStore.current() ?: return null
    // Se outro caller já trocou o access token enquanto a gente esperou, usa o dele.
    if (current.accessToken != staleSession.accessToken) return current
    // ...roda o refresh contra /auth/refresh, salva a nova sessão, devolve ela
}
```

Três coisas importam aqui:

1. **`Mutex.withLock`** — só uma coroutine executa o corpo de cada vez. As outras suspendem
   até pegar o lock.
2. **O recheck dentro do lock** — quando você adquire o lock, alguém pode já ter feito
   refresh. Se `tokenStore.current()` mostra um access token diferente do que você entrou,
   refresh é desnecessário — devolve o existente.
3. **`runBlocking` para fazer ponte entre o interceptor síncrono do OkHttp e o mutex
   suspendendo** — a cadeia do interceptor do OkHttp roda síncrono numa thread worker, então
   precisamos fazer ponte para coroutines. `runBlocking` aqui é seguro porque já estamos numa
   thread fora da main.

O mutex é **process-wide estático** (`private val refreshMutex = Mutex()` no companion).
Precisa existir exatamente uma instância do mutex; senão, cada caller serializa contra ele
mesmo.

### Por que não `synchronized`?

`synchronized` bloqueia uma thread. Dentro dele queremos chamar uma função suspensiva (a
chamada de refresh do Ktor). Misturar os dois precisa de `runBlocking`, que funciona mas
segura a thread pela duração do round-trip de rede — e o dispatcher do OkHttp tem uma pool de
threads fixa. Queime o suficiente delas e o resto da sua stack de rede faz starvation.

`Mutex` coopera com coroutines: quando você `withLock`, outras coroutines podem usar o
dispatcher enquanto você espera. Numa ViewModel de Activity que processa muitas requisições,
a diferença é o gap entre "UI responsiva" e "ANR".

### Por que não `Channel<Unit>(1)` / `CountDownLatch`?

Dá pra usar — mas a semântica é mais desajeitada. Um Mutex é exatamente "um writer de cada
vez"; é o que a gente quer. Channels são úteis quando também é preciso fazer broadcast do
resultado pra N waiters ao mesmo tempo; aqui todo waiter segue o mesmo caminho (recheck →
return current), então sem broadcast.

### O fluxo, ponta a ponta

```
Caller A (request)  Caller B (request)  Caller C (request)
       │                  │                  │
       ▼                  ▼                  ▼
   401 (expirado)    401 (expirado)     401 (expirado)
       │                  │                  │
       ▼                  ▼                  ▼
  withLock {          withLock {         withLock {
    refresh()           recheck:           recheck:
    saveTokens          tokens             tokens
    return new          mudaram →          mudaram →
  }                     return             return
       │                  │                  │
       ▼                  ▼                  ▼
   replay w/          replay w/          replay w/
   new token          new token          new token
```

Exatamente um refresh de rede dispara. Os outros dois callers esperam em microssegundos,
depois replayam a requisição original com o token novo.

### A válvula de escape `SKIP_AUTH`

Algumas chamadas **não podem** disparar refresh em 401:

- O próprio refresh (senão é loop infinito).
- Login (sem token ainda para anexar ou refreshar).
- Endpoints anônimos (`/auth/policy`, `/health`).

[`AuthInterceptor.SKIP_AUTH`](../../app/src/main/java/com/umain/fortress/network/interceptor/AuthInterceptor.kt)
é um sentinel header por requisição. Callers o adicionam; o interceptor vê, retira, e devolve
a resposta sem mexer. O cliente Ktor `anonymous` em [`FortressHttpClient`](../../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
nem instala esse interceptor — não pode entrar em loop. Cinto e suspensório.

### E o body da requisição original?

OkHttp consegue replayar requisições GET / HEAD / DELETE trivialmente — os bodies são vazios
(ou bufferados). Para POST/PUT, o body original é um `RequestBody` — se for um stream de uso
único, o replay falha. Em produção, marque `RequestBody.isOneShot() == false` para bodies de
requisição sensíveis, ou bufferize-os. Os bodies da demo são todos JSON pequeno, que OkHttp
buffera por padrão.

### Por que isso vive em OkHttp e não em Ktor

Ktor tem o próprio plugin `Auth` com padrão single-flight similar. A gente empurra para
OkHttp porque:

1. O interceptor do OkHttp também roda para consumers que não são Ktor (carregamentos de
   imagem com Coil neste repositório).
2. Certificate pinning, logging e política de retry já vivem em OkHttp; auth combina com a
   camada.
3. O plugin do Ktor não casa tão limpo com uma ponte `runBlocking` quando você também quer um
   mutex process-wide compartilhado com fluxos de requisição que não são Ktor.

Se seu app é Ktor-only, o plugin `Auth` do Ktor é a escolha mais simples.

---

## ⚔️ Atacante — "Eu armo sua concorrência"

### Bypass 1 — Disparar uma tempestade de refresh

Se eu acho um vetor DoS não autenticado que faz o cliente disparar N requisições autenticadas
paralelas com o mesmo token expirado, e seu interceptor não tem mutex, eu causo N refreshes.
O primeiro tem sucesso, o resto é rejeitado por detecção de reuso, a linhagem é revogada, o
usuário deslogado. Eu não roubei nada — **neguei serviço** ao usuário legítimo.

**Counter:** o mutex. Tempestade de refresh vira um refresh, N replays.

### Bypass 2 — Race no swap do access token

Num interceptor bugado que:

1. Lê o access token antigo,
2. Executa um refresh que troca o access token,
3. Replaya a requisição original *com o token velho que ele capturou no passo 1*,

…o replay ainda dá 401. Pior, em algumas implementações, o replay dispara um segundo refresh
— alimentando o bypass 1.

**Counter:** o recheck dentro do mutex faz o passo 1 ler o token *atual*, e o caminho de
replay usa o fresco devolvido do lock.

### Bypass 3 — Replayar um refresh velho que eu pesquei antes

Se eu peguei um refresh token via um MITM antigo (antes de pinning), eu corro contra o
cliente legítimo. Se meu refresh chega primeiro, eu pego um par fresco; o refresh do cliente
legítimo fica revogado.

**Counter:**
- Pin certificates para que o MITM não tivesse sido possível em primeiro lugar
  ([08-network-warfare.md](08-network-warfare.md)).
- Detecte reuso no servidor: quando o refresh #N é usado duas vezes, **revogue a linhagem
  inteira** para esse par usuário × device. Os dois clients são chutados, usuário precisa
  re-logar. Telemetria expõe o evento para fraude.
- Atrele o refresh a uma chave pública bound ao device (claim `cnf`) para que meu refresh
  roubado falhe na checagem de device.

### Bypass 4 — Forçar o interceptor a entrar em loop

Se seu interceptor não tem uma válvula de escape para a própria chamada de refresh, chamar
`/auth/refresh` enquanto expirado produz: refresh falha 401 → interceptor refresha → refresh
falha 401 → ... queimando sua stack de rede e bateria até timeout.

**Counter:** o header `SKIP_AUTH`, ou — melhor ainda — um cliente separado para o caminho de
refresh que nem instala o interceptor. Este repositório usa os dois, cinto e suspensório.

### Bypass 5 — Roubar a *resposta* original antes do replay

Se o interceptor lê o body do 401 antes de decidir fazer refresh, e o body do 401 tem info
útil (token bound ao UID, nonce esperado, …), um atacante que consegue ler a resposta
descriptografada — TLS quebrado, servidor em plaintext — tem um side channel.

**Counter:** TLS mais pinning eliminam o side channel em plaintext. O interceptor fecha a
primeira resposta antes de retry (`firstResponse.close()`) para que conexões sejam
reaproveitadas eficientemente.

---

## Cross-reference

- **O que o servidor de refresh realmente faz** → [01-stateless-auth.md](01-stateless-auth.md) (rotação), [06-token-lifecycle.md](06-token-lifecycle.md) (detecção de reuso)
- **Por que um MITM não consegue pescar o token** → [08-network-warfare.md](08-network-warfare.md)
- **Onde o refresh token vive at rest** → [02-hardware-vault.md](02-hardware-vault.md)

## Referências

- [Part 3 — The Interceptor Pattern: Mastering Transport Concurrency](https://blog.stackademic.com/part-3-the-interceptor-pattern-mastering-transport-concurrency-dbe8eab8939b)
- [OkHttp — Interceptors](https://square.github.io/okhttp/features/interceptors/)
- [Kotlin coroutines — Mutex](https://kotlinlang.org/api/kotlinx.coroutines/kotlinx-coroutines-core/kotlinx.coroutines.sync/-mutex/)
