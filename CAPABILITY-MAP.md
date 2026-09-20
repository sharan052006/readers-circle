# Capability Map: Reader's Circle

Source PRD: `C:\Users\shara\Downloads\readers-circle-prd.md` v1.0 Draft

| Module id | Responsibility | Depends on |
|---|---|---|
| identity | Auth (register/login/refresh JWT), users table, roles ADMIN/ORGANIZER/READER, Spring Security, frontend auth UI | — |
| circles | Circle CRUD + city filter, ACTIVE/INACTIVE, memberships PENDING/APPROVED/REJECTED, join-request flow | identity |
| events | Event CRUD DRAFT→PUBLISHED→COMPLETED/CANCELLED, registrations REGISTERED/CANCELLED with capacity/deadline atomic check | circles, identity |
| chat | Per-circle WebSocket/STOMP send/receive/history, member-only access, organizer moderation | circles, identity |
| gallery | Event-scoped PHOTO/VIDEO upload/view/delete, post-COMPLETED only | events, identity |

Build order: identity → circles → events, chat (parallel) → gallery

Rules:
- Module ids are kebab-case, stable, never renamed.
- Dependency arrows point one way; no cycles.
- Contracts live in the provider module's spec (e.g. membership-check in SPEC-circles.md, attendee-check in SPEC-events.md, auth principal/JWT in SPEC-identity.md).
- Each module recurses Specify → Plan → Tasks → Implement in dependency order.
- Specs saved alongside this map as SPEC-<module-id>.md.
