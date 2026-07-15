create table if not exists {tableName}
(
    id                    varchar(36) not null,
    create_time           bigint      default 0,
    max_inactive_interval bigint      default 0,
    last_access_time      bigint      default 0,
    username              varchar(100) default '',
    attributes            text        default '{}',
    primary key (id)
);

create index if not exists idx_{tableName}_username on {tableName}(username);
create index if not exists idx_{tableName}_last_access_time on {tableName}(last_access_time);
