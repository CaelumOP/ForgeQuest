-- 4.1: one name only (the username). Apply AFTER the 4.1 Worker is live: the 4.0 Worker still reads users.name.
ALTER TABLE users DROP COLUMN name;
