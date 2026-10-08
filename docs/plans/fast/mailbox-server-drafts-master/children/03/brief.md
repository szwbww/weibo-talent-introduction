# Child 03 Approved Execution Brief

- Exact plan: docs/plans/2026-10-08/mailbox-server-drafts-03-ui.md
- Plan identity: commit:352a3393c31fd72a582ccbb7946f83703da52a95
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-mailbox-server-drafts-master
- Branch: fast/mailbox-server-drafts-master
- Approval: Human “批准 并 实施”, 2026-10-08; applies to the unchanged exact plan bytes, authorizes implementation despite historical pending-review/planning-only descriptions.
- Dependencies: 01,02
- Product base: 05bb797a51dd94b8aa712bc8f36e2426e3e801a4

Read the complete exact child plan and master docs/plans/2026-10-08/mailbox-server-drafts-master.md. The child plan's change-file table is the exhaustive product/test authorization; its requirements, invariants, commands and downstream interfaces are binding. No additional product files, plan amendments or behavioral redesign are authorized. Preserve original migrations. No push/merge/history rewriting. Use JDK11 explicitly. MySQL/Docker integration tests must execute, not skip. Do not run build/tests/formatters mid-flight; run required commands once after the final implementation state, following the task runtime rule. Collect actual runtime smoke proof, not only test output; temporary smoke resources must be removed. Do not widen file scope for docs: this report is the authorized documentation of this child.

Use execute-p. Controller owns fast-p evidence commits. Commit only authorized product/test files as `feat(fast-p): implement 03`. Write execution evidence only to `docs/plans/fast/mailbox-server-drafts-master/children/03/execution.md`; exclude all fast-p artifacts from product commit. Stop with BLOCKED or PLAN_CONFLICT if a prerequisite, scope expansion or amendment is required. Do not review later children. Independent verification is the fast-p four-gate verifier, not verify-p/review-p/fix-v.

## Prior Child Handoff
- Child01 and child02 independently LIGHT_PASS; both dirty-metadata and clean checkpoint validators passed, no repairs/findings.
- Read prior execution.md/verify-log.md and actual Models/Service/Controller endpoints for precise snapshots, API response/error structure and sending projection. Do not infer codec fields or reconstruct old shared contracts.
- Child01 fresh evidence: 17 unit/controller, 8 real MySQL, 39 migration tests; raw success objects, code/message/currentVersion/currentState errors, typed context with response-only attachmentDownloads.
- Child02 fresh evidence: 100 unit and 27 real MySQL tests, 1528 Node tests in each invocation; actual HTTP/MySQL send smoke confirmed one SMTP, SENT/version2/null content, Session isolation and replay.
- Original existing send endpoints accept optional draftRef={id,version}; old requests/fingerprint unchanged. Only durable success can close exact content version. Newer ACTIVE versions survive; GET draft/attempt status is authority, never HTTP success alone.
- Backend and migration baseline now include V152. Docker needs environment-only DOCKER_API_VERSION=1.44 plus Maven -Dapi.version=1.44; JDK11 explicit. UI browser smoke must use actual server persistence, not static demo or mocked transport; retain evidence and remove temporary resources.

## Epoch 2 — Approved amendment and resume contract
- Current plan identity (supersedes epoch1 header): commit:2466ad4bdc14fe15d77578ba75103d384eebf6be.
- Current master identity: commit:2466ad4bdc14fe15d77578ba75103d384eebf6be.
- Human approval: “批准 继续” (2026-10-08), approving the five exact regression-test paths and 03's 7→12 file exception; ledger A1/A2.
- Resume the existing seven-file working implementation, not from scratch. Epoch1 PLAN_CONFLICT and failure receipts stay append-only in execution.md. New identity means a NEW execution epoch with fresh checklist/commands; epoch1 test passes are not proof of completion.
- Additional authorized files and bounded changes are exactly the amended plan's rows8–12. Delete obsolete incidental EOF/release-key assertions, never re-pin them; preserve original meaningful content/resource/safety contracts. Add authenticated typed durable CAS fixtures and awaited lifecycle assertions for material/meeting consumers. Do not add production fallbacks to compensate for obsolete mocks.
- All original I-1–I-8/S-1–S-4 invariants and required commands remain. Finish all reachable original implementation and required fresh JS/combined backend/MySQL gates and actual desktop/mobile real-server persistence browser smoke before implementation commit.
- Append full epoch2 report to execution.md; product commit subject remains feat(fast-p): implement 03; exclude evidence and amended plans from product commit. Controller owns plan/evidence commits.

## Epoch 3 — Current approved contract
- Current exact child/master plan identity (supersedes historical headers): commit:02af6d42cdf3617c48335a9ad3aeddb025d1302f.
- Human approval “批准” (2026-10-08) releases the epoch2 blocker, recorded as A3/A4. Exactly one additional authorized path: src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailReplyDraftRepositoryIT.kt; its HTTP fixture must use an actual Spring transaction proxy with existing datasource/transaction manager. Preserve every HTTP/MySQL/CAS assertion, no production/schema/pom changes.
- Resume all twelve retained paths, reconcile against current thirteen-file plan; new identity means NEW epoch3 checklist and fresh required commands. Do not rerun the prior failed command just to confirm its reported failure; correct the now-authorized fixture first.
- Epoch2 full JS1562 PASS and all backend gates except that repository IT passed; these are history only, not new completion evidence. Fresh final required commands plus actual desktop/mobile server-persistence browser smoke remain mandatory.
- Append epoch3 execution result, never overwrite epoch1/2. Product commit feat(fast-p): implement 03 only when complete; exclude plans/evidence. No independent verification or whole-system review in writer.
