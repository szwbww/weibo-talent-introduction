# Manual Acceptance — discovery-recheck-master

## Epoch 2 — 2026-10-26T10:16:36.718962+08:00

- Reviewed code boundary: b161e87464d42b9fa2cd076ef9255b0e3b2b29ab..50f8484ed2c30d51adf781c8b07a16920feab75e
- Machine report: machine-verification.md, Epoch 2; PASS / PROGRESSING
- Machine evidence commit: 0921e44334555a3c1a25926933ca1b9d80145499
- Master plan: docs/plans/2026-10-24/discovery-recheck-master.md
- Governing master: commit da8940153f4ad22a5ac3a094f444a7ac4858f695; SHA256 5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99
- Status: PENDING

仅人类可提供以下结果；机器测试、浏览器截图及既有轮次签字不代替本轮人工结果。每项须提供 PASS/FAIL 及证据或说明。后续人类结果只追加，不覆盖本清单或既有结果。以下5项逐字来自总计划。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-M1 | YES | 数量故障消除；前置条件：01的15501测试输入或重新冻结的生产实际范围。；操作步骤：打开审核并刷新；切页、手工暂缓一人，再刷新。 | 单一后台扫描，四卡同轮完整值；失败显示明确码而非假0；人工操作仍有效。 | PENDING | — | — | — |
| A-M2 | YES | 统一入口与对照隔离；前置条件：两名待审核、一名人工通过、一名候选同步失败。；操作步骤：先默认仅对照；再对前两名应用；打开每人8步。 | 对照不改当前准入；应用只计真正提交；人工保护不覆盖；同步失败单独展示。 | PENDING | — | — | — |
| A-M3 | YES | 取消、旧史与原文保护；前置条件：运行中重审和已准入项，保护原文与旧无轨迹记录各一。；操作步骤：取消任务；看原准入；预览及批量取消保护；看旧核验详情。 | 已准入不回滚；原文保护/取消保护仍可用；旧详情写“历史未记录”，不补造8个成功时间。 | PENDING | — | — | — |
| A-M4 | YES | 历史收益可复核；前置条件：10生成的固定原文和待审核manifest。；操作步骤：两次REUSE对照，抽查新增绑定与资格证据原句；单独REFRESH一轮。 | 两次固定对照计数一致；绑定/新RAW/准入/同步分列；外部刷新不计代码收益；未自动触发发信。 | PENDING | — | — | — |
| A-M5 | YES | 真实页面与CSS；前置条件：08–09已联调，1280×720、640×900。；操作步骤：核对09的配置、记录、8步详情；操作免费Apollo配置后准备一轮REFRESH；查看全程请求类型。 | 白底不透、正文清晰、窄屏表格局部滚动；免费detail请求0；原Apollo设置可保存且新轮冻结值可见；原人工按钮可用。 | PENDING | — | — | — |

### Master coverage

- A-M1：需求1、I-M1/3。
- A-M2：需求2、I-M1/2、must-not-change保护/门槛/同步。
- A-M3：I-M2、保留原文/取消语义/旧功能。
- A-M4：需求3、I-M3/4、邮件不受影响。
- A-M5：I-M4、所有前端样式合同、Apollo设置保持。

## Human Sign-off — Epoch 2

- Decision: PENDING
- Boundary: 50f8484ed2c30d51adf781c8b07a16920feab75e
- Accepted master identity: PENDING — commit da8940153f4ad22a5ac3a094f444a7ac4858f695 / SHA256 5279a76a70aa84729ec849f881c97a31cd768e623b8da07c0c3e216a04a4cd99
- Retroactively authorized file accepted: PENDING — src/test/js/mailboxGroupPush.test.js (A14精确澄清晚于机械插入；原时序保留)
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING

全部必需项目PASS后，人类仍须明确接受上述candidate、治理总计划身份及A14文件，才可形成最终决定。完整Maven测试exit1/唯一O-10-1及24skip、历史PARTIAL、非本轮全源重解析及私人证据限制，均见Epoch2机器报告；不由签字模板推定接受。
