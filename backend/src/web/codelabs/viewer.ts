/**
 * Codelab viewer page (/codelabs/:slug).
 *
 * Renders every step server-side; the client script toggles visibility based on the
 * current step index. localStorage persists `currentStep` and a `completed` set keyed by
 * slug.
 */
import { escapeHtml, slugify, type Codelab } from "./loader.js";
import { sharedStyle, siteFooter, siteHeader } from "./library.js";

export function renderViewer(lab: Codelab): string {
  const totalSteps = lab.steps.length;
  const stepNav = lab.steps
    .map((s, i) => stepNavItemHtml(s.title, i, s.blurb))
    .join("\n");
  const stepPanels = lab.steps
    .map((s, i) => stepPanelHtml(s.title, s.bodyHtml, i, totalSteps, lab.slug))
    .join("\n");
  const refs = lab.references
    .map((r) => `<li><a href="${escapeHtml(r.url)}" rel="noopener" target="_blank">${escapeHtml(r.title)}</a></li>`)
    .join("");

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>${escapeHtml(lab.title)} — Fortress codelabs</title>
  <meta name="description" content="${escapeHtml(lab.summary)}" />
  <link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Cpath fill='%237A5CFF' d='M32 4 8 14v18c0 13 10 24 24 28 14-4 24-15 24-28V14L32 4Z'/%3E%3Cpath fill='%230EB47A' d='M44 26 28 42l-8-8 3-3 5 5 13-13 3 3Z'/%3E%3C/svg%3E" />
  ${sharedStyle()}
  ${viewerStyle()}
</head>
<body data-page="viewer" data-slug="${lab.slug}" data-total="${totalSteps}">
${siteHeader("viewer")}
<header class="lab-hero">
  <div class="hero-inner">
    <p class="crumb"><a href="/codelabs">Codelabs</a> &nbsp;/&nbsp; <span>${escapeHtml(lab.title)}</span></p>
    <h1>${escapeHtml(lab.title)}</h1>
    <p class="lab-summary">${escapeHtml(lab.summary)}</p>
    <div class="hero-meta">
      <span class="pill pill-${lab.level}">${lab.level}</span>
      ${lab.status === "draft" ? '<span class="pill pill-draft">draft</span>' : ""}
      <span class="muted dot">·</span>
      <span class="muted">${lab.estimatedMinutes} min</span>
      <span class="muted dot">·</span>
      <span class="muted">${totalSteps} steps</span>
    </div>
  </div>
</header>

<main class="lab">
  <aside class="step-rail" aria-label="Step navigation">
    <header><span>Step Guide</span><span class="muted" id="rail-progress">0/${totalSteps}</span></header>
    <ol class="step-list">${stepNav}</ol>
    <button type="button" id="restart" class="btn btn-ghost btn-restart">Start over</button>
  </aside>

  <section class="lab-body">
    <div class="progress" aria-hidden="true">
      <div class="progress-track"><div id="progress-fill" class="progress-fill"></div></div>
      <div class="progress-meta">
        <span id="progress-label">Step 1 of ${totalSteps}</span>
        <span id="progress-percent">0% complete</span>
      </div>
    </div>

    ${stepPanels}

    ${refs ? `<section class="refs"><h3>References</h3><ul>${refs}</ul></section>` : ""}
  </section>
</main>

${siteFooter()}
${viewerClientScript(lab.slug, totalSteps)}
</body>
</html>`;
}

function stepNavItemHtml(title: string, index: number, blurb: string): string {
  const stripped = title.replace(/^Step\s+\d+:\s*/i, "");
  const idx = index;
  return `<li class="step-rail-item" data-step="${idx}">
    <button type="button" data-goto="${idx}" class="step-rail-btn">
      <span class="step-rail-marker"><span class="num">${idx + 1}</span><span class="check">✓</span></span>
      <span class="step-rail-text">
        <span class="step-rail-title">${escapeHtml(stripped)}</span>
        <span class="step-rail-blurb muted">${escapeHtml(blurb).slice(0, 90)}${blurb.length > 90 ? "…" : ""}</span>
      </span>
    </button>
  </li>`;
}

function stepPanelHtml(title: string, html: string, index: number, total: number, slug: string): string {
  const isLast = index === total - 1;
  const stepLabel = /^Step\s+\d+:/i.test(title) ? "" : (index === 0 ? "Welcome" : `Step ${index}`);
  return `<article class="step" data-step="${index}" id="${slugify(`panel-${index}-${title}`)}" hidden>
  <p class="step-tag">${escapeHtml(stepLabel || `Step ${index}`)}</p>
  <h2>${escapeHtml(title)}</h2>
  <div class="step-content">${html}</div>
  <nav class="step-nav">
    <button type="button" class="btn btn-ghost" data-action="prev" ${index === 0 ? "disabled" : ""}>Previous</button>
    ${isLast
      ? '<button type="button" class="btn btn-ink" data-action="complete">Complete codelab ✓</button>'
      : `<button type="button" class="btn btn-ink" data-action="next">${index === 0 ? "Start codelab" : "Next step"} →</button>`}
  </nav>
</article>`;
}

function viewerStyle(): string {
  return `<style>
.lab-hero {
  background: linear-gradient(180deg, var(--cloud-50) 0%, var(--lavender-100) 100%);
  border-bottom: 1px solid var(--cloud-200);
  padding: 36px 28px 30px;
}
.hero-inner { max-width: 1200px; margin: 0 auto; }
.crumb { font-size: 13px; color: var(--mist-500); margin: 0 0 12px; }
.crumb a { color: var(--lavender-700); }
.lab-hero h1 { margin: 0 0 8px; font-size: 32px; letter-spacing: -0.5px; }
.lab-summary { margin: 0 0 14px; color: var(--mist-700); max-width: 760px; }
.hero-meta { display: inline-flex; align-items: center; gap: 4px; font-size: 13px; }

main.lab {
  display: grid; grid-template-columns: 300px 1fr; gap: 28px;
  max-width: 1200px; margin: 0 auto; padding: 28px 24px 60px;
}
@media (max-width: 880px) {
  main.lab { grid-template-columns: 1fr; }
  .step-rail { position: static !important; max-height: none !important; }
}

.step-rail {
  background: var(--cloud-0); border: 1px solid var(--cloud-200); border-radius: 18px;
  padding: 14px; position: sticky; top: 80px; max-height: calc(100vh - 100px); overflow: auto;
}
.step-rail > header {
  display: flex; justify-content: space-between;
  font-size: 12px; font-weight: 600; color: var(--mist-700);
  text-transform: uppercase; letter-spacing: 0.06em;
  padding: 4px 6px 10px;
}
.step-list { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 4px; }
.step-rail-btn {
  width: 100%; display: flex; gap: 10px; align-items: flex-start;
  text-align: left; padding: 10px; border-radius: 12px;
  background: transparent; border: none; cursor: pointer; color: inherit;
}
.step-rail-btn:hover { background: var(--cloud-100); }
.step-rail-item.active .step-rail-btn { background: var(--lavender-100); }
.step-rail-marker {
  position: relative; flex: 0 0 28px; height: 28px;
  border-radius: 50%; background: var(--cloud-100); color: var(--mist-700);
  display: inline-flex; align-items: center; justify-content: center;
  font-size: 12px; font-weight: 700;
}
.step-rail-marker .check { display: none; }
.step-rail-item.done .step-rail-marker { background: var(--sage-500); color: var(--cloud-0); }
.step-rail-item.done .step-rail-marker .num { display: none; }
.step-rail-item.done .step-rail-marker .check { display: inline; }
.step-rail-item.active .step-rail-marker { background: var(--lavender-500); color: var(--cloud-0); }
.step-rail-text { display: flex; flex-direction: column; gap: 2px; line-height: 1.25; }
.step-rail-title { font-size: 13px; font-weight: 600; color: var(--ink-900); }
.step-rail-blurb { font-size: 12px; }
.btn-restart { width: 100%; margin-top: 12px; }

.lab-body { max-width: 760px; }
.progress {
  background: var(--cloud-0); border: 1px solid var(--cloud-200); border-radius: 18px;
  padding: 18px 20px; margin-bottom: 20px;
}
.progress-track { background: var(--cloud-100); height: 8px; border-radius: 999px; overflow: hidden; }
.progress-fill {
  background: var(--ink-900); height: 100%; border-radius: 999px;
  width: 0%; transition: width 0.3s ease;
}
.progress-meta {
  display: flex; justify-content: space-between; margin-top: 10px;
  font-size: 12px; color: var(--mist-700);
}

.step {
  background: var(--cloud-0); border: 1px solid var(--cloud-200);
  border-radius: 22px; padding: 28px 30px; margin-bottom: 16px;
}
.step-tag {
  display: inline-block; padding: 2px 10px; border-radius: 999px;
  background: var(--cloud-100); color: var(--mist-700);
  font-size: 11px; font-weight: 600; margin: 0 0 10px; letter-spacing: 0.05em;
  text-transform: uppercase;
}
.step h2 { margin: 0 0 8px; font-size: 24px; letter-spacing: -0.25px; }
.step-content { font-size: 15px; line-height: 1.6; color: var(--ink-900); }
.step-content h3 { margin: 22px 0 8px; font-size: 18px; }
.step-content p { margin: 12px 0; }
.step-content ul, .step-content ol { padding-left: 22px; margin: 12px 0; }
.step-content li { margin: 4px 0; }
.step-content code {
  font-size: 13px; background: var(--cloud-100); padding: 2px 6px; border-radius: 6px;
  color: var(--lavender-700);
}
.step-content pre {
  background: var(--ink-900); color: var(--cloud-50);
  padding: 16px 18px; border-radius: 14px; overflow-x: auto;
  font-size: 13px; line-height: 1.55; margin: 16px 0;
}
.step-content pre code { background: transparent; color: inherit; padding: 0; }
.step-content blockquote {
  background: var(--sage-100); border-left: 4px solid var(--sage-500);
  border-radius: 10px; padding: 12px 16px; margin: 16px 0;
}
.step-content blockquote p { margin: 0; color: var(--mist-700); }
.step-content blockquote strong { color: var(--sage-500); }
.step-content a { color: var(--lavender-700); text-decoration: underline; }

.step-nav {
  display: flex; justify-content: space-between; align-items: center;
  border-top: 1px solid var(--cloud-200); padding-top: 18px; margin-top: 28px; gap: 12px;
}
.step-nav .btn-ghost { padding: 8px 16px; }

.refs {
  background: var(--cloud-100); border-radius: 18px; padding: 18px 22px; margin-top: 18px;
}
.refs h3 { margin: 0 0 8px; font-size: 14px; color: var(--mist-700); text-transform: uppercase; letter-spacing: 0.06em; }
.refs ul { padding-left: 20px; margin: 0; font-size: 14px; }
.refs li { margin: 4px 0; }
</style>`;
}

function viewerClientScript(slug: string, total: number): string {
  return `<script>
(function () {
  const SLUG = ${JSON.stringify(slug)};
  const TOTAL = ${total};
  const KEY = "fortress.codelabs.progress." + SLUG;

  function load() {
    try {
      const raw = JSON.parse(localStorage.getItem(KEY) || "{}");
      return {
        current: typeof raw.current === "number" ? raw.current : 0,
        completed: Array.isArray(raw.completed) ? raw.completed : [],
      };
    } catch (_) { return { current: 0, completed: [] }; }
  }
  function save(state) { localStorage.setItem(KEY, JSON.stringify(state)); }

  let state = load();
  if (state.current >= TOTAL) state.current = TOTAL - 1;

  const panels = Array.from(document.querySelectorAll(".step"));
  const railItems = Array.from(document.querySelectorAll(".step-rail-item"));
  const progressFill = document.getElementById("progress-fill");
  const progressLabel = document.getElementById("progress-label");
  const progressPercent = document.getElementById("progress-percent");
  const railProgress = document.getElementById("rail-progress");

  function render() {
    panels.forEach((p, i) => { p.hidden = i !== state.current; });
    railItems.forEach((li, i) => {
      li.classList.toggle("active", i === state.current);
      li.classList.toggle("done", state.completed.includes(i));
    });
    const reached = Math.max(state.current + 1, state.completed.length);
    const pct = Math.round((Math.min(reached, TOTAL) / TOTAL) * 100);
    progressFill.style.width = pct + "%";
    progressLabel.textContent = "Step " + (state.current + 1) + " of " + TOTAL;
    progressPercent.textContent = pct + "% complete";
    railProgress.textContent = state.completed.length + "/" + TOTAL;
    window.scrollTo({ top: 0, behavior: "smooth" });
  }

  function goto(i) {
    if (i < 0 || i >= TOTAL) return;
    if (!state.completed.includes(state.current)) {
      state.completed = state.completed.concat(state.current);
    }
    state.current = i;
    save(state); render();
  }

  document.querySelectorAll('[data-action="next"]').forEach((b) => b.addEventListener("click", () => goto(state.current + 1)));
  document.querySelectorAll('[data-action="prev"]').forEach((b) => b.addEventListener("click", () => goto(state.current - 1)));
  document.querySelectorAll('[data-action="complete"]').forEach((b) => b.addEventListener("click", () => {
    state.completed = Array.from(new Set(state.completed.concat(state.current)));
    if (!state.completed.includes(TOTAL - 1)) state.completed.push(TOTAL - 1);
    save(state); render();
    alert("Codelab marked complete. Your progress is saved locally.");
  }));
  document.querySelectorAll(".step-rail-btn").forEach((b) => b.addEventListener("click", () => {
    const i = parseInt(b.getAttribute("data-goto"), 10);
    if (!Number.isNaN(i)) goto(i);
  }));
  document.getElementById("restart").addEventListener("click", () => {
    if (confirm("Reset progress for this codelab?")) {
      state = { current: 0, completed: [] };
      save(state); render();
    }
  });
  render();
})();
</script>`;
}
