/**
 * Library page (/codelabs).
 *
 * Self-contained HTML. Filters (level + tag + search + status) are rendered server-side
 * and refined client-side via a tiny vanilla-JS layer that reads/writes
 * localStorage["fortress.codelabs.filters"].
 *
 * Renders the existing landing-page palette so the codelabs site reads as one project.
 */
import { escapeHtml, loadCodelabs, type Codelab } from "./loader.js";

export function renderLibrary(): string {
  const labs = loadCodelabs();
  const levels = ["beginner", "intermediate", "advanced"] as const;
  const allTags = Array.from(new Set(labs.flatMap((l) => l.tags))).sort();

  const cards = labs
    .map((l) => cardHtml(l))
    .join("\n");

  return `<!doctype html>
<html lang="en">
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1" />
  <title>Codelabs — Fortress</title>
  <meta name="description" content="Hands-on codelabs derived from the Fortress documentation library and Jackson Mafra's mobile-security writing. Beginner, intermediate and advanced tracks across attestation, fingerprinting, overlay defence, and Android tooling." />
  <link rel="icon" href="data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 64 64'%3E%3Cpath fill='%237A5CFF' d='M32 4 8 14v18c0 13 10 24 24 28 14-4 24-15 24-28V14L32 4Z'/%3E%3Cpath fill='%230EB47A' d='M44 26 28 42l-8-8 3-3 5 5 13-13 3 3Z'/%3E%3C/svg%3E" />
  ${sharedStyle()}
  ${librarySpecificStyle()}
</head>
<body data-page="library">
${siteHeader("library")}
<main class="library">
  <aside class="filters" aria-label="Filter codelabs">
    <h2>Filter</h2>
    <p class="muted">Narrow the catalogue by level or tag.<br/> Choices persist between visits.</p>

    <section class="filter-group">
      <header><span>Search</span></header>
      <input id="filter-search" type="search" placeholder="Search title, tag, summary" autocomplete="off" />
    </section>

    <section class="filter-group">
      <header><span>Level</span><button type="button" data-clear="level">Reset</button></header>
      ${levels.map((lv) => `
        <label class="checkbox">
          <input type="checkbox" name="level" value="${lv}" />
          <span class="pill pill-${lv}">${lv}</span>
        </label>`).join("\n")}
    </section>

    <section class="filter-group">
      <header><span>Tag</span><button type="button" data-clear="tag">Reset</button></header>
      <div class="tag-cloud">
        ${allTags.map((t) => `
          <label class="checkbox tag-chip">
            <input type="checkbox" name="tag" value="${escapeHtml(t)}" />
            <span>${escapeHtml(t)}</span>
          </label>`).join("\n")}
      </div>
    </section>

    <button type="button" id="filter-reset" class="btn btn-ghost">Reset all filters</button>
  </aside>

  <section class="catalogue">
    <header class="cat-head">
      <div>
        <h1>Codelabs</h1>
        <p class="muted">Step-based, hands-on builds derived from the Fortress documentation and Jackson Mafra's mobile-security writing.</p>
      </div>
      <div class="cat-meta" id="cat-meta">${labs.length} codelabs</div>
    </header>

    <div class="cards" id="cards">
      ${cards}
    </div>

    <p id="empty" class="empty" hidden>No codelabs match the current filters.</p>
  </section>
</main>
${siteFooter()}
${libraryClientScript()}
</body>
</html>`;
}

function cardHtml(l: Codelab): string {
  const isDraft = l.status === "draft";
  const tags = l.tags
    .slice(0, 4)
    .map((t) => `<span class="chip">${escapeHtml(t)}</span>`)
    .join("");
  const labelClass = `pill pill-${l.level}`;
  return `<article class="card" data-slug="${l.slug}" data-level="${l.level}" data-status="${l.status}"
                    data-steps="${l.steps.length}"
                    data-tags="${escapeHtml(l.tags.join("|"))}"
                    data-search="${escapeHtml((l.title + " " + l.summary + " " + l.tags.join(" ")).toLowerCase())}">
  <a class="card-link" href="/codelabs/${l.slug}">
    <header class="card-head">
      <span class="${labelClass}">${l.level}</span>
      ${isDraft ? '<span class="pill pill-draft">draft</span>' : ""}
      <span class="pill pill-progress" data-progress-pill hidden></span>
      <span class="muted dot">·</span>
      <span class="muted">${l.estimatedMinutes} min</span>
    </header>
    <h3>${escapeHtml(l.title)}</h3>
    <p class="card-summary">${escapeHtml(l.summary)}</p>
    <footer class="card-chips">${tags}</footer>
  </a>
</article>`;
}

// ---------------------------------------------------------------------------------------
//  Shared site chrome — also used by the viewer page.
// ---------------------------------------------------------------------------------------

export function siteHeader(active: "library" | "viewer"): string {
  return `<header class="site">
  <a class="brand" href="/">
    <svg viewBox="0 0 64 64" width="22" height="22" aria-hidden="true">
      <path fill="#8E6BE6" d="M32 4 8 14v18c0 13 10 24 24 28 14-4 24-15 24-28V14L32 4Z"/>
      <path fill="#2EB37A" d="M44 26 28 42l-8-8 3-3 5 5 13-13 3 3Z"/>
    </svg>
    <span>Fortress</span>
  </a>
  <nav>
    <a href="/" class="${active === "library" ? "" : ""}">Overview</a>
    <a href="/codelabs" class="${active === "library" ? "active" : ""}">Codelabs</a>
    <a href="https://github.com/jacksonmafra-umain/UmainFortress" rel="noopener">GitHub</a>
  </nav>
</header>`;
}

export function siteFooter(): string {
  return `<footer class="site-foot">
  <span>Fortress codelabs · open source under MIT</span>
  <span>By <a href="https://umain.com" rel="noopener">Umain</a> · <a href="https://medium.com/@jacksonfdam" rel="noopener">Jackson Mafra</a></span>
</footer>`;
}

// ---------------------------------------------------------------------------------------
//  Styles
// ---------------------------------------------------------------------------------------

export function sharedStyle(): string {
  return `<style>
:root {
  --ink-950: #06070b;
  --ink-900: #0e1018;
  --ink-800: #161a26;
  --ink-700: #1f2435;
  --cloud-0: #ffffff;
  --cloud-50: #faf7ff;
  --cloud-100: #f1ecfa;
  --cloud-200: #e3dbf1;
  --cloud-300: #cfc3e2;
  --lavender-100: #e9deff;
  --lavender-300: #bea1ff;
  --lavender-500: #8e6be6;
  --lavender-700: #5635ae;
  --mist-300: #a9a3bd;
  --mist-500: #6e6883;
  --mist-700: #3d3852;
  --sage-100: #d6f2e5;
  --sage-500: #2eb37a;
  --coral-100: #fde2e2;
  --coral-500: #e5484d;
  --amber-100: #ffe9b8;
  --amber-600: #8a5300;
}
* { box-sizing: border-box; }
html, body { margin: 0; padding: 0; }
body {
  font-family: -apple-system, BlinkMacSystemFont, "Inter", "Segoe UI", Roboto, sans-serif;
  background: var(--cloud-50);
  color: var(--ink-900);
  line-height: 1.5;
  -webkit-font-smoothing: antialiased;
}
code, pre, .mono { font-family: "JetBrains Mono", ui-monospace, SFMono-Regular, Menlo, monospace; }
a { color: var(--lavender-700); text-decoration: none; }
a:hover { text-decoration: underline; }
.muted { color: var(--mist-500); }
.dot { opacity: 0.5; padding: 0 4px; }

.site {
  display: flex; align-items: center; justify-content: space-between;
  padding: 16px 28px; border-bottom: 1px solid var(--cloud-200);
  background: var(--cloud-0);
  position: sticky; top: 0; z-index: 10;
}
.site .brand { display: inline-flex; align-items: center; gap: 8px; font-weight: 700; color: var(--ink-900); }
.site nav { display: inline-flex; gap: 22px; }
.site nav a { color: var(--mist-500); font-size: 14px; }
.site nav a.active { color: var(--ink-900); font-weight: 600; }

.site-foot {
  margin-top: 80px; padding: 24px 28px; border-top: 1px solid var(--cloud-200);
  display: flex; justify-content: space-between; color: var(--mist-500); font-size: 13px;
}

.pill {
  display: inline-flex; align-items: center;
  padding: 2px 10px; border-radius: 999px;
  font-size: 11px; font-weight: 600; text-transform: capitalize;
  letter-spacing: 0.02em;
}
.pill-beginner { background: var(--sage-100); color: var(--sage-500); }
.pill-intermediate { background: var(--lavender-100); color: var(--lavender-700); }
.pill-advanced { background: var(--coral-100); color: var(--coral-500); }
.pill-draft { background: var(--amber-100); color: var(--amber-600); }

.btn {
  display: inline-flex; align-items: center; gap: 8px;
  padding: 10px 16px; border-radius: 999px; font-weight: 600; font-size: 14px;
  border: none; cursor: pointer; transition: background 0.15s ease, transform 0.05s ease;
}
.btn:active { transform: translateY(1px); }
.btn-primary { background: var(--lavender-500); color: var(--cloud-0); }
.btn-primary:hover { background: var(--lavender-700); }
.btn-ink { background: var(--ink-900); color: var(--cloud-0); }
.btn-ink:hover { background: var(--ink-700); }
.btn-ghost { background: transparent; color: var(--mist-500); border: 1px solid var(--cloud-300); }
.btn-ghost:hover { background: var(--cloud-100); color: var(--ink-900); }
.btn[disabled] { opacity: 0.45; cursor: not-allowed; }
</style>`;
}

function librarySpecificStyle(): string {
  return `<style>
main.library {
  display: grid; grid-template-columns: 260px 1fr; gap: 28px;
  max-width: 1200px; margin: 0 auto; padding: 32px 24px;
}
@media (max-width: 880px) {
  main.library { grid-template-columns: 1fr; }
  .filters { position: static !important; }
}

.filters {
  background: var(--cloud-0); border-radius: 18px; border: 1px solid var(--cloud-200);
  padding: 20px; align-self: start; position: sticky; top: 80px;
}
.filters h2 { font-size: 14px; margin: 0 0 4px; }
.filter-group { margin-top: 18px; padding-top: 14px; border-top: 1px solid var(--cloud-200); }
.filter-group:first-of-type { padding-top: 0; border-top: none; }
.filter-group header {
  display: flex; justify-content: space-between; align-items: center;
  margin-bottom: 10px; font-size: 12px; font-weight: 600;
  color: var(--mist-700); text-transform: uppercase; letter-spacing: 0.06em;
}
.filter-group header button {
  background: transparent; border: none; color: var(--lavender-700);
  font-size: 11px; cursor: pointer; font-weight: 600;
}
.filter-group input[type="search"] {
  width: 100%; padding: 8px 12px; border-radius: 10px;
  border: 1px solid var(--cloud-300); font: inherit; background: var(--cloud-50);
}
.filter-group input[type="search"]:focus { outline: 2px solid var(--lavender-300); border-color: var(--lavender-500); }

label.checkbox {
  display: flex; align-items: center; gap: 10px;
  font-size: 13px; padding: 6px 0; cursor: pointer; user-select: none;
}
label.checkbox input { accent-color: var(--lavender-500); }
.tag-cloud { display: flex; flex-wrap: wrap; gap: 6px; }
.tag-chip {
  display: inline-flex; align-items: center;
  gap: 0; padding: 5px 12px;
  border: 1px solid var(--cloud-300); border-radius: 999px;
  background: var(--cloud-0); color: var(--mist-700);
  font-size: 12px; font-weight: 500; line-height: 1;
  cursor: pointer; user-select: none;
  transition: background 0.12s ease, border-color 0.12s ease, color 0.12s ease;
}
.tag-chip:hover { border-color: var(--lavender-300); color: var(--ink-900); }
.tag-chip input {
  /* Visually hidden but still focusable + label-clickable. */
  position: absolute; opacity: 0; width: 1px; height: 1px;
  pointer-events: none; margin: 0; padding: 0; border: 0;
}
.tag-chip.is-checked {
  background: var(--lavender-100);
  border-color: var(--lavender-500);
  color: var(--lavender-700);
}
.tag-chip:focus-within { outline: 2px solid var(--lavender-300); outline-offset: 1px; }

.pill-progress {
  background: var(--cloud-100); color: var(--mist-700);
}
.pill-progress.is-completed {
  background: var(--sage-100); color: var(--sage-500);
}
.pill-progress.is-in-progress {
  background: var(--lavender-100); color: var(--lavender-700);
}

.catalogue h1 { font-size: 32px; margin: 0; letter-spacing: -0.5px; }
.cat-head { display: flex; justify-content: space-between; align-items: flex-end; gap: 24px; margin-bottom: 24px; }
.cat-meta {
  align-self: center; padding: 4px 12px; border-radius: 999px;
  background: var(--cloud-100); color: var(--mist-700); font-size: 12px; font-weight: 600;
}

.cards { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 16px; }
.card {
  background: var(--cloud-0); border: 1px solid var(--cloud-200); border-radius: 20px;
  transition: border-color 0.15s ease, transform 0.05s ease, box-shadow 0.15s ease;
}
.card:hover { border-color: var(--lavender-300); box-shadow: 0 8px 24px rgba(86, 53, 174, 0.07); }
.card .card-link { display: block; padding: 18px 20px; color: inherit; }
.card .card-link:hover { text-decoration: none; }
.card-head { display: flex; align-items: center; gap: 6px; margin-bottom: 10px; font-size: 12px; }
.card h3 { margin: 6px 0 8px; font-size: 17px; line-height: 1.3; }
.card-summary { font-size: 14px; color: var(--mist-700); margin: 0 0 14px; }
.card-chips { display: flex; flex-wrap: wrap; gap: 6px; }
.chip {
  font-size: 11px; padding: 2px 8px; border-radius: 999px;
  background: var(--cloud-100); color: var(--mist-700);
}
.empty { color: var(--mist-500); font-size: 14px; padding: 24px; text-align: center; }
</style>`;
}

function libraryClientScript(): string {
  return `<script>
(function () {
  const KEY = "fortress.codelabs.filters.v1";
  const def = { search: "", levels: [], tags: [], statuses: [] };
  let state;
  try { state = Object.assign({}, def, JSON.parse(localStorage.getItem(KEY) || "{}")); }
  catch (_) { state = Object.assign({}, def); }

  const search = document.getElementById("filter-search");
  const levelInputs = document.querySelectorAll('input[name="level"]');
  const tagInputs = document.querySelectorAll('input[name="tag"]');
  const statusInputs = document.querySelectorAll('input[name="status"]');
  const cards = Array.from(document.querySelectorAll(".card"));
  const empty = document.getElementById("empty");
  const meta = document.getElementById("cat-meta");

  function persist() { localStorage.setItem(KEY, JSON.stringify(state)); }
  function syncUI() {
    search.value = state.search || "";
    levelInputs.forEach((i) => { i.checked = state.levels.includes(i.value); });
    tagInputs.forEach((i) => {
      i.checked = state.tags.includes(i.value);
      var chip = i.closest(".tag-chip");
      if (chip) chip.classList.toggle("is-checked", i.checked);
    });
    statusInputs.forEach((i) => { i.checked = state.statuses.includes(i.value); });
  }

  function applyProgressBadges() {
    cards.forEach(function (c) {
      var pill = c.querySelector('[data-progress-pill]');
      if (!pill) return;
      var slug = c.getAttribute('data-slug');
      var total = parseInt(c.getAttribute('data-steps') || '0', 10);
      try {
        var raw = JSON.parse(localStorage.getItem('fortress.codelabs.progress.' + slug) || 'null');
        if (!raw || typeof raw !== 'object') {
          pill.hidden = true; c.removeAttribute('data-progress'); return;
        }
        var completedCount = Array.isArray(raw.completed) ? raw.completed.length : 0;
        var current = typeof raw.current === 'number' ? raw.current : 0;
        if (total > 0 && completedCount >= total) {
          pill.hidden = false;
          pill.textContent = 'completed';
          pill.classList.add('is-completed');
          pill.classList.remove('is-in-progress');
          c.setAttribute('data-progress', 'completed');
        } else if (completedCount > 0 || current > 0) {
          pill.hidden = false;
          pill.textContent = total > 0
            ? 'in progress · ' + Math.min(completedCount, total) + '/' + total
            : 'in progress';
          pill.classList.add('is-in-progress');
          pill.classList.remove('is-completed');
          c.setAttribute('data-progress', 'in-progress');
        } else {
          pill.hidden = true;
          c.removeAttribute('data-progress');
        }
      } catch (_) {
        pill.hidden = true;
      }
    });
  }

  function applyFilters() {
    const q = (state.search || "").trim().toLowerCase();
    let visible = 0;
    cards.forEach((c) => {
      const level = c.getAttribute("data-level");
      const status = c.getAttribute("data-status");
      const tags = (c.getAttribute("data-tags") || "").split("|");
      const haystack = c.getAttribute("data-search") || "";
      let ok = true;
      if (state.levels.length && !state.levels.includes(level)) ok = false;
      if (ok && state.statuses.length && !state.statuses.includes(status)) ok = false;
      if (ok && state.tags.length && !state.tags.every((t) => tags.includes(t))) ok = false;
      if (ok && q && !haystack.includes(q)) ok = false;
      c.hidden = !ok;
      if (ok) visible++;
    });
    empty.hidden = visible !== 0;
    meta.textContent = visible + " of " + cards.length + " codelabs";
  }

  function bind() {
    search.addEventListener("input", (e) => {
      state.search = e.target.value;
      persist(); applyFilters();
    });
    levelInputs.forEach((i) => i.addEventListener("change", () => {
      state.levels = Array.from(levelInputs).filter((x) => x.checked).map((x) => x.value);
      persist(); applyFilters();
    }));
    tagInputs.forEach((i) => i.addEventListener("change", () => {
      var chip = i.closest(".tag-chip");
      if (chip) chip.classList.toggle("is-checked", i.checked);
      state.tags = Array.from(tagInputs).filter((x) => x.checked).map((x) => x.value);
      persist(); applyFilters();
    }));
    statusInputs.forEach((i) => i.addEventListener("change", () => {
      state.statuses = Array.from(statusInputs).filter((x) => x.checked).map((x) => x.value);
      persist(); applyFilters();
    }));
    document.querySelectorAll("[data-clear]").forEach((btn) => {
      btn.addEventListener("click", () => {
        const which = btn.getAttribute("data-clear");
        if (which === "level") state.levels = [];
        if (which === "tag") state.tags = [];
        persist(); syncUI(); applyFilters();
      });
    });
    document.getElementById("filter-reset").addEventListener("click", () => {
      state = Object.assign({}, def);
      persist(); syncUI(); applyFilters();
    });
  }

  syncUI(); bind(); applyProgressBadges(); applyFilters();
  // Refresh the badges if the user comes back from a viewer page (back-button cache).
  window.addEventListener("pageshow", applyProgressBadges);
})();
</script>`;
}
