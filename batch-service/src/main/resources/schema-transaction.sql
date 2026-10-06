CREATE TABLE IF NOT EXISTS processed_transactions (
    transaction_id BIGINT PRIMARY KEY,
    account_number VARCHAR(50) NOT NULL,
    amount NUMERIC(10, 2) NOT NULL,
    status VARCHAR(20) NOT NULL
);
