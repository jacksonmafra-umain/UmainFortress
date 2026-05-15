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
  <meta name="description" content="A working Android security showcase. Half fintech app (Kotlin + Jetpack Compose), half documentation library that tells every modern Android security story from two sides — the defender who built the control, and the attacker who knows where the seams are. By Jackson Mafra, Umain." />
  <meta property="og:title" content="Fortress — Android Security 2026" />
  <meta property="og:description" content="A working Android security showcase: fintech app + 16-chapter dual-narrative documentation (defender × attacker). By Jackson Mafra, Umain." />
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
    .doc .title { color: var(--mist-100); font-weight: 500; flex: 1; }
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
        A working Android security showcase. Half of it is a fintech app (Kotlin + Jetpack
        Compose). The other half is a 16-chapter documentation library that tells every modern
        Android-security story from two sides: 
        <br> 🛡️ the defender who built the control, and
        <br> ⚔️ the attacker who knows where the seams are.
      </p>
      <div class="cta-row">
        <a class="btn btn-primary" href="/codelabs">Try the codelabs →</a>
        <a class="btn btn-ghost" href="https://github.com/jacksonmafra-umain/UmainFortress" target="_blank" rel="noopener">View on GitHub</a>
        <a class="btn btn-ghost" href="https://github.com/jacksonmafra-umain/UmainFortress/tree/main/docs" target="_blank" rel="noopener">Read the docs</a>
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
            How a specific security control is actually built, the API, the constraint, the <br/> 
            invariant that makes it hold. Real Kotlin and TypeScript, not pseudocode.
          </p>
        </div>
        <div class="card attacker">
          <h3>⚔️ Attacker</h3>
          <p>
            Where the same control breaks: the seven Bypass-N scenarios, the assumptions you have <br/> 
            to keep audit-tight, the silent failure modes you'd miss without thinking hostile.
          </p>
        </div>
      </div>
    </section>
    <section>
      <h2>Hands-on codelabs</h2>
      <p class="sub">
        Twenty-eight step-based codelabs derived from the same material, <strong>eight fully
        authored end-to-end</strong> (one Beginner, five Intermediate, two Advanced), the rest
        staged with reference links to their Medium long-form.         
      </p>
      <p class="sub" style="margin-top: 4px;">
        The defender's core arc, read in this order:
        <a href="/codelabs/mobile-top-10">OWASP Mobile Top 10</a> →
        <a href="/codelabs/stateless-auth-blueprint">Stateless auth</a> →
        <a href="/codelabs/hardware-vault">Hardware vault</a> →
        <a href="/codelabs/interceptor-pattern">Interceptor pattern</a> →
        <a href="/codelabs/network-warfare">Network warfare</a> →
        <a href="/codelabs/device-attestation-101">Device attestation</a> →
        <a href="/codelabs/biometric-hardening">Biometric hardening</a> →
        <a href="/codelabs/android-overlay-attacks">Overlay attacks</a>.
      </p>
      <div class="cta-row" style="margin-top: 8px;">
        <a class="btn btn-primary" href="/codelabs">Open the codelab library →</a>
        <a class="btn btn-ghost" href="/codelabs/mobile-top-10">Try the beginner lab</a>
      </div>
    </section>

    <section>
      <h2>Documentation library</h2>
      <p class="sub">
        A full <a href="https://github.com/jacksonmafra-umain/UmainFortress/tree/main/docs/pt-BR" target="_blank" rel="noopener">pt-BR translation</a>
        of every chapter ships alongside.
      </p>
      <div class="docs-list">
        ${docsList()}
      </div>
    </section>

    <section>
      <h2>The app</h2>
      <p class="sub">
        Fortress Bank, a fintech demo where every defensive surface is visible to the user.
         <br/>  A SecurityChip in the app bar shows live integrity verdicts; sensitive flows step-up via
        BiometricPrompt bound to a fresh challenge; a hidden Dev Mode simulates attacks so each
        control can be seen reacting in real time.
      </p>
      <p class="sub mono" style="font-size: 13px;">
        Splash · Onboarding · Login · Biometric Unlock · Home · Cards · Add Card · Scan ·
        Analytics · Profile · Accounts · Account Detail · Transfer · Transfer Keypad ·
        Security Center · Dev Mode
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

const REPO = "https://github.com/jacksonmafra-umain/UmainFortress";

const DOCS: DocEntry[] = [
  { num: "01", title: "Stateless auth blueprint", href: `${REPO}/blob/main/docs/01-stateless-auth.md`, done: true },
  { num: "02", title: "Hardware-backed token vault", href: `${REPO}/blob/main/docs/02-hardware-vault.md`, done: true },
  { num: "03", title: "OkHttp interceptor pattern", href: `${REPO}/blob/main/docs/03-interceptor-pattern.md`, done: true },
  { num: "04", title: "Passkeys — beyond passwords", href: `${REPO}/blob/main/docs/04-passkeys.md`, done: true },
  { num: "05", title: "Play Integrity attestation", href: `${REPO}/blob/main/docs/05-play-integrity.md`, done: true },
  { num: "06", title: "Token lifecycle", href: `${REPO}/blob/main/docs/06-token-lifecycle.md`, done: true },
  { num: "07", title: "Biometric hardening + user intent", href: `${REPO}/blob/main/docs/07-biometric-hardening.md`, done: true },
  { num: "08", title: "Network warfare + cert pinning", href: `${REPO}/blob/main/docs/08-network-warfare.md`, done: true },
  { num: "09", title: "Zero-trust device binding", href: `${REPO}/blob/main/docs/09-zero-trust.md`, done: true },
  { num: "10", title: "System design — staff interview", href: `${REPO}/blob/main/docs/10-system-design.md`, done: true },
  { num: "11", title: "Root detection in 2026", href: `${REPO}/blob/main/docs/11-root-detection.md`, done: true },
  { num: "12", title: "APK decompiling — the dark art", href: `${REPO}/blob/main/docs/12-decompiling.md`, done: true },
  { num: "13", title: "Play Integrity bypass", href: `${REPO}/blob/main/docs/13-play-integrity-bypass.md`, done: true },
  { num: "14", title: "RASP strategies", href: `${REPO}/blob/main/docs/14-rasp-strategies.md`, done: true },
  { num: "15", title: "KernelSU on Android emulators", href: `${REPO}/blob/main/docs/15-emulator-rooting.md`, done: true },
  { num: "16", title: "Content-provider exploitation", href: `${REPO}/blob/main/docs/16-content-providers.md`, done: true },
];

function docsList(): string {
  return DOCS.map((d) => {
    if (d.done && d.href) {
      return `<a class="doc" href="${d.href}" target="_blank" rel="noopener"><span class="num">${d.num}</span><span class="title">${d.title}</span></a>`;
    }
    return `<div class="doc coming"><span class="num">${d.num}</span><span class="title">${d.title}</span></div>`;
  }).join("\n        ");
}
