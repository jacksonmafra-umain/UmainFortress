# 08 — Guerra de rede: defesa contra MITM e certificate pinning

> "TLS sem pinning é um aperto de mão amigável com qualquer certificado que o usuário aceitou
> sem querer." — *Fortress field notes*

**TL;DR** — TLS prova que o servidor é "alguém legítimo" — mas a trust store diz "legítimo"
significa qualquer um com um cert válido de qualquer uma das ~150 CAs (mais o que o usuário
adicionou). Pinning estreita isso para "a issuer que o Fortress realmente usa". Este arquivo
passa pelo setup do `CertificatePinner` do OkHttp, pelos trade-offs (dor de rotação, app
brickado se você errar), e pelo toolkit do atacante de mitmproxy a certs root instalados pelo
usuário a BURP.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Recusar falar com qualquer cadeia de cert que a gente não esperava | Sentar entre o app e o servidor, ler/modificar tudo |
| **Ideia central** | Validar a cadeia contra uma lista estática de hashes SPKI | Instalar uma CA, torcer pro app confiar na user store |
| **Pior falha** | Confiar na user store em release builds | Pinar um cert específico que você não consegue rotacionar |

---

## 🛡️ Defensor — "Eu só acredito em um conjunto de fingerprints"

### Pinning na camada certa

[`FortressHttpClient.buildPinner`](../../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
devolve um `CertificatePinner` do OkHttp. O cliente Ktor herda isso de graça porque os dois
clients compartilham o engine do OkHttp. Pinning na camada do OkHttp significa que **toda**
chamada HTTPS (Ktor, carregamentos de imagem com Coil, OkHttp manual) é protegida pela mesma
política.

### Pin o hash SPKI, não o cert

```kotlin
CertificatePinner.Builder()
    .add("api.fortress.bank",
        "sha256/AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=",  // leaf
        "sha256/BBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB=",  // intermediate (backup)
        "sha256/CCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC=")  // intermediate (backup #2)
    .build()
```

- **Hash SPKI (Subject Public Key Info)**, não hash do cert inteiro. SPKI sobrevive a
  renovações que reusam o keypair; hashes de cert não.
- **Múltiplos pins** — no mínimo: leaf atual + intermediate(s) de backup. RFC 7469
  (deprecated para browsers, ainda útil como modelo mental) recomenda N+1 pins.
- **Pin de backup off-line / fora da frota** — mantenha um num cofre para você poder
  rotacionar quando o primário precisar mudar.

Na build da demo o conjunto de pins é intencionalmente vazio — o backend é local, sem HTTPS.
O deploy de produção deve embutir pins no build e ter um playbook de rotação.

### Por que não Trust Manager substituído?

Você pode escrever um `X509TrustManager` custom para fazer a mesma coisa. O `CertificatePinner`
do OkHttp é:

- Menos código.
- Validado contra a cadeia de cert pós-TLS-handshake (depois da checagem da própria cadeia do
  OkHttp).
- Compositivo com debug overrides — release builds aplicam estrito, debug builds podem ser
  conectados a mitmproxy via [`network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml).

### Network security config

[`network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml):

```xml
<base-config cleartextTrafficPermitted="false">
    <trust-anchors><certificates src="system" /></trust-anchors>
</base-config>

<domain-config cleartextTrafficPermitted="true">
    <domain includeSubdomains="true">10.0.2.2</domain>
</domain-config>

<debug-overrides>
    <trust-anchors>
        <certificates src="system" />
        <certificates src="user" />
    </trust-anchors>
</debug-overrides>
```

As propriedades relevantes:

- **`cleartextTrafficPermitted="false"`** em `base-config` — produção recusa HTTP. A exceção
  de dev local é só `10.0.2.2`.
- **`<trust-anchors>` exclui `user`** em `base-config` — mesmo que o usuário instale uma CA,
  a release build do app não confia. mitmproxy / Burp precisam de uma CA de nível de
  sistema, que exige root.
- **`<debug-overrides>` adiciona `user`** — debug builds CONFIAM na user store, então o mesmo
  toolchain pode ser ligado pela sua equipe de QA para testes sem afetar comportamento de
  release.

O bloco `<debug-overrides>` é stripado de APKs `release` em merge-time. O manifest de release
nunca vê isso.

### Verifique o manifest mergeado

Após cada build de release, confirme que o manifest mergeado não inclui `<debug-overrides>` e
que `cleartextTrafficPermitted` é `false` em todo lugar que importa:

```bash
$ apkanalyzer manifest print build/outputs/apk/release/app-release.apk \
    | grep -A2 'networkSecurityConfig\|cleartextTrafficPermitted'
```

Isso pertence ao CI. Manifests deslizam; checá-los mecanicamente é o único jeito de saber.

### Playbook de rotação

Quando seu TLS leaf precisa rotacionar:

1. **Seis semanas antes**: adicione o pin do *novo* leaf ao lado dos pins atuais. Ship uma
   release.
2. **No cutover**: mude DNS / load balancer para servir o cert novo. Clients existentes
   aceitam qualquer um.
3. **Depois do cert novo estar vivo por um ciclo de TTL**: remova o pin antigo. Ship uma
   release.

A janela entre passo 1 e passo 3 é sua zona segura de rollback. Sem pins de backup, um
cutover mal feito brica o app pra todo mundo até atualizarem.

### `MaxAge` e desastres de pin estagnado

HPKP de browser morreu porque um header `Public-Key-Pins` mal configurado com um `max-age`
longo podia bricar um domínio. O equivalente mobile é shipar um conjunto de pins sem backups.
Se o leaf precisa rotacionar em estilo emergência (CA comprometida, revogação de cert), e seu
único pin é o leaf, você **não tem como atualizar o app** sem usuários efetivamente baixarem
uma release. O app deles só para de funcionar.

**Sempre**: pelo menos um pin de *backup* atrelado a um intermediate ou keypair de backup.
Mantenha a private key de backup offline. O propósito do pin de backup é exatamente a
emergência.

### Blob de download via app-bundle

Pins são tipicamente fornados em build-time. Se você opera em escala fintech e precisa de
atualizações *dinâmicas* de pin (um leak força uma rotação imediata), sirva um pin set
assinado:

- Servidor publica `pins-v1.json` assinado por uma chave de assinatura offline.
- App no launch busca, verifica a assinatura contra uma public key embutida, cacheia.
- Se a assinatura falhar, fallback para o conjunto baked-in.

Isso adiciona um problema de bootstrap (você tem que *pegar* o pin set de algum jeito), mas
deixa você rotacionar sem updates do app. Vale só se sua cadência de rotação é mais rápida
que a cadência de release do seu app.

---

## ⚔️ Atacante — "Eu sento no seu fio"

### Bypass 1 — Instalar minha CA na user store, torcer pra você confiar

Em qualquer telefone Android não rootado eu posso instalar uma CA na user store. Se o
`networkSecurityConfig` do app alvo confia em `user` em `base-config`, mitmproxy / Burp /
Charles funciona out of the box. Eu vejo cada requisição, cada resposta, cada token.

**Counter:** não confie na user store em `base-config`. Coloque em `<debug-overrides>` só.

### Bypass 2 — Rootar o device e adicionar minha CA na system store

Agora o app *confia* em mim, independente da config — desde que ele não pinhe.

**Counter:** pinhe. A conexão TLS completa (o cert é "válido"), mas o `CertificatePinner` vê
uma cadeia que não bate com o pin set e derruba a conexão.

### Bypass 3 — Patch do CertificatePinner via Frida

Se eu tenho execução de código dentro do app (root + Frida), eu consigo
`Java.use("okhttp3.CertificatePinner")` e substituir `check$okhttp` por um no-op. Agora minha
CA é confiada de novo.

**Counter:**
- RASP: detecte injeção de Frida, recuse iniciar a camada de rede. Veja
  [14-rasp-strategies.md](14-rasp-strategies.md).
- Veredito de Play Integrity exigido no resume da sessão — um processo adulterado deveria
  falhar integrity.
- Atrele operações sensíveis a uma chave TEE-residente — patchar o pinner não me ajuda a
  extrair a chave. Mesmo se eu leio todo o tráfego, operações de assinatura ainda exigem a
  TEE.

### Bypass 4 — Substituir OkHttp em build time

Eu entro no seu CI, troco sua dependência do OkHttp por um fork que ignora pins, builda a
release. Agora nada protege o fio.

**Counter:**
- Integridade do CI. Assine seu pipeline de build; exija provenance.
- Certs de assinatura da app store (você assina release builds, não só o CI).
- Verifique reproducible builds onde for possível.

### Bypass 5 — Bypass via VPN-com-mitmproxy

Configure a VPN do device para rotear pelo mitmproxy com uma CA que o usuário confiou. Mesmo
resultado do Bypass 1, transport diferente.

**Counter:** pinning combate isso do mesmo jeito que Bypass 1. O caminho da CA não importa
— só o fingerprint SPKI no fim da cadeia.

### Bypass 6 — Downgrade para HTTP

Se `cleartextTrafficPermitted="true"` existe em qualquer lugar com o qual seu app pode falar,
eu vou achar. Misconfigurations: URLs de dev que escapam para release builds; CDNs de imagem
servidas por HTTP; hosts de API legacy.

**Counter:**
- `cleartextTrafficPermitted="false"` no topo.
- Exceções domain-specific só para hosts de dev conhecidos (loopback/emulador).
- Asserção de CI sobre o manifest de release mergeado.

### Bypass 7 — Downgrade de TLS 1.0 / 1.1 / cipher

Se o servidor aceita TLS antigo ou RC4 / 3DES, eu derrubo e quebro a crypto.

**Counter:** problema do servidor majoritariamente. Client-side: configure OkHttp com
`ConnectionSpec` restringindo a `MODERN_TLS` só. Recuse qualquer coisa abaixo de TLS 1.2;
prefira TLS 1.3.

### Bypass 8 — Overrides de `setSSLSocketFactory`

Em qualquer lugar do seu codebase que constrói um `OkHttpClient` com um `SSLSocketFactory`
custom que não carrega a política de pinning é um buraco. Bug comum: um cliente OkHttp
*separado* para analytics / carregamento de imagem que pula pinning.

**Counter:** centralize a construção do OkHttp. O [`FortressHttpClient`](../../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
deste repositório expõe dois clients (anonymous + authenticated) — todo consumer flui por
ele.

---

## Cross-reference

- **Onde a política de pin é aplicada** → [`FortressHttpClient`](../../app/src/main/java/com/umain/fortress/network/FortressHttpClient.kt)
- **Que network-config importa em install time** → [`AndroidManifest.xml`](../../app/src/main/AndroidManifest.xml), [`network_security_config.xml`](../../app/src/main/res/xml/network_security_config.xml)
- **O que ainda funciona quando o fio é comprometido** → [07-biometric-hardening.md](07-biometric-hardening.md) (ações assinadas pela TEE)
- **Como um atacante identifica um client não pinhado** → [12-decompiling.md](12-decompiling.md)
- **Anti-hooking** → [14-rasp-strategies.md](14-rasp-strategies.md)

## Referências

- [Part 8 — Network Warfare: MITM Defence, Certificate Pinning](https://blog.stackademic.com/part-8-network-warfare-mitm-defense-certificate-pinning-8abeb5685aae)
- [OkHttp — CertificatePinner](https://square.github.io/okhttp/4.x/okhttp/okhttp3/-certificate-pinner/)
- [Android Developers — Network security configuration](https://developer.android.com/training/articles/security-config)
- [Android Developers — Trust Manager and certificate pinning](https://developer.android.com/privacy-and-security/security-ssl)
