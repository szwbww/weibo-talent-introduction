# Child 03 approved execution brief

- Child: 03
- Exact approved plan: docs/plans/2026-11-05/expert-material-filter-03-projections.md
- Plan identity: sha256:ef85056d991448e846a729ca2fdff8d8ab83869cfc645ee36181d5d72abca2ed
- Master plan: docs/plans/2026-11-05/expert-material-filter-master.md
- Master identity: sha256:344158c4a2d30f05e7141544a7d4de64d3b33435752f322651ca861ec0f801fd
- Master base: 71bb4879c77c9fcb69f1688e7192ad9aea5c0c4b
- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-expert-material-filter-master
- Branch: fast/expert-material-filter-master
- Epoch: 1
- Execution report: docs/plans/fast/expert-material-filter-master/children/03/execution.md
- Verification report: docs/plans/fast/expert-material-filter-master/children/03/verify-log.md

Read the exact child plan in full from this worktree. Its T1..T4, acceptance matrix, prescribed APIs, downstream contracts, manual A-n scope, and required commands are the complete approved child specification; the master supplies only its explicitly cross-referenced global invariants. Do not inspect later child plans.

## Authorized Files
- `src/main/kotlin/com/weibo/talentintroduction/mail/repository/MailRecordRepository.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailboxService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileService.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/MailboxServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/service/AutoReplyPreviewServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/service/OperatorStatusReconcileServiceTest.kt`
- `src/test/kotlin/com/weibo/talentintroduction/mail/repository/MailboxSignatureProjectionIT.kt`
- `src/test/kotlin/com/weibo/talentintroduction/campaign/OperatorStatusWriteSeamGuardTest.kt`

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
- Depends on 01 shared rule/message filtering and ordered completion of 02. Child product base is child 02 terminal code head, supplied at dispatch. Do not alter preceding children. Four SQL EXISTS consume the fixed shared const, no query/owner-model redesign. Guard changes are existing NoiseSite line numbers plus movement comments only. Required commands are the exact two commands in child 03 lines 137-138. No downstream product child; defer whole-system review to the human.

## Baseline evidence
Baseline command outputs and exact counts are recorded in ledger.md under Baseline before dispatch. Required new Eligibility/SQL IT classes do not exist at the approved master base, so no pre-change pass is claimed for them.

## Predecessor output and dispatch boundary
- Child 01 LIGHT_PASS: code head 51268418bfeb0dd666c1fcc079cbb6f4cb239c4a; evidence 89a56a9ee6d43fb78c9e76440a3d2869c94345b1; reusable stateless eligibility API, fixed alias `a` SQL const, and raw-first filtered message attachment resolution are checked in.
- Child 02 LIGHT_PASS: code head 00d48c74955626519fcfcc6de859f1fcc63b8c82; evidence ed2a17d8a2fc30dbbd6b5cb54049b18ce6055ac5; new MIME/business/save/bridge/upload paths reuse the same rule with no new DTO/schema/state.
- Complete-manifest checkpoint through 02 passed with `--allow-dirty-artifacts` then clean mode: exit 0, VALID, CHILD_CHECKPOINT; clean checkpoint HEAD e9ee3aae67ffe4519106dd83d596c8695f708666, child 03 still PENDING.
- Child 03 product base is 00d48c74955626519fcfcc6de859f1fcc63b8c82, not an evidence SHA. Prior required commands were independently fresh with zero skips; their results do not replace child 03 commands.
- Use existing `ExpertMaterialEligibility.isMaterial`, compile-time `MATERIAL_SQL`, and `ERROR_MESSAGE`; do not edit any predecessor product/test file. Preserve the exact existing query scopes/owner matching, preview classification, and operator authority boundaries.
- No unresolved helper prerequisite: explicitly human-authorized metadata-only stale-registration cleanup restored the unmodified mandatory worktree helper. No further metadata/helper changes are authorized.

