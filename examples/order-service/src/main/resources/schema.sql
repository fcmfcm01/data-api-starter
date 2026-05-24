CREATE TABLE orders (
    order_no      VARCHAR(20)  PRIMARY KEY,
    status        VARCHAR(20)  NOT NULL,
    amount        DECIMAL(10,2) NOT NULL,
    customer_name VARCHAR(50)  NOT NULL
);
