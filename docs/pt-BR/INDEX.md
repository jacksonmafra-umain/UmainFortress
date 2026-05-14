# Índice da documentação Fortress (pt-BR)

> Versão em português brasileiro. Versão original em inglês: [`../INDEX.md`](../INDEX.md).

Cada arquivo é uma **narrativa dupla**: 🛡️ Defensor (como eu construí isso) ao lado de
⚔️ Atacante (como eu tentaria quebrar). O estilo canônico está definido em
[07 — Hardening biométrico](07-biometric-hardening.md); trate como a referência de
profundidade e tom.

Termos técnicos (token, hash, endpoint, challenge, nonce, bypass, deny-list, payload, JWT,
JWKS, JWS, mutex, race condition, replay, step-up, vault, hardening, etc.) ficam em inglês — é
o vocabulário real que devs brasileiros usam no dia a dia.

## Série principal de autenticação (sintetiza a série Stackademic em 10 partes)

| # | Tópico | Status |
|---|---|---|
| 01 | [Modelo stateless de autenticação](01-stateless-auth.md) — JWT, escala, rotação de chave | ✅ |
| 02 | [Vault de tokens com lastro de hardware](02-hardware-vault.md) — Keystore + StrongBox | ✅ |
| 03 | [Padrão de interceptor OkHttp](03-interceptor-pattern.md) — single-flight refresh, race conditions | ✅ |
| 04 | [Passkeys](04-passkeys.md) — `androidx.credentials`, servidor FIDO2 | ✅ |
| 05 | [Play Integrity](05-play-integrity.md) — standard request, verificação server-side | ✅ |
| 06 | [Ciclo de vida do token](06-token-lifecycle.md) — rotação, revogação, detecção de reuso | ✅ |
| 07 | [Hardening biométrico + intenção do usuário](07-biometric-hardening.md) — binding via `CryptoObject` | ✅ |
| 08 | [Guerra de rede](08-network-warfare.md) — certificate pinning, defesa contra MITM | ✅ |
| 09 | [Zero trust](09-zero-trust.md) — device binding, risk signals | ✅ |
| 10 | [System design](10-system-design.md) — arquitetura nível staff | ✅ |

## Design do app

| Tópico | Status |
|---|---|
| [Design system — a paleta "Vault"](design-system.md) — tokens de cor, escala de tipografia, componentes, ícones, light/dark, acessibilidade | ✅ |

## Aprofundamentos ofensivos

| # | Tópico | Status |
|---|---|---|
| 11 | [Detecção de root em 2026](11-root-detection.md) — o que funciona de verdade | ✅ |
| 12 | [Decompilação de APK](12-decompiling.md) — a arte sombria | ✅ |
| 13 | [Bypass do Play Integrity](13-play-integrity-bypass.md) — o que circula na natureza | ✅ |
| 14 | [Estratégias RASP](14-rasp-strategies.md) — runtime application self-protection | ✅ |
| 15 | [KernelSU em emuladores Android](15-emulator-rooting.md) (Apple Silicon) | ✅ |
| 16 | [Explorando content providers](16-content-providers.md) | ✅ |

## Ordem de leitura

Se você é novo em autenticação mobile: 01 → 02 → 03 → 06 → 07 → 04 → 05 → 09 → 08 → 10.

Se você veio pela ofensiva: 12 → 11 → 13 → 14 → 16 → 07 (defesa) → 09 → 02.

## Convenções de tradução

- **Defender / Attacker** viram **Defensor / Atacante** nos cabeçalhos.
- **TL;DR** fica como TL;DR.
- Termos técnicos de mercado ficam em inglês: token, hash, endpoint, nonce, challenge, bypass,
  deny-list, allow-list, race, replay, step-up, mutex, fingerprint, payload, JWS, JWKS, callback,
  stack trace, log, thread, deadlock, refresh, attestation, signing, verifier, vault, hardening,
  auth-gated.
- **Counter:** dentro dos bypass scenarios mantém em inglês — alinha visualmente com a versão EN.
- "Fortress field notes" não traduz (é o nome de marca de campo do projeto).
- Primeira ocorrência de termo técnico traduzido recebe o original em parênteses:
  "cerimônia biométrica (biometric ceremony)". Ocorrências seguintes ficam só em PT.
- Trechos de código, paths de arquivo, mensagens de commit e identificadores ficam em inglês.
- Aspas das frases epígrafe traduzem.
- Cross-references entre docs apontam para os arquivos pt-BR irmãos (mesma pasta).
- Cabeçalho de seção `Cross-reference` mantém o inglês; `Referências` traduz para PT.
