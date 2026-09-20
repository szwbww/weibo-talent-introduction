# Fast-P Child Brief — backend

Complete approved contract for child `backend` of the fast-p run on master plan
`docs/plans/2026-09-20/00-manual-expert-material-upload-main.md`.

- Child ID: `backend`
- Child plan (exact, approved, do not edit): `docs/plans/2026-09-20/manual-expert-material-upload-backend.md`
- Master plan (exact, approved, do not edit): `docs/plans/2026-09-20/00-manual-expert-material-upload-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main`
- Branch: `fast/manual-expert-material-upload-main`
- `child_base_sha`: `d2a7f65ecbc46b5165863dfcab94ae5972f50605`
- Depends on: none (first child)
- Epoch: 1
- Implementation commit message (exact): `feat(fast-p): implement backend`
- Execution report path: `docs/plans/fast/manual-expert-material-upload-main/children/backend/execution.md`

You MUST read the child plan in full before writing code; it is the authoritative source for the
implementation steps, the current-state audit, and every acceptance criterion. This brief only adds the
run-level contract and the frozen cross-plan interface.

## Authorized files (exactly these 10 — nothing else may be modified or added)

1. `src/main/resources/db/migration/V130__add_manual_expert_material_upload.sql`
2. `src/main/resources/application.yml`
3. `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt`
4. `src/main/kotlin/com/weibo/talentintroduction/document/domain/ManualExpertMaterialUpload.kt`
5. `src/main/kotlin/com/weibo/talentintroduction/document/repository/ManualExpertMaterialUploadRepository.kt`
6. `src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt`
7. `src/main/kotlin/com/weibo/talentintroduction/document/controller/ExpertMaterialController.kt`
8. `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt`
9. `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt`
10. `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt`

If the plan cannot be implemented without touching another file, a new field, a new state, or a new
subsystem: STOP and return `PLAN_CONFLICT`. Do not widen scope. Master plan Invariant I-6 makes this
file list a hard boundary; `mail_attachment` gains exactly one shared field (`manual_upload_id`).

## Global constraints (master plan)

- I-2 frozen HTTP contract: `POST /api/expert-contacts/{contactId}/materials/uploads`, single multipart
  field `file`, success HTTP 201. Errors: anonymous 401, missing file / bad request 400, unknown expert
  404, over-limit 413 with `code=PAYLOAD_TOO_LARGE`. Do not change path, part name, or status codes.
- I-3 frozen list contract: after a successful upload, `GET /api/expert-contacts/{contactId}/materials`
  must return the new item with `storageState=STORED`, `documentStatus=PENDING_REVIEW`, `canFetch=false`,
  the real `actualSize`, and source `type=MANUAL_UPLOAD`, `id=contactId`, `subject=手动上传`,
  `uploadedBy=<session username>`, `receivedAt=<upload time>`, `accountCode=null`.
- I-4 single capacity unit end to end: business limit is exactly `104857600` bytes, enforced by
  counting the bytes actually read; `MultipartFile.size` is not trusted. `application.yml` parser ceiling
  becomes `max-file-size: 100MB` / `max-request-size: 101MB` only as container pre-protection. The
  existing outbound/manual-reply attachment service keeps its own 10 MiB rejection.
- I-5 data ownership: manual material is a third owner class
  (`manual_upload_id → manual_expert_material_upload.expert_contact_id`); `mail_record_id` and
  `inbound_processing_id` stay null; no transfer row is created; material download still validates both
  `expert_document` and the manual owner. Manual upload must not become a mail message attachment and
  must not advance `MATERIALS_RECEIVED`.
- I-7 do not change the mail attachment register/on-demand/retry/state machine, the mailbox message
  attachment count, auto-reply attachment intent, the operator-status reconciliation rules, the existing
  material download/preview/AI ownership and `realPath.startsWith(realBasePath)` checks, or the
  `ExpertMaterials` single-store/selectionOnly/static-asset-cache-key contracts.
- The worktree baseline is the current working tree, which contains unrelated uncommitted work
  (SharePoint file-card WIP). Never revert, reformat, or "clean up" it.

## Child-plan invariants I-1..I-8

Implement exactly as written in the child plan's `## 关键不变量` section (three-way owner exclusivity,
file+DB atomicity with temp file and final-file cleanup, exact 100 MiB boundary with streamed counting,
stored-on-success semantics without a transfer row, explainable and filterable manual source, session
identity that cannot be forged, manual material not becoming a mail event, and V130 MySQL 5.7/8.0
compatibility using the `information_schema.TABLE_CONSTRAINTS + PREPARE` guard style of
`V129__add_material_request_codes.sql`).

Reuse the existing seams named by the child plan (`normalizeOutboundFileName`,
`normalizeOutboundContentType`, `MailAttachmentService.inferDocumentType`,
`OutboundAttachmentException.payloadTooLarge`, `ExpertMaterialService` resolver). Do not introduce a
second file-name/MIME/document-type rule set.

## Required commands (run all of them; report exit codes and counts)

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=ManualExpertMaterialUploadFlowTest,ExpertMaterialServiceTest,ExpertDocumentBrowseServiceTest,DocumentTextExtractorTest,OperatorStatusReconcileServiceTest,OutboundAttachmentServiceTest test
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmigrationIt=true -Dtest=FlywayMigrationIntegrationTest test
git diff --check
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

The migration integration test needs local Docker; if Docker is unavailable, record the exact command,
the exact error, and that the gate could not run — do not silently skip it and do not claim it passed.

Baseline for the full-suite gate is recorded below; a pre-existing failure that is present at baseline
and unrelated to this child must be reported as such, not fixed.

## Downstream interface frozen for the `frontend` child

The frontend child may only rely on:

- `POST /api/expert-contacts/{contactId}/materials/uploads` with one multipart part named `file`,
  called once per file, response 201 on success; error statuses 400/401/404/413 with the server message
  and, for 413, `code=PAYLOAD_TOO_LARGE`.
- `GET /api/expert-contacts/{contactId}/materials` returning manual items with the I-3 field values and
  the source triple `type=MANUAL_UPLOAD`, `id=<contactId>`, `subject=手动上传`, plus `uploadedBy` and
  `receivedAt`.
- No `storagePath`, absolute root path, or temp file name in any response body.

The execution report must include three frozen sample payloads for the frontend child: one 201 success
response, one 413 response, and one GET manual item (none of them containing `storagePath`).

## Commit and report discipline

- Modify only the authorized files. Stage explicitly (`git add <path>`); never `git add -A`/`-u`.
- Never stage or commit anything under `docs/plans/` (the controller owns all fast-p evidence).
- Commit the implementation locally as `feat(fast-p): implement backend`.
- Write the full result to the execution report path listed above (do not commit it).
- Do not push, merge, rebase, amend, squash, or rewrite history. Do not start frontend work.
- Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the commit SHA, a command summary,
  and the report path.

## Baseline (recorded by the controller at `d2a7f65`)

- `node --test src/test/js/*.test.js`: exit 1, 1023 tests / 1006 pass / 17 fail in 12 files, all from the
  pre-existing split static-asset cache key. Not this child's scope.
- `mvn test`: exit 1 after 03:06 min, but the failure is only the `node-test` `exec-maven-plugin` step.
  Baseline surefire aggregate: 3490 tests / 0 failures / 0 errors / 13 skipped (all Java tests green).
  So at baseline the full-suite command is red for the pre-existing JS cache-key failures above; treat the
  Java suite as the backend child's regression signal and report the JS red as pre-existing/unrelated.
