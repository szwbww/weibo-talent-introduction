# Manual Acceptance — draft-review-master

## Epoch 2 — 2026-10-30T21:17:52+08:00

- Reviewed code boundary: b15bb069f43f89c45ee6859dba04a0baff5d774d..60142d42148b9ab94a358249ca784bb8da1f48cd
- Governing master: docs/plans/2026-10-29/draft-review-master.md; commit32407a46d5b05971fc913cda1802b6ba002e8dc7; sha256:a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b
- Master identity state: AMENDMENT_RECORDED; A1–A18 exact approvals and individual file adjudications are preserved in the fast-p ledger and machine-verification.md.
- Machine report epoch: 2 — machine-verification.md, complete independent DraftReviewAggregateEpoch2 report
- Machine result: PASS; convergence PROGRESSING; V-1 RESOLVED
- Status: PENDING
- Human results: none supplied. Browser screenshots, HTTP/SMTP diagnostics and tests are machine evidence, not human acceptance.

按各来源计划的 A-n 原文前置条件和步骤执行；下表仅作统一登记，不替代原步骤、不新增产品要求。使用已准备的测试环境、开启认证、两个独立登录环境及测试 SMTP 收件箱；不得使用生产收件人。需要桌面1440/手机390的项目按来源分别执行。本清单不授权部署、推送、合并或额外修复。

所有人工作答应包含条目 ID、PASS/FAIL、观察或证据；尚未执行维持 PENDING。只追加人类明确提供的结果，不由代理推断。任一必选项目未完成即不能签收。

### Master scenarios

来源：[总计划人工验收清单](../../2026-10-29/draft-review-master.md)，A-1/A-2，原文101–118行。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| MASTER-A-1 | YES | 两端完整闭环：两草稿、保存提交、评论退回重提、批准、新来信确认、最后手动发送 | 草稿互不覆盖；完整内容/状态历史；批准和确认不发信；最后显式点击仅1封；user1不能改已审正文 | PENDING | — | — | — |
| MASTER-A-2 | YES | 保持项与 UI 回归：原管理员正文/附件、普通发送、受限用户和撤权、1440/390布局 | 原稿附件保留；普通发送无审批；user1仅邮箱和姓名、无附件；撤权清空；无草稿列表弹框、单编辑框、正文下小翻译、白色确认框 | PENDING | — | — | — |

### 01 — workflow

来源：[01人工验收原文](../../2026-10-29/draft-review-01-workflow.md)，A-1…A-4，176–207行。可使用接口客户端，cookie不得写入证据文档。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 01-A-1 | YES | 权限与多草稿：授权甲/未授权乙、不同clientRequestId、未提交admin读取、reader发送/附件 | 甲两个不同id；乙403；admin未提交详情404；user1发送与附件403 | PENDING | — | — | — |
| 01-A-2 | YES | 状态、完整历史与并发：旧version、提交撤回退回重提批准、批准后四种reader写 | 旧version409；状态依原计划；历史含各版；已审reader保存/撤回/删除/评论全拒绝；无新发件 | PENDING | — | — | — |
| 01-A-3 | YES | 双方评论、软删、撤权后再读历史/评论 | 普通列表不含已删；历史保留评论和删除；撤权后403且无正文 | PENDING | — | — | — |
| 01-A-4 | YES | 批准时真实新对话：取得token后收新来信，用旧token批准再刷新重试 | 旧token409 REVIEW_CONTEXT_CHANGED；刷新后APPROVED；批准不新增发送邮件 | PENDING | — | — | — |

### 02 — query

来源：[02人工验收原文](../../2026-10-29/draft-review-02-query.md)，A-1/A-2，87–102行。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 02-A-1 | YES | 三份草稿两位专家，size1两页、普通列表、reader待审参数 | 待审3/专家2/每人2和1；两页不重复；普通列表不丢丙；reader待审参数403 | PENDING | — | — | — |
| 02-A-2 | YES | 审批/退回和账号范围变化，关闭自动发信账号仍读取，比较旧邮件计数 | 待审3→2→1；甲最终离开待审；disabled真实账号可读；reader新增计数0；旧邮件计数不因审核变化 | PENDING | — | — | — |

### 03 — send

来源：[03人工验收原文](../../2026-10-29/draft-review-03-send.md)，A-1…A-3，115–140行。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 03-A-1 | YES | 批准不发、显式发送及重复send、reader访问同接口 | 批准0封；首次显式发送1封；重复仍1封；reader403；APPROVED/SENT | PENDING | — | — | — |
| 03-A-2 | YES | 新来信后无确认发送、只确认、再来信后用旧确认、刷新再确认并手动发送 | 无确认409；只确认0封；旧确认409；新确认后显式发送1封 | PENDING | — | — | — |
| 03-A-3 | YES | 原普通回复/附件/会议及安全提醒；审核发送安全提醒；测试SMTP mock未知结果后重试 | 普通回复无审批且附件/会议正常；安全提醒保留；UNKNOWN显示待确认且重复不增SMTP | PENDING | — | — | — |

### 04 — reader

来源：[04人工验收原文](../../2026-10-29/draft-review-04-reader.md)，A-1…A-4，170–201行。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 04-A-1 | YES | 新建A/B、粗体列表保存、历史切换及刷新 | 不同编号；A格式保持；单编辑框；历史默认折叠；无发送/附件；专家仅姓名 | PENDING | — | — | — |
| 04-A-2 | YES | 提交撤回重提、双方评论、退回改稿、批准后检查写入口和历史 | 状态/退回原因正确；评论和各版本可见；已审只读且无编辑/删除/撤回/评论 | PENDING | — | — | — |
| 04-A-3 | YES | 离线切专家/退出、恢复保存、延迟甲响应后切乙、撤权刷新 | 失败留甲保留输入；恢复可切；迟到正文不进乙；撤权后邮件/草稿/评论历史清空 | PENDING | — | — | — |
| 04-A-4 | YES | 删除后历史及1440/390富文本、按钮、评论样式 | 已删各版本仍可读且不能恢复编辑；编辑器100–240px、#dce4ef边框/7px圆角；无横溢；手机仅邮箱tab、按钮可点 | PENDING | — | — | — |

### 05 — admin

来源：[05人工验收原文](../../2026-10-29/draft-review-05-admin.md)，A-1…A-5，210–250行。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| 05-A-1 | YES | 顶部待审核3筛甲乙、切专家、点原草稿chip | 待审仅甲乙、counts2/1；真实右侧对话；无草稿弹框；原chip仍是admin草稿 | PENDING | — | — | — |
| 05-A-2 | YES | 原管理员正文/附件→用户待审→人工回复→批准，未点发送查记录 | 始终单编辑框；未审只读无附件/发送；原正文附件保留；批准后可显式发但不自动新增发件 | PENDING | — | — | — |
| 05-A-3 | YES | 更新确认取消/再次发送/只确认/再点击发送/确认后又有来信 | 取消后仍需确认；只确认0封；再点击才1封；后续来信重新确认；无模拟v2/v3 | PENDING | — | — | — |
| 05-A-4 | YES | inline翻译/评论/历史，慢网甲翻译后切乙，390确认框 | 小文字翻译在正文下且不改源；各版/评论完整；迟到译文不串乙；白色框、按钮可触达、ESC取消 | PENDING | — | — | — |
| 05-A-5 | YES | 两端完整审核发送链、普通回复、审批最后待审后的右侧保持 | 仅最后显式发送1封；普通无需审核；reader限制不变；左计数减而右侧保留已审可发送、不跳专家 | PENDING | — | — | — |

## Human Sign-off

- Decision: PENDING
- Boundary: 60142d42148b9ab94a358249ca784bb8da1f48cd
- Accepted master identity: PENDING — governing sha256:a1a5c2473521af76e41ed4aa30d726cf6a93d1001fc9cc3248a21b8224854a1b, commit32407a46d5b05971fc913cda1802b6ba002e8dc7
- Reporter: —
- Timestamp: —
- Note: 所有必选条目通过后，由人类明确签收上述代码和总计划身份。当前没有任何人工作答或签收；不生成 final-decision.md，不执行集成或部署。
