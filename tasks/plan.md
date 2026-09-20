# Plan: identity module (Reader's Circle)

Source: SPEC-identity.md + CAPABILITY-MAP.md (`identity` → no deps, provider for circles/events/chat/gallery)

## 1. Components & Dependencies
- **A. Scaffolding**: `backend/pom.xml` (Boot 3.3, Security, JPA, Validation, Flyway, JJWT, Testcontainers), `frontend/package.json` (Vite5/React18/Router6/Axios/Vitest), `docker-compose.yml` (postgres:16), `application.yml` (dev/test).
  Depends on: spec approval. Blocks everything.
- **B. Persistence**: Flyway `V1__identity.sql` (users UUID PK, citext email unique, password_hash, role enum, created_at, deactivated flag), `User` entity + `UserRepository`.
  Depends on: A.
- **C. Domain Auth**: `JwtService` (HS256, 15m/7d), `AuthService.registerReader/login/refresh+rotation+reuse-detect`, `BootstrapAdmin` via env, BCrypt(10).
  Depends on: B.
- **D. Security chain**: `JwtAuthFilter`, `SecurityConfig` (stateless, `/api/auth/*` public, `/api/users/**` ADMIN-only, `/api/users/me` authenticated), 401/403 JSON envelope `{code,message}`, `AuthUser` principal.
  Depends on: C.
- **E. REST API**: `AuthController` (register/login/refresh/me), `UserAdminController` (list/get/patch-role/deactivate), `@Valid` DTOs, global exception → 409 email, 400 validation.
  Depends on: D.
- **F. Frontend auth**: `apiClient` (attach + 401→refresh→retry once), `authContext` + `useAuth`, `LoginPage/RegisterPage`, `RequireAuth/RequireRole` guards, `/me` hydration.
  Depends on: E contract (can scaffold UI in parallel, integrate after E).
- **G. Verification**: unit + MockMvc matrix + Testcontainers IT + Vitest; coverage ≥80% identity package.

## 2. Implementation Order
A → B → C → D → E → F → G (F scaffold may parallel C–E, integration after E).

## 3. Risks & Mitigations
- Secret leak / weak JWT → secret from env only, fail-fast if missing in prod; never commit `.env`; HS256 key ≥256-bit.
- Refresh reuse race → transactional token version/jti table or hashed store; reuse → invalidate chain + 401.
- Duplicate email race → DB unique constraint + catch → 409; normalize lowercase/trim.
- XSS token theft → access in memory only, refresh in httpOnly SameSite=Lax cookie; no token in localStorage.
- Testcontainers needs Docker → fallback to `mvn test -DskipITs` documented, but CI requires Docker; local `docker compose up -d postgres` for dev.
- Scope creep into circles → hard gate: no circle/membership tables in V1 migration.

## 4. Parallel vs Sequential
- Sequential: A→B→C→D→E (security chain meaningless without domain).
- Parallel: F-UI-scaffold alongside C–D; F-integration + G after E.
- No parallel DB migrations.

## 5. Verification Checkpoints
1. After B: `docker compose up -d postgres; mvn flyway:migrate` applies V1 clean.
2. After C: `mvn -Dtest=AuthServiceTest test` green (hash, duplicate, rotation).
3. After D+E: `mvn test` MockMvc 401/403 matrix green; manual `curl register→login→me→refresh→users(403 as reader, 200 as admin)`.
4. After F: `npm run dev` register→login→reload persists→logout clears; `npm test -- --run` green.
5. Final: `mvn verify` + `npm run build` + coverage gate; contract (JWT claims, AuthUser, /me, error envelope) demo for circles consumer.
