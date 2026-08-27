# Manual Acceptance — docs/plans/2026-08-26/00-execution-order.md

## Epoch 3 — 2026-08-27T03:11:21Z

- Reviewed code boundary: f2935072c819a9167e75220a6a959b0769462fde..f2d1acc61609fba5fa53d03c8d7b3368486482e4
- Machine report epoch: 3
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 01-A-1 | Yes | IP 零命中检索 | IP 条目为 PARTIAL，选中 `Pre-contract IP boundary`，推荐“按有据部分回答”。 | PENDING | — | — | — |
| 01-A-2 | Yes | 外发审计 | 外发 QA 使用审计出现该事实，顺序与正文一致。 | PENDING | — | — | — |
| 01-A-3 | Yes | 检索器关闭退化 | 关闭后结果回到基线，日志有 `outcome=DISABLED`。 | PENDING | — | — | — |
| 01-A-4 | Yes | 运营矩阵权威 | 手选事实独占、无 422，原文回答逐字取其 `answerBody`。 | PENDING | — | — | — |
| 01-A-5 | Yes | 自动发门禁 | IP 来信仍“转人工”，硬性闸门非空。 | PENDING | — | — | — |
| 01-A-6 | Yes | 检索确定性 | 五分钟内两次打开的已选事实集合和顺序相同，无来源变化提示。 | PENDING | — | — | — |
| 02-A-1 | Yes | 未识别诉求 | 状态为 PARTIAL 而非 GROUNDED，诊断含逐字引文。 | PENDING | — | — | — |
| 02-A-2 | Yes | 未识别诉求门禁 | 自动回复预判为“转人工”，硬性闸门非空。 | PENDING | — | — | — |
| 02-A-3 | Yes | 枚举器关闭退化 | 关闭 LLM 后恢复基线状态、无诊断，日志 `ASK_ENUM available=false`。 | PENDING | — | — | — |
| 02-A-4 | Yes | 自动路径未波及 | 默认 auto 枚举关闭，日志 `source=AUTO available=false`，判定保持基线。 | PENDING | — | — | — |
| 02-A-5 | Yes | 申请材料事实可选 | `Getting started materials` 出现在工作台已选事实。 | PENDING | — | — | — |
| 02-A-6 | Yes | coverage key 编辑 | 保存既有规则成功、旧顺序不变，新两项列在末尾。 | PENDING | — | — | — |
| 03-A-1 | Yes | 邻近事实去重 | 无据条目的事实名称无重复，整合正文中同一事实只出现一次。 | PENDING | — | — | — |
| 03-A-2 | Yes | CTA 唯一 | 索取材料/会议措辞仅一次，位于最后答复段。 | PENDING | — | — | — |
| 03-A-3 | Yes | 不暴露库存状态 | 无据段不以内部“无确认答案/记录”措辞开头。 | PENDING | — | — | — |
| 03-A-4 | Yes | 渲染预览 | 三页签存在，默认显示替换变量后的文本，raw 页签仍显示占位符。 | PENDING | — | — | — |
| 03-A-5 | Yes | 未整合预览 | 仅“配置预览”可用，其他两项置灰，内容保持基线。 | PENDING | — | — | — |
| 03-A-6 | Yes | 运营说明保护 | 一键预判后手写说明不被覆盖，机器代填标识不再出现。 | PENDING | — | — | — |
| 03-A-7 | Yes | 既有页面导航 | 两个顶部页签等宽且可用左右方向键切换，无控制台错误。 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: f2d1acc61609fba5fa53d03c8d7b3368486482e4
- Reporter: user
- Timestamp: —
- Note: —
