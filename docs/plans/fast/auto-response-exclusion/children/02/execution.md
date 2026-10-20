## Execution Result: READY_FOR_VERIFICATION

Plan: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion/docs/plans/2026-10-20/mailbox-pending-confirmation-label.md
Plan SHA-256: 96de6aeac5609cfb4a049b0c631525fcb5096d91e56f96e292914f7b81bbd9b9
Execution ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion/docs/plans/2026-10-20/mailbox-pending-confirmation-label.md@96de6aeac5609cfb4a049b0c631525fcb5096d91e56f96e292914f7b81bbd9b9
Execution epoch: NEW
Approval basis: child 02 brief (`docs/plans/fast/auto-response-exclusion/children/02/brief.md`) + approved plan V3
Executor: AutoResponseFrontendImpl (fast-p child 02)
Target worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion
Target branch: fast/auto-response-exclusion
Worktree ID: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion@fast/auto-response-exclusion@.git/worktrees/weibo-talent-introduction-fast-auto-response-exclusion
Pre-execution code SHA: c203bd4
Post-execution code SHA: 86be61d
Evidence HEAD: 86be61d
Implementation boundary: c203bd4..86be61d

### Task Status
| Requirement | Status | Files | Evidence |
|---|---|---|---|
| T1 chip/tab 显示“待确认”，key 仍 `CHIP_UNMATCHED`/`unmatched` | IMPLEMENTED | mailbox-chat.js | `{ key: CHIP_UNMATCHED, label: "待确认" }`; behavior test asserts `dataset.chip === "unmatched"` |
| T2 空态（列表空态/详情占位）、错误提示、状态 toast 文案 | IMPLEMENTED | mailbox-chat.js | `暂无待确认来信`、`请选择左侧待确认来信`、`待确认来信加载失败，请重试。`、`获取待确认来信失败` |
| T3 aria-label 同步（右栏、详情滚动区、搜索框） | IMPLEMENTED | mailbox-chat.js | `待确认来信处理` ×2、`待确认来信详情` ×3、`搜索待确认来信` |
| T4 旧标签 badge 显示映射，class 仍按原 key | IMPLEMENTED | app.js | `MAILBOX_TAG_BADGE_LABELS` + `renderMailboxTagBadges`；行为测试断言 `badge warn` 与自定义标签不变 |
| T5 index.html 旧 option 仅改显示，value 保持 `待匹配` | IMPLEMENTED | index.html | `<option value="待匹配">待确认</option>`；样式测试断言 |
| T6 全部版本化资源统一新键 | IMPLEMENTED | index.html | 16 处 `?v=20261020-auto-response-confirmation`；无旧键残留 |
| T7 显示/参数/选项/草稿/分页回归测试 | IMPLEMENTED | mailboxChatBehavior.test.js, mailboxChatStyle.test.js | 新增 3 个行为用例 + 4 个样式/静态契约用例；既有请求参数、I-8 分页、I-6 草稿隔离断言保留并通过 |

### Commands
| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 1732, pass 1732, fail 0 |
| `rg -n -F '20261020-apollo-matching-v2' src/test` | PASS | 0 hits（原文） |
| `rg -n -F '20261020-apollo-matching-v2' src/main src/test` | PASS | 0 hits（无旧键残留） |

### Changed Files
- `src/main/resources/static/mailbox-chat.js` — tab/空态/错误/aria 用户可见文案改为“待确认”，key/id/action/请求参数不变。
- `src/main/resources/static/app.js` — 新增显示层 `MAILBOX_TAG_BADGE_LABELS`，`renderMailboxTagBadges` 用原 tags 值查 class。
- `src/main/resources/static/index.html` — option 显示改名；16 处版本化资源统一为 `20261020-auto-response-confirmation`。
- `src/test/js/mailboxChatBehavior.test.js` — 更新显示断言；新增旧标签映射、筛选原值、请求参数/分页不变用例。
- `src/test/js/mailboxChatStyle.test.js` — 更新 chip 标签断言；新增 option 原值、文案同步、缓存键同键、CSS 不变用例。
- `src/test/js/discoveryVerification.test.js` — 仅将固定键正则 slug 由 `apollo-matching-v2` 改为 `auto-response-confirmation`（见 Deviations）。
- `src/test/js/discoveryReview.test.js` — 同上，仅一行固定键正则 slug。

### Deviations
- 超出 brief 的 5 文件清单，修改了 2 个固定键测试（各 1 行，仅 slug）：
  `discoveryVerification.test.js:95`、`discoveryReview.test.js:993`。二者把旧键写死为正则 `^\d{8}-apollo-matching-v2$`，
  `rg -F '20261020-apollo-matching-v2'` 因日期被 `\d{8}` 替代而未命中。不改则 `node --test src/test/js/*.test.js` 必然 2 例失败，
  与 brief 的“更新真正固定这些引用的测试”及验收 I-3 冲突。
- 新增/更新测试仅在授权测试文件中；未修改任何 CSS、后端、枚举、DB、DOM id/class/action、`CHIP_UNMATCHED`、`unmatchedOnly`、`/api/mail/unmatched-inbound`、option value 或后端 tag 值。

### Freshness
- Plan identity rechecked: YES (sha256 96de6ae… 不变)
- Worktree identity rechecked: YES (branch fast/auto-response-exclusion, HEAD 86be61d)
- Reported commits reachable from target branch: YES
- Required commands run this invocation: YES（提交后重跑全部三项）
- Historical evidence used only as baseline: YES

### Remaining Blocker
- None（若控制方拒绝 2 个固定键测试的越界修改，需授权或调整提交；否则全量 node 测试无法通过）

### Next Action
- READY_FOR_VERIFICATION → run `verify-p`

## Epoch 2 — amended I-3 cache-key pin regression

Plan amendment: commit `a73ebf3` (docs: amend plans for cache-key test authorization) authorizes 7 frontend files, adding
`src/test/js/discoveryVerification.test.js` and `src/test/js/discoveryReview.test.js`. Plan SHA-256 was re-read for this epoch.

- Commit: `aee499b` — `feat(fast-p): implement 02 cache-key pin regression`
- Changed file: `src/test/js/mailboxChatStyle.test.js` (one new `it` in “待确认显示：文案、option 原值与缓存键”)
- Regression: asserts both discovery test files contain the exact pinned assertions
  `assert.match(values[0], /^\d{8}-auto-response-confirmation$/);` and
  `assert.match(keys[0], /^\d{8}-auto-response-confirmation$/);`, and neither contains `apollo-matching-v2`.
- Production behavior, CSS, index.html and the two discovery tests are unchanged in this commit.

### Commands (epoch 2, run after the final edit)
| Command | Result | Evidence |
|---|---|---|
| `node --check src/main/resources/static/mailbox-chat.js` | PASS | exit 0 |
| `node --check src/main/resources/static/app.js` | PASS | exit 0 |
| `node --test src/test/js/*.test.js` | PASS | exit 0; tests 1733, pass 1733, fail 0 |

### Freshness (epoch 2)
- Worktree/branch: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-auto-response-exclusion` @ `fast/auto-response-exclusion`, HEAD `aee499b`
- Required commands run this epoch: YES
- `docs/plans/**` changes remain uncommitted/controller-owned: YES
