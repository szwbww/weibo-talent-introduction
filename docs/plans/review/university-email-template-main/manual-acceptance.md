# Manual Acceptance — 00-university-email-template-main

Checklist generated only from the master plan's `## 人工验收清单` (A-1…A-4). This file replaces the master plan's planned export `00-university-email-template-main-acceptance.md` (the review workflow allows only the files under `docs/plans/review/<master-slug>/`).

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

## Human Sign-off

- Decision: PENDING
- Boundary: `2541ef8f91411a086ca0cda30cf796ed3b155cc4`
- Reporter: (human)
- Timestamp: (pending)
- Note: (pending)
