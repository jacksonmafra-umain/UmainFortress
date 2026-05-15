#!/usr/bin/env node
/**
 * Copy the canonical codelab markdown files from docs/codelabs/ into the backend tree so
 * Vercel's `includeFiles: "{web,ui}/**"` glob bundles them with the function.
 *
 * Called by both `npm run dev` and `npm run build`. Idempotent — wipes the destination
 * directory first so deleted source files don't linger.
 *
 * Usage:
 *   node scripts/sync-codelabs.mjs            # default: docs/codelabs → src/web/codelabs
 *   SRC=other/codelabs node scripts/...       # override source
 *   DST=src/web/labs node scripts/...         # override destination
 */
import { copyFile, mkdir, readdir, rm, stat } from "node:fs/promises";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const backendRoot = resolve(here, "..");
const repoRoot = resolve(backendRoot, "..");

const src = resolve(repoRoot, process.env.SRC ?? "docs/codelabs");
const dst = resolve(backendRoot, process.env.DST ?? "src/web/codelabs");

async function main() {
  const exists = await stat(src).then(() => true).catch(() => false);
  if (!exists) {
    console.error(`[sync-codelabs] source not found: ${src}`);
    process.exit(1);
  }
  await mkdir(dst, { recursive: true });

  // Only manage .md files — the destination shares space with hand-authored
  // TypeScript (loader.ts, library.ts, viewer.ts) and must not be wiped wholesale.
  const sourceFiles = (await readdir(src)).filter((f) => f.endsWith(".md"));
  const sourceSet = new Set(sourceFiles);

  // Remove only destination .md files that no longer exist in source; skip on EPERM
  // (read-only mounts in sandboxes) and let the subsequent copyFile overwrite the rest.
  const existing = (await readdir(dst)).filter((f) => f.endsWith(".md"));
  for (const f of existing) {
    if (!sourceSet.has(f)) {
      try { await rm(join(dst, f), { force: true }); }
      catch (err) { console.warn(`[sync-codelabs] could not remove stale ${f}:`, err.code || err.message); }
    }
  }

  // Overwrite every source file into destination.
  for (const f of sourceFiles) {
    try { await copyFile(join(src, f), join(dst, f)); }
    catch (err) { console.warn(`[sync-codelabs] could not copy ${f}:`, err.code || err.message); }
  }

  console.log(`[sync-codelabs] ${sourceFiles.length} markdown file(s) → ${dst}`);
}

main().catch((err) => {
  console.error("[sync-codelabs]", err);
  process.exit(1);
});
