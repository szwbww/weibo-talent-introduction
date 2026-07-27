# QA 重构 02：事实卡数据与管理基础

## 需求描述

新增独立 `answerBody` 作为 QA 的权威事实正文，并把 QA 管理页简化为“标题、分类、匹配短语、匹配方式、优先级、事实正文、启用状态”。运营可逐条清洗事实，但此阶段旧自动回复仍读取 `reply_body`，因此不会因事实清洗改变线上外发。

必须不变：

- 旧自动 QA、人工单规则、旧组装台、模板兼容读取仍使用 `reply_body`。
- 规则 ID、分类、keywords、priority、enabled 与历史 `mail_record_qa_rule` 关联不变。
- 回复片段变体编辑器不变。

Out of scope：切换 AI/自动回复到 `answerBody`；引入 `replyPolicy`；删除旧列；清洗 29 条规则的实际业务内容（由运营在此功能上线后完成）。

## 关键不变量

### Invariant I-1：双正文单向隔离
- Rule：新增 `answer_body` 后，存量只做一次 `answer_body=reply_body` 初始化；运营更新 `answerBody` 只写 `answer_body`，绝不反写 `reply_body`。新建规则因 `reply_body NOT NULL` 暂未移除，首次保存将相同事实文本写入两列，并固定旧开关为转人工安全值。
- Applies to：V79、createRule、updateRule、setRuleEnabled。
- Violation consequence：清洗事实时直接改变旧自动邮件，失去 expand→switch 安全边界。
- 来源：original；K-qa-rule-runtime-vs-migration-writes。

### Invariant I-2：事实正文不是邮件
- Rule：新建/更新 `answerBody` 必须非空、≤4000 字符、占位符合法；拒绝 HTML 文档标签和邮件框架短语：开头 `Dear/Hi/Hello`，`Thank you for your email`，结尾 `Best regards/Kind regards/Sincerely`，以及 `Please let us know if you have any further questions`。
- Applies to：QaFactBodyPolicy、create/update API。
- Violation consequence：QA 再次承担语气、称呼、签名，grounded 输出重复且机械。
- 来源：original。

### Invariant I-3：旧复杂字段只读冻结
- Rule：本阶段管理 UI 不展示 `replySubject/sectionTitle/coverageKeys/variants/autoReplyEnabled/handoffRequired`；API 为滚动兼容可继续返回旧字段，但 create/update 不得修改存量旧值。非空 QA `variants` 请求必须返回 400；`coverageKeys` 请求字段忽略并保留 existing，直到 grounded 引擎切换。
- Applies to：controller DTO、management service、app.js。
- Violation consequence：运营继续维护两套正文/标签，或旧字段被无意清空。
- 来源：original；K-content-variant-input-read-contract。

### Invariant I-4：删除仍清理 QA 变体
- Rule：虽然停止新增 QA 变体，deleteRule 仍调用 `deleteForOwner(QA_RULE,id)`；历史/非生产环境的孤儿数据不能残留。
- Applies to：deleteRule。
- Violation consequence：owner_id 重用或审计时出现悬空变体。
- 来源：original。

### Invariant I-5：历史关联不可破坏
- Rule：已有 `mail_record_qa_rule` 外键引用的规则，删除仍由数据库 RESTRICT；本计划不级联、不重建规则 ID。
- Applies to：deleteRule、V79。
- Violation consequence：历史外发事实来源丢失。
- 来源：K-audit-selected-source。

## 样式契约

### S-1：QA 事实卡编辑弹窗
- 复用：`.modal-panel` (`styles.css:2198-2210`)、`.form-grid` (`803-809`)、`.span-2` (`886-889`)、通用 label/input/select/textarea (`815-861`)、`.checkbox-row` (`864-884`)、现有 `.var-editor-wrap/.var-editor-toolbar/.var-insert-*` (`styles.css:5580-5660` 附近)。
- 新增：无新 class、无 CSS。
- DOM 结构：

```html
<form id="qaRuleForm" class="form-grid">
  <label class="span-2">事实标题<input name="displayName" placeholder="如：项目组织方身份" required></label>
  <label>规则 ID<input name="id" disabled placeholder="系统自动生成"></label>
  <label>事实分类<select name="categoryId" required><option value="">选择事实分类</option></select></label>
  <label class="span-2">专家表达/匹配短语<input name="keywords" placeholder="如: who are you, organization, official" required></label>
  <label>匹配方式<select name="matchMode"><option value="ANY">ANY（命中任一）</option><option value="ALL">ALL（全部命中）</option></select></label>
  <label>匹配优先级<input name="priority" type="number" required></label>
  <label class="span-2">标准事实正文
    <div class="var-editor-wrap">
      <div class="var-editor-toolbar">
        <div class="var-insert-wrap">
          <button type="button" class="var-insert-btn" data-var-insert-target="qaRuleAnswerBody">+ 插入变量 ▾</button>
          <div class="var-insert-menu" hidden></div>
        </div>
      </div>
      <div class="var-validation-hint" id="varHint-qaRuleAnswerBody" hidden></div>
      <textarea id="qaRuleAnswerBody" name="answerBody" rows="8" placeholder="只写已核验事实，不写称呼、寒暄和签名" required></textarea>
    </div>
  </label>
  <label class="checkbox-row"><input name="enabled" type="checkbox">启用事实</label>
  <div class="form-actions modal-actions">
    <button class="button primary" type="submit">保存事实</button>
    <button class="button secondary" type="button" id="clearQaRuleBtn">取消</button>
  </div>
</form>
```

- 禁止项：新增 inline style；新增近似表单 class；修改 `styles.css`；在事实正文下恢复 coverage/variant/subject/auto/handoff 控件。

### S-2：QA 列表
- 复用：现有 `.panel/.table-wrap/table/.badge/.button`；状态分别使用 `.badge.ok` (`styles.css:766-770`) 与 `.badge.warn` (`772-776`)。
- 新增：无新 class、无 CSS。
- DOM 结构：表头固定为 `<tr><th>ID</th><th>事实标题</th><th>分类</th><th>匹配短语</th><th>事实正文</th><th>优先级</th><th>当前旧路由</th><th>状态</th><th>操作</th></tr>`；事实正文只显示转义后的前 120 字符；状态单元格只含一个 `.badge.ok` 或 `.badge.warn`。
- 禁止项：原始 HTML 注入；coverage 标签；变体数量 badge；reply subject 列。

## 现状审计

### `qa_rule`
- Schema/mapping：V1 创建；关键列 `keywords TEXT NOT NULL`、`reply_body TEXT NOT NULL`、两个布尔开关、`enabled`；后续有 `display_name/section_title/supersedes_children/coverage_keys`。Spring Data JDBC 由 `QaRule` 全字段映射。
- Write paths：
  1. Flyway V3/V17/V18/V38/V40/V41/V44/V45/V46/V52/V57/V63/V65/V68/V70/V75/V76/V77 等 INSERT/UPDATE。
  2. `QaRuleManagementService.createRule()` — `ruleRepository.save(domain)`。
  3. `updateRule()` — `existing.copy(...)` 后 save。
  4. `setRuleEnabled()` — copy enabled 后 save。
  5. `deleteRule()` — 先清变体，再 `deleteById`；历史外键可能阻止。
- Read paths：
  1. QA 管理 controller/list。
  2. `QaMatchService`：enabled、keywords、matchMode、priority、replyBody、旧开关、supersedesChildren。
  3. `AiReplyDraftService/AiReplyPointByPointComposer/AiReplyHighRiskClaimValidator/LlmStitchService`：replyBody/coverageKeys。
  4. `AutoMailReplyService/AutoReplyPreviewService/PendingMailOperationService`：通过 match/repository 消费 replyBody。
  5. `MailComposeTemplateService`：仅 legacy QA block 兼容读取；子计划 01 后无正常生产引用。
- Interaction points：V79/管理页写 answerBody → 子计划 04 grounded 读；管理页更新必须不改变旧 runtime 读 replyBody；delete 与 mail_record 外键交互。

### `content_variant`
- Schema/mapping：V67；`owner_type/owner_id/variant_order/content/enabled`，无 owner 外键。
- Write paths：`ContentVariantService.replaceForOwner/deleteForOwner`；QA 管理 create/update/delete；回复片段管理 create/update/delete。
- Read paths：QA 管理列表、QaMatchService、PendingMailOperationService、MailComposeTemplateService；回复片段/frame/template。
- Production：总数 0。
- Interaction points：本计划停止 QA replace 写，但保留 delete；回复片段路径不得受影响。

### QA 管理 API/UI
- API：`QaRuleManagementController` request/response 当前公开完整邮件主题/正文、两个开关、variants、coverageKeys；`GET /coverage-keys` 供前端加载。
- UI：`loadQa()` 同时请求分类、规则、coverage catalog；`saveQaRule()` 收集 coverage checkbox 和 QA variant textarea；modal 基线见 `index.html:1550-1604`。
- Interaction points：controller response → `state.qaRules` → QA 表格、模板编辑器、工作台；因此旧 `replyBody` response 暂时保留供非本页消费者。

### 前端样式盘点
- 可复用 class：见 S-1/S-2。
- 设计 token：`--primary #2563eb`、`--text-main #1e293b`、`--text-muted #94a3b8`、`--border rgba(15,23,42,.11)`、`--radius-sm 7px`，`styles.css:1-59`。
- DOM 约定：modal 固定 `.modal-shell > .modal-backdrop + .panel.editor-panel.modal-panel > .panel-head + form.form-grid`。
- 改动前基线：`index.html:1573-1598` 包含 coverage、replySubject、replyBody、variants、两个开关；本计划按 S-1 整块替换。

## 实现方案

### T1：V79 扩展列
- 文件：`src/main/resources/db/migration/V79__add_qa_answer_body.sql`
- `ADD COLUMN answer_body TEXT NULL` → `UPDATE ... SET answer_body=reply_body WHERE answer_body IS NULL` → `MODIFY answer_body TEXT NOT NULL`。
- 不修改任何旧正文、开关、ID、updated_at；迁移 SQL 不重新保存实体。遵守 I-1、I-5。

### T2：domain 与事实正文策略
- 文件：
  - `src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt`
  - `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaFactBodyPolicy.kt`
- `QaRule` 增加 `answerBody: String`，物理映射 `answer_body`；保留全部旧属性。
- `QaFactBodyPolicy.validate()` 实现 I-2；只校验新写，不扫描/阻断迁移回填的历史值。
- 遵守 I-1、I-2。

### T3：管理 service 切到事实正文
- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt`
- create：校验 answerBody；`replyBody=answerBody` 仅满足旧列；`autoReplyEnabled=false/handoffRequired=true`；不写 QA variants；coverage 空。
- update：只更新 category/keywords/matchMode/priority/displayName/answerBody/enabled；旧 replySubject/replyBody/两个开关/section/supersede/coverage 全部从 existing 保留。
- variants 非空立即拒绝；delete 保留变体清理。遵守 I-1..I-5。

### T4：API 兼容演进
- 文件：`src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt`
- request 新增必填 `answerBody`；过渡期接受 nullable `replyBody/variants/coverageKeys`，但 service 按 I-3 处理。
- response 新增 `answerBody`；保留 deprecated `replyBody/autoReplyEnabled/handoffRequired/variants/coverageKeys` 到总计划结束。
- `/coverage-keys` 暂保留给旧前端缓存/回滚版本，标记 deprecated；新 UI 不调用。
- 遵守 I-1、I-3、I-5。

### T5：QA 管理 UI 简化
- 文件：
  - `src/main/resources/static/index.html`
  - `src/main/resources/static/app.js`
- 按 S-1 替换 modal；`loadQa()` 只请求 categories/rules；删除 QA coverage render、QA variant fill/save/validation；不能删除 reply snippet 的共用 variant functions。
- `fillQaRuleForm/saveQaRule` 使用 answerBody；变量工具目标改为 `qaRuleAnswerBody`。
- 表格按 S-2 渲染；旧路由 badge 只读展示，不可编辑。遵守 I-3、S-1、S-2。

### T6：测试
- 文件：
  - `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt`
  - `src/test/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementControllerTest.kt`
  - `src/test/js/qaFactCardEditor.test.js`
- 覆盖：迁移回填；update 单向隔离；create 安全旧开关；事实正文规则；旧字段 preserve；variants reject；reply snippet variant DOM/function 未删除；UI 不请求 coverage endpoint。
- 测试必须逐项断言 I-1 至 I-5、S-1、S-2。

## 变更文件清单

| # | 文件 | 变更 |
|---|---|---|
| 1 | `src/main/resources/db/migration/V79__add_qa_answer_body.sql` | 新增 answer_body |
| 2 | `src/main/kotlin/com/weibo/talentintroduction/qa/domain/QaRule.kt` | domain 映射 |
| 3 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaFactBodyPolicy.kt` | 新增事实正文校验 |
| 4 | `src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementService.kt` | 事实卡写路径 |
| 5 | `src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt` | API 演进 |
| 6 | `src/main/resources/static/index.html` | 简化 QA modal/table |
| 7 | `src/main/resources/static/app.js` | QA 管理交互 |
| 8 | `src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleManagementServiceTest.kt` | service 测试 |
| 9 | `src/test/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementControllerTest.kt` | 新增 API 测试 |
| 10 | `src/test/js/qaFactCardEditor.test.js` | 新增前端契约测试 |

共 10 个文件、2 个子系统（QA backend + QA admin frontend），符合限制。

## 修正记录

| 日期 | 项 | 修正 |
|---|---|---|
| 2026-07-17 | T6 测试范围 | 增加 `src/test/js/qaCoverageKeyEditor.test.js`：该历史契约测试断言本计划明确删除的 coverage UI，必须随功能移除而删除或改写为事实卡契约；否则总计划的全量 JS 验证失败。 |

## 验收标准

- I-1：update answerBody 后 SQL 断言 `answer_body=新值`、`reply_body=旧值`；create 两列相同且旧开关为 `0/1`。
- I-2：每个禁止短语、空白、4001 字、非法 placeholder 返回 400；纯事实和合法变量通过。
- I-3：UI/API update 后旧字段逐列不变；非空 variants 返回 400；新 UI 源码不请求 `/coverage-keys`，不出现 `qaRuleVariantsContainer`。
- I-4：删除无历史外键的新规则后对应 QA variant 为 0；REPLY_SNIPPET variants 保留。
- I-5：删除被 `mail_record_qa_rule` 引用的规则失败，历史关联仍可查。
- S-1：`styles.css` diff 为空；表单 DOM 与契约逐元素一致；无新增 inline style/class；旧复杂控件不存在。
- S-2：表头固定 9 列；空态/数据行 `colspan=9`；事实正文转义且最多 120 字符；badge 只复用契约 class。
- 集成：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaRuleManagementServiceTest,QaRuleManagementControllerTest,QaMatchServiceTest,ReplySnippetServiceTest test
node --test src/test/js/qaFactCardEditor.test.js src/test/js/composeTemplatePreview.test.js
```

## 人工验收清单

### A-1：存量事实卡回填
- 前置条件：V79 前任选 3 条规则，记录 id/reply_body。
- 操作步骤：1. 执行迁移；2. 打开 QA 页面；3. 编辑其中一条事实正文并保存。
- 预期结果：三条初始 answerBody 与原 reply_body 相同；被编辑规则刷新后显示新 answerBody；数据库 reply_body 仍是旧值。
- 覆盖：I-1、observable outcome。

### A-2：事实正文拒绝邮件框架
- 前置条件：打开任意事实卡。
- 操作步骤：分别提交 `Dear Professor...`、`...Best regards`、纯事实 `The application does not charge experts a service fee.`。
- 预期结果：前两次显示明确 400 错误且不保存；第三次保存成功。
- 覆盖：I-2。

### A-3：简化界面
- 前置条件：进入 QA 管理。
- 操作步骤：查看列表并打开编辑弹窗。
- 预期结果：列表为 S-2 的 9 列；弹窗没有覆盖能力、内容变体、邮件主题、自动/交接开关；有事实正文和启用开关。
- 覆盖：I-3、S-1、S-2。

### A-4：旧自动回复回归
- 前置条件：选择一封可命中 QA 的历史来信，记录 auto preview。
- 操作步骤：只修改该 QA 的 answerBody；再次查看 auto preview。
- 预期结果：preview 的旧回复正文完全不变，证明 runtime 仍读 reply_body。
- 覆盖：I-1、qa_rule 写→旧 runtime 读 interaction point。

### A-5：回复片段变体回归
- 前置条件：新建/编辑一个 REPLY_SNIPPET 变体。
- 操作步骤：保存、刷新、再次编辑。
- 预期结果：变体数量与文本完整保留；QA 页面简化不影响共用变体函数。
- 覆盖：must-NOT-change、I-4。

### A-6：历史外键回归
- 前置条件：选择线上已有 `mail_record_qa_rule` 引用的 QA ID。
- 操作步骤：调用删除。
- 预期结果：删除失败；历史邮件审计仍显示该规则 ID。
- 覆盖：I-5。
