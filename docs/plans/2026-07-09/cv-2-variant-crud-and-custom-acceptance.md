# cv-2-variant-crud-and-custom 人工验收记录

- 导出自: `docs/plans/2026-07-09/cv-2-variant-crud-and-custom.md` 的 `## 人工验收清单`（权威版，本文件为衍生物）
- 导出日期: 2026-07-09
- 验收人: ______
- 验收开始/结束日期: ______ / ______

---

### A-1: QA 规则变体保存与回显（覆盖需求第 1 条、I-1/I-3）
- 前置条件: 任一启用 QA 规则；本阶段无 UI，用 curl/Postman。
- 操作步骤: 1) PUT `/api/qa-rules/{id}`（现行管理端点）body 在原字段基础上加 `"variants": ["Alt body one ${senderName}", "Alt body two"]`；2) GET 详情；3) 再 PUT `"variants": []`；4) 再 GET。
- 预期结果: 步骤 2 返回 variants 恰为两条且顺序一致；步骤 4 返回 variants 为空数组；`SELECT count(*) FROM content_variant WHERE owner_type='QA_RULE' AND owner_id={id}` 依次为 2、0。

- [ ] 通过
- 实际结果/备注: ______

---

### A-2: 非法变体拒绝（覆盖 I-2）
- 前置条件: 同 A-1。
- 操作步骤: PUT variants 含 `"${不存在变量}"`；再 PUT variants 含与 replyBody 完全相同的文本。
- 预期结果: 两次均返回 4xx 与明确中文错误信息；GET 详情变体保持上一次合法值（未部分写入）。

- [ ] 通过
- 实际结果/备注: ______

---

### A-3: CUSTOM 类型隔离（覆盖需求第 3 条、I-4）
- 前置条件: POST `/api/reply-snippets` 建 `{"snippetType":"CUSTOM","content":"Custom paragraph.","isDefault":false,...}`。
- 操作步骤: 1) 再 POST 同 body 但 `"isDefault": true`；2) 打开人工回复界面（未匹配来信 → 人工回复），查看骨架与致谢语选项。
- 预期结果: 步骤 1 返回 4xx（CUSTOM 不可默认）；步骤 2 骨架的尊语/开场白/结束语与 ACK 下拉均不含 "Custom paragraph."。

- [ ] 通过
- 实际结果/备注: ______

---

### A-4: 片段变体进模板块即刻生效（覆盖 interaction point、CV-1 I-1）
- 前置条件: 某片段被模板内容块引用；PUT 该片段带 1 条变体。
- 操作步骤: 对 3 名不同专家手工发送该模板。
- 预期结果: 该段落出现主体与变体两种文案中的至少两种；同一专家重发恒同。

- [ ] 通过
- 实际结果/备注: ______

---

### A-5: 既有四类型回归（覆盖 must-NOT-change 第 1 条）
- 前置条件: 既有默认尊语/开场白/结束语片段不动。
- 操作步骤: 打开人工回复界面走一次完整组装（选 QA 规则 + ACK）并发送。
- 预期结果: 骨架取值、ACK 选项、发送成功与改动前一致；默认片段仍每类型唯一（设另一条为默认后旧默认自动取消）。

- [ ] 通过
- 实际结果/备注: ______

---

## 汇总

- 条目数: 5；通过: __；不通过: __
- 结论（全部通过方可视为验收完成；不通过项回溯 plan 或提 fix）: ______
