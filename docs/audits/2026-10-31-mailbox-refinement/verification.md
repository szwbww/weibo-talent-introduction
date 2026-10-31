# 收发信箱修复核验（2026-10-31）

范围：本轮确认的 11 项；main 本地修复，按用户要求提交本地代码；未发布，未操作线上邮件或授权。保留工作区原有的其他改动。

## 实现与证据

| 修复 | 代码依据 |
| --- | --- |
| 可写端补全称呼与签名 | `mailbox-chat.js` 的 `mc-complete-reply-identity`；`ReplyIdentityController.inspect` 校验 FULL；默认设置仍限 admin |
| 可读草稿沿用原邮件主题 | `MailReviewDraftRepository.targetSubject` 按 contact/account/target 查找；service create/save/submit 忽略调用者自定义主题；旧空主题兼容，已审核快照不改 |
| 保存/提交错误及状态 | `mailbox-readonly.js` 的 `reviewFailure` 区分操作，展示服务端原因；已保存正文提交失败不误报“保存失败” |
| 窗口焦点不重载 | `checkCurrentAccess` 仅查 `/access`；同权限保留正文/滚动位置；降权锁定编辑并关闭可写操作弹窗 |
| 可写显示邮箱 | 共享工作台与用户列表仅 FULL 显示邮箱；READ 隐藏 |
| 备注与资料可见性 | 权限守卫 READ_CONTACT 开放备注、所在地/建议时间 GET；编辑仍需 FULL；排期摘要允许授权读 |
| 管理端筛选角标 | `MailboxSuspensionService.pendingBadge` 独立返回 `pendingReviewDraftCount`，仅 SUBMITTED 且未删除；零隐藏；导航待处理数量不改变 |
| 待处理归属 | FULL 授权在已有专家事务锁内转移未处理来信；READ/撤销归还；V172 修复既有 FULL 授权历史待处理；已处理及模拟邮件不动 |
| 用户端摘要信息 | 会话投影补充状态/层级、待处理归属/挂起；列表和头部展示账号、主题、最后回复、收发数、标签、排期、备注、所在地/建议时间 |
| 可读审核草稿布局 | `mc-reader-review` 复用现有折叠卡片，默认展开；草稿选择/历史/新增归一行，主题只读，状态提示和底部操作分组；FULL 普通草稿保留原逻辑 |
| 管理端授权标签 | `mc-grant-badge` 使用 `width:max-content` 与 `align-self:flex-start`，恢复小标签，文案 `user1: 写` |

## 自动与人工验证

- 前端全部测试：`node --test src/test/js/*.test.js`，2015 项通过，0 失败；日志 `/tmp/mail-full-js3.log`。
- Kotlin 主代码及全部测试源码已编译；后端 129 项针对性测试全部通过（合并各类最终结果，非全量后端测试）。
  - `/tmp/mail-refine-kotlin4.log`：AuthInterceptorTest 19、MailboxConversationControllerTest 46、MailReviewDraftIT 19、MailboxReadAccessProjectionTest 8、ReplyIdentityControllerTest 4、ExpertMailPermissionGuardTest 9，合计 105 项通过。
  - `/tmp/mail-refine-kotlin6.log`：按项目的 `kotlin:test-compile@test-compile` 配置重新编译；ExpertMailReadAccessIT 16、InboundPendingOwnerIT 8，合计 24 项通过，BUILD SUCCESS。
  - 旧“FULL 授权保留 admin 历史待处理”断言已按本轮批准的新语义更新；仍验证物理重复收信不额外修改记录、授权/收信并发、降权回收、事务回滚、完整迁移与已有 FULL 回填。
  - 覆盖审核状态流、固定历史主题、旧空主题、审批后冻结、备注只读/可写边界与审核计数。
- 使用专用本地 MySQL 与 Testcontainers。未连接线上数据库。
- `git diff --check` 无空白错误。

## 页面核验

直接加载当前 `src/main/resources/static`，仅 API 使用本地测试数据；这些是实际前端源码的测试截图，不是线上已部署截图。桌面视口 1600×1000。

- `admin-badge.jpg`：授权标签约 60.6px，父容器 358px；待处理 5 与待审核 1 独立计数。
- `full-reply.jpg`：邮箱、可编辑备注、补全称呼与签名、原有三个工作区与默认展开人工回复。
- `read-submitted.jpg`：共享摘要/备注可见；原主题只读；已提交正文锁定，可取消提交。

数据库 V172 已加入源码，线上尚未执行。此次浏览器验证不发送邮件；发送链路沿用现有实现。
