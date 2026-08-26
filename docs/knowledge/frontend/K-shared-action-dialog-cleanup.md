---
id: K-shared-action-dialog-cleanup
domain: frontend
created: 2026-07-13
last_used: 2026-08-21
hit_count: 5
source: create-p:material-reminder-batch-send
severity: P1
---
经验：`#actionDialog/#actionDialogForm` 是多个业务共用的确认弹窗。某个 opener 若给 form、cancel、select 增加监听器，或在异步加载时禁用 submit，关闭时未完整恢复，会让后续业务重复提交或永久无法确认。

正确做法：每个 opener 必须拥有成对的 setup/cleanup；cleanup 移除本次注册的全部监听器、使未完成异步响应失效、恢复共用 submit 的 `disabled=false`，然后关闭 dialog。异步模板/数据预览需用 request sequence 或等价 token 丢弃迟到响应，且只有当前选择的最新请求成功后才允许提交。

关联位置：`src/main/resources/static/index.html:#actionDialog`、`src/main/resources/static/app.js:openBatchTagMailDialog` 及其他复用 actionDialog 的 opener。
