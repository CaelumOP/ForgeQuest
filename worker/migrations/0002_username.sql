-- 4.0: friends are added by unique username (stored lowercase by the Worker) instead of the 6-char code.
-- `code` stays (NOT NULL UNIQUE; dropping it needs a table rebuild) but is no longer exposed or looked up.
ALTER TABLE users ADD COLUMN username TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_username ON users(username);

-- Users who joined under v1 have no username: send them back through Join and drop their v1 links.
DELETE FROM friends WHERE user_id IN (SELECT id FROM users WHERE username IS NULL)
                       OR friend_id IN (SELECT id FROM users WHERE username IS NULL);
DELETE FROM requests WHERE from_id IN (SELECT id FROM users WHERE username IS NULL)
                        OR to_id IN (SELECT id FROM users WHERE username IS NULL);
UPDATE users SET joined = 0 WHERE username IS NULL;
