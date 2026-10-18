# Manual Acceptance — discovery-samples-master

## Epoch 1 — 2026-10-19T01:31:15+08:00

- Reviewed code boundary: `3efc192faf043badc1ef27bef124a71d3120c4c5..eb6080147141bc3d7dd00054705ba8973ea2b270`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 留存到离线读取：按总计划 A-1 的 list、replay 和两份 JSON 操作 | 正 executionId；`source=OPENALEX`；`stage=EXTRACTION`；正文 SHA 通过；403 显示 `NO_BODY`；无准入/收录人数 | PENDING | N/A | N/A | N/A |
| A-2 | Yes | 业务保护与资源边界：按总计划 A-2 与子计划 01 A-2、02 A-2/A-3 检查 caps、disabled、unwritable、cache、pause、apollo 验收产物 | 业务对照 `equal=true`；cache 新下载=0；不可写仅 `IO_ERROR`；Apollo 登记失败仍失败；采样上限断言 true | PENDING | N/A | N/A | N/A |
| A-3 | Yes | 不夸大优化效果：按总计划 A-3 对同代码/同输入运行 A/B compare，未提供人工真值 | 输入相同；绑定变化=0；`accuracy=null`；403 不算修复；不填写成功率提升 | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: `eb6080147141bc3d7dd00054705ba8973ea2b270`
- Governing master identity: sha256:`b0eb3402f6144f13f4e99de5082ca43eaa27136b2bc96e8f7cab3d676725ed7f` (recorded commit `cd6189360196700cf65c5e21ab3512ddedb98abc`)
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
