# Child 01 — Verify Log

_Epoch 1 · attempt 1 · 2026-10-11T22:18:31+08:00 · reviewed 91a79dc..bd7053_

## Light Verification: LIGHT_PASS
Child: 01 docs/plans/2026-10-11/contact-profile-01-storage.md
Boundary: 91a79dcda9d4ddf55525c801f9c9b9b454321f63..bd7053979bea2cc123c58ffb070abf7092917e98
Verifier: Verify01

### Four Gates
| Gate | Result | Evidence |
|---|---|---|
| Authorized scope | PASS | `git show --name-only bd70539` = exactly the 9 brief-authorized files (main: V153 migration, ExpertManualProfile.kt, ExpertProfileIdentityResolver.kt, ExpertManualProfileService.kt, ExpertManualProfileController.kt; tests: 3 new + FlywayMigrationIntegrationTest.kt); the other range commits touch only `docs/plans/fast/**` bookkeeping; no expert_contact/ES/frontend edits, no applied-migration rewrite; `git status -uall` shows no untracked residue |
| Plan and invariants | PASS | I-1/I-2: `requireKnown` (ExpertProfileIdentityResolver.kt:30-52) trim+normalize, 1–64 code points, profile-row → existsByOrcidId → ES orcidId evidence, `_id` never guessed; single-writer grep = Service UPDATE(:305-355)/INSERT(:356) only, Resolver adds one `SELECT COUNT`(:88); I-3: GET no-row no-insert (Service.kt:48-51), CAS update → 409 / first insert defaults+version=1 (Service.kt:54-91, 156-179); I-4/I-5–I-23: field table (domain:60-120) matches plan §1 limits+groups, CRLF/code-point/trim (Service.kt:192-203), birthDate rules (:205-231), companies rules (:254-276); tests: ServiceTest 17 (lines 129/147/201/265/303/330/339/361/418/468/504/543/594/669/698/734/751), ResolverTest 11 (65/78/97/109/125/145/153/177/193/224/236), ControllerTest 9 (121/150/169/199/220/263/275/299/311), IT V153 DDL+preservation (FlywayMigrationIntegrationTest.kt:227), 19-field real-service round-trip (:344), concurrent insert/update exactly 1 success + 1×409 (:486); historical target `152` and V152 test name preserved (:229/:193) |
| Required commands | PASS | ① unit command → exit 0, Tests run 37 (11/17/9), F 0, E 0; same run also executed JS gate `node --test` tests 1568 / fail 0 (= baseline B2) ② env-fixed migrationIt command (DOCKER_HOST + `-Dapi.version=1.41` + argLine) → exit 0, Tests run 42, F 0, E 0 (= baseline B3r 39 + 3 new); implementer-reported 37/42 confirmed |
| Downstream interfaces | PASS | GET returns exactly `{expertId,version,identity,education,technology,matching,updatedBy,updatedAt}` with the 19 group keys asserted (ControllerTest:145-149); PATCH groups `identity/education/technology/matching` (domain:24-58) and body `{expertId,level,group,expectedVersion,values}`; empty companies → `[]` (domain:203-207); `requireKnown(expertId: String?, level: ExpertIndexLevel): String` (ExpertProfileIdentityResolver.kt:30) exposes distinct 400/404/503 exceptions (domain:236-252) per 04 plan:117; raw documentNumber in GET matches 02 plan:32 (mask owned by 02 display) |

### AUTO_FIX
- N/A

### RECORD_ONLY
- N/A

### Required Action
- COMPLETE_CHILD

