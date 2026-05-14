# 14 — RASP: Runtime Application Self-Protection

> "Análise estática diz pro atacante onde olhar. RASP te diz quando ele está olhando de
> volta." — *Fortress field notes*

**TL;DR** — Runtime Application Self-Protection (RASP) é a camada de **checagens dentro do
processo** que disparam enquanto seu app roda e sinalizam quando o próprio runtime foi
comprometido — Frida acoplado, debugger acoplado, framework de hooking carregado, emulador
detectado. RASP não consegue prevenir um atacante sofisticado uma vez que ele tem código no
processo; consegue **alertar** o servidor, forçar a sessão para um modo mais apertado, e
recusar as ações mais sensíveis. Este arquivo passa pela palette de probes, como combiná-las,
e como o atacante desarma cada uma.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Disparar um tripwire quando o processo é hospedado por um runtime hostil | Patchar os tripwires para que meus hooks rodem silenciosos |
| **Ideia central** | Um saco de probes baratas, sampladas, com o veredito mandado pro servidor | Qualquer checagem que o app faz, eu posso desabilitar de dentro do processo |
| **Pior falha** | Confiar num único boolean dentro do processo para gatear ações sensíveis | Checagens ingênuas que fail open em exceção (catch-all → "tudo bem") |

---

## 🛡️ Defensor — "Eu planto tripwires, reporto tripwires disparados"

### A palette de probes

```kotlin
data class RaspProbes(
    val frida: FridaProbe.Result,
    val debugger: DebuggerProbe.Result,
    val hooking: HookingProbe.Result,
    val emulator: EmulatorProbe.Result,
    val codeIntegrity: CodeIntegrityProbe.Result,
)
```

Cada probe devolve `Clean | Suspect(reasons: List<String>) | Tripped(reasons)`. O coletor
agrega e manda pro servidor via `POST /me/rasp/snapshot`. O **servidor** decide o que fazer
— nunca o cliente.

### Detecção de Frida

Indicadores comuns de Frida visíveis dentro do processo:

```kotlin
object FridaProbe {
    fun run(): Result {
        val reasons = mutableListOf<String>()

        // 1. Process maps — Frida injeta libfrida-agent.so ou frida-gadget
        runCatching {
            File("/proc/self/maps").readLines().forEach { line ->
                if (line.contains("frida") || line.contains("gum-js-loop")) {
                    reasons += "Process map contém agente Frida"
                    return@forEach
                }
            }
        }

        // 2. Named pipes — frida-server cria pipes em /data/local/tmp
        runCatching {
            File("/data/local/tmp").listFiles()?.forEach { f ->
                if (f.name.startsWith("frida-")) reasons += "Named pipe Frida em ${f.name}"
            }
        }

        // 3. TCP port 27042 (frida-server default) — listing de ports abertas
        runCatching {
            File("/proc/net/tcp").readLines().forEach { line ->
                // formato local_address: "0100007F:69A2" → 127.0.0.1:27042 em hex
                if (line.contains(":69A2")) reasons += "Port Frida default 27042 aberta"
            }
        }

        // 4. /proc/self/status TracerPid — Frida e gdb dão ptrace
        runCatching {
            File("/proc/self/status").readLines().firstOrNull { it.startsWith("TracerPid:") }
                ?.substringAfter(":")?.trim()?.toIntOrNull()?.let {
                    if (it != 0) reasons += "TracerPid=$it (ptrace acoplado)"
                }
        }

        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

Cada probe individual é bypassável — mas empilhar três ou quatro sobe o custo de bypass para
"script Frida-com-anti-detecção que mira nas suas probes específicas", o que significa que
você moveu o atacante para fora da tier "kit de script gratuito".

### Detecção de debugger

```kotlin
object DebuggerProbe {
    fun run(context: Context): Result {
        val reasons = mutableListOf<String>()
        if (Debug.isDebuggerConnected()) reasons += "Debugger JDWP conectado"
        if (Debug.waitingForDebugger()) reasons += "Esperando por debugger"
        // ApplicationInfo.FLAG_DEBUGGABLE deveria estar off em release.
        if ((context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            reasons += "App é debuggable (release build não deveria ser)"
        }
        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

### Detecção de framework de hooking

Xposed, LSPosed, Riru, EdXposed deixam artefatos:

```kotlin
val hookingPaths = listOf(
    "/system/framework/XposedBridge.jar",
    "/system/lib/libxposed_art.so",
    "/system/lib64/libxposed_art.so",
    "/system/etc/init.d/00xposed",
)
val knownHookingPackages = setOf(
    "de.robv.android.xposed.installer",
    "io.github.lsposed.manager",
    "io.va.exposed",
)
```

Detecte ou via presença em filesystem ou via `PackageManager.getInstalledPackages` (com a
ressalva de que Magisk DenyList esconde pacotes — veja [11-root-detection.md](11-root-detection.md)).

### Detecção de emulador

Features de device real que emuladores frequentemente perdem:

```kotlin
object EmulatorProbe {
    fun run(context: Context): Result {
        val reasons = mutableListOf<String>()
        if (Build.FINGERPRINT.startsWith("generic") || Build.FINGERPRINT.contains("sdk_gphone")) {
            reasons += "Build fingerprint parece emulator-like"
        }
        if (Build.HARDWARE.contains("goldfish") || Build.HARDWARE.contains("ranchu")) {
            reasons += "String de hardware de emulador"
        }
        // Emuladores modernos com Play Services podem ter uma lista de sensors fake-mas-plausível,
        // então checagens de absent-sensor não são confiáveis. Build properties são mais honestas.
        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

Cuidado aqui: emuladores de desenvolvimento shipam com Play Services completo e passam em
muitas dessas checagens ingênuas. Detecção de emulador é mais útil quando **combinada** com
o veredito `MEETS_VIRTUAL_INTEGRITY` do Play Integrity — quando os dois disparam, você está
confiante.

### Integridade de código

Detecte adulteração do próprio APK:

```kotlin
object CodeIntegrityProbe {
    fun run(context: Context): Result {
        val signers = runCatching {
            context.packageManager
                .getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
                .signingInfo
                ?.apkContentsSigners
        }.getOrNull() ?: return Result.Tripped(listOf("Não consegue ler signing info"))

        val installer = context.packageManager.getInstallSourceInfo(context.packageName)
            .installingPackageName
        val reasons = mutableListOf<String>()
        if (installer != "com.android.vending") reasons += "Installer é $installer (não é Play)"
        val sha = signers.first().toByteArray().sha256().toHex()
        if (sha != EXPECTED_RELEASE_SIGNER_SHA256) reasons += "Mismatch de fingerprint do signer"
        return if (reasons.isEmpty()) Result.Clean else Result.Tripped(reasons)
    }
}
```

### Compondo o veredito

Rode probes em cadência de baixa frequência (launch do app + a cada 5 minutos + em toda
operação sensível). Agregue para um `RaspVerdict`:

```kotlin
sealed class RaspVerdict {
    data object Clean : RaspVerdict()
    data class Suspect(val reasons: List<String>) : RaspVerdict()    // continua, mas aperta
    data class Tripped(val reasons: List<String>) : RaspVerdict()    // recusa ops sensíveis
}
```

Ligue no [`IntegrityCheck`](../../app/src/main/java/com/umain/fortress/security/IntegrityCheck.kt)
ao lado do Play Integrity e do sinal de device-binding, então o `SecurityChip` existente e a
risk engine consomem RASP transparentemente. O `DevModeStore` da demo do Fortress simula o
mesmo shape de veredito externamente (veja [docs/13-play-integrity-bypass.md](13-play-integrity-bypass.md))
para que o resto do app possa ser exercitado sem um Frida acoplado real.

### O que fazer quando RASP dispara

- **Suspect**: mande telemetria pro servidor com as reasons. Não diga pro usuário ainda —
  mantenha o atacante incerto sobre o que disparou.
- **Tripped**: recuse operações sensíveis (transferências, reveals), force step-up biométrico
  em ops read-only. Mostre um genérico "Não conseguimos verificar este device — tente
  depois" — não a razão. **Não imprima qual probe disparou** — isso entrega ao atacante a
  próxima coisa pra patchar.
- **Persistentemente Tripped** (3+ eventos numa sessão): servidor invalida a sessão, força
  re-login, marca o device-binding para revisão.

### O que RASP não consegue fazer

- **Parar** um atacante determinado. Se ele tem código no seu processo, ele consegue
  desabilitar suas probes. RASP é tripwire, não muro.
- **Substituir** enforcement server-side. Operações sensíveis *têm* que exigir prova
  server-attested da integridade do device (Play Integrity standard token bound à
  requisição). RASP é auxiliar.
- **Rodar pra sempre**. Cadência de probe deveria ser sampled, não contínua — rodar em toda
  recomposição do Compose é dreno de bateria e stream barulhento de telemetria.

---

## ⚔️ Atacante — "Eu desarmo seus tripwires enquanto fico no seu processo"

### Bypass 1 — Hooke a própria probe

Mais direto: meu script Frida
`Java.use("com.umain.fortress.security.FridaProbe").run.implementation = function() { return
Result.Clean }`. A probe devolve Clean independente da realidade.

**Counter:**
- Cross-check output da probe em múltiplos call sites. Se o site estático reporta Clean mas
  uma chamada via reflection dinâmica da mesma probe reporta Tripped, o caminho estático foi
  hookado.
- Compute resultados de probe a partir de dicas server-side também (Play Integrity diz
  virtual, RASP diz clean → contradição).
- Assine o resultado da probe, mande pro servidor, servidor decide. A probe hookada ainda
  deixa inconsistências de Play Integrity / assinatura de device-binding que o servidor
  consegue ver.

### Bypass 2 — Patchar o APK para no-op as probes

Decompila → edita smali → re-assina. A probe nunca roda.

**Counter:**
- Probe de integridade de código pega o re-sign (mismatch de cert de assinatura).
- `appIntegrity.appRecognitionVerdict` de Play Integrity rejeita binários não reconhecidos.
- Server-side: recuse qualquer sessão de um binário cujo cert digest não bate com o release
  cert registrado.

### Bypass 3 — Frida stalker / anti-anti-frida

Scripts anti-detecção do Frida que pró-ativamente spoofam `/proc/self/maps`, hookam a chamada
`File.readLines`, ou NX o código da probe. A corrida armamentista é real e a toolchain é
madura.

**Counter:**
- Múltiplas probes — scripts de anti-detecção tipicamente miram em *signatures conhecidas*
  de probe. Probes inline custom (não de bibliotecas RASP open-source) te dão variância de
  signature.
- Probes nativas: implemente o readline-and-grep em C++, onde hooking via Frida é mais
  difícil (ainda possível, mas mais fricção).
- Randomização de frequência: não probe em exatamente os mesmos offsets toda sessão.

### Bypass 4 — Framework de hooking targeted que se esconde

LSPosed em modo módulo consegue hookar seletivamente o alvo sem deixar artefatos usuais em
filesystem. Se seu RASP só checa `/system/framework/XposedBridge.jar`, você não vê LSPosed.

**Counter:**
- Múltiplos indicadores: mapa de memória do processo + ordem de carregamento de classes +
  reflection na cadeia parent do ClassLoader (Xposed mangula o ClassLoader parent).
- Combine com Play Integrity; ROMs com Xposed-mod tipicamente falham Strong.

### Bypass 5 — Emulador com properties hardware-faked

Emuladores Android Studio modernos (e Genymotion) shipam com `Build.HARDWARE = ranchu` mas
você pode editar o config do emulador para mentir sobre strings de hardware. De repente meu
emulador parece um Pixel.

**Counter:**
- `MEETS_VIRTUAL_INTEGRITY` de Play Integrity é a resposta canônica — o serviço de veredito
  do Google sabe o shape do secure element do emulador, independente das mentiras de
  userspace.
- Combine outputs de probe: emulador + acelerômetro faltando + sem stack de telefonia ainda
  é um sinal forte mesmo se `Build.HARDWARE` é fake.

### Bypass 6 — Disparar o caminho fail-open do RASP

Se seu código de probe tem um top-level `try/catch (Throwable) { return Result.Clean }`,
tudo que eu preciso é jogar exceção dentro da probe e seu defensor vê "clean".

**Counter:**
- Falhas de probe default para **Suspect**, não Clean. Uma exceção numa checagem de
  segurança é em si um sinal.
- Telemetria em tipos de exceção de probe e frequências — um spike de `SecurityException`
  do `File.readLines` é fingerprint de uma tentativa de hooking.

### Bypass 7 — Desabilitar RASP no nível de injeção de dependência

Se RASP é uma classe Koin/Hilt-bound, meu hook Frida intercepta o container DI e substitui
a instância de RASP por um stub que sempre devolve Clean.

**Counter:**
- O resultado de RASP que chega no servidor deve ser assinado com a chave de device-binding
  (TEE). Meu stub pode devolver Clean, mas minha assinatura forjada não verifica server-side.
- Servidor exige attestation RASP fresh em toda ação sensível.

### Bypass 8 — Rodar meu exploit num processo filho

Algum RASP só checa o processo principal. Se eu consigo fork ou spawn de instância Frida
sandboxed com namespace pid próprio, o `/proc/self/maps` do processo principal parece limpo.

**Counter:**
- Isso é mais difícil pro atacante do que parece no Android (sem fork plain do contexto de
  app), mas vale defender: probe a **UID inteira** ao listar processos (`pm list packages`
  para enumerar apps sob seu UID, `ps` para checar filhos inesperados).

---

## Cross-reference

- **O sinal de Play Integrity que complementa RASP** → [05-play-integrity.md](05-play-integrity.md), [13-play-integrity-bypass.md](13-play-integrity-bypass.md)
- **Por que detecção de root está no mesmo barco** → [11-root-detection.md](11-root-detection.md)
- **O que o atacante precisa fazer para nem entrar no processo** → [12-decompiling.md](12-decompiling.md)
- **A assinatura TEE-bound em que resultados RASP deveriam ser embrulhados** → [09-zero-trust.md](09-zero-trust.md)
- **Dev Mode simulando esses vereditos RASP** → [`DevModeScreen`](../../app/src/main/java/com/umain/fortress/ui/screens/devmode/DevModeScreen.kt)

## Referências

- [Exploring Android Protections — RASP Times](https://medium.com/@justmobilesec/exploring-android-protections-on-rasp-times-3d140e8df115)
- [Frida — Dynamic instrumentation toolkit](https://frida.re/)
- [LSPosed — A modern Xposed framework](https://github.com/LSPosed/LSPosed)
- [Android Developers — Debug.isDebuggerConnected](https://developer.android.com/reference/android/os/Debug#isDebuggerConnected())
- [OWASP MASVS — MSTG-RESILIENCE-1..13](https://mas.owasp.org/MASVS/)
