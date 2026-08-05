---
id: K-selectable-reply-frame-server-resolved-snapshot
domain: llm
created: 2026-08-05
last_used: 2026-08-05
hit_count: 0
source: create-p:trust-reply-configurable-workbench
severity: P1
---
经验：回复框架可选后，若客户端提交片段正文或最终发送时只回传默认 frame，工作台预览与服务端重整合会形成双事实源；片段编辑/禁用也无法可靠判 stale。
正确做法：客户端只传各 slot 的 snippet ID；服务端严格校验 type/enabled/content，fresh resolve 正文，并以 slot/ID/updatedAt/content hash 生成独立 frame version。整体 selection 缺失表示兼容默认，显式全 null 表示不使用；frame stale 只阻止 assembly、保留已验证 locked answers，不改变 evidence/versionId。
关联：[[K-manual-frame-three-consumers]]、[[K-locked-item-assembly-list-not-set]]、[[K-ai-reply-evidence-version-deterministic]]。
