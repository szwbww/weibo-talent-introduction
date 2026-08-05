# Execution Report — trust-reply-configurable-workbench-02

- Result: `READY_FOR_VERIFICATION`
- Plan: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench/docs/plans/fast/trust-reply-configurable-workbench/children/02/brief.md`
- Plan SHA-256: `6d8893b3550f5b3ba5924d78a2b666eda00986f6ec59aa5a2d37d0003336778a`
- Execution ID: `…/children/02/brief.md@6d8893b3550f5b3ba5924d78a2b666eda00986f6ec59aa5a2d37d0003336778a`
- Execution epoch: NEW
- Executor: `ImplChild02`
- Target worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench`
- Target branch: `fast/trust-reply-configurable-workbench`
- Worktree ID: `/Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-configurable-workbench@fast/trust-reply-configurable-workbench@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/trust-reply-configurable-workbench`
- Pre-execution HEAD: `6296f2652b90e391f896da30e9f510c7f8f12057`
- Implementation commit: `c99c3aab8d1435b4fd1fc9ee5f808bb0bea38725` (`feat(fast-p): implement trust-reply-configurable-workbench-02`, 10 files +1445 −71, excludes `docs/plans/fast/**`)
- Plan identity rechecked after execution: YES (unchanged)
- Worktree identity rechecked after execution: YES (branch + git-dir unchanged)

## What changed

### Task 1 — selectable frame resolver (`ReplySnippetService.kt`)

- New DTOs: `ReplyFrameSelection` (four nullable snippet IDs), `ResolvedReplyFrame` (selection + deterministic version + authoritative per-slot text), `ReplyFrameOption`.
- `listSelectableFrameOptions()`: enabled + non-blank main snippets of the four frame slots only, fixed slot order (SALUTATION → GREETING → ACK → CLOSING) then displayOrder then id; CUSTOM and variants never enter.
- `resolveDefaultSelectableFrame()`: SALUTATION/GREETING/CLOSING current enabled defaults, ACK always null; missing enabled default ⇒ slot null (matches legacy `resolveManualFrame` shape).
- `resolveSelectableFrame(selection)`: per-ID fresh `findById` with strict expected type / enabled / non-blank content checks; null omits the slot; an all-null selection is the explicit "no frame" choice (I-2). Fails closed with `IllegalArgumentException` (workbench layer maps to 422).
- Deterministic frame version (I-3): fixed slot sequence, per slot id/NULL + type + enabled + updatedAt + content SHA-256; no observed time.
- `resolveManualFrame` / `resolveAck` signatures and semantics untouched (I-6).

### Task 2 — workbench frame domain/HTTP contract (`TrustReplyWorkbenchService.kt`, `TrustReplyWorkbenchController.kt`)

- New transport DTOs: `TrustReplyFrameSelection`, `TrustReplyFrameSnapshot(selection, version)`, `TrustReplyFrameOption`.
- `bootstrap` request optionally carries `frameSnapshot`; response returns `frameOptions` + server canonical `frameSnapshot`. Priority: caller explicit selection > recoverable saved selection > current default; saved frame stale ⇒ top-level frame = current default with `FRAME_STALE` saved state (locks still restored).
- `saveState` request/response and `assemble` request/response carry nullable `frameSnapshot`; all new domain fields have defaults so existing Kotlin construction points (incl. `AiTrainingController`) compile unchanged.
- Unified `resolveFrameSnapshot` / `resolveFrameForAssemble`: whole snapshot missing ⇒ current default; selection present ⇒ strict fresh resolve; expected version mismatch ⇒ 409 `TRUST_REPLY_FRAME_STALE`; invalid selection ⇒ 422 `TRUST_REPLY_FRAME_SELECTION_INVALID`.
- HTTP layer: `TrustReplyFrameSnapshotHttpRequest`/`TrustReplyFrameSelectionHttpRequest` on bootstrap/assemble/state PUT; existing `@ExceptionHandler` maps the two new error codes.

### Task 3 — explicit-frame locked composer (`AiReplyPointByPointComposer.kt`)

- New overload `composeLockedItems(orderedAnswers, resolvedFrame)`: fixed order SALUTATION → GREETING → ACK → orderedAnswers → CLOSING, blocks joined by a single blank line; blank frame blocks filtered; every non-OMIT locked answer verbatim, original order, exactly once (I-5).
- Old `composeLockedItems(orderedAnswers)` and all other default-frame consumers unchanged (I-6).
- `TrustReplyWorkbenchService.assemble` resolves the frame only after locked-item/claim/version validation, then calls the explicit overload; response returns the canonical frame snapshot; draftHash remains over server raw.

### Task 4 — durable state v3 (`TrustReplyWorkbenchStateStore.kt`)

- `SCHEMA_VERSION` → `trust-reply-workbench-state-v3`; `PREVIOUS_SCHEMA_VERSION` (v2) and `LEGACY_SCHEMA_VERSION` (v1) retained; `ACCEPTED_REQUEST_SCHEMA_VERSIONS = {v1, v2, v3}`.
- `encodePayload` writes only v3 (payload construction already uses the constant).
- `decodePayload` supports v1/v2/v3: v2 rows are presented as the current matrix schema with null `frameSnapshot` (I-2 default compat, keeps the pre-existing StateStoreTest contract that v2 decodes to `SCHEMA_VERSION`); unknown schema ⇒ null ⇒ INVALID on restore.
- Payload adds exactly one shared field: `frameSnapshot` (IDs + deterministic version, never resolved text) (I-7).
- Restore split (I-4): source/evidence/fact-mapping stale ⇒ `STALE` without locks; frame-only stale ⇒ `FRAME_STALE` with revalidated locks restored and top-level frame = current default; all valid ⇒ `RESTORED` with the saved frame. Frame-only staleness never touches evidenceSetVersion or locked versionId.

### Task 5 — tests

- `ReplySnippetServiceTest`: options fixed order / disabled / blank / CUSTOM exclusion; default frame incl. missing default slots; strict fail-closed on missing/disabled/type-mismatch/blank; all-null empty frame stability; version determinism and sensitivity to content/updatedAt/id/slot.
- `AiReplyPointByPointComposerTest`: SALUTATION→GREETING→ACK→answers→CLOSING order; blank block filtering; duplicate answers byte-verbatim; old overload regression untouched.
- `TrustReplyWorkbenchServiceTest`: bootstrap frameOptions + default/caller canonical snapshot; caller invalid 422 / stale 409; v3 restore with saved frame; frame-stale restore keeps locks + default frame; v1/v2 restore with default-frame compat; saveState persists canonical frame (doAnswer-captured payload); saveState invalid/stale fail closed. `Mockito.reset` added to `setUp` so mocks are isolated per test.
- `TrustReplyWorkbenchItemFlowTest`: frame switch changes raw/rendered/hash only, locked identity/evidence/requestKey identical; stale expected frame version fails before composer/preview; explicit all-null selection never falls back to defaults.
- `TrustReplyWorkbenchControllerTest`: bootstrap/assemble/state PUT frameSnapshot round trips; `TRUST_REPLY_FRAME_STALE` (409) and `TRUST_REPLY_FRAME_SELECTION_INVALID` (422) mapped to stable codes.

## Invariants evidence

- **I-1**: clients submit only IDs + expected version; every non-null ID is freshly re-read with strict type/enabled/content checks; invalid ⇒ 422 `TRUST_REPLY_FRAME_SELECTION_INVALID`. Covered by `ReplySnippetServiceTest.resolveSelectableFrame fails closed…`, `TrustReplyWorkbenchServiceTest.bootstrap rejects invalid caller frame selection…`, `saveState rejects invalid frame selection…`.
- **I-2**: missing snapshot ⇒ current defaults (`resolveDefaultSelectableFrame`, ACK null); explicit four-null selection ⇒ answers-only assembly with no default fallback. Covered by `TrustReplyWorkbenchItemFlowTest.assemble with all null frame selection never falls back to defaults`.
- **I-3**: version = fixed slot sequence of id/NULL, type, enabled, updatedAt, content hash; stable across calls, changes on any input change; expected-version mismatch ⇒ 409 `TRUST_REPLY_FRAME_STALE`. Covered by `ReplySnippetServiceTest.frame version is deterministic…` and `TrustReplyWorkbenchItemFlowTest.assemble fails closed on stale expected frame version…`.
- **I-4**: frame change invalidates only assembly; locked versionId/answerText/claims/evidenceSetVersion/requestKey unchanged; frame stale restores locks with `FRAME_STALE`. Covered by `TrustReplyWorkbenchItemFlowTest.frame switch changes assembly but never locked item identity` and `TrustReplyWorkbenchServiceTest.bootstrap frame stale restores locks…`.
- **I-5**: raw order strictly SALUTATION/GREETING/ACK/answers/CLOSING with single blank-line separators; duplicates preserved byte-verbatim; old overload regression intact. Covered by the three new `AiReplyPointByPointComposerTest` cases.
- **I-6**: `resolveManualFrame`/`resolveAck` untouched; `AiReplyDraftService` matched/FREE_FORM and composer default-frame methods unchanged (only `assemble` calls the explicit overload); old composer overload tests still pass unchanged.
- **I-7**: v3 payload adds only `frameSnapshot`; v1/v2/v3 decode coverage in `TrustReplyWorkbenchStateStoreTest` (pre-existing, passing) plus service-level v1/v2 default-compat restore tests; unknown schema INVALID (pre-existing test, passing).

## Commands (run freshly after final implementation state)

| Command | Exit code | Result |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home PATH=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home/bin:$PATH mvn test` | 0 | `Tests run: 2118, Failures: 0, Errors: 0, Skipped: 4` (baseline at ed944d1: 2092 run, 4 skipped); `BUILD SUCCESS` |
| `node --test src/test/js/*.test.js` | 0 | 413 pass, 0 fail, 0 skipped (baseline: 413 pass) |
| `git diff --check` | 0 | clean (no whitespace errors) |

## Changed files (implementation commit c99c3aa)

- `src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt` — selectable frame options, strict resolver, deterministic version
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposer.kt` — explicit-frame locked composer overload
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchService.kt` — frame DTOs, unified resolution, bootstrap/saveState/assemble integration, frame-stale restore
- `src/main/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchStateStore.kt` — v3 payload + v1/v2/v3 decode
- `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt` — frame HTTP DTOs and mappings
- `src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt` — options, strict types, version tests
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/AiReplyPointByPointComposerTest.kt` — frame order and locked-bytes fidelity
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt` — bootstrap/state/frame-stale tests
- `src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchItemFlowTest.kt` — assemble/reassemble final-chain tests
- `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt` — frame HTTP round trips and error codes

## Deviations

- None. The only design note: stored v2 payloads are presented by `decodePayload` as the current matrix schema (`SCHEMA_VERSION`) with a null `frameSnapshot`, which is required both by I-7's "v1/v2 缺失字段按 I-2 的兼容默认解析" and by the pre-existing, out-of-scope `TrustReplyWorkbenchStateStoreTest` contract that a v2 row decodes to `SCHEMA_VERSION`. Business semantics for v2 rows (matrix revalidation, default frame) are unchanged.
- Test-infrastructure note: `Mockito.reset(...)` added to `TrustReplyWorkbenchServiceTest.setUp` because mock fields persist across tests in that class and accumulated stubs made the new tests order-dependent. No assertion weakened.

## Freshness

- Plan identity rechecked: YES (SHA-256 unchanged)
- Worktree identity rechecked: YES (branch + git-dir unchanged)
- Reported commit reachable from target branch: YES (HEAD `c99c3aa` on `fast/trust-reply-configurable-workbench`)
- Required commands run this invocation: YES
- Historical evidence used only as baseline: YES

## Next action

- `READY_FOR_VERIFICATION` → run `verify-p` for child 02.
