-- CSDL CHO LAB 1: CHAT APP (CLIENT-SERVER & P2P)
CREATE DATABASE IF NOT EXISTS `ltm_chat` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `ltm_chat`;

DROP TABLE IF EXISTS `messages`;
DROP TABLE IF EXISTS `users`;

CREATE TABLE `users` (
  `id` INT AUTO_INCREMENT PRIMARY KEY,
  `username` VARCHAR(50) NOT NULL UNIQUE,
  `password` VARCHAR(100) NOT NULL DEFAULT '123456',
  `full_name` VARCHAR(100) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `messages` (
  `id` VARCHAR(50) PRIMARY KEY,
  `sender` VARCHAR(50) NOT NULL,
  `receiver` VARCHAR(50) NOT NULL,
  `content` TEXT,
  `type` VARCHAR(30) NOT NULL DEFAULT 'TEXT_MESSAGE',
  `file_name` VARCHAR(255) DEFAULT NULL,
  `file_size` BIGINT DEFAULT 0,
  `timestamp` VARCHAR(50) DEFAULT NULL,
  `created_at` DATETIME DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO `users` (`username`, `password`, `full_name`) VALUES
('Alice', '123456', 'Alice Nguyen'),
('Bob', '123456', 'Bob Tran'),
('Charlie', '123456', 'Charlie Le'),
('David', '123456', 'David Pham'),
('Emma', '123456', 'Emma Hoang'),
('Frank', '123456', 'Frank Vu');
