# Manual Acceptance — apollo-verification-master

## Epoch 2 — 2026-10-19T11:38:17+08:00

- Reviewed code boundary: `3efc192faf043badc1ef27bef124a71d3120c4c5..bffa8ff4a35fd7b066ed522dcbd41efb245a2376`
- Machine report epoch: 2
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | F123 账本对账：打开深度发现执行记录、准入批次并逐条核验。 | 123档案；自动55=直接15+Apollo40；已明确36、未明确19、待核验45、错误5、排队18；未明确筛选19；三批40/40/43；理由显示完整CODE及中文。 | PENDING |  |  |  |
| A-2 | Yes | F-RULE 准入口径与重验：启动 ENFORCE，检查结果后执行学术补全、RAW晋升扫描和候选重验。 | 已绑定当前教授为已明确、Apollo本人且年龄未知为未明确并自动准入；同名不同机构和身份未闭环博士生待核验；前两者重验后仍 AUTO_PASSED 且候选存在；无新增邮件。 | PENDING |  |  |  |
| A-3 | Yes | 人工与历史保护：准备 HOLD、REJECTED、MANUAL_APPROVED、LEGACY_APPROVED、SBIR、已有联系人、APPLICATION 与无账本旧任务，运行核验、原人工审核/撤销并查看旧/其他任务。 | 保护档案原决定与运营信息不变；审核页签可操作；旧任务 Apollo 栏未记录；其他任务保持原列；不自动恢复历史档案。 | PENDING |  |  |  |
| A-4 | Yes | 中断、失败与真实效果：切换测试 HTTP 429/500、一次 ES 写失败，暂停采集、重启、恢复网络并重新打开已完成采集。 | 采集状态不被核验 worker 改写；核验归属原执行继续；错误单列；投影失败显示候选同步待处理；重试不增加分母/准入；不声称错误减少等于召回提升。 | PENDING |  |  |  |
| A-5 | Yes | 测试环境 1366×900/100% 对照冻结截图，切换原3页签，展开批次/下载量/证据，缩至390px，Tab 操作，关闭后换另一任务。 | 保留原弹窗/来源控件；主色#1e40af、正文#1e293b；最大1180px、圆角18px；表头#f8fafc不重叠；仅弹窗纵向滚动、表格横向滚动；无卡片仪表盘或串任务响应。 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `bffa8ff4a35fd7b066ed522dcbd41efb245a2376`
- Reporter: user
- Timestamp:
- Note:
