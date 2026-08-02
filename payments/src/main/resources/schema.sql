CREATE TABLE IF NOT EXISTS payments (
    id integer primary key autoincrement,
    order_id bigint,
    invoice_id bigint,
    amount float,
    status varchar(255) check (status in ('PENDING','SUCCESS','FAILED')),
    fail_mode varchar(255),
    created_at timestamp
);
