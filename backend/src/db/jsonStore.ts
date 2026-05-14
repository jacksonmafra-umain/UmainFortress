import fs from "node:fs/promises";
import path from "node:path";
import { fileURLToPath } from "node:url";

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const DATA_DIR = path.resolve(__dirname, "..", "..", "data");

/**
 * Tiny disk-backed JSON store. Every mutation rewrites the entire file via tempfile + atomic
 * rename, so a crash mid-write can never leave a half-written file. This is the right shape
 * for a demo: zero infra, fully inspectable on disk, easy to reset (`rm backend/data/*.json`).
 *
 * NOT for production. There is no concurrency control beyond the process-wide [serializeWrites]
 * queue — a multi-instance deploy would race. See docs/01-stateless-auth.md for the production
 * picture (DynamoDB, Postgres, Redis for refresh-token revocation, etc.).
 */
export interface Collection<T> {
  all(): Promise<T[]>;
  upsert(item: T, key: keyof T): Promise<T>;
  remove(predicate: (item: T) => boolean): Promise<void>;
  find(predicate: (item: T) => boolean): Promise<T | undefined>;
  replace(items: T[]): Promise<void>;
}

const writeQueues = new Map<string, Promise<unknown>>();

function serializeWrites<T>(file: string, fn: () => Promise<T>): Promise<T> {
  const previous = writeQueues.get(file) ?? Promise.resolve();
  const next = previous.then(fn, fn);
  writeQueues.set(
    file,
    next.catch(() => undefined),
  );
  return next;
}

export function collection<T extends object>(
  fileName: string,
  seed: () => T[],
): Collection<T> {
  const filePath = path.join(DATA_DIR, fileName);

  async function load(): Promise<T[]> {
    try {
      const raw = await fs.readFile(filePath, "utf8");
      return JSON.parse(raw) as T[];
    } catch (err: unknown) {
      if ((err as NodeJS.ErrnoException).code === "ENOENT") {
        const initial = seed();
        await save(initial);
        return initial;
      }
      throw err;
    }
  }

  async function save(items: T[]): Promise<void> {
    await fs.mkdir(DATA_DIR, { recursive: true });
    const tmp = `${filePath}.tmp`;
    await fs.writeFile(tmp, JSON.stringify(items, null, 2), "utf8");
    await fs.rename(tmp, filePath);
  }

  return {
    async all() {
      return load();
    },
    async upsert(item, key) {
      return serializeWrites(filePath, async () => {
        const items = await load();
        const idx = items.findIndex((existing) => existing[key] === item[key]);
        if (idx >= 0) items[idx] = item;
        else items.push(item);
        await save(items);
        return item;
      });
    },
    async remove(predicate) {
      return serializeWrites(filePath, async () => {
        const items = await load();
        const next = items.filter((item) => !predicate(item));
        await save(next);
      });
    },
    async find(predicate) {
      const items = await load();
      return items.find(predicate);
    },
    async replace(items) {
      return serializeWrites(filePath, async () => save(items));
    },
  };
}
