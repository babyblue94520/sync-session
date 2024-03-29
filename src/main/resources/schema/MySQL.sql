CREATE TABLE IF NOT EXISTS `{tableName}`
(
    `id`                    varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
    `create_time`           bigint(13) NULL DEFAULT 0,
    `max_inactive_interval` bigint(13) NULL DEFAULT 0,
    `last_access_time`      bigint(13) NULL DEFAULT 0,
    `effective_time`        bigint(13) NULL DEFAULT 0,
    `username`              varchar(30) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL DEFAULT '',
    `attributes`            text CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NULL,
    PRIMARY KEY (`id`) USING BTREE,
    KEY `username`(`username`) USING BTREE,
    KEY `id_username` (`id`,`username`) USING BTREE,
    KEY `effective_time` (`effective_time`,`username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci ROW_FORMAT = Dynamic;
