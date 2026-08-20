---
id: K-manual-reply-preflight-requires-adopt-context
domain: frontend
created: 2026-08-20
last_used: 2026-08-20
hit_count: 0
source: create-p:manual-send-safety-confirm
severity: P1
---

# 人工回复预检只在「采用过 AI 草稿」后才跑

`schedulePreflightCheck()`（`app.js:9451-9454`）第一件事就是：

```javascript
const adopt = aiReplyState.adoptContext;
if (!adopt || Number(adopt.recordId) !== Number(state.mailbox.detailContext?.id)) return;
```

`doPreflightCheck()`（`:9466-9540`）同样以 `adopt.draftId` 作为陈旧性守卫的身份基准
（`:9505-9512` 的 `stillSameDraft`）。

**结论：运营自己手写的回复（从未点过"采用到人工回复"）全程不跑预检**，
第一次得到内容安全反馈就是发送失败。任何"发送前就提示风险"的需求，
若指望预检面板承担，必须先意识到这条前置。

想放开成"编辑器有内容即预检"，绕不开重做那套以 `draftId` 为身份的陈旧性守卫
（见 [[K-ai-preflight-stale-response-draft-identity]]），是独立一刀，不要顺手塞进别的计划。

附带事实：预检结果容器 `#manualReplyPreflight` **不在 `index.html` 里**，
由 `app.js:9961` 动态生成（`class="ai-reply-feedback"`）。按 [[K-dom-stub-tests-hide-dangling-refs]]
的手法 grep `index.html` 会误判为悬空引用，需一并搜 `app.js` 的模板串。

关联：[[K-preview-mirrors-pipeline]]、[[K-manual-send-safety-gate-first-hit-only]]
