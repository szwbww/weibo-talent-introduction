<!-- status: approved -->
<!-- schema_version: 3 -->
<!-- run_id: 20260623-002033-fix-src-test-kotlin-com-weibo-talentintroduction-mail-controller-mailboxcontroll -->
<!-- draft_sha256: 957279a9a97c6f0752fd8b1ee344dbb8c2e2b8d5045ff4021458d675abb5c523 -->
# Plan: Fix MailboxControllerTest Compilation

## Goal
Restore compilation of `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt` after `MailboxItemResponse` schema change and `MailboxService.listMailbox` signature change.

## Files Likely Modified
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt` (ONLY)

## Files Read (no modification)
- `src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/MailboxController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt` (incl. `MailboxItemResponse` data class)

## Steps

### 1. Inspect current signatures
- Read `MailboxItemResponse` definition. Confirm full param list and ordering (named-arg vs positional).
- Read `MailboxService.listMailbox(...)` final signature including `pending: Boolean` position and any default value.
- Read `MailboxController` to learn:
  - Endpoint path + query params (does it accept `pending`?).
  - How controller maps query → `listMailbox(pending = ...)`.

### 2. Update `MailboxItemResponse` constructions
For each constructor call in test:
- Add `tags = emptyList()` unless test asserts tag content.
- Add `processStatus = null`, `reasonType = null`, `inboundProcessingId = null` unless assertion targets them.
- Ensure `source: String` non-null (use existing value if test referenced a previous field; default `"INBOUND"` or matching enum string if needed by test intent).
- Ensure `expertContactId: Long?` passed as `Long?` (nullable).
- Prefer **named arguments** to avoid positional drift.

### 3. Update mock stubs & verifications
- Every `whenever(mailboxService.listMailbox(...))` and `verify(mailboxService).listMailbox(...)`:
  - Add `pending` argument (match controller default — likely `false` or `any()`).
- If existing matchers use `any()` for some params, add `any()` for `pending` too. Keep argument order matching service signature.

### 4. Update MockMvc requests
- If controller exposes `pending` query param: add `.param("pending", "false")` where the original test exercised default behavior; add explicit `true`/`false` cases only if test originally covered them.
- If controller does not expose `pending` (server-derived): no MockMvc change.

### 5. Verify
```
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -Dtest=MailboxControllerTest
```
Iterate (max 3 rounds per fix-v rule). Each round: read compiler errors → minimal edit to test file → re-run.

## Test Plan
- Single-class run above must pass green.
- All original assertion intents preserved: filter param wiring, paging, status mapping, attachment exposure.
- No production-code changes; no new helpers/classes/interfaces introduced in test (fix-v constraint).

## Risks
- **Positional arg drift**: if test previously used positional construction of `MailboxItemResponse`, adding fields shifts meaning. Mitigation: switch entire test to named arguments.
- **Hidden assertions on new fields**: a test may assert response JSON shape; new fields (`tags`, `processStatus`, etc.) appear in serialized output. If MockMvc uses `jsonPath` strict checks, may need to add expectations. Mitigation: scan all `jsonPath` and `andExpect(content().json(...))` usages; extend only where strict-match fails.
- **`pending` default semantics**: if controller treats absent param as `false`, mock verifications calling `listMailbox(..., false)` are correct. If absent → `null`/special, align with controller code.
- **fix-v 3-round cap**: if still failing after 3 rounds, stop and report root cause.

## Rollback
Single file change. Revert via:
```
git checkout -- src/test/kotlin/com/weibo/talentintroduction/mail/controller/MailboxControllerTest.kt
```

## Out of Scope
- No edits to controller, service, response DTO, or any production class.
- No unrelated test cleanup or refactoring.
