create table if not exists `log` (
    `port`  varchar(100) not null default '',
    `id`    varchar(100) not null default '',
    `type`  bigint default 0,
    primary key (`port`, `id`)
);
