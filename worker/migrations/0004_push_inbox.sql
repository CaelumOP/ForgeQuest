-- 4.1: push alerts, inbox read state, per-friend mutes, workout events. Additive only, so the 4.0 Worker keeps working
-- until the 4.1 Worker is deployed. (users.name is dropped later in 0005, after the 4.1 Worker no longer reads it.)
ALTER TABLE users ADD COLUMN alerts_on INTEGER NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN share_workouts INTEGER NOT NULL DEFAULT 1;
ALTER TABLE users ADD COLUMN pings_on INTEGER NOT NULL DEFAULT 1;

-- user_id does not want activity pushes from muted_id.
CREATE TABLE IF NOT EXISTS mutes (
  user_id TEXT NOT NULL,
  muted_id TEXT NOT NULL,
  PRIMARY KEY (user_id, muted_id)
);

-- One row per new workout count reported on a Riyadh day (done_count = the week's `done` after the workout).
CREATE TABLE IF NOT EXISTS workout_events (
  user_id TEXT NOT NULL,
  day INTEGER NOT NULL,
  done_count INTEGER NOT NULL,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, day, done_count)
);
CREATE INDEX IF NOT EXISTS idx_workout_events_user_created ON workout_events(user_id, created_at);

-- At most one activity push per sender -> receiver per Riyadh day.
CREATE TABLE IF NOT EXISTS activity_push (
  from_id TEXT NOT NULL,
  to_id TEXT NOT NULL,
  day INTEGER NOT NULL,
  PRIMARY KEY (from_id, to_id, day)
);

-- Inbox read state for 4.1 clients; the legacy `seen` column keeps serving 4.0 clients.
ALTER TABLE nudges ADD COLUMN seen_v4 INTEGER NOT NULL DEFAULT 0;
UPDATE nudges SET seen_v4 = seen;
