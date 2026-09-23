# Fast-P Child Brief — c2（前端：定时与手动多选）

- Child ID: `c2`
- 权威子计划：`docs/plans/2026-09-23/02-batch-sender-filter-frontend.md`（计划身份 `commit:a58ce98`）
- 总计划：`docs/plans/2026-09-23/00-batch-sender-filter-main.md`（身份 `commit:a58ce98`；人工批准的改写见 ledger `## Amendments` A1–A6）
- 迁移号与本 child 无关（后端 V135）；共享收件箱的 owner UI 在并行分支上，本 child 仍以当前 main 的 `index.html`/`app.js` 为基线。
- Worktree：`/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-23-batch-sender-filter-main`
- Branch：`fast/2026-09-23-batch-sender-filter-main`
- `child_base_sha`：`248c30a`（c1 的 `Code head`；含后端 `senderAccountCodes` 接口与 V135 迁移；若 c1 出现修复轮则以当时最新 Code head 为准）
- 执行报告路径：`docs/plans/fast/2026-09-23-batch-sender-filter-main/children/c2/execution.md`
- 实现提交信息：`feat(fast-p): implement c2`

## 授权文件（只能是子计划 `## 变更文件清单` 的这 3 个）

1. `src/main/resources/static/index.html`
2. `src/main/resources/static/app.js`
3. `src/test/js/batchSenderFilter.test.js`

禁止：改 CSS（`styles.css` 必须零 diff）、改后端 Kotlin/迁移、改计划文件、改 `docs/plans/fast/**`、新建其他文件、push/merge/rebase/amend。

## 上游接口（c1 已实现，必须按实际代码核对，不得臆造字段名）

- 配置 API：`senderAccountCodes: string[]`（有序去重，`[]`=全部可发送账号）在 `POST/PUT /api/mail/batch-send/configs`、`GET` 详情/列表、编辑器预估快照、手动执行快照四处同名同值。
- 手动快照未知 code 由后端返回 422；前端不得把未知 code 静默改成 `[]`。
- 账号选项来自只读 `GET /api/mail/sender-accounts`（`accountCode`/`senderEmail`/`enabled`/`autoSendPaused`）。

## 必须保持的不变量（子计划 I-1～I-3、S-1、S-2）

- I-1：option `value` = `accountCode`，label = `senderEmail · accountCode`；不按 `inboundMailboxCode` 分组/去重；停用账号标注「已停用，本次不会发信」且历史已选值保留可见，不因列表刷新静默删除。
- I-2：`buildConfigEditorRecipientSnapshot`、配置 POST/PUT、`deepCloneConfig`、`readManualFormValues`、`buildManualExecutionSnapshot` 全部携带有序去重 `senderAccountCodes`；空选确实发 `[]`。
- I-3：只新增 S-1/S-2 逐字规定的两个 `batch-config-field` 块；不新增 class、不改 `styles.css`、不加 inline style；`index.html` 的 11 个带版本资源键同值统一 bump（执行前用当前键反查固定字面量测试）。
- S-1/S-2：DOM 与契约逐字对应；两处既有标题副文案按契约替换文字、保留父元素与 class；手动块含既有 `.batch-config-diff-badge`/`.batch-config-diff-original`。
- 测试必须真实解析 `index.html` 源文本断言新增 id/data-tag-picker 与 registry/绑定调用对齐（DOM stub 全绿不算证据），并覆盖：配置 A 保存→GET 回显 A→手动带入 A→改 B 的差异→预估/执行快照为 B；独立手动空选为 `[]`；停用历史 code 不被抹掉；同物理收件箱的两个逻辑 code 是两个 option。

## 必需命令（实现后必须全部重跑并记录 exit code / 计数）

```bash
node --check src/main/resources/static/app.js
node --test src/test/js/batchSenderFilter.test.js src/test/js/batchSendTaskConsoleInteraction.test.js
node --test src/test/js/*.test.js   # 全量 JS 回归
```

- 基线（本 worktree `baseline/js.txt`）：`node --check` exit 0；全量 `node --test src/test/js/*.test.js` 1121 pass / 0 fail。全量若有失败，必须区分「基线已失败」与「本次新增失败」。

## 操作约束

- 计划不改 CSS；视觉验收（宽屏双列/窄屏单列、chip/focus/dropdown/「已修改」）由人工在联合验收执行，实现只需保证复用既有 class 与 DOM 契约。
- 实现提交只包含这 3 个文件；`docs/plans/**` 一律排除。
