CREATE TABLE IF NOT EXISTS products (
    id integer primary key autoincrement,
    name varchar(255),
    price float,
    stock integer
);

CREATE TABLE IF NOT EXISTS orders (
    id integer primary key autoincrement,
    created_at timestamp,
    customer varchar(255),
    status varchar(255) check (status in ('CREATED','PROCESSING','COMPLETED','FAILED'))
);

CREATE TABLE IF NOT EXISTS order_items (
    id integer primary key autoincrement,
    order_id bigint,
    product_id bigint,
    product_name varchar(255),
    quantity integer,
    price float
);

CREATE TABLE IF NOT EXISTS invoices (
    id integer primary key autoincrement,
    total float,
    created_at timestamp,
    order_id bigint unique,
    status varchar(255) check (status in ('PENDING','PAID','VOID'))
);
