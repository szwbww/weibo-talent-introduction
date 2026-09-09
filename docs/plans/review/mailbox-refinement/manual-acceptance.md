# Manual Acceptance — 00-mailbox-refinement-master

## Epoch 1 — 2026-09-09

- Reviewed code boundary: af25bf54df2bc70dd0fe9e3254b246a49395219c..9b6591c04545769d4ffad97dd285742958b3c8f3
- Machine report epoch: 1 (PASS)
- Status: PENDING

Checklist generated only from the master plan manual items (master A-1 + its referenced child checklists 01 A-1..A-5, 02 A-1..A-11, 03 A-1). Preconditions per master A-1: three machine gates passed; isolated acceptance environment with the 01 data matrix; business interfaces pointed at that environment; two test login users; serve the isolated production sources (src/main/resources/static at final_code_head) in a real browser at 1440x900 / 1920x1080 / 760 / 390px; record browser version, viewport, commit and screenshots per step.

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---|---|---|---|---|---|---|
| 01 A-1 | Yes | 排序与处理跨路径 (4-expert matrix A/B/C/D, size=2 pages, followed, pendingOnly, mark D's last pending resolved, re-request all) | 全部 D,A / B,C; 关注 B,D,A,C; 待处理 D,A; D处理后 全部 A,B,D,C; C仍最后 | PENDING |  |  |  |
| 01 A-2 | Yes | 真实筛选 (keyword=meeting-z9, recipientEmail alias, date+label correct vs wrong date, followed + non-matching q) | keyword/recipient+date+label 命中A; 错日期与不匹配q不命中; receivedCount不因过滤缩水 | PENDING |  |  |  |
| 01 A-3 | Yes | 主题历史兼容 (UTF-8 Q / Windows-1252 Q stored subjects; new mail via IMAP) | 列表/timeline/02人工回复可读 `Re: Remote advisory collaboration…`; 旧库subject不改; 新落库已解码; 无新增附件下载任务 | PENDING |  |  |  |
| 01 A-4 | Yes | 标签与来源回归 (equal numeric id inbound processing vs OUTBOUND mail_record; metadata-only attachment) | 标签仅真实processing卡片; 删除两入口同步; 发件tags=[]; 仅读资料元信息无内容下载; 原关注/处理/发送不变 | PENDING |  |  |  |
| 01 A-5 | Yes | 专家标签来源与批量读取 (20 same-level experts; A add/remove tags; B none; C missing profile; stub one-layer ES failure) | A.expertTags 无邮件标签; B=[]; C=null; 删除后summary同步; 同层1次/最多3层3次批量; 某层失败不破坏排序/分页/整页 | PENDING |  |  |  |
| 02 A-1 | Yes | 排序与tab (4-expert matrix followed) | 仅3 tab; 全部 D,A,B,C; 关注 B,D,A,C; 待处理 D,A; 处理D后 A,B,D,C 且草稿不清空; C仍最后 | PENDING |  |  |  |
| 02 A-2 | Yes | 筛选 (A正文meeting-z9 + 会议安排 label) | 无顶部重复筛选块、按钮只有⋯; popover白底430px两列、关键词跨两列; 应用匹配A角标3; 未应用不改结果; 重置保留tab/q; 非法日期提示不查询 | PENDING |  |  |  |
| 02 A-3 | Yes | 管理保存 (real-profile + missing-profile experts; simulated save failure) | 管理不在邮件区、480px居中两列、保存非整行; 取消状态层级不变; 保存后头部/详情一致; 标签即时提示; 部分失败不报全成功; 缺画像不可加tag | PENDING |  |  |  |
| 02 A-4 | Yes | 邮件翻译/标签 (english resolved/unresolved inbound + outbound) | 无原文/技术折叠; 译文原位、二次不请求; 已处理来信仍可加标签且刷新保留; 删custom跨入口同步; 发件无加标签/标记; 人工草稿不变 | PENDING |  |  |  |
| 02 A-5 | Yes | 首次进入 (≥60-message expert, very long last mail, cache cleared) | 最新窗口最后一封顶部≈8px、不跳编辑器底、不标记处理; 主题可读 | PENDING |  |  |  |
| 02 A-6 | Yes | 缓存与竞态 (A/B ≥60 mails; A network throttled; draft during load-older; switch experts; refresh; abort to B; scroll 0) | 同邮件同位置、草稿保留; 晚响应不混入/串信; 0位置恢复为0; 不自动拉完远端历史 | PENDING |  |  |  |
| 02 A-7 | Yes | 工作台与发送 (workbench generate+adopt, rich edit, new inbound refresh, target confirm, test send) | 工作台默认关闭/人工默认打开; 采用不自动发送; 新来信不覆盖编辑; 目标确认准确; 测试邮箱收到最终正文; 原账号/QA审计存在 | PENDING |  |  |  |
| 02 A-8 | Yes | 全局/关注/资料 (direct mailbox entry; two users; 40 metadata-only materials) | 导航/登录/检查回复/批量发送/自动回复真实状态; 关注用户隔离; 共享资料组件; 查看无40份内容下载、仅所选文件获取 | PENDING |  |  |  |
| 02 A-9 | Yes | 任务钻取 (task with mail records) | 旧列表按任务过滤; 返回聊天字段唯一、账号齐全、无重复请求; 全局入口保留 | PENDING |  |  |  |
| 02 A-10 | Yes | 视觉逐项检查 (1440x900/1920x1080/760/390px, default zoom; S-6对照; hover/active/disabled/focus; popover/manage/translation/manual open; narrow scroll) | 桌面左栏306px/gap16px; ≤1100 275px/12px; 正文13px/1.85圆角11px; 管理480px白底保存高32px; 筛选430px不裁切; 窄屏无横向溢出; 管理始终不在消息区 | PENDING |  |  |  |
| 02 A-11 | Yes | 专家标签单行及悬停 (A 10 tags incl. long/mixed/quote/bracket; B none; C read failure; A pending mail + mail tag) | 原待专家回复区=专家标签; 收发数完整、标签单行20px省略不增高; hover完整; 键盘可读; B无占位、C "标签暂不可用"; A卡片及姓名行无待处理标记但仍待处理tab/优先; 邮件标签不出现于此; 三tab及排序不变 | PENDING |  |  |  |
| 03 A-1 | Yes | 缓存版本和总体验收 (open old page then refresh to 01/02/03 artifacts; DevTools no "Disable cache") | 7资源均请求 v=20260909-mailbox-refinement 且200或正确304; 无旧JS错误; 布局符合02; 原按钮与共享组件保留 | PENDING |  |  |  |
| Master A-1 | Yes | 整体交付汇总 (all child A-n executed and collected; R1..R8 + M1..M6 each have a pass record) | R1..R8及M1..M6全部有通过记录; 无"待专家回复"tab; 无可见"更多"文字; 全部待处理组在首; 生产发送/资料/关注回归有实际证据; 截图/浏览器版本/视口/commit已记录 | PENDING |  |  |  |

## Human Sign-off
- Decision: PENDING
- Boundary: 9b6591c04545769d4ffad97dd285742958b3c8f3 (af25bf54df2bc70dd0fe9e3254b246a49395219c..9b6591c04545769d4ffad97dd285742958b3c8f3)
- Reporter: <human identity or user>
- Timestamp: <value>
- Note: master identity CONSISTENT (governing = invoked = recorded 351d69a/ed5a0f14…); no retroactively authorized files. Machine PASS is not final acceptance; every mandatory item above must have a human result and evidence, and the human must sign off this boundary.
