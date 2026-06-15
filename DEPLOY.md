# Deployment Guide / 部署指南

Inner Cosmos is a Spring Boot 3.3.6 / Java 17 app (MyBatis-Plus). It ships with
two databases by profile:

| Profile   | Database | Purpose                  | Schema init                    |
|-----------|----------|--------------------------|--------------------------------|
| `dev`     | H2 file  | Local development        | `schema.sql` + seed on demand  |
| `demo`    | H2 file  | No-API-key demo (mock)   | `schema.sql` + demo seed       |
| `mysql`   | MySQL 8  | Local MySQL dev          | additive SchemaM runners only  |
| `prod`    | MySQL 8  | Production (12-factor)   | additive SchemaM runners only  |

`dev` / `demo` / `mysql` profiles are unchanged — H2 remains the local default.

---

## 1. Local development (H2, mock LLM)

Requirements: Java 17+ (runs on 21), the bundled Maven wrapper.

```bash
./mvnw spring-boot:run                              # dev profile (H2)
./mvnw spring-boot:run -Dspring-boot.run.profiles=demo   # demo seed + mock LLM
```

Entry URL: http://localhost:8080/pages/index.html

Default seeded accounts (only when `SEED_ENABLED=true`, default in `demo`):
- admin / admin123  (ADMIN)
- demo / demo123    (USER)

## 2. Production via Docker Compose (recommended)

`docker-compose.yml` brings up the app + MySQL 8 (+ optional Prometheus/Grafana)
and runs the app with `SPRING_PROFILES_ACTIVE=prod`.

```bash
# Minimum viable local-prod (uses the safe defaults already in compose):
docker compose up -d --build

# Real deployment — override the secrets/env:
DB_PASSWORD=$(openssl rand -base64 24) \
MYSQL_ROOT_PASSWORD=$(openssl rand -base64 24) \
LLM_API_KEY=sk-... \
CORS_ORIGINS=https://your-domain.com \
docker compose up -d --build
```

Health check (wait for `UP`):
```bash
curl http://localhost:8080/actuator/health
```

The MySQL schema is created automatically by Spring Boot's additive
`SchemaM{n}Initializer` runners (idempotent, `information_schema`-guarded) on
first boot — no manual `CREATE TABLE` needed. `schema.sql` is NOT run in prod,
so existing data is never dropped on restart.

## 3. Required / optional environment variables (prod)

The prod profile (`application-prod.yml`) reads standard Spring env vars with
compose-friendly defaults:

| Variable                       | Default (compose)                                  | Notes                                     |
|--------------------------------|----------------------------------------------------|-------------------------------------------|
| `SPRING_DATASOURCE_URL`        | `jdbc:mysql://mysql:3306/inner_cosmos?...`         | Full JDBC URL overrides everything        |
| `SPRING_DATASOURCE_USERNAME`   | `innercosmos`                                      | Falls back to `DB_USER`                   |
| `SPRING_DATASOURCE_PASSWORD`   | `innercosmos`                                      | Falls back to `DB_PASSWORD` — **set in real prod** |
| `DB_HOST` / `DB_PORT` / `DB_NAME` | `mysql` / `3306` / `inner_cosmos`               | Used only to build the default URL        |
| `LLM_API_KEY`                  | _(empty)_                                          | Required for a real LLM provider          |
| `LLM_PROVIDER`                 | `minimax`                                          | minimax / glm / deepseek / openai-compatible |
| `CORS_ALLOWED_ORIGINS`         | _(empty)_                                          | Your frontend origin(s), comma-separated |
| `SERVER_PORT`                  | `8080`                                             |                                           |
| `MYSQL_ROOT_PASSWORD`          | `rootpass-change-me`                               | MySQL container root; **change in prod**  |

## 4. Running the fat-jar directly (no Docker)

```bash
./mvnw -DskipTests package
SPRING_PROFILES_ACTIVE=prod \
SPRING_DATASOURCE_URL='jdbc:mysql://localhost:3306/inner_cosmos?useUnicode=true&characterEncoding=utf8&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Shanghai' \
SPRING_DATASOURCE_USERNAME=innercosmos \
SPRING_DATASOURCE_PASSWORD=... \
java -jar target/inner-cosmos-0.1.0.jar
```

## 5. MySQL vs H2 note

- **H2** (`dev`/`demo`): file DB at `./data/innercosmos`, `MODE=MySQL`. Schema is
  rebuilt/seeded from `schema.sql` per `SQL_INIT_MODE`. Never use in production.
- **MySQL** (`prod`): driver `com.mysql.cj.jdbc.Driver`, utf8mb4. The additive
  `SchemaM{n}Initializer` runners apply any column/constraint migrations not yet
  present and are idempotent, so upgrades are non-destructive.

## 6. Observability

Actuator endpoints (prod exposes `health,metrics,prometheus,info`):
- Health: `GET /actuator/health`
- Prometheus: `GET /actuator/prometheus`

Compose includes Prometheus (`:9090`, localhost-bound) and Grafana (`:3000`).
