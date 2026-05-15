/**
 * Codelab loader, parser and HTML renderer.
 *
 * Reads every `.md` file under `src/web/codelabs/` at module load, parses a hand-rolled
 * YAML-subset frontmatter, splits the body into step blocks, and renders a restricted
 * Markdown subset (headings, paragraphs, lists, fenced code blocks, inline code,
 * **bold**, _italic_, links, blockquotes) to HTML.
 *
 * Zero runtime dependencies. The subset covers everything our authoring guide allows; if
 * we ever want footnotes / tables / images, expand it here.
 */
import { readdirSync, readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";

export type CodelabLevel = "beginner" | "intermediate" | "advanced";
export type CodelabStatus = "published" | "draft";

export interface CodelabReference {
  title: string;
  url: string;
}

export interface CodelabStep {
  /** Slugified id used in DOM anchors. */
  id: string;
  /** Human-readable step title (without the leading "Step N:" if present). */
  title: string;
  /** Original markdown text for the step body, excluding the heading line. */
  bodyMarkdown: string;
  /** Pre-rendered HTML for the step body. */
  bodyHtml: string;
  /** First paragraph used by the side rail as a short blurb. */
  blurb: string;
}

export interface Codelab {
  slug: string;
  title: string;
  level: CodelabLevel;
  status: CodelabStatus;
  estimatedMinutes: number;
  company: string;
  tags: string[];
  summary: string;
  references: CodelabReference[];
  steps: CodelabStep[];
}

const HERE = dirname(fileURLToPath(import.meta.url));

let cached: Codelab[] | null = null;

/**
 * Returns the parsed catalogue. Builds on first call and memoises for the lifetime of
 * the process (Vercel functions cold-start fresh, which is fine — markdown parsing is
 * single-digit milliseconds total).
 */
export function loadCodelabs(): Codelab[] {
  if (cached) return cached;
  const dir = HERE;
  let entries: string[] = [];
  try {
    entries = readdirSync(dir);
  } catch (err) {
    console.error("[codelabs] could not read", dir, err);
    cached = [];
    return cached;
  }
  const files = entries.filter((f) => f.endsWith(".md") && !f.startsWith("_") && f !== "README.md");
  const labs = files
    .map((file) => {
      try {
        return parseCodelab(file, readFileSync(join(dir, file), "utf8"));
      } catch (err) {
        console.error("[codelabs] failed to parse", file, err);
        return null;
      }
    })
    .filter((c): c is Codelab => c !== null);
  labs.sort((a, b) => {
    const order: Record<CodelabLevel, number> = { beginner: 0, intermediate: 1, advanced: 2 };
    const dl = order[a.level] - order[b.level];
    if (dl !== 0) return dl;
    return a.title.localeCompare(b.title);
  });
  cached = labs;
  return cached;
}

export function findCodelab(slug: string): Codelab | undefined {
  return loadCodelabs().find((c) => c.slug === slug);
}

// ---------------------------------------------------------------------------------------
//  Frontmatter
// ---------------------------------------------------------------------------------------

interface ParsedFrontmatter {
  data: Record<string, unknown>;
  body: string;
}

function splitFrontmatter(raw: string): ParsedFrontmatter {
  const trimmed = raw.replace(/^﻿/, "");
  if (!trimmed.startsWith("---")) return { data: {}, body: trimmed };
  const end = trimmed.indexOf("\n---", 3);
  if (end < 0) return { data: {}, body: trimmed };
  const fmRaw = trimmed.slice(3, end);
  const body = trimmed.slice(end + 4).replace(/^\r?\n/, "");
  return { data: parseYamlSubset(fmRaw), body };
}

/**
 * Tiny YAML subset just rich enough for our frontmatter shape:
 * - `key: scalar`
 * - `key:` followed by indented `- item` lines
 * - `key:` followed by indented `key: value` lines (nested maps)
 * - block scalars introduced by `>` (folded) or `|` (literal)
 *
 * Anything else is ignored. Designed to fail soft — bad frontmatter doesn't crash the
 * loader, it just produces a thin object that downstream code can defend against.
 */
function parseYamlSubset(input: string): Record<string, unknown> {
  const lines = input.split(/\r?\n/);
  const root: Record<string, unknown> = {};
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];
    if (!line || /^\s*#/.test(line)) { i++; continue; }
    const m = line.match(/^([A-Za-z_][\w-]*):\s*(.*)$/);
    if (!m) { i++; continue; }
    const key = m[1];
    const inline = m[2];

    if (inline === "") {
      // Look ahead: either a list of `- …` items, a nested map, or a block scalar marker
      const peek = lines[i + 1] ?? "";
      const indented = /^\s+(.*)$/.exec(peek);
      if (indented && indented[1].startsWith("- ")) {
        const list: unknown[] = [];
        i++;
        while (i < lines.length && /^\s+- /.test(lines[i])) {
          const itemStart = lines[i];
          const itemText = itemStart.replace(/^\s+-\s?/, "");
          const nextIndentMatch = /^(\s+)- /.exec(itemStart)!;
          const baseIndent = nextIndentMatch[1].length + 2;
          if (itemText.includes(":") && !/^["']/.test(itemText)) {
            const obj: Record<string, unknown> = {};
            const km = itemText.match(/^([A-Za-z_][\w-]*):\s*(.*)$/);
            if (km) obj[km[1]] = parseScalar(km[2]);
            i++;
            while (i < lines.length && lines[i].startsWith(" ".repeat(baseIndent))) {
              const subLine = lines[i].slice(baseIndent);
              const subm = subLine.match(/^([A-Za-z_][\w-]*):\s*(.*)$/);
              if (subm) obj[subm[1]] = parseScalar(subm[2]);
              i++;
            }
            list.push(obj);
          } else {
            list.push(parseScalar(itemText));
            i++;
          }
        }
        root[key] = list;
      } else if (peek.startsWith("  ")) {
        // Could be a folded/literal scalar — only handle the `>` form here for `summary: >`
        i++;
        const parts: string[] = [];
        while (i < lines.length && /^\s{2,}/.test(lines[i])) {
          parts.push(lines[i].trim());
          i++;
        }
        root[key] = parts.join(" ").trim();
      } else {
        root[key] = "";
        i++;
      }
    } else if (inline === ">" || inline === "|") {
      i++;
      const parts: string[] = [];
      while (i < lines.length && /^\s{2,}/.test(lines[i])) {
        parts.push(lines[i].trim());
        i++;
      }
      root[key] = inline === "|" ? parts.join("\n") : parts.join(" ");
    } else {
      root[key] = parseScalar(inline);
      i++;
    }
  }
  return root;
}

function parseScalar(raw: string): unknown {
  const trimmed = raw.trim();
  if (trimmed === "") return "";
  if (/^-?\d+(\.\d+)?$/.test(trimmed)) return Number(trimmed);
  if (trimmed === "true") return true;
  if (trimmed === "false") return false;
  if (/^"[^"]*"$/.test(trimmed)) return trimmed.slice(1, -1);
  if (/^'[^']*'$/.test(trimmed)) return trimmed.slice(1, -1);
  return trimmed;
}

// ---------------------------------------------------------------------------------------
//  Codelab parsing
// ---------------------------------------------------------------------------------------

function parseCodelab(filename: string, raw: string): Codelab {
  const { data, body } = splitFrontmatter(raw);
  const slug = String(data.slug ?? filename.replace(/\.md$/, ""));
  const title = String(data.title ?? slug);
  const level = (String(data.level ?? "beginner") as CodelabLevel);
  const status = (String(data.status ?? "published") as CodelabStatus);
  const estimatedMinutes = Number(data.estimated_minutes ?? data.estimatedMinutes ?? 10);
  const company = String(data.company ?? "Fortress");
  const tags = Array.isArray(data.tags) ? data.tags.map(String) : [];
  const summary = String(data.summary ?? "");
  const references: CodelabReference[] = Array.isArray(data.references)
    ? (data.references as Array<Record<string, unknown>>).map((r) => ({
        title: String(r.title ?? r.url ?? ""),
        url: String(r.url ?? ""),
      })).filter((r) => r.url)
    : [];

  const steps = splitSteps(body);

  return {
    slug,
    title,
    level,
    status,
    estimatedMinutes,
    company,
    tags,
    summary,
    references,
    steps,
  };
}

/**
 * Split a body into steps. A "step" starts on a line whose first heading begins with
 * `## ` and runs until the next `## ` heading at the same level. Horizontal-rule
 * separators (`---`) between steps are tolerated and stripped.
 */
function splitSteps(body: string): CodelabStep[] {
  const lines = body.split(/\r?\n/);
  const blocks: Array<{ heading: string; body: string[] }> = [];
  let current: { heading: string; body: string[] } | null = null;
  for (const line of lines) {
    if (/^##\s+/.test(line)) {
      if (current) blocks.push(current);
      current = { heading: line.replace(/^##\s+/, "").trim(), body: [] };
    } else if (current) {
      if (/^---+\s*$/.test(line)) continue;
      current.body.push(line);
    }
  }
  if (current) blocks.push(current);

  return blocks.map((b, idx) => {
    const stripped = b.heading.replace(/^Step\s+\d+:\s*/i, "");
    const blurb = firstParagraph(b.body.join("\n"));
    const bodyMd = b.body.join("\n").trim();
    return {
      id: slugify(`step-${idx}-${stripped}`),
      title: b.heading,
      bodyMarkdown: bodyMd,
      bodyHtml: renderMarkdown(bodyMd),
      blurb,
    };
  });
}

function firstParagraph(text: string): string {
  const paras = text.trim().split(/\n\s*\n/);
  const first = paras[0] ?? "";
  return first.replace(/\s+/g, " ").trim();
}

export function slugify(input: string): string {
  return input
    .toLowerCase()
    .normalize("NFD")
    .replace(/[̀-ͯ]/g, "")
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
}

// ---------------------------------------------------------------------------------------
//  Markdown → HTML (minimal subset)
// ---------------------------------------------------------------------------------------

export function escapeHtml(s: string): string {
  return s
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

function renderInline(text: string): string {
  // Inline code first (so we don't get bold/italic inside).
  let out = "";
  let i = 0;
  while (i < text.length) {
    const tick = text.indexOf("`", i);
    if (tick < 0) {
      out += transformInline(text.slice(i));
      break;
    }
    out += transformInline(text.slice(i, tick));
    const end = text.indexOf("`", tick + 1);
    if (end < 0) {
      out += transformInline(text.slice(tick));
      break;
    }
    out += `<code>${escapeHtml(text.slice(tick + 1, end))}</code>`;
    i = end + 1;
  }
  return out;
}

function transformInline(raw: string): string {
  let s = escapeHtml(raw);
  // Links: [text](url)
  s = s.replace(/\[([^\]]+)\]\(([^)\s]+)\)/g, (_m, txt, url) => {
    const safe = url.startsWith("http") || url.startsWith("/") || url.startsWith("#") ? url : "#";
    return `<a href="${safe}">${txt}</a>`;
  });
  // Bold **text**
  s = s.replace(/\*\*([^*]+)\*\*/g, "<strong>$1</strong>");
  // Italic _text_ (avoid swallowing snake_case by requiring start/end word boundary)
  s = s.replace(/(^|[\s(])_([^_]+)_(?=$|[\s,.;:!?)])/g, "$1<em>$2</em>");
  return s;
}

/**
 * Block-level markdown renderer. Order of operations:
 *
 *   fenced code → blockquote → unordered list → ordered list → headings → paragraph
 *
 * Code blocks bypass the rest entirely (their contents are HTML-escaped verbatim).
 */
export function renderMarkdown(input: string): string {
  const lines = input.split(/\r?\n/);
  const html: string[] = [];
  let i = 0;
  while (i < lines.length) {
    const line = lines[i];

    // Fenced code block
    const fence = /^```(\w+)?\s*$/.exec(line);
    if (fence) {
      const lang = fence[1] ?? "";
      const buf: string[] = [];
      i++;
      while (i < lines.length && !/^```\s*$/.test(lines[i])) {
        buf.push(lines[i]);
        i++;
      }
      i++; // consume closing fence
      const codeClass = lang ? ` class="lang-${escapeHtml(lang)}"` : "";
      html.push(`<pre><code${codeClass}>${escapeHtml(buf.join("\n"))}</code></pre>`);
      continue;
    }

    // Blockquote (one or more `>` lines)
    if (/^>\s?/.test(line)) {
      const buf: string[] = [];
      while (i < lines.length && /^>\s?/.test(lines[i])) {
        buf.push(lines[i].replace(/^>\s?/, ""));
        i++;
      }
      html.push(`<blockquote>${renderMarkdown(buf.join("\n"))}</blockquote>`);
      continue;
    }

    // Unordered list
    if (/^[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^[-*]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^[-*]\s+/, ""));
        i++;
      }
      html.push(`<ul>${items.map((it) => `<li>${renderInline(it)}</li>`).join("")}</ul>`);
      continue;
    }

    // Ordered list
    if (/^\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\d+\.\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\d+\.\s+/, ""));
        i++;
      }
      html.push(`<ol>${items.map((it) => `<li>${renderInline(it)}</li>`).join("")}</ol>`);
      continue;
    }

    // Headings (### and deeper inside steps; ## already consumed by splitSteps)
    const h = /^(#{3,6})\s+(.+)$/.exec(line);
    if (h) {
      const level = h[1].length;
      html.push(`<h${level}>${renderInline(h[2])}</h${level}>`);
      i++;
      continue;
    }

    // Blank line
    if (line.trim() === "") { i++; continue; }

    // Paragraph: collect contiguous non-blank lines that don't start any block syntax
    const buf: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() !== "" &&
      !/^```/.test(lines[i]) &&
      !/^>\s?/.test(lines[i]) &&
      !/^[-*]\s+/.test(lines[i]) &&
      !/^\d+\.\s+/.test(lines[i]) &&
      !/^#{3,6}\s+/.test(lines[i])
    ) {
      buf.push(lines[i]);
      i++;
    }
    html.push(`<p>${renderInline(buf.join(" "))}</p>`);
  }
  return html.join("\n");
}
