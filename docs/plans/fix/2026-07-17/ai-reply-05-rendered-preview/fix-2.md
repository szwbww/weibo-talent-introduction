# Verification Blocked

**Round:** 2/3  
**Failing:** `src/main/resources/static/app.js:9383-9389` — 采用草稿后的 raw 传递条件只比较 `editor.innerText`；富文本格式编辑不改变文本，仍会提交 raw 模板，`PendingMailOperationService` 随后以纯文本 HTML 重渲染并丢弃用户格式。  
**Planned fix:** 记录并比较采用时的 rendered HTML baseline，只有文本和 HTML 均未变才传 raw。  
**Blocked because:** 本轮仍有 1 个 P1；fix-1 也有 1 个 P1，P1 数未严格下降。

## Root cause analysis

原计划修正记录将“用户编辑”定义为不再使用陈旧 raw，但 fix-1 的实现把编辑判定缩窄为纯文本相等，遗漏了富文本编辑器的 HTML 维度。该遗漏发生在 Phase 5→6 跨计划接口，继续追加修复轮不符合收敛规则。

## Decomposition proposal

1. 子计划 A：定义 AI adopt 的 text/HTML baseline 与未编辑判定；范围仅 `src/main/resources/static/app.js`、`src/test/js/aiReplyLoadingFeedback.test.js`。
2. 子计划 B：验证 template raw 与最终 manual-rich 渲染的 HTML 传递；范围仅 `UnmatchedInboundMailController.kt`、`PendingMailOperationService.kt` 及其定向测试。
