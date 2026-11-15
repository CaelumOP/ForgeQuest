// Minimal Node typings for the SQLite-backed test run (the package ships Workers types only).
declare module "node:sqlite" {
  export class StatementSync {
    get(...args: unknown[]): unknown;
    all(...args: unknown[]): unknown[];
    run(...args: unknown[]): { changes: number | bigint; lastInsertRowid: number | bigint };
  }
  export class DatabaseSync {
    constructor(path: string);
    exec(sql: string): void;
    prepare(sql: string): StatementSync;
  }
}
declare module "node:fs" {
  export function readFileSync(path: URL | string, encoding: "utf8"): string;
}
declare const process: { env: Record<string, string | undefined> };
interface ImportMeta { readonly url: string }
