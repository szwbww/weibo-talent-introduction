# Contact Profile Fast-P Aggregate Repair

## Status

`DRAFT — human approval required before execution.`

## Review Basis

- Master: `docs/plans/2026-10-11/contact-profile-development-master.md`
- Master SHA-256: `3428e4e7f4c21c2cb5148e745884b988117719a84f478a52beee3a69d04fa204`
- Approved master commit: `91a79dcda9d4ddf55525c801f9c9b9b454321f63`
- Reviewed implementation boundary: `96b89bb203019881a7fc36be557d30ad2a9c43a3..0e4b5a89507e03350cddb883f7266c494317e25f`
- Evidence HEAD: `df4942cab74b6ef1c5a3c15f79e08e3dc825043e`
- Aggregate review result: `FAIL / INITIAL`; repair is eligible under `repair-p`.

## Confirmed Findings

### F-1 — P1: cleanup error can permanently consume an upload slot

`ExpertMeetingVideoService.upload` deletes the temporary path and only then releases the two-slot semaphore. `Files.deleteIfExists(tempPath)` may throw `IOException`; that skips `uploadSlots.release()`. Repeated cleanup failures can exhaust both permits and make later uploads return 429 forever. This violates 04 I-6/I-9: every handling branch must release the slot.

### F-2 — P1: known same-name upload streams before its required precheck

04 requires identity, capacity, and same-name prechecks before request-body reading, with the database unique key still the final race arbiter. The implementation checks a duplicate only after `writeStreamedFile` has consumed and moved the body. A known conflict can therefore consume a full 1 GiB transfer and an upload slot before its deterministic 409. This reclassifies the prior RECORD_ONLY 04 O-1 as a mandatory resource-contract failure.

### F-3 — P1: no progress event is treated as proof that a sent upload never submitted

The video UI calls `xhr.send(entry.file)`, then treats abort/network/timeout as unsubmitted whenever no upload progress callback has advanced `uploadedBytes`. Progress delivery is not a transmission acknowledgement: a request can have reached the server before the first callback. The UI then exposes retry/cancelled rather than `uncertain`, allowing a duplicate upload contrary to 05 I-3/I-4. Existing tests encode this incorrect 0-progress case.

### F-4 — P2: manual-profile native maxlength is UTF-16, not the required Unicode code-point limit

The manual-profile editor emits HTML `maxlength` for every contract field, while its own validator and 01 contract count Unicode code points. Browsers enforce `maxlength` in UTF-16 code units, so a valid 64-code-point astral-character value can be blocked before the intended validator runs. This violates the shared 01/02 field contract.

## Authorized Scope

Only these files may change during this repair:

1. `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoService.kt`
2. `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMeetingVideoServiceTest.kt`
3. `src/main/resources/static/app.js`
4. `src/test/js/expertMeetingVideo.test.js`
5. `src/test/js/expertManualProfile.test.js`

No migration, schema, controller, CSS, index, plan, fast-p ledger, deployment document, or unrelated test change is authorized.

## Repair Steps

### R-1 — restore backend pre-stream and release guarantees

In `ExpertMeetingVideoService`:

1. After the existing identity/name/size/capacity validation and before acquiring/reading the upload body, check the canonical expert key plus normalized display filename. If it already exists, return the existing fixed `VIDEO_NAME_CONFLICT`/409 behavior without reading the input stream.
2. Keep the unique database constraint and duplicate-key mapping as the final race arbiter.
3. Structure cleanup so an `IOException` while deleting a temporary/final artifact cannot prevent `uploadSlots.release()` on any path. Preserve the existing typed outcome where possible; do not turn cleanup failure into a success.
4. Add service tests with a read-forbidden input stream for the known duplicate path, and a deterministic cleanup-failure seam proving subsequent uploads can still obtain all two permits. Keep the 64 KiB streaming and 1 GiB boundary unchanged.

### R-2 — treat every interrupted sent XHR as uncertain

In `app.js` and `expertMeetingVideo.test.js`:

1. Once `xhr.send(entry.file)` has been invoked for an active entry, treat abort, network error, and timeout as `uncertain` unless an explicit definitive HTTP response establishes a non-submission business outcome.
2. Do not infer “not started” from absence of a progress event. Keep pending-item removal before `send` as the only safely cancelled path.
3. Pause the queue, refresh the list, preserve the existing fixed uncertain text, and require explicit user action after the refresh; never automatically retransmit.
4. Replace the current zero-progress assumptions with regression cases for abort/error/timeout after `send` but before progress, asserting `uncertain`, no automatic retry, and list refresh.

### R-3 — leave code-point enforcement to the existing validator

In `app.js` and `expertManualProfile.test.js`:

1. Remove native HTML `maxlength` from manual-profile text controls; retain the current `expertManualProfileCodePointLength` validation and server enforcement.
2. Update the structural assertion to verify the field-limit metadata is still used by the code-point validator, not represented by native UTF-16 `maxlength`.
3. Add an astral-character boundary test: a value at the contract code-point limit is accepted by the editor/validator, while one code point over shows the existing validation error and does not PATCH.

## Required Verification

Run from `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master` with Java 11:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DvideoLargeIt=true -Dtest=ExpertMeetingVideoServiceTest,ExpertMeetingVideoControllerTest,ExpertMeetingVideoStreamingIntegrationTest test
node --test src/test/js/expertManualProfile.test.js
node --test src/test/js/expertMeetingVideo.test.js
node --test src/test/js/expertContactSharedNote.test.js src/test/js/expertManualProfile.test.js
node --test src/test/js/*.test.js
node --check src/main/resources/static/app.js
DOCKER_HOST=unix:///Users/lukai/.orbstack/run/docker.sock JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dapi.version=1.41 '-DargLine=-Dapi.version=1.41' -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ExpertProfileIdentityResolverTest,ExpertManualProfileServiceTest,ExpertManualProfileControllerTest test
```

Before a new aggregate PASS, rerun the complete master acceptance/must-not-change matrix and complete the still-pending human acceptance checklist.

## Review-Fast-P Execution Handoff

- Repair artifact: `docs/plans/fix/contact-profile-development-master/repair.md`.
- Authorized files: exactly the five files in **Authorized Scope**; no other product, test, schema, plan, ledger, review-evidence, or deployment file is authorized.
- Product commit subject: `fix(fast-p): repair contact profile upload lifecycle`.
- Docs-only evidence commit subject: `docs(fast-p): record contact profile repair verification`.
- Execute only after human approval with `execute-p` against `docs/plans/fix/contact-profile-development-master/repair.md`.
- The product commit may contain exactly the five authorized product/test files and no other file.
- After every command in **Required Verification** succeeds, append exact exit codes/counters to `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-10-11-contact-profile-development-master/docs/plans/review/2026-10-11-contact-profile-development-master/repair-execution.md`.
- The docs-only evidence commit may contain only that committed `repair-execution.md` and must use `docs(fast-p): record contact profile repair verification`.
- Return with the committed `repair-execution.md` to `$review-fast-p docs/plans/fast/2026-10-11-contact-profile-development-master/human-review-handoff.md` for aggregate review; it must independently re-review this repair and must not treat this draft as implementation approval.
- This handoff authorizes no extra files, plan amendment, push, or merge.

## Human Gate

Approve this bounded repair plan before any product/test implementation. A different scope, changed contract, or additional finding requires a new review decision.
