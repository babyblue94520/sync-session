create table if not exists session
(
    id                    varchar(32) not null,
    attributes            text        default '{}',
    create_time           bigint      default '0',
    last_access_time      bigint      default '0',
    max_inactive_interval bigint      default '0',
    username              varchar(30) default '',
    primary key (id)
)
