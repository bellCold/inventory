CREATE TABLE product
(
    id       BIGSERIAL    PRIMARY KEY,
    name     VARCHAR      NOT NULL,
    quantity BIGINT       NOT NULL DEFAULT 0,

    -- 입고 시 미등록 상품 판별 기준. 동시 신규 등록 시 중복 생성을 막는다.
    CONSTRAINT uk_product_name UNIQUE (name),
    -- 재고는 음수가 될 수 없다 (DB 레벨 최종 방어선)
    CONSTRAINT ck_product_quantity_non_negative CHECK (quantity >= 0)
);