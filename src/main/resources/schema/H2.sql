create table if not exists `{tableName}`
(
    id                    varchar(32) not null,
    create_time           bigint      default '0',
    max_inactive_interval bigint      default '0',
    last_access_time      bigint      default '0',
    effective_time        bigint      default '0',
    username              varchar(30) default '',
    attributes            text        default '{}',
    primary key (id)
);
