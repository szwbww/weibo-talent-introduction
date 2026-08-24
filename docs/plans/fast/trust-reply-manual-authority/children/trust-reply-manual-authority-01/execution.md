# Execution Report — trust-reply-manual-authority-01

Append-only. Epoch 1.

## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-manual-authority/docs/plans/2026-08-24/01-mail-request-extraction-correctness.md
Plan SHA-256: ba025f7dd7f7295645ce7bbbb1a25cadde7e48e75ee72d449dd35c723eea64a4
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-manual-authority/docs/plans/2026-08-24/01-mail-request-extraction-correctness.md@ba025f7dd7f7295645ce7bbbb1a25cadde7e48e75ee72d449dd35c723eea64a4
Execution epoch: NEW
Approval basis: current invocation (approved child brief trust-reply-manual-authority-01)
Executor: Impl01
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-manual-authority
Target branch: fast/trust-reply-manual-authority
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction/.worktrees/trust-reply-manual-authority@fast/trust-reply-manual-authority@/Users/lukai/IdeaProjects/weibo-talent-introduction/.git/worktrees/trust-reply-manual-authority
Pre-execution code SHA: 8dc7c968153c3ec5131ada276086bcbe0c9add88 (plan-seeding HEAD); child base 99cef49a37f79b409504e89cd5cd942370966c39
Post-execution code SHA: 7989af65e5c62d414d5a4557d79e3f06007bc4f9
Evidence HEAD: 7989af65e5c62d414d5a4557d79e3f06007bc4f9 (single implementation commit; no separate evidence commit required)
Implementation boundary: 99cef49a37f79b409504e89cd5cd942370966c39..7989af65e5c62d414d5a4557d79e3f06007bc4f9 (working-tree delta staged as one commit)

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| 阶段 1: 收紧 bullet marker（符号/数字 marker 后必须空白） | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt; src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractorTest.kt | `BULLET_LINE_PATTERN = Regex("^(?:[-*•]\\s+|\\d+[.)]\\s+)")`; 4 new tests: 脱敏线上签名 1×QUESTION、五种合法 marker 保序、`*Name*`/`-not a list` 拒绝、缩进续行折叠 |
| 阶段 2: ask/span 统一绝对坐标（claim 前 rebase） | IMPLEMENTED | src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt; src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt; src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt | `absoluteMatchedSpans` 派生（range.first/last + requestRange.first）仅供 shadow claiming；`MatchedIntentSpan` 注释改为"range 属于传入字符串局部坐标，调用者必须 rebase"；新增非零起点回归（claimed=3 / unrecognized=1 / enumerated 守恒） |
| 阶段 3: requestKey 漂移 saved-state STALE 回归 | IMPLEMENTED | src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt | 旧矩阵含 5 条签名 requestKey → bootstrap 不抛 422，返回默认矩阵 + STALE + 锁定项为空；显式同矩阵仍 422（TRUST_REPLY_REQUEST_KEY_INVALID） |
| I-1 offset 指向原始全文 | PRESERVED | QaRequestExtractor.kt / QaRequestExtractorTest.kt | CRLF/CR 原文切片测试、脱敏 fixture offset 回切断言继续通过 |
| I-2 仅显式 marker 为 bullet | PRESERVED | QaRequestExtractor.kt / QaRequestExtractorTest.kt | 边界表测试（合法 marker 保留、无空白 marker 拒绝、续行不变） |
| I-3 claim 单一坐标系 | PRESERVED | QaFactSelectionService.kt / AiReplyIntentCatalog.kt | 非零 offset 回归在修复前失败（4 全 unrecognized），修复后 claimed=3；零起点测试（orthopaedic）不受影响 |
| I-4 枚举仍是影子信号 | PRESERVED | QaFactSelectionService.kt / QaFactSelectionServiceTest.kt | 既有 `shadow enumeration never changes status counts or fact ids` 继续通过（63+66+18 全绿） |
| I-5 旧快照失败关闭不迁移 | PRESERVED | TrustReplyWorkbenchServiceTest.kt | 新回归：隐式旧矩阵 → STALE 默认选择，无 422；显式脏矩阵仍 422 |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -q -Dtest=QaRequestExtractorTest,QaFactSelectionServiceTest,TrustReplyWorkbenchServiceTest test`（第 1 次，fixture 修正前） | FAIL | exit 1；2 处新测试夹具失败：QaRequestExtractorTest `markdown emphasis and hyphenated text are not bullets`（缺空行段落边界）、TrustReplyWorkbenchServiceTest `old saved matrix with signature request keys...`（期望 requestKey 未镜像 stub item.intents）→ 已修正夹具后重跑 |
| 同上（第 2 次，最终态） | PASS | exit 0；Tests run: 147 total — QaRequestExtractorTest 18 / QaFactSelectionServiceTest 66 / TrustReplyWorkbenchServiceTest 63，Failures 0, Errors 0, Skipped 0（surefire-reports） |
| `git diff --check` | PASS | exit 0，无空白错误 |

### Changed Files
- src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractor.kt — bullet marker 收紧（符号/数字 marker 后必须空白）
- src/main/kotlin/com/weibo/talentintroduction/llm/service/AiReplyIntentCatalog.kt — MatchedIntentSpan 坐标注释修正（局部坐标 + 调用者 rebase 责任）
- src/main/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionService.kt — shadow claiming 前把局部 span rebase 为绝对坐标（requestRange.first）
- src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRequestExtractorTest.kt — 脱敏线上 fixture、marker 边界表、缩进续行、offset 回归（+4）
- src/test/kotlin/com/weibo/talentintroduction/llm/service/QaFactSelectionServiceTest.kt — 非零 request offset claimed/unrecognized 回归（+1，含 QaRequestExtractor import）
- src/test/kotlin/com/weibo/talentintroduction/llm/service/TrustReplyWorkbenchServiceTest.kt — requestKey 漂移 saved-state STALE 回归（+1）

### Deviations
- `scripts/worktree_identity.py`（execute-p skill）在本机因公共仓库元数据含已消失的 `/sessions/...` 过期 worktree 条目而报 FileNotFoundError；未改动该脚本，改用等价内联计算得到完全相同的 Worktree ID 字段（root/branch/git_dir/common_dir/head 一致，见上）。
- 阶段 3 显式错误矩阵断言使用 `TRUST_REPLY_REQUEST_KEY_INVALID`（未知 requestKey 时代码实际抛出的 422 code）；显式脏选择 422 `TRUST_REPLY_FACT_SELECTION_INVALID` 由既有测试 `bootstrap fails closed when implicit saved fact selection is unusable` 继续覆盖（未改动、仍通过），满足验收"显式脏矩阵仍 422"。
- 未运行 formatter/linter/全量测试套件（按 brief 要求由 verifier/controller 统一执行）。

### Freshness
- Plan identity rechecked: YES（SHA-256 前后一致 ba025f7d...）
- Worktree identity rechecked: YES（等效内联计算，字段一致）
- Reported commits reachable from target branch: YES（7989af6 为 HEAD 且是 fast/trust-reply-manual-authority 祖先）
- Required commands run this invocation: YES（mvn 定向测试 + git diff --check 均为本次最终态下新跑）
- Historical evidence used only as baseline: YES

### Remaining Blocker
- None

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`
