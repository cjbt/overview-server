ALTER TABLE "user" ADD last_activity_at TIMESTAMP DEFAULT NULL;
ALTER TABLE "user" ADD last_activity_ip VARCHAR DEFAULT NULL;
-- We allow NULL because new users have no activity
