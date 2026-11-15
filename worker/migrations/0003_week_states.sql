-- 4.0: per-user, per-Sunday-week result reported by the device ("hit" | "free" | "open"; "miss" only for last week),
-- used to compute the shared streak between two friends. Additive only: no existing row is changed.
CREATE TABLE IF NOT EXISTS week_states (
  user_id TEXT NOT NULL,
  week_start INTEGER NOT NULL,
  state TEXT NOT NULL,
  updated_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, week_start)
);
