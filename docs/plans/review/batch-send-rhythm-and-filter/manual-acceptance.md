# Manual Acceptance — batch-send-rhythm-and-filter

## Epoch 2 — 2026-08-13T01:19:18Z

- Reviewed code boundary: a6c27bbbca02a3b018d8a16aeb11822abd905e19..fc136629fc9645334f71a3024c2b6fa96c909dee
- Machine report epoch: 2
- Status: PENDING
- Note: MySQL IT was explicitly HUMAN-SKIPPED; this checklist contains only the master plan's manual items.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 01-A1 | yes | 执行轮次 | 单次量 = 轮次 × 每轮数量 | PENDING | | | |
| 01-A2 | yes | 未用尽轮次 | 不误报轮次耗尽 | PENDING | | | |
| 01-A3 | yes | 手动单轮 | 恰好一轮 | PENDING | | | |
| 01-A4 | yes | 日限额回归 | 01 阶段闸门未失效 | PENDING | | | |
| 01-A5 | yes | seeded 配置迁移 | 实际发送量不变 | PENDING | | | |
| 01-A6 | yes | 旧 typed API | 不静默改轮次为 1 | PENDING | | | |
| 01-A7 | yes | 轮次耗尽 | 不空等轮间隔 | PENDING | | | |
| 01-A8 | yes | 改执行轮次 | 不影响调度注册 | PENDING | | | |
| 01-A9 | yes | 账号/预热 | 先于轮次预算生效 | PENDING | | | |
| 02a-A1 | yes | 同日定时多次触发 | 不再被日额度拒绝 | PENDING | | | |
| 02a-A2 | yes | 账号限额 | 唯一兜底 | PENDING | | | |
| 02a-A3 | yes | 预热压制 | 定时路径仍生效 | PENDING | | | |
| 02a-A4 | yes | 手动单轮 | 恰好一轮 | PENDING | | | |
| 02a-A5 | yes | 轮次预算 | 仍是硬闸门 | PENDING | | | |
| 02a-A6 | yes | 材料提醒 | 路径同步生效 | PENDING | | | |
| 02a-A7 | yes | 阶段回归 | 字段与列尚未删除 | PENDING | | | |
| 02b-A1 | yes | 删除列/API | `daily_cap` 删除且 API 不返回 | PENDING | | | |
| 02b-A2 | yes | 阶段兼容 | 04 前前端保存成功 | PENDING | | | |
| 02b-A3 | yes | 发送行为 | 与 02a 逐字一致 | PENDING | | | |
| 02b-A4 | yes | 轮次预算 | 仍生效 | PENDING | | | |
| 02b-A5 | yes | 旧 typed API | dailyCap 形态不变 | PENDING | | | |
| 02b-A6 | yes | 材料提醒 | 正常保存与执行 | PENDING | | | |
| 03-A1 | yes | 单选地区 | 两条目标来源一致 | PENDING | | | |
| 03-A2 | yes | 多选地区 | 取并集 | PENDING | | | |
| 03-A3 | yes | 非法地区 | 明确拒绝 | PENDING | | | |
| 03-A4 | yes | Other | 覆盖未填国家专家 | PENDING | | | |
| 03-A5 | yes | 空地区 | 不限制 | PENDING | | | |
| 03-A6 | yes | 专家列表单选 | 行为不变 | PENDING | | | |
| 03-A7 | yes | 已知偏差 | 待发统计不含地区筛选 | PENDING | | | |
| 04a-A1 | yes | cron 预览 | 返回最近 5 次 | PENDING | | | |
| 04a-A2 | yes | 非法 cron | 明确原因、不报错 | PENDING | | | |
| 04a-A3 | yes | 列表时间 | 返回下次/最近执行 | PENDING | | | |
| 04a-A4 | yes | 手动执行 | 计入最近执行时间 | PENDING | | | |
| 04a-A5 | yes | 脏 cron | 不打挂列表 | PENDING | | | |
| 04a-A6 | yes | 查询次数 | 不随配置数增长 | PENDING | | | |
| 04a-A7 | yes | 既有字段/调度 | 行为不变 | PENDING | | | |
| 04b-A1 | yes | 编辑器 | 无日限额；轮次/总量正确 | PENDING | | | |
| 04b-A2 | yes | 地区多选 | 可用且样式/标签一致 | PENDING | | | |
| 04b-A3 | yes | 自定义 cron | 测试按钮可用 | PENDING | | | |
| 04b-A4 | yes | 执行时间 | 合并列正确 | PENDING | | | |
| 04b-A5 | yes | 非法 cron 保存 | 拒绝并显示原因 | PENDING | | | |
| 04b-A6 | yes | 其余字段/三频率 | 行为不变 | PENDING | | | |
| 04b-A7 | yes | 手动 tab | 来源选择、diff、轮次确认正确 | PENDING | | | |
| 04b-A8 | yes | 标签多选 | 未受影响 | PENDING | | | |
| 05-A1 | yes | 三处地区 | 全部中文展示 | PENDING | | | |
| 05-A2 | yes | 中文地区 | 仅显示层，筛选仍命中 | PENDING | | | |
| 05-A3 | yes | 配置地区 | 存英文领域值 | PENDING | | | |
| 05-A4 | yes | 未分类 | 可选、可保存 | PENDING | | | |
| 05-A5 | yes | 未分类发送 | ES 与重试路径均可发 | PENDING | | | |
| 05-A6 | yes | STEM/文社科 | 行为不变 | PENDING | | | |
| 05-A7 | yes | 已知限制 | 旧 typed API 不接受 UNCLASSIFIED | PENDING | | | |
| 05-A8 | yes | 专家筛选/地区计数 | 联动不变 | PENDING | | | |

## Human Sign-off

- Decision: PENDING
- Boundary: fc136629fc9645334f71a3024c2b6fa96c909dee
- Reporter: user
- Timestamp: PENDING
- Note: PENDING
