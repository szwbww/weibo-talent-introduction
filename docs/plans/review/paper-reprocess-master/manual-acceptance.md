# Manual Acceptance — paper-reprocess-master

## Epoch 3 — 2026-11-03T15:30:19+08:00

- Reviewed code boundary: 3a425461dbe6088e4d4dda1a53141416a392670c..7e131474f403d9b28178148909180ac8f1d7d1bb
- Governing master identity: sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e; AMENDMENT_RECORDED
- Machine report epoch: 3
- Machine result: FAIL
- Status: PENDING
- Source: docs/plans/2026-11-01/paper-reprocess-master.md — 人工验收清单 A-1–A-6

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | YES | 原按钮补绑；前置条件：测试环境已有一条明确漏绑且已建空名档案，来源原文可读。<br>操作步骤：1. 用原专家重审按钮固定此人；2. 确认ENFORCE；3. 查看第3步。 | 确认前业务不变；确认后姓名等于原文真值，显示证据页码/原句；随后按现行资格返回准入或待核验。 | PENDING | — | — | — |
| A-2 | YES | 漏建档与自动衔接；前置条件：测试源121篇，含一个符合现行资格的新作者；页面只选1篇且筛选命中1篇。<br>操作步骤：1. 使用新按钮正式执行；2. 等完成；3. 打开作者逐项详情。 | 范围121；新建该作者1档案、自动核验并准入1；不需二次点击；原源任务计数不变。 | PENDING | — | — | — |
| A-3 | YES | 只读对照与历史；前置条件：固定已存原文SHA、first快照、专家/准入版本、邮件条数、游标。<br>操作步骤：1. 执行仅解析对照；2. 查看新旧绑定差异；3. 核对快照。 | 实际新增/补齐/准入均0；外部请求0；仅新审计结果新增，其他快照及邮件/游标不变。 | PENDING | — | — | — |
| A-4 | YES | 保护、冲突与取消；前置条件：准备人工通过/已联系/不同非空姓名3条；另有本任务已准入1条。<br>操作步骤：1. 执行后查看保护；2. 取消任务；3. 刷新专家和旧记录。 | 3条不覆盖；已经准入1条保留；剩余取消单列；旧核验历史不改。 | PENDING | — | — | — |
| A-5 | YES | 恢复与样式；前置条件：1440px及390px浏览器，存在运行中的整批任务。<br>操作步骤：1. 关闭并重新进入；2. 看相同runId；3. 查看原文/8步详情；4. 对照05 CSS视觉值。 | 同任务继续；关闭不取消；入口14px标题/8px圆角、统计23px、手机3列，确认按钮始终可达；原单篇下载/保护/删除仍可用。 | PENDING | — | — | — |
| A-6 | YES | 对照结论不夸大；前置条件：06生成固定真值报告，包含仍缺源/歧义的项。<br>操作步骤：1. 逐条核对一条成功、一条缺源、一条冲突；2. 查看总体计数。 | 每条有bodyHash/recordId/目标标识与原因；报告分别写绑定、补齐、新建、准入；不将325直接填为修复/准入人数。 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: 7e131474f403d9b28178148909180ac8f1d7d1bb
- Governing master identity: sha256 85232e2db6f01c6c3411daae13fb8739e2f3cc47e1010bb6fd049aef0e197e2e
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING
