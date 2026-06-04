# 专家联系页和待处理邮件页重构执行计划总览

> 执行对象：交给其他 agent 分阶段开发。
>
> 总方案来源：`docs/expert-contact-and-pending-mail-refactor-plan.md`。
>
> 重要约束：SQL 改动不要输出局部 SQL，必须输出完整 SQL 文件。实现 agent 修改迁移时，必须给出完整 `src/main/resources/db/migration/Vxx__*.sql` 文件内容。

## 0. 执行顺序

必须按下面顺序执行。前一阶段没有通过验证，不要进入下一阶段。

| 顺序 | 计划文件 | 目标 |
| --- | --- | --- |
| 1 | `docs/plans/20260604-expert-contact-pending-mail-refactor-01-data-and-audit.md` | 新增运营状态字段、操作日志表、日志查询能力 |
| 2 | `docs/plans/20260604-expert-contact-pending-mail-refactor-02-status-and-level-services.md` | 统一专家状态和层级变更服务/API，并给现有切换自动/人工补日志 |
| 3 | `docs/plans/20260604-expert-contact-pending-mail-refactor-03-auto-promotion-rules.md` | 自动收信流程增加回复次数 > 2 和附件材料自动入有效层 |
| 4 | `docs/plans/20260604-expert-contact-pending-mail-refactor-04-document-browser.md` | 专家上传资料文件浏览、下载、在线预览 |
| 5 | `docs/plans/20260604-expert-contact-pending-mail-refactor-05-pending-mail-operations.md` | 待处理邮件页增加状态/层级变更、QA 回复、富文本人工回复、处理日志 |
| 6 | `docs/plans/20260604-expert-contact-pending-mail-refactor-06-frontend-refactor.md` | 专家联系页和待处理邮件页前端交互收口 |
| 7 | `docs/plans/20260604-expert-contact-pending-mail-refactor-07-verification.md` | 全链路复验、回归测试、人工验收清单 |

## 1. 业务要求映射

### 专家联系页面

| 用户要求 | 落地阶段 |
| --- | --- |
| 原始、筛选、有效三层保留 | Phase 2、Phase 6 |
| 自动回复/人工回复切换按钮保留 | Phase 2、Phase 6 |
| 状态精简为未联系、已联系、已回复、已回复材料、已邀约、已完成 | Phase 1、Phase 2、Phase 6 |
| 增加手动变更专家状态下拉框 | Phase 2、Phase 6 |
| 回复次数超过 2 次自动进有效层 | Phase 3 |
| 发送材料自动进有效层 | Phase 3 |
| 自动获取邮件附件并按专家存储 | 现有能力 + Phase 3 校正 + Phase 4 展示 |
| 文件浏览、下载、在线浏览 | Phase 4、Phase 6 |

### 待处理邮件页面

| 用户要求 | 落地阶段 |
| --- | --- |
| 点击查看未识别邮件内容 | Phase 5、Phase 6 |
| 手动变更专家状态下拉框 | Phase 5、Phase 6 |
| 变更专家层级下拉框 | Phase 5、Phase 6 |
| QA 邮件回复下拉框 | Phase 5、Phase 6 |
| 人工回复邮件富文本框并发送 | Phase 5、Phase 6 |
| 保留标记已处理，处理后列表不显示 | Phase 5、Phase 6 |
| 所有处理记录写日志，并可查询 | Phase 1、Phase 5、Phase 6 |

## 2. 当前代码关键入口

实现前必须先重读这些文件，不要只按计划盲改：

- `docs/design.md`
- `CLAUDE.md`
- `docs/expert-contact-and-pending-mail-refactor-plan.md`
- `src/main/resources/static/app.js`
- `src/main/resources/static/index.html`
- `src/main/resources/static/styles.css`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/domain/ExpertContact.kt`
- `src/main/kotlin/com/weibo/talentintroduction/common/domain/ConversationStatus.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/service/ExpertContactManagementService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/campaign/controller/ExpertContactManagementController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/AutoMailReplyService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/ManualExpertMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/UnmatchedInboundMailService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/controller/UnmatchedInboundMailController.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/service/MailAttachmentService.kt`
- `src/main/kotlin/com/weibo/talentintroduction/mail/domain/MailAttachment.kt`
- `src/main/kotlin/com/weibo/talentintroduction/document/domain/ExpertDocument.kt`
- `src/main/resources/db/migration/`

## 3. 统一口径

### 3.1 专家层级

保留三层，不改名：

| 页面文案 | 系统值 |
| --- | --- |
| 原始 | `RAW` |
| 筛选 | `CANDIDATE` |
| 有效 | `APPLICATION` |

### 3.2 运营状态

新增运营视角状态，不要直接删减 `ConversationStatus`：

| 页面文案 | 系统值 |
| --- | --- |
| 未联系 | `NOT_CONTACTED` |
| 已联系 | `CONTACTED` |
| 已回复 | `REPLIED` |
| 已回复材料 | `MATERIALS_RECEIVED` |
| 已邀约 | `INVITED` |
| 已完成 | `COMPLETED` |

原因：`current_status` 仍被自动回复、会议排期、材料处理内部流程使用；页面需要的是少量可筛选、可人工标注的运营状态。

### 3.3 操作日志

所有人工操作必须由后端服务写 `operator_action_log`。前端日志只用于展示，不作为审计来源。

必须记录：

- 变更专家状态。
- 变更专家层级。
- 切换自动/人工回复。
- 绑定待处理邮件。
- 发送 QA 邮件。
- 发送人工富文本邮件。
- 标记待处理邮件已处理。

### 3.4 SQL 输出

任何 SQL 迁移都必须是完整文件，不允许给片段。

## 4. 验证要求

每个阶段完成后至少执行：

```bash
node --check src/main/resources/static/app.js
```

后端阶段完成后执行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
```

如果 Maven 因 githook 写 `.git/hooks` 失败，需要记录真实错误；不要把这种环境问题误判为业务代码失败。

## 5. 禁止事项

- 不要删除原始/筛选/有效三层入口。
- 不要移除自动回复/人工回复切换按钮。
- 不要把 `current_status` 直接粗暴压缩成 6 个值。
- 不要绕过 `ConversationStateService.transition(...)` 直接改 `currentStatus`。
- 不要让前端自己伪造操作日志。
- 不要在下载/预览附件时直接返回任意路径文件。
- 不要在发送手动邮件后自动标记已处理，除非页面上明确用户点击了标记已处理。
