# PayFlow — Auth + Payment Workflow

React + Spring Boot 2.7 (Java 11) + PostgreSQL

---

## What changed from v1

| Area | Change |
|---|---|
| Java version | 17 → **11** |
| Spring Boot | 3.2 → **2.7.18** |
| jjwt | 0.12.x (Java 17 API) → **0.11.5** (Java 11 API) |
| `jakarta.*` imports | Replaced with **`javax.*`** throughout |
| Security DSL | Lambda DSL → **chained fluent DSL** (Spring Boot 2.7 style) |
| Payment workflow | **New** — full CRUD with fee calculation |

---

## Project Structure

```
payment-app-v2/
├── backend/
│   ├── pom.xml                              # Spring Boot 2.7 / Java 11
│   └── src/main/java/com/paymentapp/
│       ├── config/SecurityConfig.java
│       ├── controller/
│       │   ├── AuthController.java
│       │   └── PaymentController.java       ← NEW
│       ├── dto/
│       │   ├── Auth.java
│       │   └── PaymentDto.java              ← NEW
│       ├── entity/
│       │   ├── User.java / UserSession.java
│       │   ├── Account.java                 ← NEW
│       │   ├── Fee.java                     ← NEW
│       │   ├── Payee.java                   ← NEW
│       │   └── Payment.java                 ← NEW
│       ├── repository/
│       │   ├── AccountRepository.java       ← NEW
│       │   ├── FeeRepository.java           ← NEW
│       │   ├── PayeeRepository.java         ← NEW
│       │   └── PaymentRepository.java       ← NEW
│       ├── security/JwtService.java         (updated for jjwt 0.11.5)
│       └── service/
│           ├── AuthService.java
│           └── PaymentService.java          ← NEW
│
├── frontend/src/App.jsx                     (updated — 3-screen payment flow)
│
├── users_auth_schema.sql
├── register_schema.sql
└── payment_schema.sql                       ← NEW
```

---

## Database Setup

```bash
psql -U postgres -d paymentdb -f users_auth_schema.sql
psql -U postgres -d paymentdb -f register_schema.sql
psql -U postgres -d paymentdb -f payment_schema.sql
```

`payment_schema.sql` creates and seeds:
- `account` — 4 sample from-accounts
- `payee` — 8 sample payees (mobile, internet, credit cards, utilities)
- `fee` — 5 fee tiers as per spec
- `payment` — empty (populated via API)

---

## Backend — Run

```bash
# Verify Java 11
java -version   # must show openjdk 11 or similar

cd backend
mvn spring-boot:run
```

Starts on **http://localhost:8080**

---

## Frontend — Run

```bash
cd frontend
npm run dev
```

Starts on **http://localhost:5173**

---

## Payment API Endpoints

| Method | URL | Description |
|---|---|---|
| GET | `/api/payment` | List all payments |
| GET | `/api/{id}/payment` | Get one payment |
| POST | `/api/payment` | Create payment |
| PUT | `/api/payment` | Update payment |
| DELETE | `/api/{id}/payment` | Delete payment |
| GET | `/api/accounts` | Active from-accounts (dropdown) |
| GET | `/api/payees` | All payees (dropdown) |
| GET | `/api/payment/fee?amount=X` | Preview fee for amount |

All payment endpoints require `Authorization: Bearer <token>`.

---

## Fee Calculation (per document spec)

| Min Amount (₹) | Max Amount (₹) | Fee (₹) |
|---|---|---|
| 0 | 99 | 10 |
| 100 | 999 | 25 |
| 1,000 | 9,999 | 50 |
| 10,000 | 99,999 | 100 |
| 1,00,000+ | — | 500 |

---

## Payment Request Body (POST/PUT)

```json
{
  "accountId": 1,
  "payeeId": 2,
  "paymentAmount": 1500.00,
  "paymentDate": "25/06/2026",
  "memo": "June electricity bill"
}
```

**Validations:**
- `paymentDate` cannot be in the past
- `paymentAmount` must be > 0
- `memo` max 100 characters (optional)
- All other fields mandatory

---

## Code Coverage

Run with:
```bash
mvn test jacoco:report
```

Report at: `target/site/jacoco/index.html`

Target: 80%+ line coverage (enforced by JaCoCo build rule).
