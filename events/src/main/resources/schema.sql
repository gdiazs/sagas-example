CREATE TABLE IF NOT EXISTS saga_events (
    id integer primary key autoincrement,
    order_id bigint,
    service varchar(255),
    type varchar(255),
    payload varchar(2000),
    created_at timestamp
);
