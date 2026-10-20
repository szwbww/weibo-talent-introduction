# Child 03 approved execution brief

- Worktree: /Users/lukai/IdeaProjects/weibo-talent-introduction-fast-reply-identity-plan
- Branch: fast/reply-identity-plan
- Exact approved plan: docs/plans/2026-10-21/reply-identity-03-global-settings.md
- Plan identity: sha256:a47dc08ffaee7311503a82356490d42af4dadb0f2bbefe231782d33e92d80786
- Depends on: none
- Child product base: b0d2f278a9dddc0be0dcf6fbd2e9b5a947c8a135
- Approval: user `/FAST-P docs/plans/2026-10-21/reply-identity-plan.md` on 2026-10-21.

Read the complete exact child plan from disk; its `变更文件清单`, commands, invariants, downstream API contracts, and acceptance criteria are authoritative. This brief does not widen scope. Read `docs/design.md` before mail/conversation changes. Use execute-p; record immutable plan and target-worktree identities. Approved plans must not be edited. If scope or behavior needs amendment, stop with PLAN_CONFLICT.

## Global constraints

- Default SSOT is reply_snippet.is_default and main content. No new persistent field/table.
- Complete only trusted MISSING slots; never overwrite existing content. Comparison never rewrites final MIME bodies.
- New identity findings are NORMAL and explicitly confirmable. Preserve original STRONG, authentication, attachment, meeting, QA/RAG, draft, thread, sender-account and idempotency behavior.
- Context from actual server sending rules. Isolate asynchronous responses by full draft owner/context/revision. Settings save, completion and send confirmation remain separate actions.
- Preserve unrelated main-worktree changes; this isolated worktree starts at the master base. Never read runtime secrets from or connect to production services.
- JDK11 is mandatory. MySQL tests must actually run with mysqlIt=true, isolated Docker only. Existing skipped integration tests are not evidence of passing them.
- Keep one product writer. Do not create another worktree, push, merge, reset, amend, or rewrite history.
- No mid-flight build/lint/test/formatter runs: batch required commands after implementation. This orchestration constraint overrides incremental test sequencing, not final required evidence.
- Exercise changed runtime behavior beyond tests through an isolated smoke scenario. For frontend use a real browser with visual proof; do not substitute DOM stubs for browser verification. Record any missing mandatory evidence instead of claiming completion.
- Do not add unlisted changelog/docs; fast-p execution evidence documents the change within this explicit file scope.

## Authorized contract (verbatim child plan)

# 03 全局称呼与签名设置

状态：详细计划，待实施。执行顺序第一步（文件编号沿用原稿）。基线 `b0d2f278a9dddc0be0dcf6fbd2e9b5a947c8a135`，2026-10-21。代码证据见 [审计回执](reply-identity-evidence.md) E01–E05、E07、E21–E24；以下设计均为拟新增，不代表已实现。

## 需求描述

O1：所有专家使用同一组默认 SALUTATION、CLOSING；提供一次保存两项选择及正文的接口，供 02「默认设置」调用。O2：陈旧设置提交明确返回冲突，不把半份配置保存成功。

必须保持：N1 原片段管理 CRUD/启停/设默认仍可用；N2 名称、排序、启停、变体和其他类型默认不随新设置保存改变；N3 不修改已存在的草稿/历史邮件；N4 原来明确选择片段 ID 的消费者继续按指定 ID 解析。

范围外：每人/每专家覆盖、新表新列、变体编辑、历史数据修复、所有旧管理页面的乐观锁改造。此阶段可单独部署：新增接口可用，旧 UI 保持兼容；人工发送行为尚不变化。

## 关键不变量

### I-1: 复用全局默认事实
- Rule：默认由 `reply_snippet.is_default` 表达；SALUTATION/CLOSING 各选一个 enabled、content 非空的主片段。不以名字或固定 ID 定位，不随机取变体。没有有效默认返回 null，多个默认按 displayOrder、id 升序取第一个并返回配置诊断。
- Applies to：getIdentityDefaults、saveIdentityDefaults、后续 01 的期望值读取。
- Violation consequence：不同专家或接口看到不同默认。
- 来源：用户要求；E02:115–120；K-selectable-reply-frame-server-resolved-snapshot。

### I-2: 原子写入与并发边界
- Rule：新 PUT 在同一个事务内：锁定片段集合→读取当前快照→比较指纹→验证两个选择/正文/已有变体→保存两个 content/default 并清除同类型其他 default→提交。中途失败整份回滚。事务不能包含 HTTP/ES/SMTP。
- Rule：既有 create/update/setEnabled/setDefault/delete 也必须在业务读取前获取同一有序锁；`clearOtherDefaults` 在调用方事务内执行。新旧写请求的“读取旧行→写整行”不能交错。锁方案和空表/并发插入行为见 T1。
- Rule：新 PUT 拒绝与 GET 快照不同的 baseFingerprint，409/code=`REPLY_IDENTITY_DEFAULTS_CHANGED`。旧管理接口没有版本参数，仍保留最后提交覆盖语义；不得宣称能阻止旧浏览器主动提交陈旧表单。若旧管理先提交，后到的新设置必然识别冲突。
- Applies to：ReplySnippetService 上述写入口、新 GET/PUT。
- Violation consequence：首尾半更新、服务端旧行对象覆盖刚保存的内容。
- 来源：E02:221–299；原始并发设计。

### I-3: 窄更新保留变体
- Rule：新入口只改选中两行 content/isDefault/updatedAt 和同类型旧默认的 isDefault/updatedAt；其他列与 `content_variant` 逐行不动。未选中片段的弹窗编辑不属于请求。
- Rule：使用原行 copy；禁止调用现有 `update(...variants=emptyList())`，因为该方法会删除重建变体。对选中片段运行 requireValidTemplatePlaceholders、validateVariantTexts(newContent, existingVariantContents, REPLY_SNIPPET)；冲突拒绝，不自动去重/删变体。
- Applies to：新保存路径；旧管理原有显式变体编辑不改变。
- Violation consequence：修改签名导致变体或管理字段丢失。
- 来源：E02:246–251；E04:65–85。

### I-4: 单次快照读取与后续生效
- Rule：新 GET 只用一次有序查询读取片段行，在内存选出两默认、候选和指纹；不能分别查称呼、签名产生跨提交混合。指纹使用确定性长度前缀 UTF-8 序列 SHA-256，包含两类型所有行的 id/type/content/displayOrder/isDefault/enabled/name/updatedAt，按 type、id 排序；不含当前时间。
- Rule：返回的是全局配置，不是草稿补丁。保存不触发草稿或邮件写入。原默认 frame 消费者下一次读取自然使用新默认；明确选 ID 的消费者保留原语义。若选中 ID 的正文被编辑，按 ID 引用的模板也会读到新正文，设置 UI 必须说明这个影响。
- Applies to：GET/PUT 返回值、01 默认快照消费者、02 设置说明。
- Violation consequence：UI 误把半份配置或陈旧数据展示为最新，影响范围被隐瞒。
- 来源：E03（模板按 ref_id 读取）；K-manual-frame-three-consumers。

## 现状审计

路径前缀：`src/main/kotlin/com/weibo/talentintroduction/`。检索命令和逐行回执见 E03/E05；本节“写路径”指这些已检索生产源码、migration、scripts 的范围，不包含数据库外部手工操作。

|存储|Schema|写路径|读路径与交互|
|---|---|---|---|
|reply_snippet|`V47__create_reply_snippet.sql:1–10`；`V64__add_subject_variants_and_snippet_variant_group.sql:2`；`V96__add_name_to_reply_snippet.sql:1`。无每类型唯一默认约束；updated_at 有 ON UPDATE，但实体 save 可能带原值|`ReplySnippetService.kt:182 create / :222 update / :255 setEnabled / :261 setDefault / :275 delete / :296 clearOtherDefaults`；V47:12 初始化；V64/V96 变更列|同 service:20/23 管理读取、29 manual frame、42 ACK、58 options、83/100 selectable frame、115 默认选择。模板 service:333/575/616/757 按 ID 读取；LLM/RAG/workbench 具体调用 E03|
|content_variant|`V67__create_content_variant.sql:1–11`，owner/type/order/content/enabled，索引 idx_owner|ContentVariantService:65 replaceForOwner（先删后建）、88 deleteForOwner。调用方 ReplySnippetService:212/246/277；QaRuleManagementService:130 删除 QA owner（E05）|listByOwner:55、buildPool:126；本轮只读取所选片段变体进行兼容性验证。新保存不改变任何变体字段|
|前端片段缓存|`app.js` state.replySnippets / replySnippetsLoaded（E24）|loadReplySnippets 从 GET 更新；旧保存操作重新加载|02 每次打开新设置 GET；成功后使旧缓存失效，不能长期拿 ensureReplySnippetsLoaded 缓存当全局配置|

交互点：P1 新默认写→resolveManualFrame/defaultSelectableFrame；P2 旧管理写→新 GET/PUT 指纹冲突；P3 新主正文写→按 ID 引用的模板/已有变体；P4 新设置保存→已有草稿不变、后续补全读取新配置。

关键修正：初稿声称只锁新 PUT 即可兼容旧写，代码不支持：setEnabled:255 无事务，update:223 在 save:233 前先读取。另 `GlobalExceptionHandler.kt:82–84` 有 catch-all，不能仅抛 ResponseStatusException 就假定新接口必返回409。T3 明确局部异常映射，并以含全局 advice 的 HTTP 测试证明。

## 实现方案

### T1 锁住完整读改写过程（I-2）
文件：`reply/repository/ReplySnippetRepository.kt`、`reply/service/ReplySnippetService.kt`。

新增返回实体列表的查询（不引入未经本仓验证的 DTO projection）：
```sql
SELECT * FROM reply_snippet ORDER BY id FOR UPDATE
```
既有五个公开写方法与新 saveIdentityDefaults 使用 `@Transactional(isolation = Isolation.REPEATABLE_READ)`，入口第一项数据库操作为上述锁查询，随后才 findById/save/delete。不按提交选择顺序锁行，不先锁单片段再锁集合；新创建也先锁集合。低频配置写采用整张片段表的有序行/间隙锁，范围含其他类型以避免新增行及交叉 setDefault 的死锁次序。不是表 DDL 锁，不增加锁表。

真实 MySQL 用两个连接证明已有行、空表、并发插入、setEnabled/update/default/delete 与新 PUT 的结果。空表并发首次创建可能发生 InnoDB deadlock；允许一个完整事务失败并由原异常流程报告，不允许半提交或无限重试。本计划不承诺把数据库异常一律变409。锁等待设置仅用于测试，不改生产全局配置。不要通过 `this.xxx()` 依赖 Spring 自调用事务；控制器调用公开事务方法。

### T2 配置 API 数据和窄写入（I-1–I-4）
文件：`reply/service/ReplySnippetService.kt`。

新增 DTO 放该文件，避免扩散文件数：
```text
IdentityDefaultsResponse:
  salutation: {id,name,content} | null
  signature: {id,name,content} | null
  salutationOptions / signatureOptions: [{id,name,content,displayOrder,isDefault}]
  baseFingerprint: string
  diagnostics: [MISSING_SALUTATION_DEFAULT | MISSING_SIGNATURE_DEFAULT | MULTIPLE_DEFAULTS]
IdentityDefaultsSaveRequest:
  baseFingerprint: string
  salutation: {id: Long, content: String}
  signature: {id: Long, content: String}
```

GET 使用 `findAllByOrderBySnippetTypeAscDisplayOrderAscIdAsc()` 一次读，过滤两类型；候选为 enabled 且非空，按 displayOrder/id 排序。缺默认保留 null，禁止自动保存第一候选。PUT 新 content trim 后非空；每项不超过10000字符（新增接口边界，不改旧接口）；id 为正、两 slot 类型严格对应、enabled=true。不存在/禁用/类型错/未知占位符/变体重复→400，无写入。先验证两项再写，统一 `now`；返回同一事务新快照。不读取 ES 或调用 MailVariableService.buildVariables；只复用其占位符语法校验。

### T3 路由、认证与冲突映射（I-1/I-2）
文件：`reply/controller/ReplySnippetController.kt`。

增加 GET/PUT `/api/reply-snippets/identity-defaults`。沿用 `AuthWebConfig.kt` `/api/**` 的会话认证（E22），不引入新的管理员角色假设。专用冲突异常定义在 service 文件，controller 局部 `@ExceptionHandler` 返回 `ResponseEntity.status(409)` 与 `{code:"REPLY_IDENTITY_DEFAULTS_CHANGED",message:"默认设置已被修改，请重新加载后核对"}`。控制器不直接写 repository。局部异常 handler 仅处理该异常，不调整全局异常行为。

### T4 可执行验证（I-1–I-4）
文件：`src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt`、同目录 `ReplyIdentityDefaultsIntegrationTest.kt`。

现有 unit mock 明确提供锁查询数据；验证 save 被调用前两个 slot 已验证。新 IntegrationTest 采用 E21 的 MySQL 8.0.36 + 真实 DataSourceTransactionManager/事务代理模式；使用 Spring Data JDBC 真实 repository bean，不用 mock 证明锁。用 latch 控制并发，禁止 sleep 猜执行顺序。HTTP 测试放同文件的测试类，挂载真实全局 advice 和 AuthWebConfig：未登录、400、409、200 分别断言。测试只连接隔离容器，禁止生产库。

## 变更文件清单

5 文件；1 个独立子系统（reply 片段管理），0 个新持久字段。

|文件|作用|
|---|---|
|src/main/kotlin/com/weibo/talentintroduction/reply/repository/ReplySnippetRepository.kt|有序锁查询|
|src/main/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetService.kt|原子设置、旧写路径统一锁、DTO|
|src/main/kotlin/com/weibo/talentintroduction/reply/controller/ReplySnippetController.kt|GET/PUT/局部409映射|
|src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplySnippetServiceTest.kt|规则与既有CRUD回归|
|src/test/kotlin/com/weibo/talentintroduction/reply/service/ReplyIdentityDefaultsIntegrationTest.kt|真实事务、并发、HTTP|

## 验收标准

- I-1：两个用户 GET 相同默认；legacy/defaultSelectableFrame 在新保存之后均读取选中首尾；无默认为 null；测试种子 ID 非1/3/25，证明无硬编码。
- I-2：第二项非法、模拟第二次 save 失败都不落第一项；同一指纹两个并发 PUT 且内容不同，恰一个200、另一个409；旧管理在新 PUT 前完成→新PUT409；新PUT先完成→后续旧请求读取最新服务端行，不覆盖未请求更改的字段。用真实事务覆盖空表与并发 create，不将旧表单 last-write-wins 宣称为 CAS。
- I-3：比较选中/未选行和变体整行快照；新保存变体数、ID、order、content、enabled、时间不变；名称/启停/排序/其他slot均不变；现有 CRUD 仍能主动编辑变体。
- I-4：配置 GET 无跨提交混合；按 ID 的模板新读取内容更新，明确选择其他 ID 不变；保存过程对草稿/邮件表写入为0。
- 命令：`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -DmysqlIt=true -Dtest=ReplySnippetServiceTest,ReplyIdentityDefaultsIntegrationTest test`。确认测试非 skipped，再运行完整 `mvn test`。Docker 不可用须如实记录未验证，不能以 mock 或 skipped 算通过。

## 人工验收清单

本阶段未含新 UI，可在测试环境用浏览器开发者工具发送认证 API 请求；不得对真实专家发信。账号甲乙为两个独立测试会话。以下创建均经现有回复片段后台。

### A-1: 全局统一与默认消费者
- 前置条件：创建称呼甲 `Dear Dr ${expertFamilyName},`、称呼乙 `Hi ${expertName},` 和签名甲 `Best regards,\n${senderName}`（换行需实际输入），记录实际 IDs。
- 操作步骤：1. 用户甲 GET 新接口。2. 用返回指纹 PUT 选择称呼乙和签名甲。3. 用户乙 GET。4. 在现有默认 frame 生成入口生成草稿。
- 预期结果：甲乙看到相同 IDs；新草稿以 `Hi ` 开头并带当前专家姓名，签名带实际发件人。未部署02时不要求新按钮存在。
- 覆盖：O1、I-1/I-4、P1。

### A-2: 主正文编辑与变体/引用
- 前置条件：签名甲有两条不同变体；一个模板按 ID 引用签名甲；GREETING另有默认；准备一封已保存草稿。
- 操作步骤：1. PUT只改签名主正文为 `Kind regards,\n${senderName}`。2. 打开旧管理查看变体及其他字段。3. 重新预览按ID引用的模板。4. 打开原草稿。
- 预期结果：两条变体、名称/排序/启停、GREETING 默认不变；模板主内容预览使用 Kind regards（关闭变体选项）；旧草稿逐字不变。
- 覆盖：N2/N3、I-3/I-4、P3/P4。

### A-3: 冲突及原子失败
- 前置条件：两个浏览器先 GET 同一配置指纹。
- 操作步骤：1. 甲通过旧管理改称呼主正文。2. 乙提交旧指纹。3. 乙重新GET后提交有效称呼、空签名。4. 重新GET。
- 预期结果：步骤2 HTTP409/code REPLY_IDENTITY_DEFAULTS_CHANGED；步骤3 HTTP400；步骤4称呼保留甲的值、签名保留原值，未出现半更新。
- 覆盖：O2、I-2、P2。

### A-4: 原管理与显式选择回归
- 前置条件：独立测试片段丙、丁；工作台显式选择丙，默认选择丁。
- 操作步骤：1. 在旧管理创建/编辑/启用/禁用/设默认/删除一个临时片段。2. 新设置改丁的正文。3. 工作台保留显式丙重新解析。
- 预期结果：旧动作仍可完成；显式丙仍为丙的原文，未被丁替代；临时片段删除后列表不再显示。
- 覆盖：N1/N4、I-1/I-4。
