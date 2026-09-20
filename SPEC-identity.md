# Spec: identity — Auth, Users & Roles (Reader's Circle)

## ASSUMPTIONS I'M MAKING
1. Greenfield monorepo at `C:\Users\shara\Projects\readers-circle\` with `backend/` (Java) + `frontend/` (React). No existing code.
2. Backend: Java 21, Spring Boot 3.3.x, Maven, Spring Security, Spring Data JPA, PostgreSQL 16, UUID PKs.
3. Password hashing: BCrypt (strength 10). JWT HS256 from env secret; access 15 min, refresh 7 days, rotating refresh with reuse detection.
4. Public `POST /api/auth/register` creates READER only. ORGANIZER/ADMIN are promoted by Admin via `PATCH /api/users/{id}`. First bootstrap admin via env seed.
5. Frontend: React 18 + Vite 5 + React Router 6 + Axios. Access token in memory, refresh via httpOnly cookie (or secure storage fallback). Guarded routes by role.
6. Correct me now or spec proceeds with these.

## Objective
Provide the identity foundation all other modules depend on. Users: Readers (discover/join), Organizers (run circles), Admin (platform oversight).

User stories:
- As a Reader I can register, login, refresh my session, so I can access circles/events/chat.
- As an Admin I can list/get/update-role/deactivate users, and create/promote Organizers.
- As the platform I enforce role-based authorization (ADMIN/ORGANIZER/READER) on every API via JWT, so circles/events/chat/gallery can trust `SecurityContext`.

Out of scope (other modules): circle/membership logic, events, chat, gallery, Redis scaling, object storage, Docker/CI-CD (Phase 3 per PRD §10).

## Tech Stack
- Backend: Java 21, Spring Boot 3.3.x, Spring Security 6, Spring Data JPA (Hibernate 6), PostgreSQL 16, JJWT 0.12.x, BCrypt, Maven 3.9+, JUnit 5 + Mockito + Testcontainers.
- Frontend: React 18, Vite 5, React Router 6, Axios 1.x, Vitest + Testing Library.
- Auth: JWT HS256 (access) + opaque/ JWT refresh, Spring Security filter chain.

## Commands
```bash
# Backend (from backend/)
mvn spring-boot:run -Dspring-boot.run.profiles=dev
mvn test
mvn verify --fail-at-end
mvn spring-boot:build-image -DskipTests  # only when asked

# Frontend (from frontend/)
npm run dev -- --host --port 5173
npm run build
npm run preview -- --port 4173
npm test -- --run
npm run lint -- --max-warnings 0

# Infra (local dev)
docker compose up -d postgres
psql "postgresql://localhost:5432/readers_circle" -c "select 1"
```

## Project Structure
```
readers-circle/
  CAPABILITY-MAP.md      → approved module index
  SPEC-identity.md       → this spec
  backend/               → Spring Boot app
    src/main/java/com/readerscircle/identity/ → controller/, service/, repo/, security/, dto/
    src/main/resources/application.yml + db/migration/ → Flyway V1__identity.sql
    src/test/java/...    → unit + slice + IT tests
  frontend/              → Vite React app
    src/pages/           → LoginPage, RegisterPage
    src/lib/             → apiClient (axios + refresh), authContext, guards
    src/__tests__/       → auth flow tests
  tasks/                 → plan.md / todo.md (Phase 2+ only)
```

## Code Style
Java: Google-Java-Format via fmt-maven-plugin, constructor injection, records for DTOs, no field injection.
```java
public record RegisterRequest(
    @NotBlank @Size(max = 100) String name,
    @Email @NotBlank String email,
    @Size(min = 8, max = 72) String password) {}

@PostMapping("/api/auth/register")
@ResponseStatus(HttpStatus.CREATED)
public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
  return authService.registerReader(req);
}
```
TS: ESLint + Prettier, functional components, `useAuth()` hook, axios interceptor for 401→refresh→retry once.
```tsx
// frontend/src/lib/apiClient.ts
api.interceptors.response.use(r => r, async err => {
  if (err.response?.status === 401 && !err.config._retry) {
    err.config._retry = true;
    await refreshSession();
    return api(err.config);
  }
  throw err;
});
```
Naming: tables `users`, columns `snake_case`; endpoints kebab/plural REST; roles `ADMIN, ORGANIZER, READER`.

## Testing Strategy
- Backend: JUnit5 + Mockito for `AuthService` (duplicate email, BCrypt match, role enforcement); MockMvc slice for controllers (401/403 matrix); Testcontainers PostgreSQL IT for unique `(email)` + refresh rotation + concurrent register race. Target ≥80% on identity package.
- Frontend: Vitest + Testing Library for Login/Register forms, authContext, route guards, refresh-retry interceptor.
- Locations: `backend/src/test/...`, `frontend/src/__tests__/`. Run `mvn test` + `npm test -- --run` before every commit.

## Boundaries
- Always: validate inputs (`@Valid`, email normalize lowercase/trim); BCrypt-hash, never log passwords/tokens; run tests before commit; Flyway migration for every schema change; return 401 vs 403 correctly.
- Ask first: changing `users` table shape, JWT lifetimes/secret handling, adding deps (e.g. Redis, OAuth), CI config, altering error response envelope.
- Never: commit secrets/`.env`, store plain passwords, allow self-promotion to ORGANIZER/ADMIN via public API, delete/modify failing tests without approval, hand-roll crypto.

## Success Criteria
- [ ] `POST /api/auth/register` creates READER with 201, duplicate email → 409; password stored as BCrypt, never returned.
- [ ] `POST /api/auth/login` with valid creds → 200 + access JWT (15m, claims `sub`, `role`) + rotating refresh; invalid → 401 without user enumeration timing leak.
- [ ] `POST /api/auth/refresh` with valid refresh → new pair, old reuse → 401 + invalidate chain.
- [ ] Role matrix enforced: `GET /api/users` ADMIN-only (READER/ORGANIZER → 403, no token → 401); `PATCH /api/users/{id}` role change ADMIN-only; organizer promotion works end-to-end.
- [ ] Frontend can register → login → persist session across reload → access guarded Reader route → logout clears session; 401 triggers single refresh-retry.
- [ ] `mvn test` + `npm test -- --run` green; coverage gate met for identity package.
- [ ] Contract for downstream modules published (see below) and consumed via `SecurityContext`/axios.

## Provided Interface (for circles/events/chat/gallery)
- JWT access claims: `{ sub: userId(UUID), email, role: ADMIN|ORGANIZER|READER, iat, exp }`.
- Backend: `SecurityContext.getPrincipal() → AuthUser(userId, role)`; `@PreAuthorize("hasRole('ADMIN')")` etc.; `GET /api/users/me` (authenticated self-profile for frontend + downstream debugging).
- Frontend: `useAuth() → { user, role, login(), logout(), isAdmin }`; `apiClient` attaches `Authorization: Bearer <access>`.
- Error envelope: `{ code, message, details? }` with 400/401/403/404/409.

## Open Questions
1. Bootstrap first ADMIN: env seed (`ADMIN_EMAIL`/`ADMIN_PASSWORD`) — approved? (Proposed yes.)
2. Refresh transport: httpOnly cookie vs JSON body — proposed httpOnly cookie with CSRF SameSite=Lax; confirm?
3. `DELETE /api/users/{id}` = hard delete or `deactivated` flag? Proposed soft-deactivate (keep FK history), 401 on login after.
4. Rejected/deactivated login message generic to avoid enumeration — OK?
5. Multi-circle membership (PRD Q1) does not affect identity schema — defer to SPEC-circles.md? (Proposed yes.)

## Verification (Phase 1 gate)
- [ ] Covers all six core areas + Tech Stack + Success Criteria + Boundaries
- [ ] Human approved (reply Approve / request changes)
- [ ] Saved at `C:\Users\shara\Projects\readers-circle\SPEC-identity.md`
- [ ] Traces to `identity` in CAPABILITY-MAP.md
