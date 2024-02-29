SET NAMES utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE TABLE `prompts` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `guid` VARCHAR(36) NOT NULL,
  `content` TEXT NOT NULL,
  `display_name` VARCHAR(255) NOT NULL,
  `author` VARCHAR(255) NOT NULL,
  `created_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `updated_at` TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `prompts_U1` (`guid`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `tags` (
  `id` INT NOT NULL AUTO_INCREMENT,
  `tag` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `tags_U1` (`tag`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `prompt_tags` (
  `prompt_id` INT NOT NULL,
  `tag_id` INT NOT NULL,
  PRIMARY KEY (`prompt_id`, `tag_id`),
  CONSTRAINT `prompt_tags_F1` FOREIGN KEY (`prompt_id`) REFERENCES `prompts` (`id`) ON DELETE CASCADE,
  CONSTRAINT `prompt_tags_F2` FOREIGN KEY (`tag_id`) REFERENCES `tags` (`id`) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE `roster` (
  `user` VARCHAR(255) NOT NULL,
  `password` VARCHAR(255) NOT NULL,
  `class_key` VARCHAR(255) NOT NULL,
  PRIMARY KEY (`user`),
  UNIQUE KEY `roster_U1` (`class_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
