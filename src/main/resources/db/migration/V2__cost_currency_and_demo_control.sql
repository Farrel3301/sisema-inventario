-- Existing monetary amounts were COP. Keep their meaning; never convert or overwrite them.
ALTER TABLE products ADD COLUMN cost DECIMAL(14,2) NULL;
ALTER TABLE products ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'COP';
ALTER TABLE products ADD CONSTRAINT chk_product_cost CHECK (cost IS NULL OR cost >= 0);
ALTER TABLE products ADD CONSTRAINT chk_product_currency CHECK (currency IN ('COP','PEN'));
ALTER TABLE sales ADD COLUMN currency VARCHAR(3) NOT NULL DEFAULT 'COP';
ALTER TABLE sales ADD CONSTRAINT chk_sale_currency CHECK (currency IN ('COP','PEN'));
CREATE TABLE demo_seed_control (
 id INT PRIMARY KEY,
 completed BOOLEAN NOT NULL DEFAULT FALSE,
 completed_at TIMESTAMP(6) NULL
);
-- Coordination metadata only. Demo business data is NEVER inserted by Flyway.
INSERT INTO demo_seed_control (id, completed) VALUES (1, FALSE);
