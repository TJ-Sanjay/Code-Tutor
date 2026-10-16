-- Creates the CodeTutor database and a dedicated application user.
-- Run once as the MySQL administrator (setup-mysql.cmd does this for you).
CREATE DATABASE IF NOT EXISTS codetutor CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS 'codetutor'@'localhost' IDENTIFIED BY 'codetutor';
GRANT ALL PRIVILEGES ON codetutor.* TO 'codetutor'@'localhost';
FLUSH PRIVILEGES;
SELECT 'CodeTutor database and user are ready.' AS result;
