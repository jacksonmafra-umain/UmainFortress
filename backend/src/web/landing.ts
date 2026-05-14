/**
 * Server-rendered landing page for fortress-android.
 *
 * Self-contained HTML — no JS bundler, no Tailwind, no React. The single external script is
 * Vercel's Web Analytics, which is auto-routed by the platform when deployed (`/_vercel/insights/script.js`).
 * Locally it 404s silently and the page is unaffected.
 *
 * Palette mirrors the Android theme (see `app/.../ui/theme/Color.kt`): Midnight 800/900 base,
 * Emerald accents for "money / status: good", Violet for identity/security surfaces.
 */
export function renderLanding(): string {
  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Fortress — Android Security 2026, defender × attacker</title>
  <meta name="description" content="A working fintech Android app and a dual-narrative documentation library covering modern Android auth, biometrics, Play Integrity, RASP and certificate pinning. By Jackson Mafra, Umain." />
  <meta property="og:title" content="Fortress — Android Security 2026" />
  <meta property="og:description" content="Working Android demo + dual-narrative security docs (🛡️ defender / ⚔️ attacker)." />
  <meta property="og:type" content="website" />
  <link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Cpath fill='%237A5CFF' d='M32 4 8 14v18c0 13 10 24 24 28 14-4 24-15 24-28V14L32 4Z'/%3E%3Cpath fill='%230EB47A' d='M44 26 28 42l-8-8 3-3 5 5 13-13 3 3Z'/%3E%3C/svg%3E" />
  <style>
    :root {
      --midnight-900: #050d1a;
      --midnight-800: #0a1a2f;
      --midnight-700: #142847;
      --ink-900: #0e1420;
      --ink-800: #161e2e;
      --ink-700: #1f2a3e;
      --emerald-500: #0eb47a;
      --emerald-400: #3fe2a0;
      --violet-500: #7a5cff;
      --violet-400: #b3a1ff;
      --mist-100: #e8f0fa;
      --mist-300: #8fa0b8;
      --vermilion: #e5484d;
    }
    * { box-sizing: border-box; }
    html, body { margin: 0; padding: 0; }
    body {
      font-family: -apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif;
      background: var(--ink-900);
      color: var(--mist-100);
      line-height: 1.55;
      -webkit-font-smoothing: antialiased;
    }
    code, pre, .mono {
      font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, Monaco, Consolas, monospace;
    }
    a { color: var(--violet-400); text-decoration: none; }
    a:hover { color: var(--mist-100); text-decoration: underline; }
    main { max-width: 980px; margin: 0 auto; padding: 0 24px; }
    .hero {
      padding: 96px 24px 80px;
      background:
        radial-gradient(1200px 600px at 20% -10%, rgba(122, 92, 255, 0.35), transparent 60%),
        radial-gradient(900px 500px at 90% 10%, rgba(14, 180, 122, 0.25), transparent 60%),
        linear-gradient(180deg, var(--midnight-900) 0%, var(--ink-900) 100%);
      border-bottom: 1px solid var(--ink-700);
    }
    .hero-inner { max-width: 980px; margin: 0 auto; }
    .badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 6px 12px;
      border-radius: 999px;
      background: rgba(122, 92, 255, 0.15);
      border: 1px solid rgba(122, 92, 255, 0.35);
      color: var(--violet-400);
      font-size: 12px;
      font-weight: 500;
      letter-spacing: 0.04em;
      text-transform: uppercase;
      margin-bottom: 24px;
    }
    .badge::before {
      content: "";
      width: 6px; height: 6px;
      border-radius: 50%;
      background: var(--emerald-400);
      box-shadow: 0 0 8px var(--emerald-400);
    }
    h1 {
      font-size: clamp(40px, 6vw, 64px);
      line-height: 1.05;
      letter-spacing: -0.02em;
      margin: 0 0 16px;
      font-weight: 600;
    }
    .lede {
      font-size: clamp(17px, 2vw, 20px);
      max-width: 680px;
      color: var(--mist-300);
      margin: 0 0 32px;
    }
    .cta-row { display: flex; flex-wrap: wrap; gap: 12px; }
    .btn {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      padding: 14px 22px;
      border-radius: 14px;
      font-weight: 600;
      font-size: 15px;
      transition: transform 0.06s ease, background 0.15s ease, border-color 0.15s ease;
    }
    .btn:active { transform: translateY(1px); }
    .btn-primary {
      background: var(--mist-100);
      color: var(--midnight-900);
    }
    .btn-primary:hover {
      background: #ffffff;
      text-decoration: none;
      color: var(--midnight-900);
    }
    .btn-ghost {
      background: transparent;
      color: var(--mist-100);
      border: 1px solid rgba(232, 240, 250, 0.18);
    }
    .btn-ghost:hover {
      background: rgba(232, 240, 250, 0.06);
      border-color: rgba(232, 240, 250, 0.4);
      text-decoration: none;
      color: var(--mist-100);
    }

    section { padding: 64px 0; border-bottom: 1px solid var(--ink-700); }
    section h2 {
      font-size: clamp(24px, 3.5vw, 32px);
      margin: 0 0 8px;
      letter-spacing: -0.01em;
    }
    section .sub {
      color: var(--mist-300);
      margin: 0 0 28px;
      max-width: 720px;
    }

    .duo { display: grid; gap: 16px; grid-template-columns: 1fr; }
    @media (min-width: 720px) { .duo { grid-template-columns: 1fr 1fr; } }
    .card {
      background: var(--ink-800);
      border: 1px solid var(--ink-700);
      border-radius: 16px;
      padding: 24px;
    }
    .card h3 {
      font-size: 18px;
      margin: 0 0 6px;
      display: flex;
      align-items: center;
      gap: 10px;
    }
    .card p { margin: 0; color: var(--mist-300); font-size: 14.5px; }
    .card.defender { border-left: 3px solid var(--emerald-500); }
    .card.attacker { border-left: 3px solid var(--vermilion); }

    .stack { display: grid; gap: 16px; grid-template-columns: repeat(auto-fit, minmax(200px, 1fr)); }
    .stack .card h3 { font-size: 15px; color: var(--mist-100); }
    .stack .pill {
      display: inline-block;
      font-size: 11px;
      padding: 2px 8px;
      border-radius: 999px;
      background: rgba(122, 92, 255, 0.18);
      color: var(--violet-400);
      margin-bottom: 10px;
      font-weight: 600;
      letter-spacing: 0.04em;
      text-transform: uppercase;
    }
    .stack .pill.backend { background: rgba(14, 180, 122, 0.15); color: var(--emerald-400); }
    .stack ul { margin: 0; padding-left: 18px; color: var(--mist-300); font-size: 14px; }
    .stack li { margin-bottom: 4px; }

    .docs-list { display: grid; gap: 10px; }
    .doc {
      display: flex;
      align-items: baseline;
      gap: 14px;
      padding: 14px 16px;
      background: var(--ink-800);
      border: 1px solid var(--ink-700);
      border-radius: 12px;
      transition: border-color 0.15s ease, transform 0.06s ease;
    }
    .doc:hover { border-color: var(--violet-500); text-decoration: none; }
    .doc .num {
      font-family: "JetBrains Mono", ui-monospace, monospace;
      color: var(--violet-400);
      font-weight: 600;
      width: 28px;
      flex-shrink: 0;
    }
    .doc .title { color: var(--mist-100); font-weight: 500; }
    .doc .meta { color: var(--mist-300); font-size: 13px; margin-left: auto; }
    .doc.coming { opacity: 0.55; }

    footer {
      padding: 40px 24px 64px;
      text-align: center;
      color: var(--mist-300);
      font-size: 14px;
    }
    footer strong { color: var(--mist-100); font-weight: 600; }
  </style>
</head>
<body>
  <header class="hero">
    <div class="hero-inner">
      <span class="badge">Portfolio · Android security · 2026</span>
      <h1>The vault and the battlefield.</h1>
      <p class="lede">
        Fortress is a working fintech Android app and a paired documentation library that tells
        every modern Android-security story from both sides: 🛡️ the defender who built the control,
        and ⚔️ the attacker who knows where the cracks are.
      </p>
      <div class="cta-row">
        <a class="btn btn-primary" href="https://github.com/jacksonmafra-umain/UmainFortress">View on GitHub →</a>
        <a class="btn btn-ghost" href="https://github.com/jacksonmafra-umain/UmainFortress/tree/main/docs">Read the docs</a>
      </div>
    </div>
  </header>

  <main>
    <section>
      <h2>Why dual-narrative?</h2>
      <p class="sub">
        Every defensive control has a corresponding offensive playbook. Reading them apart leaves
        gaps that an attacker walks through. Reading them together is the only honest threat model.
      </p>
      <div class="duo">
        <div class="card defender">
          <h3>🛡️ Defender</h3>
          <p>
            How a specific security control is actually built — the API, the constraint, the
            invariant that makes it hold. Real Kotlin and TypeScript, not pseudocode.
          </p>
        </div>
        <div class="card attacker">
          <h3>⚔️ Attacker</h3>
          <p>
            Where the same control breaks: the seven Bypass-N scenarios, the assumptions you have
            to keep audit-tight, the silent failure modes you'd miss without thinking hostile.
          </p>
        </div>
      </div>
    </section>

    <section>
      <h2>The stack</h2>
      <p class="sub">Production-grade Android with a typed backend you can run with one command.</p>
      <div class="stack">
        <div class="card">
          <span class="pill">Android · app/</span>
          <ul>
            <li>Kotlin 2.2 + Jetpack Compose</li>
            <li>Ktor client on OkHttp engine</li>
            <li>Koin DI · kotlinx.serialization</li>
            <li>Android Keystore + StrongBox</li>
            <li>BiometricPrompt + CryptoObject</li>
            <li>Play Integrity + Credential Manager</li>
          </ul>
        </div>
        <div class="card">
          <span class="pill backend">Backend · backend/</span>
          <ul>
            <li>TypeScript 6 + Express 5</li>
            <li>HS256 JWT via <code>jose</code></li>
            <li>Argon2id password hashing</li>
            <li>Rotating refresh tokens, hashed at rest</li>
            <li>Atomic disk-JSON store</li>
            <li>Zero infra — <code>npm run dev</code></li>
          </ul>
        </div>
      </div>
    </section>

    <section>
      <h2>Documentation library</h2>
      <p class="sub">
        Eight finished out of sixteen. Each is a deep dive sized in the same shape as the canonical
        sample, <a href="https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/07-biometric-hardening.md">07 — Biometric Hardening</a>.
      </p>
      <div class="docs-list">
        ${docsList()}
      </div>
    </section>

    <section>
      <h2>The app, in 11 screens</h2>
      <p class="sub">
        Fortress Bank — a fintech demo where every defensive surface is visible to the user. A
        SecurityChip in the app bar shows live integrity verdicts; sensitive flows step-up via
        BiometricPrompt bound to a fresh challenge; a hidden Dev Mode simulates attacks so each
        control can be seen reacting in real time.
      </p>
      <p class="sub mono" style="font-size: 13px;">
        Splash · Onboarding · Auth · Biometric Unlock · Dashboard · Accounts · Account Detail ·
        Transfer · Cards · Security Center · Dev Mode
      </p>
    </section>
  </main>

  <footer>
    Built by <strong>Jackson Mafra</strong> — Mobile &amp; Security Engineer at
    <a href="https://umain.com">Umain</a>. <br />
    MIT, for educational use. Synthesised from the Stackademic <em>Scaling Android Auth</em> series
    plus deep dives across the Android-security community.
  </footer>

  <script>
    window.va = window.va || function () { (window.vaq = window.vaq || []).push(arguments); };
  </script>
  <script defer src="/_vercel/insights/script.js"></script>
</body>
</html>`;
}

interface DocEntry { num: string; title: string; href?: string; done: boolean }

const DOCS: DocEntry[] = [
  { num: "01", title: "Stateless auth blueprint", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/01-stateless-auth.md", done: true },
  { num: "02", title: "Hardware-backed token vault", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/02-hardware-vault.md", done: true },
  { num: "03", title: "OkHttp interceptor pattern", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/03-interceptor-pattern.md", done: true },
  { num: "04", title: "Passkeys — beyond passwords", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/04-passkeys.md", done: true },
  { num: "05", title: "Play Integrity attestation", done: false },
  { num: "06", title: "Token lifecycle", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/06-token-lifecycle.md", done: true },
  { num: "07", title: "Biometric hardening + user intent", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/07-biometric-hardening.md", done: true },
  { num: "08", title: "Network warfare + cert pinning", href: "https://github.com/jacksonmafra-umain/UmainFortress/blob/main/docs/08-network-warfare.md", done: true },
  { num: "09", title: "Zero-trust device binding", done: false },
  { num: "10", title: "System design — staff interview", done: false },
  { num: "11", title: "Root detection in 2026", done: false },
  { num: "12", title: "APK decompiling — the dark art", done: false },
  { num: "13", title: "Play Integrity bypass", done: false },
  { num: "14", title: "RASP strategies", done: false },
  { num: "15", title: "KernelSU on Android emulators", done: false },
  { num: "16", title: "Content-provider exploitation", done: false },
];

function docsList(): string {
  return DOCS.map((d) => {
    if (d.done && d.href) {
      return `<a class="doc" href="${d.href}"><span class="num">${d.num}</span><span class="title">${d.title}</span><span class="meta">✅ written</span></a>`;
    }
    return `<div class="doc coming"><span class="num">${d.num}</span><span class="title">${d.title}</span><span class="meta">🚧 staged</span></div>`;
  }).join("\n        ");
}
