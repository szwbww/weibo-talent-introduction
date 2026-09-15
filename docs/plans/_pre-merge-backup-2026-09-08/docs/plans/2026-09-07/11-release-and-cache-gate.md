# 11 · 资源注册、启用与整体验收门禁

状态：待审阅/未执行。前置：10子计划通过独立验证。 范围：10个文件；不超过2子系统。共同契约见[总计划](00-mailbox-materials-master.md)。

## 需求描述

所有后端链路和前端host就绪后统一启用；浏览器不会混用新HTML和旧脚本；提供可执行发布与回退步骤。

不得改变：现有资源路径/talent上下文、缓存键三件套、旧邮件历史文件与发送控制；不自动发布生产。

范围外：仅写部署说明与启用代码，不在计划阶段执行迁移/下载/发信/上线。

## 关键不变量

### Invariant I-1：缓存一致
- Rule：index中styles.css、trust-reply-workbench.js、app.js以及两个新JS/CSS使用同一20260907-material-chat键；新组件在app之前加载，保留原workbench加载位置。
- Applies to：index资源与7个固定键测试
- Violation consequence：新旧资源混用导致缺函数/布局错乱
- 来源：K-frontend-cache-key-triad

### Invariant I-2：启用顺序
- Rule：01–10全部独立验证通过后才metadataOnly默认true及加载新组件；启动失败/测试未通过不能开启。DB迁移向前兼容，回退优先关闭新组件与metadataOnly，不删除附件索引/已存文件。
- Applies to：配置默认、资源注册、运行手册
- Violation consequence：半套系统上线或回滚丢数据
- 来源：original

### Invariant I-3：生产证据
- Rule：先测试邮箱真实MIME与生产布局测试环境验证，再由授权发布操作上线；记录真实版本、迁移版本、网络FETCH与截图。预览计时器不能充当后端测试。
- Applies to：发布门禁/人工验收
- Violation consequence：把mock预览当作线上已修复
- 来源：original

## 样式契约

所有新DOM必须映射[完整契约S-1至S-5](ui-style-contract.md)，既有DOM/CSS逐字见[baseline](frontend-baseline.md)。禁止新增inline style、未声明class、修改既有全局规则。button复用styles.css:802/838；专家原卡复用:1659；可信内部样式复用:7329起。

### S-1至S-5：资源注册

只注册08/10已定义文件与统一缓存键，不新增业务DOM/CSS；不改既有style/link作用域。script顺序workbench→expert-materials→mailbox-chat→app，CSS顺序styles→expert-materials→mailbox-chat。

## 现状审计

[A审计E1–E7](audit.md)与[代码检索全集](code-search-evidence.md)为本计划组成部分：包括表schema、全部生产写/读入口及跨模块交互。执行时先按符号复核，不以旧行号代替源码。 本项直接核对：E7与全部跨计划写读交互。

新增/改动读写关系：本项实现方案逐任务明确生产者和消费者；只允许表中列出的文件引入这些写路径。迁移/源定位/任务字段的完整类型与默认值见总计划持久化契约，禁止再自增冗余状态。

### 前端样式盘点

[改动前逐字DOM/CSS](frontend-baseline.md)保留source路径、行号及SHA256。基准primary #1e40af、bright #3b82f6、background #f5f7fb、text #1e293b/#475569/#94a3b8；按钮32px/12px字/7px圆角；资料字12/11px；原contacts-layout与全部全局class不就地改写。新namespace派生规则只作用本host，无需改变其他使用点；所有原模块事件和原class仍按S-4来源复用。

## 实现方案

1. [I-1，S-1..S-5] index.html只增加新资源引用，不复制预览整页。styles.css后加载expert-materials.css/mailbox-chat.css；script顺序为既有workbench、新expert-materials、新mailbox-chat、既有app。全部?v=20260907-material-chat。7个固定键测试仅同步新键与必要新资源顺序，不放宽检查。
2. [I-2] MailAttachmentStorageProperties.metadataOnly默认true（仍允许配置false紧急回退）。上线前必须06文件就绪/所有权适配已部署、05DMARC consumer已注册、worker配置与存储权限已测。11不得提前合入启用资源到未包含前置代码的版本。
3. [I-2/I-3] 新运行手册列：确认实际HEAD/工作树和最高Flyway版本→测试库备份/迁移验证→启动兼容后端→历史资料dryRun审计必要时显式修复→测试邮箱19+20附件与1000附件压测→开启metadataOnly→加载版本化前端→两入口/检查/分析/发送全链验收。生产只在后续明确发布授权时执行；当前计划不直接改线上。
4. [I-2] 回退：先停新提交并等worker空闲/明确关闭活动连接；metadataOnly=false仅回到旧收信行为（可能重新出现大附件慢问题，应暂停检查再决策）；前端移除新资源注册恢复旧host，后端保留nullable/readiness兼容代码；不回滚到完全不认识null的01之前二进制，不DROP新表、不删除新文件。已排队任务继续/暂停由运维明确设置，不因页面切换丢失。
5. [I-1..I-3，S-1..S-5] 执行完整Java/Node与MySQL迁移/查询/IMAP测试；人工清单开始时才从各plan A项导出-acceptance.md。最终记录UI与接口对应真实值，不能标“已上线”而无版本证据。

任务中的领域文件路径全部以本计划下表为准；类内DTO/辅助函数不另拆文件。若必须增加文件，先修订计划与≤10文件边界。

## 变更文件清单

| # | 精确路径 | 操作 |
|---|---|---|
| 1 | `src/main/resources/static/index.html` | 修改 |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/config/MailAttachmentStorageProperties.kt` | 修改 |
| 3 | `src/test/js/batchSendTaskConsoleVisualFix.test.js` | 修改 |
| 4 | `src/test/js/checkRepliesRelocation.test.js` | 修改 |
| 5 | `src/test/js/manualReplySubjectPrefill.test.js` | 修改 |
| 6 | `src/test/js/overlayAndDialogContrast.test.js` | 修改 |
| 7 | `src/test/js/ragKnowledgeBasePage.test.js` | 修改 |
| 8 | `src/test/js/ragWorkbenchRender.test.js` | 修改 |
| 9 | `src/test/js/trustReplyWorkbenchSharedMount.test.js` | 修改 |
| 10 | `docs/runbooks/mailbox-material-chat-rollout.md` | 新增 |

## 验收标准

- I-1：index所有7项资源键相同，新资源在app之前；全部7个旧键固定测试同步，旧键0命中生产index。
- I-2：仅11激活，metadataOnly=false兼容旧接收；回退保留新索引和旧文件可读，无破坏性SQL。
- I-3：mvn/node/MySQL/协议测试记录齐全；浏览器真实接口与附件服务器文件对应；A项人工未做不能称整体完成。
- S-1..S-5：注册无新DOM样式；实际加载的CSS与08/10逐字相同；正式布局、两host、分析/回复默认状态四视口截图。

仅运行后才能记录通过。先本项测试，再mvn test（Java11，含Node），涉迁移追加MigrationIntegrationTest；协议/数据库IT必须按总计划显式启用，不用默认跳过当证据。

## 人工验收清单

### A-1：完整发布演练
- 前置条件：独立预发环境含生产同款导航与测试邮箱，旧数据V117+已存PDF；01–10代码全部完成且测试通过。
- 操作步骤：1. 按运行手册升级并记录版本；2. 投递19+20附件检查；3. 两入口各看一次、选2件获取；4. 从AI获取所选并分析；5. 可信采用后用测试SMTP人工回复；6. 重新打开浏览器。
- 预期结果：39件索引完整且初查附件内容FETCH=0；两入口同状态；只选中件落地；默认折叠/展开及全部操作符合S契约；浏览器无旧脚本冲突；旧PDF仍可读。
- 覆盖：I-1/I-2/I-3/S-1/S-2/S-3/S-4/S-5；本项可观察需求与列明的回归/交互。

### A-2：回退与安全边界
- 前置条件：预发有活动下载、已存新文件与metadata-only记录，具备上一兼容版本。
- 操作步骤：1. 停新提交并等下载安全结束；2. 按手册回退UI/配置；3. 重新访问旧专家和邮箱；4. 再恢复新资源。
- 预期结果：无DROP/文件删除；旧文件正常下载，未就绪明确提示；恢复后原队列状态仍在；没有重复自动发信；全局控制/任务钻取可用。
- 覆盖：I-1/I-2/S-4；本项可观察需求与列明的回归/交互。
