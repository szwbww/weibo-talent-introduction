# Orca 专家邮件回复顾问 Bot 设计方案

**日期**：2026-11-25  
**范围**：项目内 Skill 资产（`.agents/skills/qingfei-expert-reply/`）+ 后台邮箱只读/草稿安全写入脚本 + Orca 左上角 `Bots` 桥接配置。

---

## 1. 目标与边界

将上一轮会话（「分析 Fabiola 邮件回复策略」）训练确认的业务 QA、阶段判断 SOP、纠偏红线，与本轮新增的交互规范沉淀为可在 Orca 中长期复用的专家邮件回复 Bot：

1. **先摘要后拟稿**：收到专家姓名/邮箱时，自动拉取线上完整邮件往来与已发附件记录，**先输出最新未回复邮件的中文提炼含义**及当前沟通阶段，再按已确认 QA 拟出英文回复草稿。
2. **人工中文优先 + 自动沉淀提示**：当用户输入中文回复或修改意图时，**严格优先按用户的中文原意润色成地道英文邮件**，并在草稿后单独列出本次人工回复中有哪些新事实/新规则可沉淀到 QA 或 Skill，经确认后写入。
3. **写回系统草稿需人工确认**：仅在用户明确发出确认指令后，才调用后台 `/api/mail/mailbox/drafts/target` 将草稿写入收发件箱对应会话草稿；保留已有附件与会议上下文，遵循 `expectedVersion` 乐观锁。
4. **绝不自动发信**：工具层不封装任何发送端点，最终外发统一由人工在收发件箱页面检查后点击发送。

---

## 2. 架构与目录划分（单源真值）

Orca 左上角 `Bots` 运行目录位于 `~/.orca/bots/<bot-dir>/`，而项目仓库位于 `/Users/lukai/IdeaProjects/weibo-talent-introduction`。为避免双份规则漂移，采用**项目内唯一真值 + Bot `SOUL.md` 轻量桥接**：

- **项目内核心资产**：`.agents/skills/qingfei-expert-reply/`
  - `SKILL.md`：角色定义、四步交互状态机、格式铁律（纯 URL 单独成行、答疑结尾不索取 CV、沿用原主题与发件账号署名、未知项先向用户确认）。
  - `references/confirmed-email-qa.md`：完整继承上一轮会话确认的 **30 条近期人工邮件 QA**（第 30 条升级为最新官方政策文件统一口径）、公司注册全称与注册地（`Jiangsu Qingfei Talent Technology Co., Ltd.`，江苏省南京市）、边界区分与特例处理规则；同时在 `docs/qa提炼-完整版.md` 顶部标注关联入口。
  - `references/examples-and-pitfalls.md`：阶段回复范例（首回简短正文配 `Qiming_Program_Information.pdf`、答疑、跟进、代理沟通等）及 Fabiola / Amir 纠偏正反例。
  - `scripts/mailbox_bot.py`：纯 Python 标准库实现的后台 HTTP 客户端（支持 `fetch-thread` 与 `save-draft` 两个子命令），读取本地未入库的 `.env.mailbot` 凭据。
  - `scripts/test_mailbox_bot.py`：针对纯文本转 HTML、草稿目标推导、乐观锁 `expectedVersion` / `reopen` 与 `context` 合并保留逻辑的单元测试。
- **Orca 左上角 `Bots` 桥接模板**：`references/orca-bot-soul-template.md`
  - 新建 Orca Bot 后写入其 `~/.orca/bots/<bot>/SOUL.md`，强制每次对话读取项目内 `SKILL.md` 与 `confirmed-email-qa.md` 并调用 `mailbox_bot.py`，沉淀更新也直接写回项目内同一文件。

---

## 3. 后台接口契约与安全护栏

1. **会话鉴权**：调用 `POST /api/auth/login` 获取 `JSESSIONID`（会话 Cookie 仅在内存或本机 `0600` 临时文件中短期复用，凭据来自 `.env.mailbot` 并加入 `.gitignore`）。
2. **只读拉取（`fetch-thread`）**：
   - `GET /api/mail/mailbox/conversations?q=<email_or_name>` 精确匹配目标 `contactId`、`accountCodes`、`latestInbound`（含真实 `processingId` 与 `accountCode`）。
   - `GET /api/mail/mailbox/conversations/{contactId}/messages?limit=50` 获取按时间排序的收发正文、清洗后正文（`cleanedBody`）与已发附件（`outboundAttachments` / `firstAttachmentNames`）。
   - `GET /api/mail/mailbox/drafts/target` 读取当前收发件箱草稿状态（`id`、`version`、`state`、已有 `subject`/`text` 及附件数量）。
3. **安全写草稿（`save-draft`）**：
   - 仅在用户明确确认后执行。
   - 目标推导：存在未回复来信（`latestInbound`）时写 `kind=INBOUND&processingId=<id>&accountScope=<accountCode>`；纯跟进无来信锚点时写 `kind=OUTBOUND&processingId=0&accountScope=<accountCode>`。
   - 先 `GET /api/mail/mailbox/drafts/target` 读取最新快照：
     - 新草稿：`expectedVersion=0, reopen=false`；
     - 已有 `ACTIVE` 草稿：`expectedVersion=detail.version, reopen=false`，原样继承 `context` 中的 `outboundAttachmentDraft`、`meeting`、`meetingAccountCode`、`recipientEmail`、`followUpAnchorMailRecordId`（清空旧 `qa` 绑定，避免人工富文本草稿触发 QA 指纹冲突）；
     - 已有 `SENT`/`DISCARDED` 草稿：`expectedVersion=detail.version, reopen=true`。
   - 正文按 `MailContentService` 约定同时生成规范化 `text` 与 `<p>...<br>...</p>` 段落 `html`（独立行纯 URL 自动包裹同文本 `<a href="...">`），确保收发件箱富文本编辑器打开即见完整段落。
   - 遇到 `409`（版本冲突或草稿已入发送队列）立即报错退出，绝不强制重试覆盖。
