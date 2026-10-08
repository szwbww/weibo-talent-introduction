# 收发件箱专家备注：紧凑入口与持久化

状态：已批准执行；2026-10-08 用户明确「批准并执行」，并授权下述最小测试范围修订；实现与验证结果另行记录。
日期：2026-10-08。代码基线：`235681497c226066fa0174a2d79bc82863a1e91a`；基于当前工作树，逐文件 SHA256 见 [source-manifest.json](mailbox-expert-note-evidence/source-manifest.json)。执行前核对相关文件差异，不能覆盖其他任务未提交改动。

界面依据：[已确认的紧凑版截图](../../mockups/mailbox-notes-preview/online-preview-v2.jpg)、[预览编辑浮层](../../mockups/mailbox-notes-preview/online-edit-v2.jpg)。预览的 Map 只证明交互，不作为后端已完成的证据。

## 需求描述

O-1：在收发件箱选中专家后，标签下方、联系时间左侧显示一行「备注：＋ 添加备注」或已保存备注；长内容省略，点击打开完整内容编辑弹窗。保存后刷新页面、重新登录仍可读取。

O-2：支持纯文本多行备注、保存、取消、清空；弹窗显示最近修改人和北京时间。读取失败可重试，保存失败保留输入，不把失败当作成功或空备注。

本次落地口径（产品设计选择，不冒称数据库已有规则）：

- “专家”沿当前收发件箱的 `expert_contact_id` 单位；同一联系记录的不同邮件、账号筛选共用备注。同 ORCID 不同 campaign 的 contact 不自动合并。
- 备注对有当前系统访问权限的登录用户共享；不沿用关注/挂起的 username 私有维度。修改人只用于显示最近一次修改。
- 一条当前备注，最长 2000 个 UTF-16 code units；不增加历史记录、版本冲突机制或额外用户权限体系。
- 正式编辑框采用现有原生 `<dialog>` 的居中方式，宽 460px；紧凑入口、内容和按钮保持确认版本。预览用 `style.left/top` 临时定位，正式实现不搬这段坐标维护逻辑。这是降低实现复杂度的明确差异，非已验证的正式 UI。

必须保持：

- N-1：专家状态、层级、跟进/已提供、挂起及挂起原因、待处理数、会话归类和排序不因备注改变。
- N-2：邮件正文、人工回复草稿、发信、自动回复、AI 上下文、材料及排期数据不因备注改变。
- N-3：当前推荐联系时间、所在地编辑、标签、专家详情入口保留；在桌面宽度备注与时间同排，备注文字长度不撑高信息区。
- N-4：已有登录验证、密码强制修改规则保持；新接口在该鉴权下工作。

范围外：专家列表/详情页新增入口、备注搜索和筛选、批量编辑、自动总结、AI 使用备注、富文本、附件、历史审计、跨 ORCID/contact 合并、预览备注导入、自动保存、轮询、WebSocket、通用备注框架、自动上线。

## 关键不变量

### Invariant I-1：归属与空值唯一表示
- Rule：MySQL 新表 `expert_contact_note` 以 `expert_contact_id` 为主键，一条联系记录最多一行。无行表示无备注；非空规范化文本才存行。空字符串或纯空白保存后 DELETE 新表行，GET 返回 `note:""`、`updatedBy:null`、`updatedAt:null`。不存 null/空白占位行。
- Applies to：新服务 get/save、GET/PUT、联系人外键级联删除、前端选中/刷新。
- Violation consequence：清空后复现旧文字、不同专家串数据或多份来源不一致。
- 来源：原创；身份口径证据为 V1:79–95、MailboxConversationRepository:155–197；K-bound-expert-cross-campaign-read。

### Invariant I-2：窄写边界与长期存储
- Rule：备注业务写入只允许新表的 upsert/DELETE；新服务对 `expert_contact` 只做存在性读取。禁止使用 `ExpertContactRepository.save` 写备注，禁止改联系记录 updated_at、ES、邮件处理、跟进、挂起、发信和 AI 数据。GET 不创建记录。无回填、无预览迁移。
- Applies to：迁移、新服务、controller、所有前端备注动作。
- Violation consequence：整行保存覆盖并发业务状态，或备注操作改变会话排序/发信行为。
- 来源：K-backfill-column-specific-update；现有 ExpertContactLocationService 的独立表范式；原创。

### Invariant I-3：文本与身份边界
- Rule：PUT 只接收必填非 null 的 `note:String`。先检查原字符串 `.length <= 2000`，再将 CRLF/CR 统一 LF、trim 首尾，内部换行保留。前端 maxlength=2000、计数按 JS length。后端不截断。客户端不提交更新人/时间；服务端从 Session `AuthSessionKeys.USERNAME` 得到更新人，从显式 Asia/Shanghai 时间生成修改时间。文本只按纯文本渲染。
- Applies to：DTO、controller 身份读取、服务校验、SQL 参数、摘要/textarea/修改人显示。
- Violation consequence：伪造更新人、前后端长度不一致、中文/emoji 丢失或脚本注入。
- 来源：AuthSessionKeys.kt、AuthInterceptor.kt、MailboxSuspensionService.MAX_REASON_LENGTH 的 UTF-16 先例；原创。

### Invariant I-4：回包权威与简单并发语义
- Rule：保存事务完成后才返回成功；upsert/清空后在同一事务读回新表作为回包。单行原子写，采用最后成功提交的保存覆盖旧值，不引入 version/CAS。失败不更新前端已保存内容；网络结果不明确时保留草稿，可显式重试 PUT。更新人、时间与文本属于同一行。
- Applies to：save 的事务、SQL、PUT 响应、前端提交/失败分支。
- Violation consequence：界面显示未落库的备注，或不同保存的文本/元数据拼在一起。
- 来源：ExpertContactLocationService.save 的 upsert+回读先例；事务及最后提交语义为本计划新增约束。

### Invariant I-5：异步结果归属
- Rule：备注 GET 与 PUT 回调须同时核对 instance 未 disposed、选中 contactId、发起时 convEpoch；GET 另核对备注 readSeq，PUT 另核对持有的 dialog 对象。切专家、切账号上下文、切待匹配、unmount 必须关闭自有备注弹窗并使旧读取失效。开始 PUT 时作废较早 GET；保存期间不再启动备注 GET。
- Applies to：selectExpert、refreshConversationQuiet、备注重试、保存、teardownConversationSubViews、meetingCloseDisposeOnAccountScopeChange、unmount。
- Violation consequence：A→B→A 时旧 A 回包覆盖新 A；保存 A 的结果写入 B；旧 GET 覆盖刚保存值。
- 来源：现有 selectExpert:2800–2900 的 seq/convEpoch；K-shared-action-dialog-cleanup。

### Invariant I-6：重绘与草稿隔离
- Rule：`renderHeaderMeta` 只从当前备注状态生成 DOM，不发请求。标签/联系时间重绘不会清空已保存备注，也不重建 body 中的备注 textarea。备注不进入邮件草稿、会话消息缓存、localStorage/sessionStorage。取消/关闭不 PUT；失败保持 textarea 原值。
- Applies to：renderHeaderMeta 及它的所有调用处、备注 dialog、现有邮件草稿采集流程。
- Violation consequence：推荐时间返回后输入丢失、产生重复 GET、内部备注被带入回信。
- 来源：K-mailbox-popover-scope-and-fixed-containing-block；当前 renderHeaderMeta 调用回执；原创。

### Invariant I-7：加载和错误不能冒充空备注
- Rule：未读完显示「备注：加载中…」且不可编辑；GET 失败显示「备注：读取失败 · 重试」，不可用空文本覆盖数据库。成功读到无行才显示「＋ 添加备注」。PUT 进行中禁用保存、取消、关闭及 textarea，Escape 不关闭；失败恢复交互并展示错误；成功后才更新摘要、关闭、提示「备注已保存」。
- Applies to：前端备注状态机、GET/PUT 处理、弹窗键盘事件。
- Violation consequence：读取失败被当作空记录，后续保存覆盖真实内容；重复提交。
- 来源：原创。

### Invariant I-8：资源、鉴权与迁移兼容
- Rule：只新增 V150，不修改已执行 SQL；正式既有 /api/** 鉴权继续生效，新 GET/PUT 自身也检查非空 Session username，缺失 401。资源缓存键在 index.html 的所有当前版本化 CSS/JS 同步更新。执行时若 V150 已占用或旧键已变化，先更新本计划具体清单/证据再实施，禁止顺手改 out-of-order。
- Applies to：Flyway 文件、迁移测试、controller、index.html。
- Violation consequence：启动失败、无登录可读内部备注、用户继续收到旧 JS/CSS。
- 来源：K-flyway-latest-version-test-pin、K-flyway-version-follows-deploy-order、K-frontend-cache-key-triad。

## 样式契约

### S-1：专家信息栏下排

复用 `.mc-header`、`.mc-header-meta`、`.mc-badge`、`.expert-tag`、`.mc-text-button`；不修改它们的既有规则。出处：mailbox-chat.css:102–110、121。原有标签/ORCID/详情按钮表达式逐字保留，只增加容器。右侧保留 `contactTimingMarkup()` 及其事件。

目标 DOM（花括号代表已有内容/纯文本，不得引入额外卡片）：

```html
<header class="mc-header">
  <div class="mc-identity">{既有身份与排期摘要}</div>
  <div class="mc-actions">{既有操作}</div>
  <div class="mc-header-meta">
    <div class="mc-note-tags">{既有状态、层级、标签、ORCID、查看专家详情按钮}</div>
    <div class="mc-note-row">
      <section class="mc-note-slot" aria-label="专家备注">
        <button class="mc-note-trigger" type="button" data-action="mc-note-open" aria-haspopup="dialog">
          <span class="mc-note-label">备注：</span>
          <span class="mc-note-text">{＋ 添加备注或单行内容}</span>
          <span class="mc-note-edit">编辑</span>
        </button>
      </section>
      {contactTimingMarkup() 原样输出}
    </div>
  </div>
</header>
```

空态不渲染 `.mc-note-edit`；加载态 trigger disabled，文案「加载中…」；读取失败用相同 button/class 但 action=`mc-note-retry`，文案「读取失败 · 重试」。按钮 aria-label 分别为「添加专家备注」「查看或编辑专家备注」「重试读取专家备注」；不在 title/隐藏字段重复整段备注。

以下新增 CSS **逐字追加到 mailbox-chat.css**。`contact-timing` 的变化只通过新 `.mc-note-row` 派生选择器影响该行；不修改 styles.css 的全局定义。使用位置全集见 [meta-timing-use-sites.txt](mailbox-expert-note-evidence/meta-timing-use-sites.txt)。

```css
/* mailbox-expert-note:S1 */
.mail-chat .mc-header .mc-header-meta{min-width:0}
.mail-chat .mc-note-tags{display:flex;align-items:center;gap:8px;flex-wrap:wrap;width:100%;min-width:0}
.mail-chat .mc-note-row{display:flex;align-items:center;gap:12px;width:100%;min-width:0}
.mail-chat .mc-note-slot{flex:1;min-width:0}
.mail-chat .mc-note-row .contact-timing{flex:none;margin-left:auto;padding-left:0;border-left:0}
.mail-chat .mc-note-trigger{display:flex;align-items:center;gap:4px;width:100%;min-width:0;height:24px;padding:0;border:0;border-radius:4px;background:transparent;color:#61748e;font:inherit;font-size:11px;line-height:24px;text-align:left;cursor:pointer}
.mail-chat .mc-note-label{flex:none;color:#8494aa}
.mail-chat .mc-note-text{min-width:0;overflow:hidden;text-overflow:ellipsis;white-space:nowrap}
.mail-chat .mc-note-edit{flex:none;color:#3762d8;margin-left:6px}
.mail-chat .mc-note-trigger:hover:not(:disabled){background:#f3f6fc;color:#285ac0}
.mail-chat .mc-note-trigger:active:not(:disabled){background:#e9efff}
.mail-chat .mc-note-trigger:disabled{opacity:.55;cursor:default}
.mail-chat .mc-note-trigger:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
/* mailbox-expert-note:S1:end */
```

禁止：独立备注卡片、常驻修改时间、两行摘要、摘要区域 textarea、复制预览整套 shell 或替换既有标签来源。

### S-2：自有原生编辑弹窗

复用 `.button` / `.button.primary`（styles.css:802–850，完整原规则见 evidence/source-excerpts.md）；新 dialog 直接 append 到 body，自有节点自有事件，禁止使用共享 portalRoot.innerHTML，禁止复用 actionDialog 的共享表单。

默认居中而非维护锚点坐标，沿 contact-timing-dialog 的 `inset:0;margin:auto` 范式。按已确认预览保留 460px 宽度、白色实底、textarea、保存/取消、最近修改信息；不常驻占用 header 高度。

目标 DOM（id 带本实例唯一前缀，必须 label-for/aria-labelledby 对应）：

```html
<dialog class="mc-note-dialog" aria-labelledby="{prefix}-title">
  <header>
    <strong id="{prefix}-title">专家备注</strong>
    <button class="mc-note-close" type="button" data-note-action="close" aria-label="关闭专家备注">×</button>
  </header>
  <div class="mc-note-body">
    <label for="{prefix}-input">仅内部可见</label>
    <textarea id="{prefix}-input" aria-label="专家备注" maxlength="2000" placeholder="填写合作意向、沟通偏好、待办事项等…"></textarea>
    <div class="mc-note-hint" data-note-meta>{长度} / 2000 · {最近修改人、北京时间；无备注时不显示修改信息}</div>
    <div class="mc-note-error" role="alert" hidden></div>
  </div>
  <footer>
    <span class="mc-note-hint">Ctrl / ⌘ + Enter 保存</span>
    <div>
      <button class="button" type="button" data-note-action="cancel">取消</button>
      <button class="button primary" type="button" data-note-action="save">保存备注</button>
    </div>
  </footer>
</dialog>
```

打开时 `.value = 已读note`，聚焦 textarea；Tab 由 showModal 约束；Escape 同取消，saving 时阻止；Ctrl/Command+Enter 同保存，中文输入 `isComposing` 时不触发。普通 Enter 保留换行。关闭返回当前仍存在的触发按钮；context teardown 不抢焦点。

新增 CSS 逐字追加：

```css
/* mailbox-expert-note:S2 */
.mc-note-dialog{position:fixed;inset:0;margin:auto;padding:0;width:460px;max-width:calc(100vw - 32px);height:fit-content;max-height:calc(100dvh - 32px);overflow:auto;border:1px solid #d9e3f1;border-radius:12px;background:#fff;color:#475569;box-shadow:0 14px 48px #17325728;font:12px/1.6 var(--font-body,sans-serif)}
.mc-note-dialog,.mc-note-dialog *{box-sizing:border-box}
.mc-note-dialog [hidden]{display:none!important}
.mc-note-dialog::backdrop{background:#172c4712}
.mc-note-dialog header{display:flex;align-items:center;justify-content:space-between;padding:12px 16px;border-bottom:1px solid #edf1f7}
.mc-note-dialog header strong{font-size:14px;font-weight:600}
.mc-note-dialog .mc-note-close{border:0;border-radius:4px;background:transparent;color:#8494aa;font-size:22px;line-height:24px;cursor:pointer;padding:0 4px}
.mc-note-dialog .mc-note-close:hover:not(:disabled){background:#edf3ff;color:#244ca9}
.mc-note-dialog .mc-note-close:active:not(:disabled){background:#dbeafe}
.mc-note-body{padding:12px 16px}
.mc-note-body label{display:block;color:#97a5b8;font-size:11px;margin-bottom:6px}
.mc-note-dialog textarea{display:block;width:100%;min-height:116px;max-height:220px;resize:vertical;border:1px solid #b8caf2;border-radius:7px;padding:9px 11px;background:#fff;color:#475569;font:inherit;line-height:1.8;margin:0 0 7px}
.mc-note-dialog textarea:focus{outline:2px solid #dce8ff;outline-offset:1px;border-color:#668ee3}
.mc-note-dialog textarea:disabled{opacity:.65;cursor:wait}
.mc-note-hint{color:#97a5b8;font-size:10px}
.mc-note-error{margin-top:8px;color:#be123c;font-size:12px;line-height:1.6;overflow-wrap:anywhere}
.mc-note-dialog footer{display:flex;align-items:center;justify-content:space-between;gap:10px;padding:0 16px 14px}
.mc-note-dialog footer>div{display:flex;gap:8px}
.mc-note-dialog .button{height:30px;min-height:30px;padding:4px 12px;font-size:12px}
.mc-note-dialog button:disabled{opacity:.45;cursor:not-allowed;transform:none;box-shadow:none}
.mc-note-dialog button:focus-visible{outline:2px solid #6389d3;outline-offset:2px}
/* mailbox-expert-note:S2:end */
```

禁止 inline style、`.style.left/top`、新图标库、阴影透明度自行调整、修改共享 button/global p 样式。正文 textarea 有明确颜色，不受全局 p 降色影响。（来源：K-panel-bg-token-is-translucent、K-global-p-is-muted-in-dialogs）

### S-3：窄屏与局部作用域

```css
/* mailbox-expert-note:S3 */
@media(max-width:760px){.mail-chat .mc-note-row{flex-wrap:wrap}.mail-chat .mc-note-row .mc-note-slot{flex-basis:100%}.mail-chat .mc-note-row .contact-timing{margin-left:0}.mail-chat .mc-note-trigger{min-height:36px}.mc-note-dialog textarea{font-size:16px}.mc-note-dialog footer{flex-wrap:wrap}.mc-note-dialog .button{min-height:36px;height:36px}}
/* mailbox-expert-note:S3:end */
```

桌面 1440/1920px 下同排；窄屏 390px 下允许联系时间落到下一行，不通过压缩文字或横向滚动硬塞。所有新增/修改节点属于 S-1 或 S-2，S-3 只作它们的响应式覆盖。不得新增上述块之外的 class 或未经声明的既有规则改写。

## 现状审计

### 证据边界与检索回执

以下“已有”均来自工作树源码；“拟新增”均属本计划设计。没有查询或修改生产数据库，不把 DB_URL 默认配置当成线上数据库实测值。

原始证据包含命令、exit code、完整命中，正文引用可回查：

| 证据 | 含义 |
|---|---|
| [new-store-search.txt](mailbox-expert-note-evidence/new-store-search.txt) | `rg -n 'expert_contact_note\|ExpertContactNote' src scripts tools`，exit=1，无现有同名实现 |
| [contact-all-references.txt](mailbox-expert-note-evidence/contact-all-references.txt) | expert_contact 表、实体构造、Repository 引用检索；含迁移和直接 JDBC |
| [contact-repository-callers.txt](mailbox-expert-note-evidence/contact-repository-callers.txt) | 当前 Repository 方法使用点，逐行列出读/写 |
| [contact-writes.txt](mailbox-expert-note-evidence/contact-writes.txt) / [contact-column-writes.txt](mailbox-expert-note-evidence/contact-column-writes.txt) | save、原生 SQL、列级更新入口与调用者 |
| [frontend-lifecycle.txt](mailbox-expert-note-evidence/frontend-lifecycle.txt) | 选中、重绘、刷新、关闭的完整命中 |
| [meta-timing-use-sites.txt](mailbox-expert-note-evidence/meta-timing-use-sites.txt) | 修改容器相关 class 使用位置，决定采用派生选择器 |
| [asset-keys.txt](mailbox-expert-note-evidence/asset-keys.txt) / [asset-key-test-receipt.txt](mailbox-expert-note-evidence/asset-key-test-receipt.txt) | index 版本键；精确键反查 src/test，exit=1 |
| [flyway-latest.txt](mailbox-expert-note-evidence/flyway-latest.txt) / [migration-inventory.txt](mailbox-expert-note-evidence/migration-inventory.txt) | 全量迁移版本、最新断言位置；不能全局替换历史 target |
| [source-excerpts.md](mailbox-expert-note-evidence/source-excerpts.md) | 带行号的 DOM/CSS、生命周期、SQL 基线摘录 |

### 新表 expert_contact_note（拟新增；唯一被本功能业务写入的存储）

现有同名读写搜索无命中，回执如上。新 schema 精确定义如下，不触及共享 expert_contact/ES 字段：

```sql
CREATE TABLE expert_contact_note (
    expert_contact_id BIGINT NOT NULL,
    note VARCHAR(2000) NOT NULL,
    updated_by VARCHAR(64) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (expert_contact_id),
    CONSTRAINT fk_expert_contact_note_contact
        FOREIGN KEY (expert_contact_id) REFERENCES expert_contact(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

- 新表只有一个业务值 note；id 为归属，updated_by/updated_at 为最近修改元数据。不新增 created_at、enabled、version、history 表、Repository 实体层或索引。
- 写路径：① V150 只建表、不写初始行；② `ExpertContactNoteService.save` 对非空内容参数化 upsert，完整更新 note/updated_by/updated_at；③ 同方法对空文本按 contactId DELETE；④ 删除父 contact 的 FK CASCADE。无自动写入方。
- 读路径：① `get(contactId)` SELECT note/updated_by/updated_at；② save 事务内调用相同查询读回；③ 前端选中/刷新/重试通过 GET，保存通过 PUT 回包。已有列表、消息、AI、模板、自动回复查询不 join 该表。
- 并发：PK 保证首次保存不会产生重复行；不同用户共享内容，最后成功提交覆盖。不额外对父 contact 执行 SELECT FOR UPDATE，不新增分布式锁；数据库外键检查的内部锁仍由 MySQL 管理。
- 物理字符类型不替代应用校验；VARCHAR(2000) 可容纳应用允许的 2000 UTF-16 code units，emoji 计数仍由 I-3 控制。

### expert_contact：只读依赖，为什么不直接追加 note 字段

- Schema：`V1__create_business_tables.sql:79–95` 的 id 是 PK，`uk_campaign_expert(campaign_id,orcid_id)` 不是全局 ORCID 唯一。`ExpertContact.kt:8–35` 无通用 note。`MailboxConversationRepository.kt:155–197` 按 `u.expert_contact_id` 聚合。（来源：K-bound-expert-cross-campaign-read、K-group-before-pagination）
- 新建路径：`InitialOutreachService.kt:80–81` 和 `ManualInitialOutreachService.kt:996` 创建实体；本次不改。精确构造检索已在 contact-all-references 回执中，不把注释/扩展方法当构造。
- 更新路径按检索结果分组：
  - `ConversationStateService.transition`、`ExpertOperatorStatusService`、`ExpertContactManagementService`、`ExpertIndexLevelOperationService`、`ExpertIndexController` 的整行 save 修改状态/层级/人工标记。
  - `AutomaticApplicationPromotionService`、`UnmatchedInboundMailService`、`PendingMailOperationService`、`AutoMailReplyService` 的 save 修改晋级、邮件/人工处理相关状态。
  - `ContactCountryBackfillService.updateCountryById` 与 `SenderAccountBindingService` 调用 `updateBindingById/rebindSenderAccountById/migrateBindingByAccount/clearSenderChangeMarkById` 的定向更新。
  - 历史 V11/V12/V13/V14/V19/V48/V51/V85/V86/V94 涉及该表的结构、回填；完整原行见 contact-writes 回执。
- 读路径：Repository 的按 id/campaign/orcid/绑定账号/筛选读取、MailboxConversationRepository 聚合及消息锚点查询、所在地/挂起/关注服务存在性读取、资料/排期/发信/AI 控制器与服务。具体文件和调用行是 contact-all-references/contact-repository-callers 的逐行清单；本功能不改变这些查询的投影或条件。
- 交互判断：追加实体 note 会令备注进入既有整行 save 的并发覆盖面；独立表避免修改上述写者。新服务只读取 `id` 是否存在，用 FK 维持归属。备注编辑不改父表 updated_at，列表不会因编辑被提升排序。（来源：K-backfill-column-specific-update）
- K-expert-contact-two-write-sites 的“构造点”经复核可保留；其历史段落不能当作“整行 save 保证并发保留字段”，该推论不采用并回写纠正。

### 现有独立表/API先例与鉴权

- `V146__create_expert_contact_location.sql:1–10`：contact PK + FK CASCADE 的独立小表。
- `ExpertContactLocationService.kt:37–90`：NamedParameterJdbcTemplate、存在性查询、参数化 upsert、回读；没有强制新建 repository 的架构要求。新备注只采用这一窄服务结构，不复制时区推荐业务。
- `ExpertContactLocationController.kt:38–91`：独立 controller、GET/PUT、Session 身份读取。`AuthWebConfig.kt:26–29` 拦 `/api/**`，`AuthInterceptor.kt:20–55` 读取 Session 后查用户并检查 mustChangePassword。新 GET/PUT 路由自身再检查非空 Session，是新增显式约束。
- `GlobalExceptionHandler.kt:19–29`：IllegalArgumentException→400、NoSuchElementException→404；无需新增全局异常映射。缺 body/缺 note/null note 沿现有 Kotlin DTO/Jackson 的 400。
- 现有挂起/跟进依 username+contact；备注选择 contact-only 是本计划明确的共享语义，不是照搬挂起原因。

### 前端读写、会话缓存与交互点

- `mailbox-chat.js:2800–2900 selectExpert`：先 teardown，再换 selectedContactId，增加 seq/convEpoch；随后并行加载 contact/messages。备注增加独立 GET，不塞入 contact/messages 的 Promise.all，备注失败不得让邮件区失败。
- `renderHeaderMeta:2993–3006` 同时输出标签、ORCID、详情按钮、contactTimingMarkup。它会被 `renderHeader`、标签刷新、`repaintContactTiming:3198–3202`、管理保存/标签编辑再次调用；见 lifecycle 回执。
- `refreshConversationQuiet:7872–7903` 有“serverItems 与已有 items 同时为空”提前 return。备注刷新入口放在这个分支之前，不能只插在 loadContactTiming 后，否则空时间线备注永远不重读。
- `teardownConversationSubViews:2779`、`meetingCloseDisposeOnAccountScopeChange:7350`、`unmount:8682` 分别处理切上下文和销毁。新弹窗跟随相同清理点，但节点自有，不清空现有 portal。
- `createContactDialog:3252–3285` 在 body 创建 `<dialog>` 并自己监听；`closeContactTimingDialog:3301` 只删自己的节点。新备注使用此生命周期范式。（来源：K-mailbox-popover-scope-and-fixed-containing-block、K-shared-action-dialog-cleanup）
- 邮件缓存/草稿由 saveCurrentConversation/saveConversationState 等采集；新备注状态不加入这些对象，不改它们的持久化结构。
- 新数据不加入 summary/timeline DTO；列表不显示备注，故不需要页内批量查询。每次选择/显式刷新当前 contact 才按需 GET，不逐卡请求。

交互点清单：

| 编号 | 写入/事件 → 读取/消费者 | 处理约束 |
|---|---|---|
| IP-1 | PUT→新表→GET→重新选中/重载页面 | I-1/3/4；存储与回包同源 |
| IP-2 | 父 contact 存在性/删除→备注 GET/FK | I-1/2；未知 404，父删除级联 |
| IP-3 | Session→保存 updated_by→弹窗显示 | I-3/8；拒绝客户端冒充 |
| IP-4 | selectExpert/切账号/unmount→在途 GET/PUT | I-5；token/contact/epoch 守卫 |
| IP-5 | timing/标签/header 重绘→备注摘要/编辑草稿 | I-6；纯渲染、不重建 textarea |
| IP-6 | 备注编辑→原会话状态/排序/发信/草稿 | I-2/6；零业务副作用 |

### 前端样式盘点

既有 class 与完整规则块、token 原文在 [source-excerpts.md](mailbox-expert-note-evidence/source-excerpts.md)。正文保留目标区域原样基线：

```javascript
metaEl.innerHTML = `
    ${statusBadge}${levelBadge}${expertTagSpans}${orcid ? `<span>ORCID ${escapeText(orcid)}</span>` : ""}<button class="mc-text-button" type="button" data-action="mc-open-expert" data-contact-id="${escapeText(Number(instance.selectedContactId))}">查看专家详情 ↗</button>${contactTimingMarkup()}
`;
```

```css
.mail-chat .mc-header{flex:none;background:#fff;padding:17px 22px 14px}
.mail-chat .mc-header-meta{display:flex;align-items:center;flex-wrap:wrap;gap:8px;flex-basis:100%;font-size:11px;color:#8a9bb2}
.mail-chat .contact-timing{display:inline-flex;align-items:center;flex-wrap:wrap;gap:9px;margin-left:auto;padding-left:10px;border-left:1px solid #e1e8f2;min-height:22px;max-width:100%;font-size:11px;line-height:1.6}
```

- `.mc-header-meta` 与 `.mc-header` 原声明不动；增加 mc-note-tags/mc-note-row 改 DOM 分组。所有原 class 使用位置在 meta-timing-use-sites 回执；无需迁移其他页面。
- token：styles.css:1–80，主色 `#1e40af`、明亮蓝 `#3b82f6`、正文 `#1e293b`、次文字 `#475569`、弱文字 `#94a3b8`；圆角 7/10/18px。`--panel-bg=rgba(255,255,255,.55)` 不用于备注弹窗。
- 按钮：styles.css:802–850，默认 32px 高、0 12px padding、12px 字体；primary 采用既有渐变；本弹窗唯一局部覆盖为 S-2 声明的 30px 高、4px 12px padding。
- 局部头区：17px 22px 14px padding、8px gap；备注入口24px高、11px字；弹窗正文12px/1.6、白色实底、12px圆角；所有 hover/active/disabled/focus 实值已冻结在 S-1～S-3。
- 现有生成方式：HTML 模板字符串 + data-action + host onClick；弹窗 body 节点自行监听。新增按钮沿同一入口，不能给 document 再绑预览的全局捕获器。
- 预览代码中的 NotePreview 全局对象、Map、API 白名单、导航拦截、默认 mock catalog 均不进入正式组件；正式标签和状态来源保持现状。

### 缓存键、迁移和测试能力

- [asset-keys.txt](mailbox-expert-note-evidence/asset-keys.txt)：当前 index 的 5 个 CSS、6 个 JS 共 11 个版本引用同为 `20261006-discovery-review-merge`。数量来自该回执逐行命中；新键拟为 `20261008-expert-note`。
- 精确命令 `rg -n -F '20261006-discovery-review-merge' src/test` 无命中（exit=1），本次不因旧知识条目历史数字增加无关固定键测试修改。执行前重查。（来源：K-frontend-cache-key-triad）
- 最高迁移 V149，见 migration-inventory。新 V150；`FlywayMigrationIntegrationTest` 的最新断言149需要同步，显式 target=141/144/其他历史版本不改。原始全部位置见 flyway-latest。（来源：K-flyway-latest-version-test-pin）
- `ExpertContactLocationServiceTest.kt:40–48` 使用 mock JDBC 并执行真实 RowMapper，可借同结构测试校验/边界；这不能证明 SQL 可执行。
- `FlywayMigrationIntegrationTest.kt:21–43` 已用 `mysql:8.0.36` Testcontainers，受 `migrationIt=true` 控制。本计划在该文件补 V150 schema + 真实服务持久化测试，避免另起一套 IT 基础设施。
- `ExpertContactLocationControllerTest.kt:46–76` 提供 WebMvcTest + AuthWebConfig + Kotlin ObjectMapper 的真实请求解析先例。
- `mailboxChatBehavior.test.js` 已有 MiniDOM、bootChat、受控 API、延迟 Promise 与 mount 场景。本次直接扩展该文件，不复制或抽取通用 harness。其 DOM 检查不替代真实浏览器布局检查。（来源：K-dom-stub-tests-hide-dangling-refs）
- pom.xml:184–205 绑定 `node --test src/test/js/*.test.js`；单项门禁明确写 node 命令，不用 verify.sh 代替。（来源：K-js-test-invocation-surface）
- 本轮加载的 K-mailbox-replied-membership 只用作“不从备注推断归类”的回归提醒，不采用其中旧关注筛选细节作为当前需求；K-group-before-pagination 不扩展成列表改造；共享 actionDialog 知识只用作生命周期提醒，本次不用共享表单。

## 实现方案

### T-1：新增窄存储与服务（I-1～I-4、I-8）

精确文件：清单 F1、F2、F4、F6。

1. F1 创建上文 SQL，新表初始为空，不 ALTER 其他表、不回填、不加入 ES。
2. F2 新增 `ExpertContactNoteService`，只注入 `NamedParameterJdbcTemplate`。同文件声明 `ExpertContactNoteView(contactId:Long,note:String,updatedBy:String?,updatedAt:String?)`，无需新 repository/domain 文件。
3. get(contactId)：positive id 校验；`SELECT COUNT(*) FROM expert_contact WHERE id=:contactId` 判存在，否则 NoSuchElementException；查新表三列，不存在按 I-1 返回空态。
4. save(username,contactId,rawNote)：验证 username 非空且 <=64、contactId 正数、rawNote.length <=2000；规范化换行/首尾空白；验证父 contact；在 `@Transactional` 内按空/非空执行 DELETE/upsert，再 get(contactId) 回读。
5. upsert SQL 精确列范围：

```sql
INSERT INTO expert_contact_note (expert_contact_id, note, updated_by, updated_at)
VALUES (:contactId, :note, :username, :updatedAt)
ON DUPLICATE KEY UPDATE
    note = VALUES(note),
    updated_by = VALUES(updated_by),
    updated_at = VALUES(updated_at)
```

清空 SQL：`DELETE FROM expert_contact_note WHERE expert_contact_id=:contactId`。所有值使用 MapSqlParameterSource，禁止拼接文本。

6. updated_at 的新契约：数据库 DATETIME(3) 存北京时间本地值；应用显式 `LocalDateTime.now(ZoneId.of("Asia/Shanghai")).truncatedTo(ChronoUnit.MILLIS)`，不依赖 JVM 默认时区。回包序列化为 ISO 字符串，带 `+08:00`；前端使用 Intl.DateTimeFormat(timeZone:'Asia/Shanghai') 显示 `YYYY-MM-DD HH:mm`。这是新列明确约定，不推断其他历史列。
7. 不新增 DELETE API；清空是 PUT note=""，同一入口避免两套逻辑。
8. F4 mock JDBC 单测验证 RowMapper、SQL 参数、无写分支；F6 在真实 MySQL 上验证建表/upsert/更新/清空/FK 和新服务实例 GET，不能用 mock 绿灯替代持久化证据。F6 沿现有容器创建同一 DataSource 的 NamedParameterJdbcTemplate 与 DataSourceTransactionManager，用 TransactionTemplate 包裹直接实例化的服务 save；提交后另开连接核对。直接 new 服务不会触发 Spring 的 @Transactional 代理，不能声称直接调用已验证注解生效。补一条事务回滚后旧备注仍在的断言；生产代理事务仍需通过真实 HTTP 保存/重读验收。

新写者只由 F3 PUT 调用；新读取由 F3 GET 和 save 回读消费。已有 contact/列表/消息读取无需扩充字段。

### T-2：新增 GET/PUT 接口（I-1～I-4、I-8）

精确文件：F3、F5。

F3 为独立 controller，路径 `/api/mail/contact-notes`；与 contact-locations 平行，避免向既有 MailboxConversationController 加依赖导致无关构造修改。

| 方法 | 路径/请求 | 成功回包 |
|---|---|---|
| GET | `/api/mail/contact-notes/{contactId}` | 200 `ExpertContactNoteView` |
| PUT | 同路径，`{"note":"正文"}` | 200，事务保存后的相同 View |
| PUT 清空 | 同路径，`{"note":""}` | 200，note=""、元数据=null |

示例（API形状示意，不代表线上已有记录）：

```json
{"contactId":42,"note":"等待会议邀请\n晚间联系","updatedBy":"admin","updatedAt":"2026-10-08T10:18:00.000+08:00"}
```

时间字符串允许秒小数格式由统一 formatter 固定三位 `.SSS`；不传裸无 offset 时间。

- 请求 DTO `SaveExpertContactNoteRequest(val note:String)` 放在 controller 文件内，无默认值。请求不声明 username/operatorName/updatedAt；未知属性沿既有 JSON 配置忽略，绝不能成为服务参数。
- GET 和 PUT 都先从 getSession(false)/AuthSessionKeys.USERNAME 取非空用户名；否则返回既有 `ApiErrorResponse("UNAUTHORIZED","未登录",null)`，401。
- 负/零 id、超长 note→400；不存在的正 id→404；缺请求体、缺 note 或 note=null→400；note="" 是合法清空请求。正常无备注→200，不能混淆不存在 contact 与不存在 note。
- F5 装载 AuthWebConfig，分别验证未登录、会话失效、mustChangePassword 的原403规则，以及伪造 body 用户名不能改变传入服务的 Session 身份。mock 服务只能证明 HTTP 语义，真 SQL由F6验证。

### T-3：在正式组件接入读写与弹窗（I-1～I-7，S-1～S-3）

精确文件：F7、F8、F10。

1. F7 在 instance 内增加一份备注状态：当前 contactId、readSeq、loaded/loading/error、View、saving、dialog。复用 convEpoch 与 disposed，不增加跨会话 Map 或全局缓存。
2. 按 S-1 包裹原 renderHeaderMeta 的内容，增加备注区域。`noteMarkup()` 只读内存状态；不改 existing statusBadge/levelBadge/headerExpertTagSpans/orcid/contactTimingMarkup 的来源、内容和事件。
3. `selectExpert` 设置新 contact、完成 scaffold 后独立调用 loadExpertNote(contactId)；`refreshConversationQuiet` 在消息空数组提前 return 之前调用，若 saving 则跳过。按备注 retry 按钮再 GET；不为每张列表卡加载，不轮询。
4. load 使用 hostApi；发起时读取 contactId/convEpoch/++readSeq；回调三者匹配再更新 loaded/data/error。只重绘备注 slot，不能重新 renderConversationContent 或 timeline；外部 renderHeaderMeta 重绘则从同一状态还原。
5. 空态/已有态打开同一自有 dialog；输入默认使用当前成功 GET 的完整 note；无需额外首次打开 GET。读失败只能重试，不能进入空编辑。已有弹窗重开前先清理自己的旧节点。
6. showModal 创建于 body，局部 click/input/keydown/cancel 监听。打开前关闭同组件的 contactTiming/管理/跟进/材料/模板引用浮层（调用已有 close 函数，restoreFocus:false），避免顶层弹窗叠加。不要把本节点放进共享 portal，关闭时只操作它自己。
7. 保存时先检查 maxlength，置 saving、禁用控件并阻止重复点击/快捷键；增加 readSeq 使旧 GET 失效；捕获 contactId/convEpoch/dialog 引用。PUT 只含 raw note 字符串。成功使用回包更新当前 data，关闭并提示；失败保持输入原样，在本弹窗 error 显示「备注保存失败，请重试」，取消恢复可用。服务端校验消息可以附加展示为纯文本。
8. teardownConversationSubViews 增加 closeExpertNoteDialog({restoreFocus:false}) 和 invalidateExpertNote；meetingCloseDisposeOnAccountScopeChange 同样清理；unmount 已调用 teardown，不重复解绑。context 清理可强制关闭在途保存弹窗，网络请求已发出不能承诺撤回；旧结果只影响被捕获的服务器 contact，不得回写新界面。
9. 点击取消、×、Escape 丢弃输入且无 PUT；busy 时上述关闭手势不生效，强制 teardown 例外。成功/普通关闭恢复当前仍存在的备注按钮焦点。
10. 摘要文字用 escapeText，textarea 用 value；不经过邮件富文本处理器、不记录在草稿。metadata 只在弹窗显示，非法/缺失时间不得猜为当前时间。
11. F8 原样追加 S-1～S-3。新 dialog 的正文/错误 class 均有规则；不修改 styles.css、不新增 JS/CSS资源。
12. F10 扩展现有 bootChat API fixture，加 GET 空态默认响应；新增测试直接挂载修改后的 mailbox-chat.js，操作真实生成的按钮/dialog，不能只抽取 noteMarkup 镜像断言。按验收矩阵覆盖延迟 GET/PUT、header 二次重绘、独立邮件草稿、取消与失败。

### T-4：缓存键、迁移门禁与交付（I-8，S-1～S-3）

精确文件：F6、F9、F10～F14（不改其他文件）。

- F9 将当前11个版本化资源引用统一为 `20261008-expert-note`，不增加资源、不更改加载顺序。
- F6 只把“迁移到当前最新”的149期望改150；显式 target 历史值保留。增加 V149→V150 无业务数据变化、字段/PK/FK/utf8mb4断言。新迁移依赖父表已存在。
- F10 检查 CSS 契约逐字块、新增 DOM 骨架/事件与资源键一致；现有全量 JS 用例若红，先确认是否本次改变的 DOM/请求导致。计划外修复不得扩大变更清单。
- F11～F14 删除与追加 S-1～S-3 冲突的过时整文件字节锁定断言；既有业务样式检查保留，局部作用域检查显式允许本计划 body-owned 备注弹窗样式。不得以更新旧基线文件或忽略失败替代。
- 本计划不直接执行生产迁移/上传WAR。机器验证完成后，人工验收从本文件 A-n 导出 checklist；不以独立预览的保存演示代替数据库验收。

## 变更文件清单

硬边界：以下14个开发/测试文件，两个子系统（后端备注存储/API、前端收发件箱）。规划文档、研究回执与知识记录不是执行代码清单。表外代码不得顺手修改；如版本冲突或已确认缺口需要增加文件，先修订计划。

| ID | 文件 | 动作/目的 |
|---|---|---|
| F1 | `src/main/resources/db/migration/V150__create_expert_contact_note.sql` | 新建表 |
| F2 | `src/main/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactNoteService.kt` | 新建服务及View，校验、GET/upsert/清空 |
| F3 | `src/main/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteController.kt` | 新建controller及请求DTO |
| F4 | `src/test/kotlin/com/weibo/talentintroduction/mail/service/ExpertContactNoteServiceTest.kt` | 新建服务边界/RowMapper单测 |
| F5 | `src/test/kotlin/com/weibo/talentintroduction/mail/controller/ExpertContactNoteControllerTest.kt` | 新建接口/鉴权测试 |
| F6 | `src/test/kotlin/com/weibo/talentintroduction/campaign/repository/FlywayMigrationIntegrationTest.kt` | 最新断言、新迁移及真实服务SQL持久化验证 |
| F7 | `src/main/resources/static/mailbox-chat.js` | 紧凑入口、独立读写、弹窗、生命周期 |
| F8 | `src/main/resources/static/mailbox-chat.css` | 逐字 S-1～S-3 样式 |
| F9 | `src/main/resources/static/index.html` | 同步版本键 |
| F10 | `src/test/js/mailboxChatBehavior.test.js` | 现有组件行为测试内增加备注场景/样式断言 |
| F11 | `src/test/js/contactTimingStyle.test.js` | 删除过时整文件字节锁定，保留所在地样式边界 |
| F12 | `src/test/js/mailboxChatStyle.test.js` | 删除过时整文件字节锁定，允许本计划备注局部样式 |
| F13 | `src/test/js/mailboxSuspensionStyle.test.js` | 删除过时整文件字节锁定，保留挂起业务检查 |
| F14 | `src/test/js/mailboxTemplateReferenceStyle.test.js` | 删除过时整文件字节锁定，保留模板引用样式边界 |

## 验收标准

### 每条不变量的机器验证

| 契约 | 断言与证据 |
|---|---|
| I-1 | 真实MySQL：无行GET不插入；两次非空PUT后同contact行数=1；空白清空后行数=0；两个不同contact即使ORCID相同也隔离。父contact删除后note行=0。 |
| I-2 | 捕获服务SQL：UPDATE/INSERT/DELETE目标只新表；GET只有SELECT；实际测试比较contact字段/updated_at前后相等。源审计确认没有把备注加到消息DTO、AI、ES或发信调用。 |
| I-3 | 2000长度通过、2001拒绝且旧值保持；1000个emoji通过、1001拒绝；CRLF归一/中文/引号保真；MVC验证客户端username忽略、Session决定updatedBy；DOM中`<img src=x onerror=…>`显示文本且无img节点。 |
| I-4 | 真实MySQL运行新Service：首次保存/覆盖/清空→新连接或新服务GET一致；两种用户顺序保存后后一人/文本/时间在同一行；Promise保存失败时摘要仍旧值、textarea仍草稿。不得声称顺序测试证明并发压力性能。 |
| I-5 | 延迟A GET→切B→返回A→新A GET先到→旧A GET晚到；只保留新A。A PUT在途强制teardown→B；晚响应不关B弹窗/不填B数据；账号切换/unmount后旧响应不复活DOM。 |
| I-6 | timing和标签回包分别触发renderHeaderMeta，备注仍存在，textarea内容及焦点不丢；请求数不因纯render增加；邮件草稿前后逐字相同。 |
| I-7 | 首次GET pending无编辑入口；失败→重试→成功空态；PUT失败保留草稿、按钮解禁；连续点击及Ctrl+Enter在busy时只有一个PUT；Escape取消零写，busy Escape不关闭。 |
| I-8 | 无Session401、无效Session401、mustChangePassword403；未知contact404；新表迁移与版本150通过；显式历史target未改；所有版本资源同键且原资源顺序不变。 |

### 样式与真实浏览器验证

- S-1：F10 对 CSS 标记块逐字匹配；真实DOM `.mc-note-row` 同时含 note slot 与既有 timing；在1440/1920宽度，note与timing纵向中心差<=2px，横向边界不重叠。保存1字、2000字时header高度相等，note trigger高度24px。不渲染独立备注卡片/常驻metadata。
- S-2：CSS块逐字一致，dialog直接属于body，背景computed rgb(255,255,255)，桌面宽460px；textarea输入行高、错误文案、disabled/键盘交互符合契约。无inline style/未声明class。所有label/aria目标存在于真实生成DOM，而非任意返回对象的stub。
- S-3：390px视口备注区域及弹窗不引入横向溢出；备注/时间允许分行，textarea字体16px，dialog最大宽358px且可滚动；关闭不遗留遮罩。先记录原收发件箱同视口基线，已有其他区域的溢出不扩展为本次全页响应式改造。
- 既有回归：材料、排期、标签、所在地推荐、管理按钮、挂起处理、跟进状态、人工回复草稿及默认页签的现有测试继续通过；不为本次备注改写这些业务的期望。

### 执行时必须跑的命令

```bash
node --check src/main/resources/static/mailbox-chat.js
node --test src/test/js/mailboxChatBehavior.test.js
node --test src/test/js/*.test.js
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DskipNodeTests=true -Dtest=ExpertContactNoteServiceTest,ExpertContactNoteControllerTest
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test -DskipNodeTests=true -Dtest=FlywayMigrationIntegrationTest -DmigrationIt=true
```

迁移门禁需要Docker/Testcontainers，必须记录实际执行与结果。Docker不可用就标为“真实SQL/迁移未验证”，不能把默认跳过的绿色测试当通过。当前任务只做规划，没有运行这些尚不存在的新功能测试。

全部指定门禁通过后进行一次常规构建/全量后端回归；记录运行版本、失败是否基线已有，不以无限重复测试代替完成。

## 人工验收清单

此节为权威清单。人工验收开始时再导出同目录 `mailbox-expert-note-acceptance.md`（A-n、勾选框、验收人、日期、结果/备注）；本次不生成衍生勾选文件。

涉及实际保存、清空、父记录删除和发信的验收均在隔离测试环境，使用测试联系人；生产只做已授权只读目测，不发送测试邮件或删除真实专家。

### A-1：首次添加与重载持久化
- 前置条件：新版本测试环境已迁移；已登录；后台存在一位有往来邮件且尚无备注的测试专家A。
- 操作步骤：1.收发件箱选择A；2.点「＋ 添加备注」；3.输入两行「等待会议邀请」「北京时间晚间联系」；4.保存；5.刷新浏览器并重新选择A。
- 预期结果：摘要单行显示上述文本，长处省略；再次打开看到原两行；显示本次登录用户名和北京时间，刷新后仍存在。
- 覆盖：O-1/O-2、I-1/3/4、IP-1/3、S-1/2。

### A-2：取消、快捷键和清空
- 前置条件：A已有A-1备注。
- 操作步骤：1.编辑为「不应保存」，按取消；2.重开核对；3.再次编辑按Escape；4.重开输入「快捷键保存」，按Ctrl/Command+Enter；5.重开清空文本后保存；6.刷新页面。
- 预期结果：两次取消均保留原文本；快捷键保存后显示「快捷键保存」；清空及刷新后显示「备注：＋ 添加备注」，无旧修改人和时间。
- 覆盖：O-2、I-1/4/7、S-2、IP-1。

### A-3：长度、中文和纯文本
- 前置条件：A已选中；准备2000个「中」、2001个「中」，以及字符串`<img src=x onerror=alert(1)>`。
- 操作步骤：1.粘贴2000字保存并重开；2.用API测试工具在当前测试登录会话对A PUT 2001字；3.回到页面保存上述HTML字符串并重开。
- 预期结果：2000字完整保留；2001请求400且数据库旧内容未变；HTML以字面量显示，无图片/弹窗；摘要仍单行。
- 覆盖：I-3、S-1/2、O-2。

### A-4：账号筛选和另一位专家
- 前置条件：测试专家A在两个真实测试发件账号下各有邮件；测试专家B也有邮件。A备注「A专用」，B无备注。
- 操作步骤：1.选择A；2.切换两个账号筛选，分别选回A；3.选择B并保存「B专用」；4.选回A。
- 预期结果：A两个账号范围均显示「A专用」；B显示「B专用」；选回A不显示B文字。
- 覆盖：I-1/5、IP-1/4、N-1。

### A-5：同ORCID不同活动的隔离
- 前置条件：隔离库通过测试fixture创建两个campaign和两个contact，ORCID相同、contactId不同；记录ID供API测试工具使用。
- 操作步骤：1.对第一contact PUT「第一活动」；2.GET第二contact；3.对第二contact PUT「第二活动」；4.GET第一contact。
- 预期结果：步骤2 note=""，步骤4 note="第一活动"；不进行跨活动自动合并。
- 覆盖：I-1、IP-1/2、明确归属口径。

### A-6：读取失败与保存失败
- 前置条件：A已有「服务器原值」；浏览器开发者工具可阻断`/api/mail/contact-notes/*`，或使用测试代理拦截该路径。
- 操作步骤：1.阻断后选A；2.解除阻断点重试；3.打开编辑输入「待重试内容」，再次阻断后保存；4.解除阻断再保存。
- 预期结果：首次显示「读取失败 · 重试」且不能空编辑；恢复后读到「服务器原值」；保存失败保留「待重试内容」且没有成功提示；重试成功后摘要更新为「待重试内容」。
- 覆盖：O-2、I-4/7、IP-1、S-1/2。

### A-7：切换上下文时迟到响应
- 前置条件：测试代理可延迟备注GET/PUT回包；A已有「A已保存」，B已有「B已保存」。
- 操作步骤：1.延迟A的GET，选择A后切B；2.释放A回包；3.回A打开备注，保存「A新值」并延迟PUT回包；4.通过测试自动化切换组件到B或离开收发件箱；5.释放A回包，回到B。
- 预期结果：B始终「B已保存」；无A弹窗复活、无A内容写到B；再次选择A读取其真实服务器保存结果。普通用户保存busy时不能取消，这是预期，不承诺已发送PUT被撤回。
- 覆盖：I-5/7、IP-4、S-2。

### A-8：推荐时间/标签刷新与草稿
- 前置条件：A已有备注；有所在地配置；人工回复输入框已输入测试草稿「保留邮件草稿，不发送」。
- 操作步骤：1.编辑备注并输入「尚未保存」；2.测试代理释放之前延迟的推荐时间GET/专家标签响应；3.核对textarea后取消；4.核对人工回复草稿与所在地推荐；5.重开/关闭所在地编辑。
- 预期结果：备注textarea仍「尚未保存」且焦点未被抢走；取消后原备注不变；邮件草稿逐字保留；国家和建议北京时间仍显示，所在地弹窗可开关。
- 覆盖：I-6、IP-5/6、N-2/N-3、S-1/2。

### A-9：状态、挂起和会话排序回归
- 前置条件：测试A已挂起且有1条待处理，处于跟进中；记录当前列表顺序、状态/层级、挂起原因。
- 操作步骤：1.保存备注；2.刷新收发件箱；3.检查跟进中、已挂起页签、待处理数和挂起原因；4.打开管理检查状态/层级。
- 预期结果：仍跟进中、已挂起、1条待处理，原因、状态、层级及列表相对顺序与保存前相同（验收期间不注入新邮件）；备注没有触发收信或发信。
- 覆盖：I-2、IP-6、N-1。

### A-10：鉴权与最近修改身份
- 前置条件：测试环境通过受控fixture准备两个有效测试登录会话userA/userB、一个mustChangePassword=true会话，以及无登录窗口；无需开发新用户管理页面。
- 操作步骤：1.无登录GET/PUT测试contact；2.强制改密码会话GET/PUT；3.userA PUT带`note:"身份测试",username:"伪造人"`；4.userB GET再PUT「B覆盖」。
- 预期结果：步骤1返回401，步骤2返回403；步骤3 updatedBy=userA；步骤4先读到userA文字，再返回updatedBy=userB、note="B覆盖"。无客户端伪造用户名进入数据库。
- 覆盖：I-3/4/8、IP-3、N-4。

### A-11：联系人不存在和级联清理
- 前置条件：仅在隔离库创建没有邮件/排期等其他FK依赖的临时contact C，并为C保存备注；记录C的id。
- 操作步骤：1.用SQL工具删除C；2.GET C备注；3.PUT C备注；4.查询新表C的行数；5.GET一个从未存在的正id。
- 预期结果：步骤2/3/5均404；步骤4为0；不能通过PUT生成悬空备注。
- 覆盖：I-1/2/8、IP-2。

### A-12：紧凑布局与既有入口
- 前置条件：测试环境有截图同款长姓名/多标签专家，或Matloob Khushi相同布局fixture；桌面1440px和1920px、移动390px可切换。
- 操作步骤：1.分别保存1字与2000字备注；2.比较信息区高度、右侧联系时间；3.打开备注，观察白色460px弹窗、保存/取消；4.切390px；5.关闭备注并分别打开材料、管理、新增排期、专家详情；仅查看后取消。
- 预期结果：桌面备注入口高24px、11px字，与联系时间同排，1字/2000字高度相同；长文字省略；修改人只在弹窗；移动端备注区域及弹窗不引入横向溢出，textarea16px字；既有入口仍打开原面板，无残留遮罩或按钮失效。
- 覆盖：O-1、S-1/2/3、N-3、IP-5/6。

### A-13：发信/材料/排期与备注不联动
- 前置条件：隔离环境邮件投递使用现有模拟器或测试收件地址，A已有一条备注；记录邮件/材料/排期数量。
- 操作步骤：1.连续编辑并保存备注两次；2.查看邮件、材料、排期数量；3.查看人工回复输入、模板预览和AI回复上下文；4.在隔离环境按既有流程保存一条测试排期或上传一份测试材料，再重读备注。
- 预期结果：步骤2数量与前置相同；步骤3未自动插入备注文本；步骤4备注仍是最后保存值。备注不能触发邮件投递、材料请求或日历操作。
- 覆盖：I-2/6、IP-6、N-2。

## 计划自查与实施顺序

- [x] 已完成代码检索、schema及读写路径审计；原始回执可追溯，未把框架猜测当代码事实。
- [x] 新字段/存储、空值、长度、身份、并发及时间语义均有I-n。
- [x] 前端DOM全部映射S-1/S-2，响应式S-3；新增CSS逐字，既有CSS不全局改写。
- [x] 开发文件14个；2个子系统；共享旧表新增字段0个。
- [x] O-1/O-2、N-1～N-4及IP-1～IP-6均有A-n。
- [x] 已区分单测、真实SQL、浏览器布局与人工验收，不把预览数据当持久化能力。
- [x] 已限定范围外功能和执行前版本核对；未生成实施代码、未生成提前验收勾选表。

实施顺序：T-1→T-2→T-3→T-4→机器验证→导出并执行人工验收。若部署需要分步，可先部署新表/API，旧前端不受影响；前端启用必须在API可用后。不为这个小功能拆出多份相互依赖计划。

## 修正记录

- 2026-10-08：用户选择「授权最小范围修订」，授权 F11～F14，删除旧整文件 CSS 字节锁定断言、允许计划规定的备注局部样式、保留其他回归检查。依据：全量 JS 实测暴露这些断言与本计划追加 S-1～S-3 必然冲突；不变更产品行为或历史证据文件。
- 2026-10-08：用户选择「批准局部宽度修正」，授权在 S-1 追加 `.mail-chat .mc-header .mc-header-meta{min-width:0}` 并同步 F8/F10；不改既有全局规则。依据：Chromium 实测 2000 字备注将父 flex item 撑至约 22036px，联系时间被外层裁剪；局部 min-width 修正恢复桌面同排与390px区域边界。
