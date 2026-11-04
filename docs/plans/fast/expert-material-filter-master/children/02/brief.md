# Child 02 approved execution brief

- Child: 02
- Exact approved plan: docs/plans/2026-11-05/expert-material-filter-02-ingress.md
- Plan identity: sha256:1d1792fabfafe46b3be0f5bc728d8b24c712eb0690a713d4f75a34140b5460e1
- Master plan: docs/plans/2026-11-05/expert-material-filter-master.md
- Master identity: sha256:344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd
- Master base: 71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master
- Branch: fast/expert-material-filter-master
- Epoch: 1
- Execution report: docs/plans/fast/expert-material-filter-master/children/02/execution.md
- Verification report: docs/plans/fast/expert-material-filter-master/children/02/verify-log.md

Read the exact child plan in full from this worktree. Its T1..T4, acceptance matrix, prescribed APIs, downstream contracts, manual A-n scope, and required commands are the complete approved child specification; the master supplies only its explicitly cross-referenced global invariants. Do not inspect later child plans.

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/document/service/ManualExpertMaterialUploadService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMailReceiveServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/ImapMetadataFetchIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/document/controller/ManualExpertMaterialUploadFlowTest.kt`

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
- Depends on 01 LIGHT_PASS/LIGHT_PASS_WITH_NOTES and its checked-in evidence. Use its exact rule APIs, no local signature duplicate. Child product base is the prior child terminal code head, supplied at dispatch. Do not alter child 01 files. Later 03 reads historic/new raw records and uses shared rule; it must not need an ingress-specific state/DTO. Required commands are the exact two commands in child 02 lines 150-151.

## Baseline evidence
Baseline command outputs and exact counts are recorded in ledger.md under Baseline before dispatch. Required new Eligibility/SQL IT classes do not exist at the approved master base, so no pre-change pass is claimed for them.
