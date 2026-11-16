// Runs the real D1Store SQL against an in-memory SQLite (Node's built-in node:sqlite) so the suite proves the
// production queries, not just MemStore. Enable with FQ_STORE=sqlite (npm run test:sqlite).
// The tiny D1 shim covers what D1Store uses: prepare().bind().first/all/run and db.batch().
import { readFileSync } from "node:fs";
import { DatabaseSync } from "node:sqlite";
import { D1Store } from "../src/store";

type Row = Record<string, unknown>;

class Stmt {
  constructor(
    private readonly db: DatabaseSync,
    private readonly sql: string,
    private readonly args: unknown[] = [],
  ) {}
  bind(...args: unknown[]): Stmt {
    return new Stmt(this.db, this.sql, args);
  }
  private prepared() {
    return this.db.prepare(this.sql);
  }
  async first<T = Row>(col?: string): Promise<T | null> {
    const row = (this.prepared().get(...(this.args as never[])) as Row | undefined) ?? null;
    if (row && col) return (row[col] as T) ?? null;
    return row as T | null;
  }
  async all<T = Row>(): Promise<{ results: T[]; success: true; meta: { changes: number } }> {
    return { results: this.prepared().all(...(this.args as never[])) as T[], success: true, meta: { changes: 0 } };
  }
  runSync(): { results: Row[]; success: true; meta: { changes: number } } {
    const st = this.prepared();
    // Readers (SELECT or RETURNING) give rows in D1 batch results; writers give only meta.changes.
    if (/^\s*(SELECT|WITH)\b/i.test(this.sql) || /\bRETURNING\b/i.test(this.sql)) {
      const rows = st.all(...(this.args as never[])) as Row[];
      return { results: rows, success: true, meta: { changes: rows.length } };
    }
    const r = st.run(...(this.args as never[]));
    return { results: [], success: true, meta: { changes: Number(r.changes) } };
  }
  async run() {
    return this.runSync();
  }
}

class ShimD1 {
  constructor(readonly raw: DatabaseSync) {}
  prepare(sql: string): Stmt {
    return new Stmt(this.raw, sql);
  }
  async batch(stmts: Stmt[]) {
    // D1 batches are atomic: mirror that with one transaction.
    this.raw.exec("BEGIN");
    try {
      const out = stmts.map((s) => s.runSync());
      this.raw.exec("COMMIT");
      return out;
    } catch (e) {
      this.raw.exec("ROLLBACK");
      throw e;
    }
  }
}

export class SqliteStore extends D1Store {
  readonly raw: DatabaseSync;

  constructor() {
    const raw = new DatabaseSync(":memory:");
    for (const m of ["0001_init.sql", "0002_username.sql", "0003_week_states.sql", "0004_push_inbox.sql", "0005_drop_user_name.sql"]) {
      raw.exec(readFileSync(new URL(`../migrations/${m}`, import.meta.url), "utf8"));
    }
    super(new ShimD1(raw) as unknown as D1Database);
    this.raw = raw;
  }

  /** Test hooks with the same shape the suite uses on MemStore. */
  readonly snapshots = {
    get: (userId: string) =>
      this.raw.prepare("SELECT * FROM snapshots WHERE user_id = ?").get(userId) as Row | undefined,
  };

  readonly friends = {
    set: (userId: string, ids: Set<string>) => {
      this.raw.prepare("DELETE FROM friends WHERE user_id = ?").run(userId);
      for (const id of ids) this.addFriendRow(userId, id);
    },
    get: (userId: string) => ({ add: (id: string) => this.addFriendRow(userId, id) }),
  };

  /** Rows in any table that still mention [userId] (DELETE /me must leave 0). */
  rowsReferencing(userId: string): number {
    const q = [
      "SELECT COUNT(*) AS c FROM users WHERE id = ?1",
      "SELECT COUNT(*) AS c FROM sessions WHERE user_id = ?1",
      "SELECT COUNT(*) AS c FROM snapshots WHERE user_id = ?1",
      "SELECT COUNT(*) AS c FROM friends WHERE user_id = ?1 OR friend_id = ?1",
      "SELECT COUNT(*) AS c FROM requests WHERE from_id = ?1 OR to_id = ?1",
      "SELECT COUNT(*) AS c FROM blocks WHERE blocker = ?1 OR blocked = ?1",
      "SELECT COUNT(*) AS c FROM nudges WHERE from_id = ?1 OR to_id = ?1",
      "SELECT COUNT(*) AS c FROM push_tokens WHERE user_id = ?1",
      "SELECT COUNT(*) AS c FROM week_states WHERE user_id = ?1",
      "SELECT COUNT(*) AS c FROM mutes WHERE user_id = ?1 OR muted_id = ?1",
      "SELECT COUNT(*) AS c FROM workout_events WHERE user_id = ?1",
      "SELECT COUNT(*) AS c FROM activity_push WHERE from_id = ?1 OR to_id = ?1",
    ];
    return q.reduce((n, sql) => n + Number((this.raw.prepare(sql).get(userId) as { c: number }).c), 0);
  }

  private addFriendRow(userId: string, friendId: string) {
    this.raw
      .prepare("INSERT OR IGNORE INTO friends (user_id, friend_id, created_at) VALUES (?, ?, 0)")
      .run(userId, friendId);
  }
}
