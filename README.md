# 재고 관리 시스템 MVP

상품의 입고·출고·재고 조회 API입니다. **동시 요청에서도 재고 정합성을 보장하는 것**을 핵심 목표로 했습니다.

## 기술 스택

| 구분 | 사용 기술 |
|---|---|
| Language | Java 21 |
| Framework | Spring Boot 3.5 (Web, Data JPA, Validation) |
| Database | PostgreSQL 16 |
| Migration | Flyway |
| Test | JUnit 5, AssertJ, Testcontainers (PostgreSQL) |

## 실행 방법

```bash
docker compose up -d   # PostgreSQL 기동
./gradlew bootRun      # 애플리케이션 실행 (Flyway 가 스키마 생성)
./gradlew test         # 테스트 (Docker 필요)
```

## API

| Method | URI | 설명 |
|---|---|---|
| `POST` | `/api/v1/products/inbound` | 입고. 미등록 상품이면 신규 등록 후 입고 |
| `POST` | `/api/v1/products/{id}/outbound` | 출고 |
| `GET` | `/api/v1/products/{id}` | 재고 조회 |

### 예시

```http
POST /api/v1/products/inbound
{ "name": "상품 A", "quantity": 10 }

200 OK
{ "id": 1, "name": "상품 A", "quantity": 10 }
```

```http
POST /api/v1/products/1/outbound
{ "quantity": 3 }

200 OK
{ "id": 1, "name": "상품 A", "quantity": 7 }
```

```http
GET /api/v1/products/1

200 OK
{ "id": 1, "name": "상품 A", "quantity": 7 }
```

### 에러 응답

모든 에러는 `{ "message": "..." }` 형태로 반환합니다.

| HTTP | 상황 | 예시 |
|---|---|---|
| 400 | 입력값 검증 실패 | `quantity: 0보다 커야 합니다, name: 공백일 수 없습니다` |
| 400 | JSON 파싱 불가 (깨진 JSON, 소수 수량 `1.5` 등) | `요청 본문을 해석할 수 없습니다.` |
| 404 | 존재하지 않는 상품 | `상품을 찾을 수 없습니다. (id: 999)` |
| 409 | 재고 부족 | `재고가 부족합니다. (현재 재고: 7, 요청 수량: 99)` |
| 409 | 락 대기 시간 초과, 신규 상품 동시 등록 충돌 | `동시 요청이 많아 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.` |

## DB DDL

[`src/main/resources/db/migration/V1__init.sql`](src/main/resources/db/migration/V1__init.sql)

```sql
CREATE TABLE product
(
    id       BIGSERIAL PRIMARY KEY,
    name     VARCHAR   NOT NULL,
    quantity BIGINT    NOT NULL DEFAULT 0,

    CONSTRAINT uk_product_name UNIQUE (name),
    CONSTRAINT ck_product_quantity_non_negative CHECK (quantity >= 0)
);
```

- `UNIQUE (name)`: 입고할 때 미등록 상품인지 판별하는 기준이고, 동시 신규 등록 시 중복 생성도 막습니다.
- `CHECK (quantity >= 0)`: 애플리케이션 검증이 뚫려도 음수 재고는 저장되지 않는 최종 방어선입니다.

## 설계

### 패키지 구조

```
com.assignment.inventory
├── api              ProductController, GlobalExceptionHandler (요청/응답, HTTP 상태 변환)
├── application      ProductService (트랜잭션, 유스케이스)
├── domain           Product, InsufficientStockException, ProductNotFoundException
└── infrastructure   ProductRepository (Spring Data JPA)
```

- **재고 규칙은 도메인이 지킵니다.** 재고는 `Product.increase()`, `Product.decrease()`로만 바뀌고, "음수 불가" 규칙은 `decrease()`가 직접 검사합니다.
- **도메인은 HTTP를 모릅니다.** 도메인 예외를 HTTP 상태 코드로 바꾸는 일은 `api` 레이어의 `GlobalExceptionHandler`만 합니다.

### 동시성 제어

**전략: 비관적 락 (`SELECT ... FOR UPDATE`)**

입고와 출고 모두 상품 행에 쓰기 락을 잡은 뒤 재고를 바꿉니다. 같은 상품에 대한 요청은 DB에서 한 줄로 세워져 순서대로 처리되므로, 갱신 손실이나 음수 재고가 생기지 않습니다.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
Optional<Product> findForUpdateById(Long id);
```

**신규 상품 동시 등록**

미등록 상품에 입고가 동시에 들어오면 두 요청이 모두 "상품 없음"으로 판단할 수 있습니다. 이때는 `UNIQUE (name)` 제약이 두 번째 등록을 거절합니다. 그 결과 상품은 하나만 생기고, 나머지 요청은 수량이 반영되지 않은 채 `409`로 실패하므로 클라이언트가 재시도하면 됩니다. 이 충돌은 상품이 **처음 등록되는 순간에만** 생기고, 이미 등록된 상품의 입출고는 락으로 모두 정상 처리됩니다.

**보완 장치**

- **`lock_timeout = 3s`** (Hikari `connection-init-sql`): 락 대기가 길어져 커넥션 풀이 고갈되는 것을 막습니다. 초과하면 `409`를 반환합니다.
- **DB `CHECK` 제약**: 도메인 검증을 우회하는 경로가 생겨도 음수 재고는 저장되지 않습니다.

**검토했지만 선택하지 않은 대안**

| 대안 | 선택하지 않은 이유 |
|---|---|
| 낙관적 락 (`@Version`) | 재고는 같은 상품에 요청이 몰리는 행이라 충돌이 잦고, 충돌할 때마다 재시도해야 해서 로직이 복잡해짐 |
| 원자적 UPDATE / upsert (`ON CONFLICT`) | 가장 빠르지만 재고 규칙이 SQL로 흩어짐. 출고가 실패했을 때 404(상품 없음)와 409(재고 부족)를 구분하려면 조회를 한 번 더 해야 함 |
| 분산 락 (Redis) | DB가 하나인 환경에서는 과한 인프라이고, DB 락으로 충분함 |

### 설계 결정과 가정

- **상품명을 유일 키로 사용**: 과제의 기본 데이터 구조(`id`, `name`, `quantity`)에는 별도 식별자가 없습니다. 입고할 때 미등록 상품에는 아직 `id`가 없으므로, 미등록 여부를 판별하는 기준으로 상품명을 사용했습니다.
- **입고는 상품명, 출고와 조회는 `id`로 식별**: 입고는 아직 `id`가 없는 상품을 다뤄야 하고, 출고와 조회는 이미 존재하는 상품에 대해서만 일어나기 때문입니다.
- **소수 수량 거부**: `quantity: 1.5`가 조용히 `1`로 잘리지 않도록 Jackson의 `accept-float-as-int`를 껐습니다.

## 테스트

동시성과 DB 제약 동작은 DB마다 다르기 때문에, 통합 테스트는 H2가 아닌 **실제 PostgreSQL 컨테이너**(Testcontainers)에서 실행합니다.

| 테스트 | 종류 | 검증 내용 |
|---|---|---|
| `ProductTest` | 단위 테스트 | 재고 증감, 전량 출고, 재고 부족 시 예외와 상태 유지 |
| `ProductServiceTest` | 통합 테스트 | 신규 등록 후 입고, 기존 상품 입고, 출고, 재고 부족 시 롤백, 존재하지 않는 상품 |
| `ProductConcurrencyTest` | 통합 테스트 | 동시 요청에서의 정합성 (아래) |

**동시성 테스트 시나리오**

- 재고 50개에 100건이 동시에 1개씩 출고 → 정확히 50건 성공, 50건은 재고 부족으로 실패, 최종 재고 0
- 서로 다른 수량을 동시에 출고 → 재고가 음수가 되지 않고, 성공한 출고량의 합만큼만 감소
- 입고와 출고가 섞인 100건 → 실패 없음, 갱신 손실 없이 최종 재고가 정확
- 미등록 상품에 50건 동시 입고 → 상품은 1개만 생성되고, 성공한 입고 수량만 반영

> `@Lock`을 일부러 제거하고 실행하면 동시성 테스트가 모두 실패합니다. 테스트가 우연히 통과하는 게 아니라 실제로 경쟁 조건을 잡아낸다는 뜻입니다.

## 개선 여지 (MVP 범위 외)

- **상품 코드(SKU) 도입**: 실무에서는 상품명 대신 SKU 같은 업무 식별자를 유일 키로 두고, 상품명은 중복을 허용하는 것이 적절합니다.
- **멱등성 키** (`Idempotency-Key` 헤더): 네트워크 재시도로 인한 중복 입출고를 막습니다.
- **재고 변동 이력**: 입출고 내역을 별도 테이블에 기록해 추적할 수 있게 합니다.
- **트래픽이 큰 단일 상품**: 타임딜처럼 요청이 극단적으로 몰리면 원자적 UPDATE나 Redis 기반 차감으로 전환을 검토합니다.
