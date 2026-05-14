# 15 — Rootando o emulador do Android Studio com KernelSU (Apple Silicon)

> "Um emulador rootado no seu laptop é um sparring partner sem passagem aérea. Use para
> achar o que seu app sussurra sobre si mesmo antes que um atacante ache." — *Fortress
> field notes*

**TL;DR** — Em Apple Silicon (M1 / M2 / M3 / M4), o emulador AVD do Android Studio roda um
guest ARM64 nativo e pode ser rootado via **KernelSU** — um provider de su em nível de
kernel que vive numa boot image patchada. Diferente do Magisk, KernelSU tem footprint
mínimo em userspace e é adequado para workflows de emulador onde você quer uma bancada de
teste limpa para Frida / proxy / RASP. Este arquivo passa pelo setup, pelo workflow, e pelo
que KernelSU faz (e **não** faz) bypass nas defesas do Fortress.

| | 🛡️ Defensor | ⚔️ Atacante |
|---|---|---|
| **Objetivo** | Ter um ambiente de dev rootado para validar que cada camada de defesa dispara | Pular a fricção de um telefone rootado físico para fuzzing |
| **Ideia central** | Use emuladores KernelSU para testar a *visão do atacante* do seu próprio app | Probes em userspace são fracas; patches em nível de kernel deixam eu rodar com privilégio maior |
| **Pior falha** | Acreditar que sucesso no emulador significa que produção está segura | Esquecer que Play Integrity vê o bootloader não verificado |

---

## 🛡️ Defensor — "Eu mantenho um emulador sparring no laptop"

### Por que um emulador rootado vence um telefone rootado para desenvolvimento

| Preocupação | Device físico rootado | Emulador rootado (KernelSU) |
|---|---|---|
| Tempo de provisão | Comprar/desempacotar/desbloquear/flashar — horas | Subir um AVD fresh — minutos |
| Reprodutibilidade | Cada device é diferente | Mesma imagem, mesmo estado, toda vez |
| Snapshot / reset | Factory reset, lento | snapshot do `avdmanager`, instantâneo |
| Custo | $$ | Grátis (emulador + KernelSU image) |
| Passagem aérea | A device farm do CI está no Texas | Seu laptop |
| Fidelidade ao ataque real | Alta | Média — Play Integrity se comporta diferente |

Para o dev local do Fortress: um emulador KernelSU é o jeito **default** de verificar que:
- O cert pinner recusa uma CA instalada por mitmproxy.
- O `CryptoObject` biométrico se recusa a assinar sem uma cerimônia biométrica real.
- O veredito Play Integrity nesse emulador cai para `MEETS_VIRTUAL_INTEGRITY` só —
  disparando o fallback read-only na nossa política.
- Probes RASP disparam em acoplagem de Frida.

### Setup rápido (Apple Silicon)

O [KernelSU-Next](https://kernelsu-next.github.io/webpage/) mantido pela comunidade shipa
boot images pré-construídas para o kernel do emulador AOSP.

```bash
# 1. Instale a imagem de emulador AOSP — escolha uma versão com arm64-v8a Google APIs.
#    Do Android Studio → SDK Manager → SDK Platforms → Show Package Details.
#    Pegue ex: "Android 14.0 ARM 64 v8a Google Play Intel x86_64" (sim, o label é
#    confuso; a imagem ARM v8a roda nativa em M-series).

# 2. Crie o AVD.
avdmanager create avd -n fortress-rooted -k "system-images;android-34;google_apis;arm64-v8a"

# 3. Suba uma vez para popular /sdcard etc., depois desligue.
emulator -avd fortress-rooted -no-snapshot

# 4. Baixe a boot.img KernelSU correspondente da página de releases do KernelSU-Next.
#    Bata com a versão de kernel reportada por `adb shell uname -a` no emulador rodando.

# 5. Reboote para fastboot e flashe a boot.img patchada.
adb reboot bootloader
fastboot flash boot ksu-boot-<version>.img
fastboot reboot

# 6. Instale o APK do KernelSU Manager no emulador.
adb install KernelSU-Manager.apk

# 7. Abra o KernelSU Manager — deveria mostrar "Working" com status root verde.
```

O resultado: um emulador AOSP de aparência stock com root disponível via o shim su do
KernelSU.

### Validando o Fortress no emulador rootado

```bash
# Inicie o backend local.
cd backend && npm run dev &

# Tunelize para uma URL pública que o emulador alcança (a rede do emulador é virtualizada).
./gradlew fortressTunnel

# Instale o Fortress e lance.
./gradlew :app:installDebug
adb shell am start -n com.umain.fortress/.MainActivity
```

Comportamento esperado:

| Camada | Esperado no emulador KernelSU |
|---|---|
| Launch + splash | Carrega, probe de integridade completa |
| `IntegrityCheck.current()` (impl real) | Devolveria `Untrusted` se Play Integrity diz virtual |
| Login | Sucesso — fluxos read-only não são gateados em Strong |
| Biometric unlock | Exige "extended controls → fingerprint" do emulador configurado |
| IBAN reveal step-up | Deveria ter sucesso se device-binding key foi enrolada no login |
| Transferência | Deveria ter sucesso para baixo valor; gateado para alto valor quando Strong é exigido |

Rode mitmproxy ao lado para confirmar que cert pinning recusa a CA inserida:

```bash
# Em outro terminal
mitmproxy -p 8889

# Instale a CA do mitmproxy no emulador via system trust store (KernelSU te deixa remontar /system).
# Sem pinning, requisições aparecem no mitmproxy.
# Com pinning (default do Fortress), requisições falham no setup da conexão.
```

### O que KernelSU NÃO muda

- **Estado de bootloader é não verificado** para Play Integrity. `MEETS_DEVICE_INTEGRITY` e
  `MEETS_STRONG_INTEGRITY` não serão concedidos a um emulador KernelSU.
- **Cadeias de attestation hardware** são emuladas em software e rolam para um root que
  Google sabe que é virtual.
- **Chaves TEE** ainda não podem ser exportadas. Os bytes assinados do fluxo step-up
  biométrico ainda são inforjáveis, mesmo num emulador rootado.

Em resumo, KernelSU te leva a "emulador que pode fazer operações de filesystem como root,
rodar Frida, interceptar syscalls". Não te leva a "emulador que passa Play Integrity Strong".
O piso duro ainda se mantém.

### Quando essa é a ferramenta certa

- **Verificar defesa em profundidade.** "O SecurityChip fica vermelho quando integrity diz
  virtual? O fluxo de transferência recusa?"
- **Rodar Frida acoplagens sem cabo USB.** Ótimo para probing ad-hoc.
- **Reproduzir problemas reportados por clientes** que só acontecem em devices rootados.
- **Testes de integração no CI** — embora para CI o [`fortressTunnel`](../../scripts/start-local-tunnel.sh)
  + emuladores hospedados estilo Genymotion são geralmente mais simples que um AOSP
  patchado com KernelSU.

### Quando essa é a ferramenta errada

- **Confirmar segurança production-ready.** Um Pixel real com bootloader locked, Strong
  integrity, e um secure element hardware é o ambiente de teste canônico. O emulador pode
  *disparar* defesas; não consegue *provar* que estão corretamente atreladas ao hardware em
  devices reais.
- **Escopo de penetration test.** Engagements externos de pentest deveriam usar devices
  físicos por fidelidade.

---

## ⚔️ Atacante — "Eu uso a mesma ferramenta que o defensor, só que diferente"

### Bypass 1 — Use KernelSU para Frida + acesso ao filesystem grátis

Eu instalo KernelSU num device real (não num emulador), depois acoplo Frida e rodo minhas
probes. O footprint de userspace do KernelSU é menor que o do Magisk, então detecção
ingênua de root estilo `which su` pode perder.

**Counter:**
- Play Integrity vê bootloader desbloqueado independente do KernelSU estar "escondido" em
  userspace. Strong integrity recusa.
- Detecção de root moderna foca em attestation hardware, não scans de binário `su` (veja
  [11-root-detection.md](11-root-detection.md)).

### Bypass 2 — Combine KernelSU com spoofing de fingerprint PIF / Zygisk

KernelSU + um fork Zygisk-compatible + módulo Play Integrity Fix pode às vezes pegar
veredito Device numa ROM properly-locked-but-modified. Veja [13-play-integrity-bypass.md](13-play-integrity-bypass.md).

**Counter:**
- Exija Strong para ops sensíveis. KernelSU + PIF não consegue forjar Strong de forma
  confiável.

### Bypass 3 — Patchar a probe RASP de detecção de emulador

Se o app usa `Build.HARDWARE == "ranchu"` como fast-path de checagem de emulador, eu edito
`/system/build.prop` (KernelSU me deixa remontar /system rw) para mentir sobre strings de
hardware, depois rodo o app.

**Counter:**
- Veja [14-rasp-strategies.md](14-rasp-strategies.md). Camadas de probe; pareie com Play
  Integrity `MEETS_VIRTUAL_INTEGRITY`, que a hardware abstraction layer do emulador revela.

### Bypass 4 — Strace seu próprio app para dicas de instrumentação

```bash
adb shell su -c "strace -p $(pidof com.umain.fortress) -e openat 2>&1" \
  | grep -iE "/proc/self/maps|/data/local/tmp"
```

Vejo exatamente quais paths suas probes RASP leem, então sei quais paths spoofar.

**Counter:**
- Essa é a fase de reconnaissance do atacante. Strace é observável de dentro do processo
  também (`ptrace`-acoplado → `TracerPid > 0`). As probes de Debugger / Frida pegam isso.

### Bypass 5 — Snapshot de longa duração de um momento attested

Sobe o emulador limpo → enrola device-binding → tira um snapshot do AVD. Aí para cada test
run, restaure o snapshot — toda run começa com um enrolment válido (mas stale).

**Counter:**
- Refresh tokens rotacionam; o refresh token do snapshot stale é uso único e queima no
  primeiro refresh. O próximo refresh do snapshot falha em detecção de reuso.
- Challenges de device-binding-key são bound a nonce; replayar a assinatura do snapshot
  contra um nonce novo falha na checagem de assinatura.

### Bypass 6 — Patchar a view do `/proc/self/maps` do Frida

KernelSU + um kernel module pode interceptar o open(2) em `/proc/self/maps` e alimentar o
caller do Frida com uma view redacted. A probe userspace do RASP vê um mapa limpo.

**Counter:**
- Probes em código native (NDK) que pulam a interceptação no nível da libc.
- Não gateie ops sensíveis em vereditos RASP-only; exija Play Integrity Strong da TEE, que
  o patch no nível de kernel não consegue forjar.

---

## Cross-reference

- **O que KernelSU *não* consegue derrotar: integridade de hardware** → [05-play-integrity.md](05-play-integrity.md), [11-root-detection.md](11-root-detection.md)
- **Probes em processo que o emulador rootado exercita** → [14-rasp-strategies.md](14-rasp-strategies.md)
- **Panorama de módulos de bypass** → [13-play-integrity-bypass.md](13-play-integrity-bypass.md)
- **O tunnel local que pareia com teste em emulador rootado** → [`scripts/start-local-tunnel.sh`](../../scripts/start-local-tunnel.sh), [README#local-dev](../../README.md)

## Referências

- [Rooting Android Studio Emulator with KernelSU (Apple Silicon)](https://mjais0508.medium.com/rooting-android-studio-emulator-with-kernelsu-apple-silicon-m1-m2-m3-m4-enable-root-on-google-c1b7d8417bea)
- [KernelSU-Next — Documentation](https://kernelsu-next.github.io/webpage/)
- [KernelSU upstream](https://github.com/tiann/KernelSU)
- [Android Developers — Create and manage virtual devices](https://developer.android.com/studio/run/managing-avds)
- [mitmproxy](https://mitmproxy.org/)
