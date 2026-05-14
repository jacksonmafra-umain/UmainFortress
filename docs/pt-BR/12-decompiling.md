# 12 — Decompilação de APK: a arte sombria

> "Seu APK é um documento público no momento em que a Play Store entrega para um device. Tudo
> dentro dele — classes Kotlin, recursos, bibliotecas native, comentários nervosos do seu
> dev — está a um `unzip` e um `jadx-gui` de distância de ser lido em voz alta numa
> conferência." — *Fortress field notes*

**TL;DR** — Trate seu APK como **público**. Toda constante, todo endpoint, toda heurística de
detecção termina no IDE de alguém. O trabalho do defensor é (a) **reduzir** o que tem ali (R8
minification + resource shrinking), (b) **ofuscar** o que sobrevive (renomeação de R8,
aplicada seletivamente), e (c) **estratificar atrás disso** — porque nenhuma ofuscação torna
a análise estática difícil o suficiente para importar contra um atacante motivado. Os portões
reais vivem server-side.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Tornar a análise estática lenta o suficiente para empurrar atacantes para alvos mais fáceis | Ler a mente do app para achar portões desprotegidos |
| **Ideia central** | Tudo no APK é público; a ofuscação só compra tempo | Decompila, procura por `Auth`, `verify`, `pin` — comece por aí |
| **Pior falha** | Confiar que a ofuscação do R8 "esconde" alguma coisa | R8 desligado + chaves em plaintext em `BuildConfig` |

---

## 🛡️ Defensor — "Eu reduzo, ofusco, não confio em nada que é meu"

### O que de fato está no seu APK

```
app-release.apk
├─ AndroidManifest.xml         ← decompilado para XML plain
├─ classes.dex                 ← bytecode Kotlin/Java (jadx → ~código Kotlin)
├─ classes2.dex, …             ← multidex
├─ resources.arsc              ← recursos de string
├─ res/                        ← layouts XML, drawables
├─ lib/{arm64-v8a,…}/*.so      ← libs native (seu código NDK + deps transitivas)
├─ assets/                     ← não encriptado, qualquer um lê
└─ META-INF/                   ← assinatura, certificados
```

O atacante roda `apktool d app-release.apk` e pega uma árvore de source funcional em 30
segundos. Se seu código faz qualquer coisa *expressível* — checa uma string, lê uma property,
chama uma API — o atacante está lendo o mesmo código que você escreveu.

### R8 — o que faz e o que não faz

R8 (default do Android desde AGP 3.4) faz três coisas em builds `release`:

1. **Minification** — strip de classes/métodos/fields não usados. Reduz tamanho do APK em
   30-50%.
2. **Ofuscação** — renomeia classes/métodos/fields para `a, b, c, …` para tornar código
   decompilado mais difícil de ler.
3. **Otimização** — inline de métodos pequenos, constant-fold, dead-code-elimination.

O que R8 **não** faz:

- Não *esconde* lógica. O `a.b(c)` renomeado é tão funcional quanto
  `AuthRepository.login(email)`.
- Não ofusca strings (a menos que você adicione um pass de encriptação de string como
  StringObfuscator manualmente). Endpoints, aliases de chave, mensagens de erro — todos
  legíveis.
- Não muda comportamento semântico. Um reverse engineer determinado segue o data flow.

Para Fortress o objetivo é: **fazer `jadx-gui` levar 30 minutos para achar o portão de
segurança em vez de 30 segundos**. Os trinta minutos são o suficiente para o atacante ou
desistir ou ser pego por outras camadas (Play Integrity apertando, RASP sinalizando,
telemetria de fraude).

### Uma config R8 razoável

```
# proguard-rules.pro

# Mantém entry points (Application, MainActivity) para o manifest ainda resolver.
-keep class com.umain.fortress.FortressApplication { *; }
-keep class com.umain.fortress.MainActivity { *; }

# kotlinx.serialization precisa de reflection nos descritores gerados.
-keepclassmembers class **$Companion {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclasseswithmembers,allowobfuscation class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# Ktor usa reflection em alguns lugares; mantenha as factories de engine.
-keep class io.ktor.client.engine.** { *; }

# DTOs são serializadas — nomes importam no fio.
-keep class com.umain.fortress.network.dto.** { *; }

# Não strip a cobertura de logs debug em release se você precisar de stack traces.
# Comente isso só se você precisar especificamente de stack traces ofuscadas no Crashlytics.
# -dontobfuscate
```

A demo Fortress APK é uma build debug — sem minification. **Release production** habilitaria
R8 em [`app/build.gradle.kts`](../../app/build.gradle.kts):

```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(
            getDefaultProguardFile("proguard-android-optimize.txt"),
            "proguard-rules.pro",
        )
    }
}
```

### Não ponha secrets em `BuildConfig` achando que está seguro

Erro comum: `BuildConfig.API_KEY = "sk-fortress-prod-abc123…"`. R8 não ofusca essa string.
`jadx-gui` mostra no primeiro segundo.

A `BuildConfig.BASE_URL` do Fortress é intencionalmente **não** secret — é o URL público que
clientes batem. O material de chave de assinatura vive no KMS server-side; a private key de
device-binding vive na TEE. Nenhuma constante no APK é secret.

### Mapping files: amigo e inimigo

R8 produz um `mapping.txt` mapeando `a.b.C` de volta para `com.umain.fortress.NetworkLayer`.
Mantenha esse mapping para:

- **De-ofuscação de stack traces** em Crashlytics / Sentry. Sem ele, todo crash report é
  ruído.
- **Bug bounty / triage** — quando um pesquisador reporta uma vulnerabilidade, você precisa
  achar o código sobre o qual ele está falando.

Mantenha **fora** de:

- Artefatos públicos do CI.
- O próprio APK.
- Qualquer lugar acessível a um atacante.

Faça upload do `mapping.txt` para o Play Console (ele auto-deofusca Crashlytics) e armazene
uma cópia no seu secrets manager. Tag os mappings com o version code da build para poder
de-ofuscar uma stack trace de dois anos atrás.

### O que um atacante colhe, e o que fazer com cada coisa

| Achado no APK | Resposta do defensor |
|---|---|
| Endpoints de API em código | São públicos; HTTPS + cert pinning é o que protege, não secrecy. |
| Aliases de chave ("fortress.vault.tokens") | Não-sensíveis; as *chaves* estão na TEE. |
| Hashes SPKI de cert pin | Deveriam estar em código; é por desenho. |
| Thresholds de checagem de integridade ("score < 30 permite") | Mova thresholds para server-side. Cliente só renderiza o veredito. |
| Matemática de risk scoring | Mesma coisa — server-side. Cliente nunca sabe a política. |
| Debug logs / asserts | Strip em release. R8 com `Timber` strippa chamadas `Timber.d` quando configurado. |
| Credenciais de teste em recursos | Audite seu `BuildConfig`; nunca shipe creds de dev. |
| Endpoints admin do backend | O app mobile não deveria saber sobre rotas admin. Ponto. |

### Assinatura de app — a história v1/v2/v3/v4

| Schema | O que assina | Ameaça que para |
|---|---|---|
| v1 (JAR signing) | Cada entry, por path de arquivo | Modificar um arquivo no APK |
| v2 (signing block do APK inteiro) | O conteúdo do APK inteiro | Adulteração em qualquer lugar |
| v3 (suporte a rotação de chave) | Igual ao v2 + uma prova de linhagem | Rotacionar sua chave de assinatura sem quebrar instalações existentes |
| v4 (assinatura incremental por arquivo) | Hashes por bloco | Install mais rápido para streaming installs (Android 11+) |

Sempre assine com **v2 + v3** no mínimo. A linhagem de chave do v3 significa que você pode
rotacionar sua chave de assinatura no futuro sem perder a habilidade de shipar updates para
usuários existentes.

Para Play Store: faça enroll em **Play App Signing** para o Google segurar a chave canônica de
assinatura. Sua upload key é por developer; Google assina o binário final. Aí sua chave local
de assinatura nunca precisa ser a âncora de longo prazo.

### Detectando adulteração em runtime — a camada que R8 não fornece

```kotlin
fun isOurSignature(context: Context): Boolean {
    val signers = context.packageManager
        .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        .signingInfo
        ?.apkContentsSigners
        ?: return false
    val expected = "AB:CD:EF:…(SHA-256 do Play Console)"
    val actual = signers.first().toByteArray().let { sha256(it).toHex() }
    return actual.equals(expected, ignoreCase = true)
}
```

Chame isso no launch do app; se devolver false, recuse iniciar a camada de rede. Combine com
`appIntegrity.certificateSha256Digest` do Play Integrity para o equivalente server-side.

### Bibliotecas nativas

Se você shipa um `.so` (código NDK), ele ainda está no APK e um atacante determinado pode
rodar `objdump` / `Ghidra` / `IDA Pro` nele. Native não é um ofuscador mágico — é uma
linguagem diferente que leva mais tempo para ler mas não é mais secret. Não ponha lógica lá
esperando que fique escondida; ponha lógica lá se você tem uma razão de *performance* e quer
um custo de reverse engineering um pouco maior como efeito colateral.

---

## ⚔️ Atacante — "Eu leio sua mente em cinco passos"

### Bypass 1 — `apktool d app-release.apk`

```
$ apktool d app-release.apk -o fortress/
I: Using Apktool 2.10.0 …
I: Loading resource table …
I: Decoding AndroidManifest.xml …
I: Loading resource table from file: /apktool/framework/1.apk
I: Decoding file-resources …
I: Decoding values */* XMLs …
I: Baksmaling classes.dex …
I: Baksmaling classes2.dex …
I: Copying assets and libs …
$ ls fortress/smali/
com/  io/  kotlin/  androidx/  …
```

Agora eu tenho smali — assembly Dalvik legível. Procuro `BiometricPrompt`, `cipher`,
`Authorization` para achar os caminhos de segurança.

### Bypass 2 — `jadx-gui` para leitura de mais alto nível

```
$ jadx-gui app-release.apk
```

O painel de classes mostra a árvore de-ofuscada como Java recuperado. Mesmo com ofuscação R8,
o *shape* do código é preservado. Procuro pelas strings que sei que devem existir: paths
HTTP, títulos de prompt, reasons de deny-list. Cada busca me leva ao código relevante.

Para Kotlin especificamente, `jadx` decompila para Java legível que re-mimica as máquinas de
estado de coroutines do Kotlin razoavelmente bem — você consegue seguir o fluxo `suspend`.

### Bypass 3 — Grep de string estática

```
$ jadx --output-dir src app-release.apk
$ grep -r "rooted\|magisk\|integrity\|isJailbroken\|BiometricPrompt" src/
src/.../security/RootDetector.java:    boolean rooted = checkSu();
src/.../auth/StepUpAuthenticator.java:    BiometricPrompt prompt = …
```

Em 30 segundos do decompile eu tenho as localizações de toda checagem de segurança que o
app executa localmente. A partir daí ou:

- **Patcho a checagem** com edição smali + apktool b + assino (veja Bypass 5).
- **Hooko a função** com Frida (veja [14-rasp-strategies.md](14-rasp-strategies.md)).

### Bypass 4 — Colheita de recursos

`strings.xml`, nomes de drawable, XML de layout — todos sem ofuscação. Frequentemente revela:

- Feature flags internas não stripados em release.
- Strings de modo de teste ("DEMO BUILD — DO NOT SHIP").
- Fragmentos de URL admin escondidos (você esperaria que não, mas).

```
$ grep -r "demo\|test\|admin\|debug" fortress/res/values/
```

### Bypass 5 — Patch + reempacotar + reassinar

```
$ # Patcha o smali
$ sed -i 's/iput-boolean v0, p0, .*->rooted:Z/iput-boolean v1, p0, …->rooted:Z/' \
  fortress/smali/com/umain/fortress/security/RootDetector.smali

$ # Rebuild
$ apktool b fortress -o patched.apk

$ # Assina com minha própria chave
$ keytool -genkey -keystore my.keystore -alias attacker -keyalg RSA -keysize 2048 -validity 365
$ apksigner sign --ks my.keystore --ks-key-alias attacker patched.apk

$ adb install -r patched.apk
```

O app instala, roda, e nunca vê a própria checagem de root falhar. **Da minha perspectiva o
app está patchado em cinco minutos.**

**Counters do defensor:**
- Integridade server-side (Play Integrity verifica o digest do certificado — o mismatch da
  assinatura do meu APK patchado é detectado).
- Self-signature check em runtime (per o snippet "Detectando adulteração em runtime" acima).
- Biblioteca anti-tampering combinando: checagem de assinatura, checagem de integridade de
  código, checagem do `installer package` (só `com.android.vending` deveria ter instalado
  seu app), detecção de Frida.

### Bypass 6 — Leak de mapping

Se seu `mapping.txt` termina num artefato público de CI, página de release do GitHub, ou
shipado acidentalmente no `assets/` do APK, todo o trabalho de ofuscação R8 é desfeito. Eu
pego os nomes reais das classes.

**Counter do defensor:** trate `mapping.txt` como uma credencial. Não coloque onde os
artefatos da sua build são públicos.

### Bypass 7 — Lendo libs nativas

```
$ readelf -a lib/arm64-v8a/libnative-auth.so
$ objdump -dM intel lib/arm64-v8a/libnative-auth.so | less
```

Ghidra / IDA Pro decompilam libs nativas de volta para source C-ish. Mais lento que decompile
Kotlin mas não é mágico.

**Counter do defensor:** não espere que native esconda lógica. Se você realmente precisa de
custo mais alto, olhe ofuscadores comerciais (DexGuard, Promon Shield) — eles sobem o preço
de forma significativa por um a dois ciclos de release, depois os bypasses circulam.

### Bypass 8 — `strings(1)` para o preguiçoso

```
$ strings app-release.apk | grep -iE "api[._-]?key|secret|token|password"
```

Pega tudo de `AWS_SECRET` shippado acidentalmente a leftover em comentário interno ("TODO
REMOVER ANTES DE PROD"). 30 segundos, sem decompile necessário.

**Counter do defensor:** lint-check release builds no CI para padrões de string suspeitos.
Falhe a build, não a demo.

### Bypass 9 — Diff contra o source público

Se seu app open-sourcea uma *porção* dele (um SDK que você mantém, um módulo Compose-extras),
eu posso fazer diff contra o open source para achar as partes proprietárias — é onde sua
lógica de negócio vive, e é isso que eu quero.

**Counter do defensor:** open-source as *capabilities*, não o *código da aplicação*. Uma
biblioteca de rede reusável está OK publicar; o fluxo de auth real no seu app não está.

---

## Cross-reference

- **Portões server-side que sobrevivem ao patching do APK** → [01-stateless-auth.md](01-stateless-auth.md), [05-play-integrity.md](05-play-integrity.md), [09-zero-trust.md](09-zero-trust.md)
- **Anti-hooking / anti-tampering em runtime** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **O que o atacante também quer do fio** → [08-network-warfare.md](08-network-warfare.md)
- **Root + hooks no nível do processo** → [11-root-detection.md](11-root-detection.md)
- **Content providers como side-channel para o APK** → [16-content-providers.md](16-content-providers.md)

## Referências

- [From APK to Source Code: The Dark Art of App Decompiling (2025)](https://medium.com/@vaibhav.shakya786/from-apk-to-source-code-the-dark-art-of-app-decompiling-explained-2025-edition-7f28fc2dee0f)
- [jadx — Dex to Java decompiler](https://github.com/skylot/jadx)
- [Apktool](https://apktool.org/)
- [Android Developers — R8 shrink, obfuscate and optimize](https://developer.android.com/build/shrink-code)
- [Play App Signing](https://support.google.com/googleplay/android-developer/answer/9842756)
- [APK Signature Scheme v2/v3/v4](https://source.android.com/docs/security/features/apksigning)
