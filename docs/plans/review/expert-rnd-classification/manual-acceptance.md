# Manual Acceptance — 生产型/科研型专家分类与发信门禁主计划

## Epoch 3 — 2026-08-24T21:18:49+0800

- Reviewed code boundary: `c004a18d675b86040597f17f5911aa52f718d156..0bc071bf24c84426315bc4b138d8aa4394182910`
- Machine report epoch: 3
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 测试环境：一名有邮箱、无 `expertClassification` 的 CANDIDATE；发布四个子计划且不运行回填；请求 INTRODUCTION 预估并触发大小为 1 的手动首发。 | 预估 0；任务发送数 0；该专家无新增 OUTBOUND/INTRODUCTION/SENT 邮件。 | PENDING |  |  |  |
| A-2 | Yes | 准备临床医生、近期论文科研、专利+产品研发、信息不足四名有邮箱 CANDIDATE；运行 CANDIDATE DRY_RUN、正式回填、查询分类、请求首发预估并手动首发。 | 类型依次 SERVICE_ONLY、ACADEMIC_RND、PRODUCTION_RND、UNKNOWN；仅中间两人 `sendable=true`；预估和实际目标均为 2；医生和未知人员无发送。 | PENDING |  |  |  |
| A-3 | Yes | MySQL 中准备两名未成功发送的 NEW 联系人，其 ES profile 分别 `sendable=true` 和 `false`；请求同 snapshot 预估，启动批量首发并查看结果。 | `retryable=1`；仅 `sendable=true` 联系人发送；false 联系人无 SENT。 | PENDING |  |  |  |
| A-4 | Yes | 一名 APPLICATION 联系人标签为“承诺回复材料”、分类字段缺失；请求 MATERIAL_REMINDER 预估并执行一次材料提醒。 | 该联系人仍计入预估且可发送；分类门禁不参与 MATERIAL_REMINDER。 | PENDING |  |  |  |
| A-5 | Yes | 记录一名候选回填前的 email、employment、tags、operatorStatus、updatedAt；正式回填后读取完整 `_source`。 | 五个原值逐字相同；只新增或替换 `expertClassification`。 | PENDING |  |  |  |
| A-6 | Yes | 测试环境至少 10 条无分类 CANDIDATE；重启应用，等待 2 分钟，查询缺失数和任务历史。 | 缺失数仍为 10；无新的 EXPERT_CLASSIFICATION_BACKFILL 执行记录。 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `0bc071bf24c84426315bc4b138d8aa4394182910`
- Reporter: user
- Timestamp:
- Note:
