# Manual Acceptance — 10-reply-orchestration-order

## Epoch 5 — 2026-08-29T12:46:10Z

- Reviewed code boundary: `de228e17cc0134a7c11dea7cbf82054e8d249f99..8fa4f6ca1fde33c471662acb49f53838386177a0`
- Machine report epoch: 5
- Status: PENDING
- Source: the manual checklists in the six ordered child plans declared by the governing master plan. No extra product requirement added.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 11-A1 | Yes | 五类问题有事实依据 | 五类条目均非 `UNSUPPORTED`，并显示指定五条事实 | PENDING |  |  |  |
| 11-A2 | Yes | 项目敏感性规则可保存/启用 | 保存、启用、撤销关键词均成功且无 400 | PENDING |  |  |  |
| 11-A3 | Yes | IP 实际问法命中 | 命中 `Pre-contract IP boundary`；否则候选集搜索 `ip arising` 可见 | PENDING |  |  |  |
| 11-A4 | Yes | 冻结四规则不变 | 应用 V109 前后所有字段（含 `updated_at`）相同 | PENDING |  |  |  |
| 11-A5 | Yes | 受控四事实正文不变 | 指定哈希/coverage 不变；仅 IP 关键词增长且 `updated_at` 不变 | PENDING |  |  |  |
| 11-A6 | Yes | 原规则优先级不变 | 原有行优先级/相对次序不变；仅末尾新增五行 | PENDING |  |  |  |
| 11-A7 | Yes | V109 幂等 | 重跑所有 INSERT/UPDATE 均影响 0 行且无报错 | PENDING |  |  |  |
| 11-A8 | Yes | 计划 11 零生产代码 | `main...HEAD` 仅三份清单文件，`src/main/kotlin/` 无变更 | PENDING |  |  |  |
| 12-A1 | Yes | 同一事实只出现一次 | 申请流程措辞全文仅一次 | PENDING |  |  |  |
| 12-A2 | Yes | 按主题分段 | 段落少于摘要，同主题聚合且主题首次顺序一致 | PENDING |  |  |  |
| 12-A3 | Yes | 单 CTA | 全信恰好一处动作句 | PENDING |  |  |  |
| 12-A4 | Yes | 会议 CTA 逐字保留 | 指定会议事实一字未改，且无额外 CTA | PENDING |  |  |  |
| 12-A5 | Yes | 审计规则集不变 | `canonicalFactIds` 与上线前完全相同 | PENDING |  |  |  |
| 12-A6 | Yes | 锁定/版本身份不变 | `versionId` 不变且整合无版本错误 | PENDING |  |  |  |
| 12-A7 | Yes | 全手动逃生舱 | 手写文本按顺序逐字拼接，不合并/删句/重排 | PENDING |  |  |  |
| 12-A8 | Yes | frame 顺序不变 | 尊语→开场→致谢→正文→结束语，各块一空行 | PENDING |  |  |  |
| 13-A1 | Yes | 段落连贯 | 同主题为连贯段落，有过渡/因果，不是句子拼接 | PENDING |  |  |  |
| 13-A2 | Yes | topic 驱动分段 | 改主题后事实进入新主题段，一次整合成功 | PENDING |  |  |  |
| 13-A3 | Yes | 缺口嵌入主题 | 条件式缺口不单独成段，不含禁用措辞 | PENDING |  |  |  |
| 13-A4 | Yes | 受控承诺逐字 | G2/G4 canonical 两句逐字出现 | PENDING |  |  |  |
| 13-A5 | Yes | 冻结事实逐字 | id 1/3/21 正文、占位符、`--`、en dash 均正确 | PENDING |  |  |  |
| 13-A6 | Yes | 编排失败回退 | 超时后仍整合，使用 12 确定性结果并显示 warning | PENDING |  |  |  |
| 13-A7 | Yes | 审计规则集不变 | `canonicalFactIds` 与 12 后完全相同 | PENDING |  |  |  |
| 13-A8 | Yes | 12 结论保留 | 复跑 12-A1/A3/A4/A7 全部通过 | PENDING |  |  |  |
| 14-A1 | Yes | 两摘要可并行操作 | 第 1 条局部遮罩，第 2 条仍可操作，无全屏遮罩 | PENDING |  |  |  |
| 14-A2 | Yes | 单条保存不锁全页 | 仅当前卡片遮罩，无全局保存遮罩 | PENDING |  |  |  |
| 14-A3 | Yes | 打开不自动分析 | 未分析占位/开始按钮，无 bootstrap POST，预判/重置禁用 | PENDING |  |  |  |
| 14-A4 | Yes | 手动开始分析 | 恰一次 bootstrap POST，内容正常，按钮状态正确 | PENDING |  |  |  |
| 14-A5 | Yes | 全局操作全局遮罩 | 保存框架、整合、一键预判均显示对应全局遮罩 | PENDING |  |  |  |
| 14-A6 | Yes | 整合快照与乐观锁 | `PUT /state` 含全部 `lockedItems` 和 `expectedStateVersion` | PENDING |  |  |  |
| 14-A7 | Yes | 只读可见 | 只读自动分析，显示横幅/内容，不显示未分析占位 | PENDING |  |  |  |
| 14-A8 | Yes | 样式未失真 | 工具栏/遮罩/暗色主题仅有计划新增视觉差异 | PENDING |  |  |  |
| 15-A1 | Yes | 三步页签 | 01/02/03 标号和标题正确；步骤 01 外观/行为不变 | PENDING |  |  |  |
| 15-A2 | Yes | 事实集去重 | 同一事实一行，触发来问/用量正确 | PENDING |  |  |  |
| 15-A3 | Yes | 运营事实逐字保护 | `op1` 以运营逐字来源显示，编排段落逐字包含 | PENDING |  |  |  |
| 15-A4 | Yes | 锁定段落不变 | 重排后锁定段逐字不变，其余重接 | PENDING |  |  |  |
| 15-A5 | Yes | 改 topic 改分段 | 改主题并重排后内容进新主题段，无校验失败 | PENDING |  |  |  |
| 15-A6 | Yes | 02/03 本地操作 | 非重排操作不发 workbench 请求、无遮罩 | PENDING |  |  |  |
| 15-A7 | Yes | 全手动逃生舱 | 全锁后整合按段逐字拼接，LLM 不改写 | PENDING |  |  |  |
| 15-A8 | Yes | 并发不回退 | 满足 14-A1 局部遮罩和并发操作预期 | PENDING |  |  |  |
| 15-A9 | Yes | 三步样式 | 页签、锁定卡、事实集、暗色主题均符合样式契约 | PENDING |  |  |  |
| 16-A1 | Yes | 索引有内容 | 完整训练流程后新记录出现，`total` 比基线 +1 | PENDING |  |  |  |
| 16-A2 | Yes | 编辑正文可入库 | 编辑后发送仍入库并标记运营已编辑 | PENDING |  |  |  |
| 16-A3 | Yes | 空回答说明显示 | 可入库、显示 `—`，总数与行数一致 | PENDING |  |  |  |
| 16-A4 | Yes | topic 检索 | 仅显示所选主题，数量更新，清除后恢复 | PENDING |  |  |  |
| 16-A5 | Yes | 样例不带出事实 | 开通道 A 后历史数字不泄漏，除非当前事实集也有 | PENDING |  |  |  |
| 16-A6 | Yes | 待转事实队列 | 预填草稿不自动建规则；保存后变 ACTIVE 并出队 | PENDING |  |  |  |
| 16-A7 | Yes | ES 归档失败降级 | 训练/发送成功，仅归档 warn/状态失败或部分成功 | PENDING |  |  |  |
| 16-A8 | Yes | ES 不可用列表降级 | 页面提示不可用，接口 503 指定错误，其他页面不崩 | PENDING |  |  |  |
| 16-A9 | Yes | 归档幂等 | 重复触发不新增第二份文档，`total` 不增加 | PENDING |  |  |  |

## Human Sign-off

- Decision: PENDING
- Boundary: `8fa4f6ca1fde33c471662acb49f53838386177a0`
- Reporter: user
- Timestamp: PENDING
- Note: Include verdict/evidence for every mandatory item, then explicitly accept this code boundary. Flyway IT was not run under the user-authorized epoch-5 exception.
