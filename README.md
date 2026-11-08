# ClaimWillow

Insurance claims workflow and test automation platform for a fictional home-contents insurer.

> **All data is synthetic.** ClaimWillow is a portfolio application, not a licensed insurance
> platform or a production claims decision system.

## Stack

Java 21, Spring Boot 4.1, Spring Security, Spring Data JPA, PostgreSQL 16, Flyway, React 19,
TypeScript, Vite, Tailwind CSS, TanStack Query, JUnit 5, Testcontainers, Vitest.

## Local development

Prerequisites: Java 21, Node 24, Docker.

```powershell
docker compose -p claimwillow up -d db          # PostgreSQL on localhost:5434
cd backend; .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=local"
cd frontend; npm install; npm run dev           # http://localhost:5173 (proxies /api to :8080)
```

## Tests

```powershell
cd backend; .\mvnw.cmd verify                   # unit + Testcontainers integration tests
cd frontend; npm run lint; npm test -- --run; npm run build
```
