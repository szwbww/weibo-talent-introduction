# Child 01 approved execution brief

- Child: 01
- Exact approved plan: docs/plans/2026-11-05/expert-material-filter-01-read.md
- Plan identity: sha256:96f08c602083fe57d1c20815a37589014499cdca6bc39553e4d77e960717278b
- Master plan: docs/plans/2026-11-05/expert-material-filter-master.md
- Master identity: sha256:344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd
- Master base: 71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master
- Branch: fast/expert-material-filter-master
- Epoch: 1
- Execution report: docs/plans/fast/expert-material-filter-master/children/01/execution.md
- Verification report: docs/plans/fast/expert-material-filter-master/children/01/verify-log.md

Read the exact child plan in full from this worktree. Its T1..T4, acceptance matrix, prescribed APIs, downstream contracts, manual A-n scope, and required commands are the complete approved child specification; the master supplies only its explicitly cross-referenced global invariants. Do not inspect later child plans.

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibility.kt`
- `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailboxConversationRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/document/service/ExpertDocumentBrowseService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibilityTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialArchiveServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/service/ExpertMaterialEligibilityReadIT.kt`

## Global approved constraints
- The user invocation `/fast-p docs/plans/2026-11-05/expert-material-filter-master.md` approves these exact plan bytes despite historical DRAFT labels. User further authorized all subsequent children until review-fast-p is executable, and explicitly prohibited executing review-fast-p.
- Controller owns orchestration and evidence commits. Exactly one writer uses this retained worktree and branch; no new worktrees, push, merge, rebase, amend, reset, squash, or deletion.
- Preserve the master I-1..I-6 and N-1..N-4: only ASCII-space-trimmed Locale.ROOT `.p7s` filename or normalized `application/pkcs7-signature`/`application/x-pkcs7-signature` MIME excludes a material. Preserve ordinary images, zero-byte ordinary files, `.p7m`, raw historic rows/files, ownership/security, ordering, permissions, transaction/UID/cursor semantics, DMARC/bounce/self-check/Drive routing, and existing human/operator states.
- No schema, dependencies, configuration, frontend, DTO, outgoing-attachment, historical state rewrite, generic image classification, or existing-queue cancellation changes.
- Approved file lists are the only product/test authorization. Do not amend plans or silently widen scope. Missing mandatory evidence, unlisted required edits, or a new behavior decision -> report BLOCKED or PLAN_CONFLICT.
- The original worktree is dirty and must not be changed. At preflight all 65 source-manifest paths match their approved SHA-256; 64 are identical at the master base. The sole baseline-absent path is ignored offline PDF-only `tmp/lucio-dorneles/register_materials.sh`; preserve its recorded audit, do not copy or execute it. Unrelated original application.yml/knowledge/docs edits are not imported.
- Use JDK 11 at `/Users/lukai/Library/Java/JavaVirtualMachines/zulu-11.0.32.jdk/Contents/Home`. Maven 3.9.11 and Docker Engine 29.8.2 are available (orbstack arm64); real MySQL tests must cover 5.7.44 and 8.0.36, use isolated Testcontainers, and actually execute, not skip. Use linux/amd64 platform for unsupported native image architectures if needed; never use the default development/production database.
- Respect repository instructions and existing conventions. Read docs/design.md before any mail/conversation product change. Use specialized read/edit/grep/LSP tools as applicable.
- Skip build/lint/tests/formatters mid-flight; run required final commands once after all product edits. Obtain mandated failing-before evidence before product edits when adding regression coverage, then run the final checks after implementation. Include a throwaway runtime smoke exercising the changed surface (not only test assertions), record actual output, remove scaffolding. No whole-system or full-suite review is authorized here.
- Permanent docs/changelog evidence is the execution report plus the fast-p tracked child artifacts requested by this workflow; do not create or modify an unlisted product document.
- Execution and verification are distinct. Implementers do not declare PASS; verifiers use only the four-gate light rubric, never review-p/verify-p/repair-p/final review. Reviewer product/index/branch/commit access is read-only.

## Dependency and downstream contract
- No predecessor. Seeded product boundary is the master base above. Provide stateless `ExpertMaterialEligibility.isMaterial(fileName: String, contentType: String?): Boolean`, compile-time fixed-alias `MATERIAL_SQL`, and fixed error message; later children consume these without editing child 01 files. Preserve raw source selection in resolveMessageAttachments and protect scoped/unscoped files and all-batch transfer requests. Required commands are the exact two commands in child 01 lines 178-179; any extra pure class in the new IT file must be explicitly executed as required by that plan.

## Baseline evidence
Baseline command outputs and exact counts are recorded in ledger.md under Baseline before dispatch. Required new Eligibility/SQL IT classes do not exist at the approved master base, so no pre-change pass is claimed for them.

## Epoch 2 resume directive
- Epoch 1 completed no product work; preserve its historical BLOCKED report and pause evidence b4d10ff50863cba54211d3e064b441df29d6aa05.
- Human selected “清理失效登记并继续” on 2026-11-05, specifically authorizing only the stale publish registration cleanup and continuous child execution without review-fast-p.
- Controller repeated prune dry-run (exactly one stale registration), ran `git worktree prune --verbose`, then the unmodified mandatory execute-p identity helper with expected root/branch/git-dir; all exited 0. No extant worktree directory, branch, or commit was deleted.
- Resume epoch: 2; fix_round=0; exact plan SHA-256 and product base unchanged. All T1–T4 are still pending. Do not replay baseline tests as implementation evidence.
- A fresh implementer must execute the exact approved child, append its epoch 2 report, and make the authorized local implementation commit. Prior helper failure is resolved, not a plan amendment.

