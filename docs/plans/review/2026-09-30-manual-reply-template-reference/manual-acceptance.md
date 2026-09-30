# Manual Acceptance — manual-reply-template-reference

## Epoch 1 — 2026-09-30T05:24:10Z

- Reviewed code boundary: `a37efe970e4446242b121c5628db02daa631fc92..960643302da78f6223aefa702f95d379ae9ef066`
- Machine report epoch: 1
- Status: PENDING

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 冷进入、真实模板维护与搜索 | 邮箱冷进入可见引用模板；仅已启用模板；重开读到后台更新；无来信/待匹配无入口；引用不改模板。 | PENDING | N/A | N/A | N/A |
| A-2 | Yes | 空正文填入与主题选项 | 默认保留主题；勾选后精确替换主题；填入不发送。 | PENDING | N/A | N/A | N/A |
| A-3 | Yes | 追加、替换、编辑、草稿恢复与发送 | 保留原富文本并追加；切换可恢复；替换只留模板正文；测试收件箱收到一次正确邮件。 | PENDING | N/A | N/A | N/A |
| A-4 | Yes | 取消和失败零覆盖 | 取消、X、Esc、离线失败不改主题/正文；失败可重试；无发送或模板写入。 | PENDING | N/A | N/A | N/A |
| A-5 | Yes | 默认值、缺值、失效块与示例链接 | 默认值/跳过块提示正确；示例退订链接禁填；无法构造示例状态则标未执行。 | PENDING | N/A | N/A | N/A |
| A-6 | Yes | 快速选择、关闭与跨会话隔离 | 慢网下仅最新选择显示；关闭后旧结果不重现；跨专家草稿/预览隔离；无残留遮罩。 | PENDING | N/A | N/A | N/A |
| A-7 | Yes | 通用附件在引用前后保持 | 异步上传后引用或替换不丢、不重传附件；测试收件箱只收一份正确附件。 | PENDING | N/A | N/A | N/A |
| A-8 | Yes | 会议与材料索取回归 | 材料文字和会议卡保留；有 ICS 时禁替换仅可追加；移除日历附件后可替换；旧弹框仍可用。 | PENDING | N/A | N/A | N/A |
| A-9 | Yes | 可信回复证据与跟进锚点 | 追加保留 RAG 证据；替换清旧证据；跟进 anchor 保持；模板 refId 不作为发送证据。 | PENDING | N/A | N/A | N/A |
| A-10 | Yes | 回复账号不取联系人绑定账号 | 普通来信/跟进分别用真实回复账号和对应署名，不错误采用联系人绑定账号。 | PENDING | N/A | N/A | N/A |
| A-11 | Yes | 样式、长内容与键盘 | 宽窄屏布局、滚动、Tab/Enter/Esc、焦点恢复符合 S-1 至 S-4，原页面样式无变化。 | PENDING | N/A | N/A | N/A |
| A-12 | Yes | 模板特殊字符按文本处理 | 特殊字符按字面文本显示、无图片/脚本执行、换行和草稿恢复正确，模板本身未被改写。 | PENDING | N/A | N/A | N/A |

## Human Sign-off

- Decision: PENDING
- Boundary: `960643302da78f6223aefa702f95d379ae9ef066`
- Accepted governing master identity: `sha256 858646663e9fa61c83a7687218f7ebd0a2f5c83e44047e78e023275cc43c8dac` / recorded commit `5adacbbad366065c633e6c9380a25084aa19fcba`
- Retroactively authorized files accepted: `src/test/js/mailboxOutboundAttachments.test.js` (A1)
- Reporter: N/A
- Timestamp: N/A
- Note: N/A
