CREATE TABLE IF NOT EXISTS {tableName}
(
    id                    varchar(36) CHARACTER SET utf8mb4 NOT NULL,
    create_time           bigint(13) NULL DEFAULT 0,
    max_inactive_interval bigint(13) NULL DEFAULT 0,
    last_access_time      bigint(13) NULL DEFAULT 0,
    username              varchar(100) CHARACTER SET utf8mb4 NULL DEFAULT '',
    attributes            text CHARACTER SET utf8mb4 NULL,
    PRIMARY KEY (id) USING BTREE,
    KEY idx_username(username) USING BTREE,
    KEY idx_last_access_time (last_access_time) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 ROW_FORMAT = Dynamic;
