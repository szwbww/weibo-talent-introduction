---
id: K-workbench-config-propagates-to-final-reassembly
domain: frontend
created: 2026-08-05
last_used: 2026-08-05
hit_count: 0
source: create-p:trust-reply-configurable-workbench
severity: P1
---
经验：共享工作台新增事实矩阵或回复框架配置时，只改组件内 bootstrap/assemble 会让训练评估或正式发送重整合丢字段，最终使用 flat facts/default frame，结果与用户确认的预览不一致。
正确做法：同一 canonical 配置必须从共享组件 assembly response 进入训练评估 payload、LIVE adopt snapshot、未编辑人工发送 payload和对应 controller domain DTO；本地配置预览只能展示，采用按钮只认 identity 匹配的服务端 assembly。
关联：[[K-shared-workbench-fixed-mode-host-adapter]]、[[K-trust-reply-resolved-version-single-source]]、[[K-workbench-lock-replay-needs-dedicated-state-store]]。
