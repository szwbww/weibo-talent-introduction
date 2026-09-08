# 收发件箱聊天化 + 专家资料按需获取：发布与回退手册

> 域：`MailAttachmentStorageProperties`（收信元数据模式）/ `index.html` 资源注册 /
> `mail_attachment_transfer` / `expert_follow` / 附件 worker / 收发件箱聊天 host
> 计划：`docs/plans/2026-09-07/00-mailbox-materials-master.md`（G-1..G-6、I-1..I-3、S-1..S-5）
> 本手册只写发布与回退操作；**本计划阶段不执行任何生产发布**，只有后续明确发布授权 + 人工验收（A-1..A-8）通过后才能按此执行。

**执行边界（越界即停止，先记录现状再回交）**
- 未获得明确发布授权前，不得在生产执行第 2 节任何一步。
- 不得 DROP `mail_attachment_transfer` / `expert_follow` / 既有索引；不得删除已存附件文件。
- 不得回滚到不认识 null（`uid_validity` / `storage_path` / `file_size` 可空、`content=null` 元数据附件）的 01 之前二进制。
- 不在页面切换/回退中丢失已排队任务：队列继续或暂停必须由运维显式设置。
- 本仓库 cache-key 受 7 个 JS 测试双拼写钉死；改 `index.html` 资源引用必须 7 个键同值同 bump，
  否则浏览器混用新旧脚本（K-frontend-cache-key-triad）。

---

## 1. 背景速查

| 项 | 值 | 位置 |
|---|---|---|
| 收信元数据模式默认值 | **true**（子计划 11 起）；显式 `false` = 紧急回退旧收信 | `MailAttachmentStorageProperties.kt` `metadataOnly` |
| 配置键 | `talent-introduction.mail-attachment-storage.metadata-only`（缺省用代码默认 true） | `application.yml` 仅覆盖 `base-path` |
| 元数据模式语义 | 只登记附件名称/定位（`content=null` + 远端 `source`），不读附件二进制；正文有界 | `ImapMailReceiveService` / `MailAttachmentService:125` |
| 附件传输 | 行=远端 part（`mail_attachment_transfer`，V119）；全局活动≤2、账号活动≤1；租约15s/2s续租；单文件≤100MiB；总时限10min；watchdog 到期关连接 | V119 + `AttachmentTransferWorker` |
| 新表/迁移 | V118（附件可空）、V119（transfer）、V120（uid_validity）、V121（expert_follow）；**当前最高 V121** | `src/main/resources/db/migration/` |
| 前端资源 | 7 个同键资源：`styles.css`、`expert-materials.css`、`mailbox-chat.css`、`trust-reply-workbench.js`、`expert-materials.js`、`mailbox-chat.js`、`app.js`，键 `?v=20260907-material-chat`；CSS 序 styles→expert-materials→mailbox-chat，脚本序 workbench→expert-materials→mailbox-chat→app | `index.html` |
| 上线前置条件 | 01–10 全部独立验证通过；06 文件就绪/所有权适配已部署；05 DMARC consumer 已注册；worker 配置与存储权限已测 | 总计划顺序表 |

**为什么不能只发 HTML**：08–10 的新组件在 11 才统一注册并启用默认值。若把新 `index.html`
发给不含前置代码的版本，浏览器会拿到新脚本引用的缺失函数/布局（I-1 后果）。

---

## 2. 发布步骤（顺序执行；每步记录证据，不满足即停）

### 2.1 记录实际版本
```bash
git rev-parse HEAD                       # 记录实际部署 commit
git worktree list                        # 确认工作树/分支
ls src/main/resources/db/migration/ | sort -V | tail -1   # 预期 V121__create_expert_follow.sql
```
记录到发布日志：HEAD、分支、最高 Flyway 版本、前端资源键 `?v=20260907-material-chat`。
**没有版本证据不得声称"已上线"**（I-3）。

### 2.2 测试库备份与迁移验证
```bash
mysqldump -h <TEST_DB_HOST> -u <user> -p<talent_introduction_test> > pre_material_chat_$(date +%Y%m%d_%H%M%S).sql
# 用包含 V118..V121 的应用启动测试库（Flyway 自动执行），校验：
#   mail_attachment.file_size/storage_path 可空、file_name TEXT；mail_attachment_transfer 表、
#   inbound_mail_processing.uid_validity、expert_follow 表及索引存在；sql_mode 无异常。
```
预期：迁移成功、无占位符错误；对存量库无破坏性 SQL；历史附件行原样保留。

### 2.3 启动兼容后端
启动含 01–11 全部代码、nullable/readiness 兼容（`uid_validity` 默认 0=历史未知、
附件无 transfer 行按实际文件验证就绪、文件丢失且无可靠 source 显示 `SOURCE_UNAVAILABLE`）的后端。
先让**新后端**单独跑（不立刻恢复自动检查/发送），观察启动日志无 bean/迁移错误。

### 2.4 历史资料 dryRun 审计（必要时显式修复）
06 提供 `POST /api/expert-contacts/{id}/materials/reconcile`：仅修复已绑定历史
processing-owner 的登记，不下载。
```bash
# dryRun 先行：观察 candidateAttachmentIds/createdCount
curl -X POST "$BASE_URL/api/expert-contacts/<id>/materials/reconcile" -H 'Content-Type: application/json' -d '{"dryRun":true}'
# 只有审计确认缺失且来源可信时才 dryRun:false 显式修复；绝不自动全量跑
```
预期：明确修复清单与影响行数；不触发 IMAP 下载、不重发自动回复。

### 2.5 测试邮箱 19+20 附件与 1000 附件压测
投递两封 19/20 附件邮件与一封 1000 附件压力邮件到测试邮箱 → 点"检查回复"。
预期（G-2/A-3）：
- 39/1000 条目录完整；附件内容 FETCH = 0（看 IMAP/协议日志，不把 mock 预览当证据）；
- 材料列表每页 10 条、当前账号/阶段显示真实进行中账号；
- 打开页面/展开名称/标已处理不隐式下载；单信接收 60s、账号窗口 120s 有界。

### 2.6 开启 metadataOnly
11 起代码默认 `metadataOnly=true`。确认生效：
```bash
# 新收信后附件 transfer 行为 METADATA_ONLY；无本地文件写入（MailAttachmentService 仅在 legacy 写盘）
# 紧急回退口：配置 talent-introduction.mail-attachment-storage.metadata-only=false
```
预期：新收信只登记元数据；已请求项经 worker 才落盘。

### 2.7 加载版本化前端
发布带 7 个同键资源的 `index.html`（键 `?v=20260907-material-chat`），浏览器强刷。
核对 DevTools Network：7 个资源全部同键、200 无 304 混用；旧键 0 命中。

### 2.8 两入口/检查/分析/发送全链验收
| 步骤 | 操作 | 预期 |
|---|---|---|
| 两入口同状态 | 专家详情材料区与收发件箱"材料N"抽屉 | 同一组件/同一 API/store，选中状态一致（G-4） |
| 检查回复 | 点"检查回复" | 附件目录完整、FETCH=0、无隐式下载 |
| 分析 | 勾 2 件未就绪 →"获取所选并分析" | 仅这 2 件入队下载，全部就绪后才提取；未选件保持仅元数据 |
| 发送 | 可信工作台采用 → 测试 SMTP 人工回复 | 服务端校验通过、审计落库；无重复自动发信 |
| 回归 | 重新打开浏览器 | 无缺函数/布局错乱；旧 PDF 文件仍可读 |

### 2.9 生产证据记录（I-3）
- 真实版本：HEAD / 最高 Flyway / 前端键。
- 网络 FETCH：浏览器 Network 面板与 IMAP 协议日志中的附件内容 FETCH 计数。
- 截图：正式布局 + 两 host + 分析/回复默认状态，四视口各一张（S-1..S-5 对照 baseline）。
人工验收（A-1..A-8）全部完成前，不得称"整体完成"。

---

## 3. 回退步骤（按顺序；到哪一级取决于故障面）

回退优先级（I-2）：**先关新组件与 metadataOnly，再考虑资源/代码级；永不删附件索引/已存文件。**

### 3.0 先停新提交，再动配置
1. 停止新的材料投递与自动检查入口（暂停对应计划任务/入口按钮），避免回退期间新登记。
2. 等 worker 空闲：观察 `mail_attachment_transfer` 活动行（`state=DOWNLOADING`、`lease_until`），
   全局活动 <2、账号活动 <1；或由运维明确关闭活动连接（worker 租约 15s，续租失败/失去 token
   即停止流与文件写，watchdog 最迟在租约到期关连接）。**不要在 DOWNLOADING 中途杀进程**。

### 3.1 配置级回退（首选，分钟级）
- `talent-introduction.mail-attachment-storage.metadata-only=false` → 恢复旧收信行为
  （附件随收信读字节写盘）。**旧行为可能重新出现大附件拖慢接收**——先决定是否暂停自动检查，
  再重新启用轮询，不能静默恢复。
- 已存 transfer/元数据行与已下载文件保留，旧文件照常可读。

### 3.2 前端级回退
- 从 `index.html` 移除 `expert-materials.css/js`、`mailbox-chat.css/js` 四处注册，恢复旧 host；
  剩余资源（styles.css、trust-reply-workbench.js、app.js）缓存键必须同值同步 bump，
  旧键 0 命中（K-frontend-cache-key-triad；JS 测试双拼写钉死，回退也要同步测试）。
- 用户可见效果：聊天/材料抽屉消失，专家资料区回到旧列表；已排队/已下载数据不受影响。

### 3.3 后端代码回退边界
- **保留** nullable/readiness 兼容代码（null 感知的附件元数据、transfer/关注读取）。
- **绝不回滚到 01 之前不认识 null 的二进制**——旧二进制会把可空列/无 content 附件当异常或丢行。
- **不 DROP** `mail_attachment_transfer` / `expert_follow` 或附件相关索引；**不删除**已存文件/传输产物。

### 3.4 队列与任务
- 已排队传输任务：继续或暂停由运维显式设置；暂停不得清空 `queued_at/attempt` 状态。
- 页面切换/资源回退不丢队列状态；恢复新资源后原队列与关注仍在（A-2 预期）。

### 3.5 恢复
按第 2 节顺序重新执行（迁移已存在则跳过 2.2 的 DDL 部分，只做数据核对）；
恢复后核对：无重复自动发信、任务钻取/全局控制可用、旧附件可读。

---

## 4. 观测点与日志

| 观测 | 位置/命令 | 正常信号 |
|---|---|---|
| 传输活动 | `SELECT state, COUNT(*) FROM mail_attachment_transfer GROUP BY state;` | METADATA_ONLY 为主；DOWNLOADING ≤2 |
| 租约/worker | 应用日志 worker token、lease 续租 | 续租成功直到完成；超时连接真正关闭 |
| FETCH 计数 | IMAP 协议日志 | 检查回复期间附件内容 FETCH=0 |
| 失败原因 | transfer `error_code/error_message` | LIMIT_EXCEEDED/TIMEOUT/SOURCE_UNAVAILABLE 脱敏可见，可显式重试 |
| 机器信 | DMARC purpose 行 | 独立入队、不归专家、报表可读 |

> 版本/迁移/截图证据齐了才允许声称发布成功；任何一步出现停止条件，按第 3 节回退并回交记录。
