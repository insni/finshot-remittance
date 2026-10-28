-- Finshot remittance schema (H2 in-memory; standard SQL where possible)
-- 재시작 시 깨끗하게 다시 만들기 위해 자식 테이블부터 삭제
DROP TABLE IF EXISTS transfers;
DROP TABLE IF EXISTS customers;

-- 과제 제공 테이블 (그대로)
CREATE TABLE customers (
    customer_id VARCHAR(10) PRIMARY KEY,
    name        VARCHAR(100) NOT NULL
);

INSERT INTO customers (customer_id, name) VALUES
    ('C001', 'Kim'), ('C002', 'Lee'), ('C003', 'Park');

CREATE TABLE transfers (
    transfer_id      VARCHAR(36)   NOT NULL,                       -- UUID 문자열
    customer_id      VARCHAR(10)   NOT NULL,                       -- 송금 고객
    idempotency_key  VARCHAR(64)   NOT NULL,                       -- 클라이언트 재시도 키 (Idempotency-Key 헤더)
    request_hash     CHAR(64)      NOT NULL,                       -- 정규화한 요청 body의 SHA-256 (같은 키 다른 내용 감지)
    send_currency    CHAR(3)       NOT NULL,                       -- 보내는 통화
    receive_currency CHAR(3)       NOT NULL,                       -- 받는 통화
    send_amount      BIGINT        NOT NULL,                       -- 보내는 금액 (KRW 원 단위)
    fee              BIGINT        NOT NULL,                       -- 수수료 (KRW 원 단위)
    total_debit      BIGINT        NOT NULL,                       -- 총 출금액 = 금액 + 수수료
    receive_amount   DECIMAL(15,2) NOT NULL,                       -- 받는 금액 (PHP, 소수 2자리)
    recipient_name   VARCHAR(100)  NOT NULL,                       -- 수취인 이름
    status           VARCHAR(16)   NOT NULL,                       -- 상태 머신 값
    created_at       TIMESTAMP     NOT NULL,                       -- 생성 시각
    updated_at       TIMESTAMP     NOT NULL,                       -- 마지막 변경 시각

    CONSTRAINT pk_transfers PRIMARY KEY (transfer_id),                                       -- 기본키
    CONSTRAINT fk_transfers_customer FOREIGN KEY (customer_id) REFERENCES customers (customer_id), -- 없는 고객 차단
    CONSTRAINT uq_transfers_customer_idem_key UNIQUE (customer_id, idempotency_key),         -- 중복 송금 차단 (앱이 이름으로 감지)
    CONSTRAINT ck_transfers_send_currency CHECK (send_currency = 'KRW'),                     -- 보내는 통화는 KRW만
    CONSTRAINT ck_transfers_receive_currency CHECK (receive_currency = 'PHP'),               -- 받는 통화는 PHP만
    CONSTRAINT ck_transfers_send_amount_min CHECK (send_amount >= 10000),                    -- 최소 송금액
    CONSTRAINT ck_transfers_fee_min CHECK (fee >= 3000),                                     -- 최소 수수료
    CONSTRAINT ck_transfers_total_debit CHECK (total_debit = send_amount + fee),             -- 총 출금액 정합성
    CONSTRAINT ck_transfers_status CHECK (status IN ('REQUESTED', 'SENDING', 'COMPLETED', 'FAILED', 'CANCELLED')) -- 허용 상태 5개
);

-- Part 2 일일 한도 합산용: 고객별 기간 조회
CREATE INDEX idx_transfers_customer_created ON transfers (customer_id, created_at);
