# Manual Acceptance — user1-mail-master

## Epoch 1 — 2026-10-28T15:03:32+08:00

- Reviewed code boundary: 128488eeac26781fbf7c6892ff9d3d4a3f5c7c10..7750a720b6fa276eb6e79ee468e40b7be98c7fdc
- Governing master: docs/plans/2026-10-27/user1-mail-master.md (sha256 b4b07118053d570c3987f62602aff80df554a7f6700de8e0ede74a591bfae69a; commit 258f435c8faefb15c187c6cd34669ac0ad92a717)
- Machine report epoch: 1, machine-verification.md
- Machine result: PASS, with explicit recorded baseline failures
- Status: PENDING

仅主计划 A-1～A-4 与同一代码边界的人类签署构成本轮人工门禁。下列项目未由代理执行；报告中的子计划人工项用于溯源，不新增门禁。

| ID | Mandatory | Check | Expected | Human result | Evidence/note | Reporter | Timestamp |
|---|---:|---|---|---|---|---|---|
| A-1 | Yes | 从零授权到可读 | 仅“收发件箱”业务 tab；初始“暂无已授权的专家邮件”；授权后仅 A，收/发正文可读，B 不出现。 | PENDING | — | — | — |
| A-2 | Yes | 越权、附件与撤销 | 未授权详情及所有受保护附件/写接口403；撤销后的新详情请求403；当前视图不再显示旧正文并提示“暂无权限查看该专家邮件”；列表刷新后无 A。 | PENDING | — | — | — |
| A-3 | Yes | 管理员与任务回归 | 原 tab 均可见；草稿保存、发送成功并产生原有记录；附件字节可下载；任务状态与本次改动前相同；历史邮件保留。 | PENDING | — | — | — |
| A-4 | Yes | 真实前端与身份切换 | 不串用前一账号正文/草稿；user1 手机下拉仅“收发件箱”；桌面左栏306px、气泡最终字号13px/行高1.55，390px上下排列；无待处理/草稿/材料/发送入口及额外业务请求。 | PENDING | — | — | — |

### 主计划原文：前置条件、操作、预期与覆盖

以下逐字保留主计划人工清单，未增加产品要求。

### A-1: 从零授权到可读
- 前置条件: 隔离测试环境、认证 true、完成五步；两名有收发历史的专家 A/B，user1 无 grant。
- 操作步骤: 1. user1/qfuser1 登录。2. 查看菜单和空列表。3. 另一个浏览器用 admin 为 A 授权。4. user1 刷新并打开 A。
- 预期结果: 仅“收发件箱”业务 tab；初始“暂无已授权的专家邮件”；授权后仅 A，收/发正文可读，B 不出现。
- 覆盖: I-1/I-2/I-5；需求全部基本闭环。

### A-2: 越权、附件与撤销
- 前置条件: A 已授权且存在来件、已发通用附件、日历附件；B 未授权；admin 能提供其真实接口地址。
- 操作步骤: 1. user1 直接 GET B/messages 和各附件地址。2. 用 user1 请求真实发送/草稿接口。3. admin 撤销 A。4. user1 刷新、翻更早页、切出再切回窗口。
- 预期结果: 未授权详情及所有受保护附件/写接口403；撤销后的新详情请求403；当前视图不再显示旧正文并提示“暂无权限查看该专家邮件”；列表刷新后无 A。
- 覆盖: I-1/I-2/I-3。

### A-3: 管理员与任务回归
- 前置条件: 测试管理员、测试收件箱、可发往受控邮箱的任务；同时存在 enabled=false 的历史邮箱邮件。
- 操作步骤: 1. admin 登录并逐项打开原 tab。2. 阅读/下载附件、编辑保存草稿并向受控邮箱发信。3. 运行一次既有收信/自动回复或受控任务。4. 查看停用邮箱历史会话。
- 预期结果: 原 tab 均可见；草稿保存、发送成功并产生原有记录；附件字节可下载；任务状态与本次改动前相同；历史邮件保留。
- 覆盖: I-4；必须不变的各项。

### A-4: 真实前端与身份切换
- 前置条件: 1440px 桌面、390px 手机；admin/user1 均可登录。
- 操作步骤: 1. admin 登出后 user1 登录，再反向切换。2. 刷新页面。3. 对照03样式契约核对列表和收/发气泡。4. 检查手机菜单及网络记录。
- 预期结果: 不串用前一账号正文/草稿；user1 手机下拉仅“收发件箱”；桌面左栏306px、气泡最终字号13px/行高1.55，390px上下排列；无待处理/草稿/材料/发送入口及额外业务请求。
- 覆盖: I-3/I-4/I-5；03 S-1/S-2/S-3，04 S-1。

## Human Sign-off

- Decision: PENDING
- Boundary: 7750a720b6fa276eb6e79ee468e40b7be98c7fdc
- Reporter: PENDING
- Timestamp: PENDING
- Note: PENDING

## Human Results — append only

尚未收到任何人类逐项结论、证据或签署。请按 A-1～A-4 回报 PASS/FAIL 及证据或备注；全部必需项通过后，明确签署接受上述代码边界。机器 PASS 不能代替人工结果。
