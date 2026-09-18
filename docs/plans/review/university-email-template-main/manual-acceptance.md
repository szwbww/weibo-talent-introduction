# Manual Acceptance — 00-university-email-template-main

Checklist generated from the master plan's `## 人工验收清单` (A-1…A-4) and the two child plans' `## 人工验收清单`, which the master plan designates as the per-item acceptance authority. No optional product requirement was added. This file replaces the master plan's planned export `00-university-email-template-main-acceptance.md` (the review workflow allows only the files under `docs/plans/review/<master-slug>/`).

Human results are recorded only from human-originated reports; the agent never performs, simulates, or marks these items.

## Epoch 2 — 2026-09-18

- Reviewed code boundary: `7f7b3a821f09d4255dc735c1e7b96eacf9c32164..2541ef8f91411a086ca0cda30cf796ed3b155cc4`
- Machine report epoch: `docs/plans/review/university-email-template-main/machine-verification.md#epoch-2` (result `PASS`)
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| master A-1 | Yes | 两种模板与任务交集：同校甲有 `researchFields`、乙缺，类型均为 `UNKNOWN`；自建模板甲用 `${primaryResearchField}`、模板乙用 `${primaryResearchField|your research area}`，分别建任务选方向“有”/“无”，看预估与预览 | 第一任务只预估甲、主题显示 `Quantum Computing`；第二任务只预估乙、主题显示 `your research area`；两模板可编辑/预览/启停；未自动发送 | | | | |
| master A-2 | Yes | 未分类不混作未知：另建专家丙，同机构、缺研究方向且无 `expertClassification.type`；第二任务分别选“未知＋无”和“未分类＋无”看预估 | 步骤 1 命中乙不命中丙；步骤 2 命中丙不命中乙；不保存手动覆盖时重开任务仍是原“未知＋无” | | | | |
| master A-3 | Yes | 旧任务与 CRUD 回归：查看迁移前旧任务方向选项与预估；编辑新模板名称并禁用/启用；引用期间尝试删除；改绑任务后删除 | 旧任务方向为“不限”且原预估筛选不变；名称与启停回显正确;引用期间删除被明确拒绝且任务引用保留；解除引用后删除成功 | | | | |
| master A-4 | Yes | 旧变量与其他编辑器回归：打开旧企业模板预览记录 `institution` 等变量值；QA/回复片段编辑器保存无默认值 nullable 专家变量，再加默认值保存；返回模板预览 | 旧变量值前后一致；QA/片段裸变量仍提示非法，加非空默认值可保存；旧企业模板正文及任务绑定不变 | | | | |
| child1 A-1 | Yes | 模板新增与变量完整性：新建模板，打开主题与正文的插入变量，对照 `/api/qa/template-variables-meta` 全部 key，插入 `institution` 与 `primaryResearchField` 并保存 | 菜单含接口全部 key；保存后列表出现模板；重开保留原文与块顺序；可在介绍邮件任务模板下拉中选择 | | | | |
| child1 A-2 | Yes | 门禁变化与预览：对 `institution` 有值、`researchFields` 空的专家调 `/api/compose-templates/{id}/gate-fields` 并预览；把正文改为 `${primaryResearchField}` 后再查 gate-fields 与批量预估 | 改前 `requiredKeys=[institution]` 且研究方向用默认值；改后含 `primaryResearchField`、`esFields` 含 `researchFields`，预估排除该专家且发送前拦截 | | | | |
| child1 A-3 | Yes | CRUD 与引用保护：编辑名称并禁用/重开、启用、尝试删除、改绑任务后删除 | 名称与启停回显正确；绑定期间删除给出明确“模板被任务引用”错误且任务仍指原模板；解除引用后模板从列表消失 | | | | |
| child1 A-4 | Yes | 旧编辑器回归：QA 规则与回复片段各一条，插入/保存无默认值 nullable 变量，再加默认值保存，预览旧介绍模板 | QA/片段裸变量仍提示非法；加默认值可保存；旧模板默认值不再被旧 `required_keys` 误判为必填 | | | | |
| child2 A-1 | Yes | 保存和回显：编辑任务选“研究方向：无”保存、刷新重开、手动执行页载入、改为“有”看差异与预估 | 刷新后仍为“无”；手动页初值“无”；改“有”出现该字段差异；预估切到有方向人群且未修改原任务 | | | | |
| child2 A-2 | Yes | 有/无/不限与独立类型：甲缺方向且 `type=UNKNOWN`、乙方向空串且无分类字段、丙有方向且 `type=UNKNOWN`；类型选“未知”依次切换方向，再改“未分类”，再开模板门禁 `${primaryResearchField}` 无默认值模板看预估 | 步骤 1 分别计甲/丙/甲+丙；步骤 2 计乙；步骤 3 缺方向者为 0；`UNKNOWN` 与 `UNCLASSIFIED` 不混淆 | | | | |
| child2 A-3 | Yes | 旧任务与启动回归：打开迁移前任务、保持“不限”预估、手动页另选“无”但不保存、再打开原任务 | 旧任务为“不限”且预估无额外方向过滤；手动覆盖仅作用本次快照；原任务仍为“不限”，既有研发类型与模板选择不变 | | | | |

## Human Sign-off

- Decision: PENDING
- Boundary: `2541ef8f91411a086ca0cda30cf796ed3b155cc4`
- Reporter: (human)
- Timestamp: (pending)
- Note: (pending)
