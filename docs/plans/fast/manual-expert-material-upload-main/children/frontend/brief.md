# Fast-P Child Brief — frontend

Complete approved contract for child `frontend` of the fast-p run on master plan
`docs/plans/2026-09-20/00-manual-expert-material-upload-main.md`.

- Child ID: `frontend`
- Child plan (exact, approved, do not edit): `docs/plans/2026-09-20/manual-expert-material-upload-frontend.md`
- Master plan (exact, approved, do not edit): `docs/plans/2026-09-20/00-manual-expert-material-upload-main.md`
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-manual-expert-material-upload-main`
- Branch: `fast/manual-expert-material-upload-main`
- `child_base_sha`: `80beb2bddfc77f8f65f8c51446c9a6c14f2e10df`
- Depends on: `backend` (master plan Invariant I-1: the backend contract must be machine-verified first)
- Upstream state: child `backend` reached `LIGHT_PASS_WITH_NOTES` (four gates PASS, no AUTO_FIX) at
  `80beb2bddfc77f8f65f8c51446c9a6c14f2e10df`; its evidence commit is `c591668`. The backend HTTP/list
  contract below is frozen and must not be changed from the frontend side.
- Epoch: 1
- Implementation commit message (exact): `feat(fast-p): implement frontend`
- Execution report path: `docs/plans/fast/manual-expert-material-upload-main/children/frontend/execution.md`

You MUST read the child plan in full before writing code; it is authoritative for the implementation
steps, the style contracts S-1/S-2/S-3 (verbatim CSS text), the current-state audit, and the acceptance
criteria. This brief only adds the run-level contract and the backend interface frozen by the already
verified `backend` child.

## Authorized files (exactly these 6 — nothing else may be modified or added)

1. `src/main/resources/static/expert-materials.js`
2. `src/main/resources/static/styles.css`
3. `src/main/resources/static/index.html`
4. `src/test/js/expertMaterialsShared.test.js`
5. `src/test/js/expertMaterialsStyle.test.js`
6. `src/test/js/sharepointFileCardDisplay.test.js`

If the plan cannot be implemented without touching another file, a new DOM id, a new state, or another
subsystem: STOP and return `PLAN_CONFLICT`. Do not widen scope (master plan Invariant I-6).

## Global constraints (master plan)

- I-1 sequential execution: the backend child is already verified; do not modify backend code, the
  backend HTTP contract, or the list contract.
- I-2 frozen HTTP contract (already implemented and verified): one request per file to
  `POST /api/expert-contacts/{contactId}/materials/uploads`, single multipart field `file`, explicit
  `headers:{}` so the browser sets the multipart boundary, 201 on success, error statuses 400/401/404/413
  whose server message is shown on the queue row.
- I-3 frozen list contract: the list is refreshed only through the existing shared store `GET`; never
  fabricate or insert a material row client-side, never create a second materials store.
- I-4 single capacity unit: `MAX_MANUAL_MATERIAL_BYTES = 100*1024*1024`; the frontend `file.size` check is
  only a pre-check and never replaces the backend decision.
- I-7 do not change the `selectionOnly` AI view, the same-contactId single store, paging/filter/cross-page
  selection, the transfer POST, the 2s polling, the drawer close semantics, the mail attachment
  "获取到服务器"/retry/download/preview/AI behaviour, or the existing `expert-materials.css` byte contract.
- The worktree baseline is the current working tree and contains the uncommitted SharePoint file-card WIP.
  Preserve it: the 11 versioned assets must be unified onto the new cache key without reverting that work,
  and `sharepointFileCardDisplay.test.js` must derive the key from `styles.css?v=` instead of hardcoding
  the old key.

## Child-plan invariants I-1..I-7 and style contracts S-1..S-3

Implement exactly as written in the child plan's `## 关键不变量` and `## 样式契约` sections. The S-1/S-2
CSS blocks must be appended to `styles.css` verbatim (character for character), and the S-3 text/aria
changes apply to the existing DOM only. Reuse `.button`, `.button.primary`, `.button.secondary`,
`.button.small`, `.modal-close-btn`, `.em-policy`, `.em-filters`; do not modify those shared rules.

## Required commands (run all of them; report exit codes and counts)

```bash
node --check src/main/resources/static/expert-materials.js
node --test src/test/js/expertMaterialsShared.test.js src/test/js/expertMaterialsStyle.test.js src/test/js/sharepointFileCardDisplay.test.js
node --test src/test/js/meetingConfirmationAssets.test.js src/test/js/trustReplyWorkbenchSharedMount.test.js src/test/js/ragKnowledgeBasePage.test.js src/test/js/checkRepliesRelocation.test.js src/test/js/overlayAndDialogContrast.test.js
git diff --check
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

Baseline for the JS suite is recorded below: 12 files / 17 tests are already red because the working tree
ships two different cache keys. Unifying the 11 keys onto `20260920-manual-material-upload` (I-7) is
expected to make all of them green; any JS failure that survives after that unification and is not
explained by the pre-existing split-key baseline must be reported, not hidden.

## Frozen upstream interface (verified backend child, commit `80beb2b`)

Endpoint: `POST /api/expert-contacts/{contactId}/materials/uploads`, exactly one multipart part named
`file`, HTTP 201 on success. Errors: 401 anonymous / 400 missing or invalid request / 404 unknown expert /
413 over limit with `{"code":"PAYLOAD_TOO_LARGE", ...}`.

Real 201 body produced by the verified backend (no `storagePath` anywhere):

```json
{"attachmentId":2,"documentId":3,"fileName":"cv.pdf","contentType":"application/pdf","fileSize":6,"documentType":"CV","documentStatus":"PENDING_REVIEW","storageState":"STORED"}
```

Real 413 body:

```json
{"code":"PAYLOAD_TOO_LARGE","message":"单个材料不能超过 104857600 字节","detail":"Payload Too Large"}
```

Real `GET /api/expert-contacts/{contactId}/materials` manual item (the list stays the only source of
material rows):

```json
{"attachmentId":2,"documentId":3,"source":{"type":"MANUAL_UPLOAD","id":1,"subject":"手动上传","receivedAt":"2026-09-20T14:17:40.625193","accountCode":null,"uploadedBy":"op1"},"fileName":"cv.pdf","contentType":"application/pdf","documentType":"CV","documentStatus":"PENDING_REVIEW","actualSize":6,"encodedSize":null,"storageState":"STORED","bytesDownloaded":0,"error":null,"canFetch":false,"canDownload":true,"canPreview":true,"analysisSupported":true,"canAnalyze":true,"downloadUrl":"/api/expert-contacts/1/attachments/2/download","previewUrl":"/api/expert-contacts/1/attachments/2/preview"}
```

## Commit and report discipline

- Modify only the authorized files. Stage explicitly (`git add <path>`); never `git add -A`/`-u`.
- Never stage or commit anything under `docs/plans/` (the controller owns all fast-p evidence).
- Commit the implementation locally as `feat(fast-p): implement frontend`.
- Write the full result to the execution report path listed above (do not commit it).
- Do not push, merge, rebase, amend, squash, or rewrite history.
- Return only: `READY_FOR_VERIFICATION | BLOCKED | PLAN_CONFLICT`, the commit SHA, a command summary,
  and the report path.

## Baseline (recorded by the controller at `d2a7f65`)

- `node --test --test-reporter=tap src/test/js/*.test.js`: exit 1, `# tests 1023 / # pass 1006 / # fail 17`
  in 12 files (`batch send task console visual repair`, `check replies relocation (p1)`,
  `收发件箱静态资源版本`, `manual reply subject prefill (p3)`, `S-1/S-2: 注册与源文本契约`,
  `T3`/`T4` registration suites, `P2 overlay + dialog contrast`, `RAG 知识库页`,
  `rag workbench render contracts`, `shared trust reply workbench mount contract`), all caused by the
  pre-existing split cache key.
- `mvn test`: exit 1 after 03:06 min at baseline, failing only the `node-test` `exec-maven-plugin` step
  (the same JS cache-key red). Java surefire at baseline: 3490 tests / 0 failures / 0 errors / 13 skipped.
  After the backend child: 3503 / 0 / 0 / 13 (its new flow test adds 13). Expect the same behaviour here:
  the Java suite must stay green and the JS suite must become fully green once the 11 keys are unified.
