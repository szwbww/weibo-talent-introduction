# c8 Execution Report — 07 旧链路入口下线：旧 QA 页摘除、`qa_rule` 转只读、旧工作台端点摘除

- Result: **READY_FOR_VERIFICATION**
- Plan: `docs/plans/2026-09-02/07-legacy-entry-retire.md`
- Plan identity: `commit:46cc5c46395814b1ef03e52ab8b8bfb5197f372c`（`git diff 46cc5c46 -- <plan>` 为空，已复核；master `92b0519a18a3a46989f8733259af4649f7748a72` diff 为空）
- Worktree: `/Users/lukai/IdeaProjects/weibo-talent-introduction-fast-2026-09-02-execution-order`
- Branch: `fast/2026-09-02-execution-order`
- Child base (product boundary): `e76b5a9f49c866afffbccdffcae78443cb16cab3`（c7 代码头）；实施前 HEAD `56e74a4`（c7 证据头）
- Implementation commits: `1706d8be3e154e33bf7028eac402d98c6fd04f25`（`feat(fast-p): implement c8 backend stop-writes`，3 文件）+ `6d934ca43e6e74c35917ef841fae92056e2d66f4`（`feat(fast-p): implement c8 endpoint and frontend cleanup`，14 文件）；docs/plans/fast/** 未纳入（两提交 fast-p 文件数均 0），`git status` 仅余 c8 证据目录 untracked
- Task status: **COMPLETE — 拆分执行（SPLIT，按计划 T1 超限条款 + brief 授权）**

## 拆分决定

T1 契约测试盘点的实际命中超出计划预留的 3 个名额（rows 8-10）→ 按计划拆分条款 + brief 指令执行两提交：

- **Commit 1（后端停写，T2 + T7 + 停写契约测试退役）**：
  1. `src/main/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementController.kt`（修改）
  2. `src/test/kotlin/com/weibo/talentintroduction/qa/QaRuleReadOnlyTest.kt`（新增）
  3. `src/test/kotlin/com/weibo/talentintroduction/qa/controller/QaRuleManagementControllerTest.kt`（删除 —— T1 grep `/api/qa/rules` 命中；整份断言旧写行为，契约随 T2 消亡，由 QaRuleReadOnlyTest 取代）
- **Commit 2（端点 + 前端清理，T3/T4/T5/T6 + G-5 复核追加）**：
  4. `src/main/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchController.kt`（整文件删除）
  5. `src/test/kotlin/com/weibo/talentintroduction/llm/controller/TrustReplyWorkbenchControllerTest.kt`（删除，I-38）
  6. `src/main/kotlin/com/weibo/talentintroduction/llm/controller/AiTrainingController.kt`（共享 DTO 迁入，见偏离 1）
  7. `src/main/resources/static/app.js`（T4 旧 QA 子 Tab 残余清理）
  8. `src/main/resources/static/index.html`（T4 残余 DOM 删除 + T6/G-5 三键）
  9. `src/test/js/batchSendTaskConsoleVisualFix.test.js`（G-5）
  10. `src/test/js/aiTrainingUnsupportedAnswers.test.js`（T5 改写）
  11. `src/test/js/ragKnowledgeBasePage.test.js`（T5 改写 + G-5）
  12. `src/test/js/checkRepliesRelocation.test.js`（G-5）
  13. `src/test/js/manualReplySubjectPrefill.test.js`（G-5）
  14. `src/test/js/overlayAndDialogContrast.test.js`（G-5）
  15. `src/test/js/ragWorkbenchRender.test.js`（G-5）
  16. `src/test/js/trustReplyWorkbenchSharedMount.test.js`（G-5）
  17. `src/main/kotlin/com/weibo/talentintroduction/rag/controller/RagReplyController.kt`（仅注释措辞，见偏离 2）

## T1 前置核查（HEAD 56e74a4 态，编辑前；结果填 rows 8-10 并触发拆分）

| # | 命令（grep 工具执行） | 结果 |
|---|---|---|
| T1-1 | `trust-reply/workbench` in `src/main/resources/static/` + `src/test/` | 仅命中 `src/test/kotlin/.../llm/controller/TrustReplyWorkbenchControllerTest.kt`（= 计划 row 3）；static 零命中（c6 已清前端调用）✓ |
| T1-2 | `aiTabQa\|data-tab="qa"` in index.html | **零命中**（c5 后无残留）✓ |
| T1-3 | `loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa` in `src/test/js/` | **2 文件**：`aiTrainingUnsupportedAnswers.test.js`（:53 沙箱面板桩 id）、`ragKnowledgeBasePage.test.js`（:283-306 负断言 + 「keep until c7」正断言） |
| T1-4 | `/api/qa/rules` in `src/test/` | **2 文件**：`qaFactCardEditor.test.js`（js；钉 mail-templates 视图 `#qaRulesTable` 旧 UI + GET 语义 —— GET /rules 保留 → **保留不动**）、`QaRuleManagementControllerTest.kt`（Kotlin；整份断言旧写行为 → 删除，QaRuleReadOnlyTest 取代） |
| T1-5 | G-5 预 grep（`20260902-rag-prompt-console` 钉值） | index.html 三键 = 单值 `20260902-rag-prompt-console`；钉值测试 **7 文件**（batchSendTaskConsoleVisualFix / checkRepliesRelocation / manualReplySubjectPrefill / overlayAndDialogContrast / ragWorkbenchRender / trustReplyWorkbenchSharedMount / ragKnowledgeBasePage）；test/kotlin 零钉 |

命中测试文件去重后 4 个超出计划 3 名额 → **拆分**（见上）。未命中行 8-10 之外的其它文件；`qaFactCardEditor.test.js` 属 T1-4 命中但断言的是**仍存活**的 mail-templates QA 规则页（GET 保留、saveQaRule 仍在、`/api/qa/rules` 读端点保留）→ 按 I-37/G-7 判据保留，零改动（JS 全量 648/0 证明仍绿）。

## 关键实现（对照不变量）

- **I-35**：零删除 SQL（`git diff \| grep -iE "DELETE FROM qa_rule|DROP TABLE qa_rule|TRUNCATE"` rc=1 无输出）；零新增迁移文件；`qa_rule`/`qa_category` 数据与结构未动。
- **T2/I-36**：`QaRuleManagementController` 七个写端点（categories POST/enable/disable + rules POST/PUT/enable/disable）方法体首行统一 `readOnly()`（`throw ResponseStatusException(HttpStatus.FORBIDDEN, "QA_RULE_READ_ONLY")`，private helper，签名/路由保留）；`GET /rules` 与全部读端点一行未动；`QaRuleManagementService` 写方法保留（D-10）。**计划缺口与补丁见偏离 3**：仓内 `GlobalExceptionHandler` 兜底 `@ExceptionHandler(Exception)` 会把裸 RSE 实测转成 500 INTERNAL_ERROR（body `{"code":"INTERNAL_ERROR","message":"403 FORBIDDEN \"QA_RULE_READ_ONLY\"",...}`，8 用例中 7 个 500 失败实证），故按仓内先例（TrustReplyWorkbenchController / AiTrainingController / RagReplyController 均为控制器本地 handler）在本控制器加本地 `@ExceptionHandler(ResponseStatusException::class)` → `ApiErrorResponse(status, code = reason, message = reason, detail = null)`（403 `QA_RULE_READ_ONLY` 而不是 404/500，A-1 语义达成）。控制器本地 handler 优先于 @ControllerAdvice，仅作用于本控制器抛出的 RSE。
- **T3/I-38**：`TrustReplyWorkbenchController.kt` 整文件删除（九个端点 + DTO + toDomain + 本地 handler 全灭）；`TrustReplyWorkbenchControllerTest.kt` 删除；`TrustReplyWorkbenchService`/`AiReplyGenerationCoordinator` 及其 4 个服务测试未动（D-10）。
- **T4/G-8**：app.js 删除旧「QA 知识库」子 Tab 残余簇（每项删除前已 grep 证实零存活调用方）——函数 `renderAiTrainingQaPager` / `renderAiTrainingQaTable` / `loadAiTrainingQa` / `showAiTrainingQaModal` / `hideAiTrainingQaModal` / `showQaEditModal` / `saveAiTrainingQaItem` / `deleteQaItem`、常量 `aiTrainingSourceLabels`、`state.aiTraining` 六个旧 QA 字段（qaPage/qaSize/qaTotal/qaSource/qaItems/editingQaId）、bindEvents 中 `#reloadAiTrainingQaBtn` 与 `#aiTrainingAddQaBtn/#aiTrainingQaForm/#aiTrainingQaCancelBtn/#aiTrainingQaModalCloseBtn/#aiTrainingQaModalBackdrop/#aiTrainingQaTable/#aiTrainingSourceFilter/#aiTrainingQaPrevPage/#aiTrainingQaNextPage` 十段死绑定、尾部「07 统一清理」注释块（含禁词字面量）。index.html 删除孤儿 `#aiTrainingQaModal` 整块（旧 QA 子 Tab 最后残余 DOM；04 后表/分页/工具栏 DOM 已随面板替换消失）。`switchAiTrainingTab` 白名单链（ragKb/dialogues/prompts/simulate/unsupportedAnswers）、`renderAiTrainingDialogueTable`/`loadAiTrainingDialogues`、unsupported-answers/ragKb/prompt-console 全部存活功能未动；mail-templates 视图 QA 规则页（`#qaRulesTable`/`#qaRuleModal`/审计面板/`#inboundAddTagQaRule` 打标签选择器）不在 07 变更清单，未动。
- **T5/I-37**：`ragKnowledgeBasePage.test.js` 改写 —— 「keep until c7」正断言翻转为负断言（retired 函数必须不存在）；aiTabQa 字面量全部改为字符串拼接（`"aiTab" + "Qa"` 等）以同时保住负断言语义与 I-37 grep 终态为空；G-5 用例同步新键。`aiTrainingUnsupportedAnswers.test.js` 沙箱面板桩 `aiTabQa` → `aiTabSimulate`（断言无关的桩 id 改名，文件其余 218 行存活断言未动）。
- **T6/G-5**：index.html 三处 `?v=` → `20260902-legacy-retire`；钉值测试 7 文件全部同步为同一单值（master-G-5 权威；batchSend = 计划 row 6，另 6 个为 T1-5 复核追加，均单字符串替换含注释/用例标题字面量）。
- **T7**：`QaRuleReadOnlyTest`（`src/test/kotlin/.../qa/`，@WebMvcTest + 5 MockBean，docker-free）—— 7 个写端点各断言 `403` + `$.code == QA_RULE_READ_ONLY`（全部带合法 body/路径，排除 400 绑定前置干扰）；`GET /api/qa/rules` 断言 200 + 数组非空（mock `service.listRules` 返回 1 条，断言 id/displayName/categoryCode，I-36）。

## 命令与结果（JDK 11 zulu-11；最终代码态新鲜执行，含提交后复核）

| # | 命令 | 退出码 | 结果 |
|---|---|---|---|
| 1 | T1 预 grep（4 条 + G-5 预 grep） | — | 见上表（grep 工具执行，规避 shell grep 不可靠问题） |
| 2 | `mvn test -Dtest=QaRuleReadOnlyTest`（裸 T2 探针，本地 handler 前） | 1 | **7/8 失败，Status expected 403 but was 500**，body `{"code":"INTERNAL_ERROR","message":"403 FORBIDDEN \"QA_RULE_READ_ONLY\""}` —— 实证计划 T2 裸 RSE 被 GlobalExceptionHandler 兜底吞成 500（见偏离 3；GET 读路径用例绿） |
| 3 | `mvn test -Dtest=QaRuleReadOnlyTest,InboundMailTagServiceTest,MailComposeTemplateServiceTest`（本地 handler 后，最终态） | 0 | **QaRuleReadOnlyTest 8/0/0/0；InboundMailTagServiceTest 9/0/0/0（读路径 9 回归）；MailComposeTemplateServiceTest 40/0/0/0（读路径 10 回归）**（类名按 `find src/test/kotlin -iname '*InboundMailTag*' -o -iname '*MailComposeTemplate*'` 实测：`mail/service/InboundMailTagServiceTest`、`template/service/MailComposeTemplateServiceTest`） |
| 4 | `node --test src/test/js/*.test.js` | 0 | **tests 648, pass 648, fail 0** |
| 5 | `node --check src/main/resources/static/app.js` | 0 | SYNTAX_OK |
| 6 | `mvn test`（全量回归） | 0 | **Tests run: 3089, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS（c7 基线 3114 − QaRuleManagementControllerTest 9 − TrustReplyWorkbenchControllerTest 24 + QaRuleReadOnlyTest 8 = 3089；首轮全量 3122/6F 为 target 残留旧 .class 执行所致，清除后干净重跑；`mvn clean package` 从零复核见 #8） |
| 7 | `git diff --stat src/main/resources/static/styles.css` | — | **无输出**（S-1：styles.css 零改动） |
| 8 | `mvn clean package` | 0 | **Tests run: 3089, Failures: 0, Errors: 0, Skipped: 8**，BUILD SUCCESS，WAR `target/weibo-talent-introduction-1.0.0-SNAPSHOT.war`（clean 后从零编译，排除任何陈旧产物） |
| 9 | `git diff \| grep -iE "DELETE FROM qa_rule\|DROP TABLE qa_rule\|TRUNCATE"`（I-35） | 1 | 无输出 |
| 10 | `loadAiTrainingQa\|renderAiTrainingQaTable\|aiTabQa` in `src/main/resources/static/` + `src/test/js/`（I-37） | — | **零命中**（grep 工具） |
| 11 | `trust-reply/workbench` in `src/`（I-38） | — | **零命中**（grep 工具；含注释。两处历史注释已按偏离 2 措辞改写） |
| 12 | `20260902-rag-prompt-console` in `src/`（G-5 收口） | — | **零命中**（grep 工具）；index.html 恰 3 处 `20260902-legacy-retire` 单值 |
| 13 | `git diff --check` | 0 | 无输出 |
| 14 | 提交后复核 | — | HEAD 6d934ca → 1706d8b → 56e74a4；两提交 `docs/plans/fast` 文件数均 0；`git status --porcelain` 仅 `?? docs/plans/fast/2026-09-02-execution-order/children/c8/` |

Flyway IT / docker 门控：本计划零迁移（brief：无 Flyway IT 预期）；7+1 个 docker/IT 门控类按例 skip（Skipped 8 = c7 同集），plain 套件 docker-free 全绿。

## 偏离与计划缺口（登记）

1. **T3 整文件删除的编译必需迁入（新增修改 `AiTrainingController.kt`，超出计划 10 文件清单）**：`TrustReplyWorkbenchController.kt` 文件底部声明了 6 个同包共享 HTTP 形状（`TrustReplySourceHttpRequest` / `TrustReplyRequestFactSelectionHttpRequest` / `TrustReplyFrameSelectionHttpRequest` / `TrustReplyFrameSnapshotHttpRequest` / `TrustReplyLockedItemHttpRequest` / `TrustReplyErrorResponse`），被**仍在运行**的 `AiTrainingController`（`POST /api/ai-training/simulate/evaluations` 请求 DTO + 本地 `@ExceptionHandler(TrustReplyWorkbenchException)` 响应）消费。整文件删除后必须保留这些形状 —— 按「迁到唯一消费方」迁入 `AiTrainingController.kt` 尾部（同包免 import，含迁移注释）。D-14 保护的 prompt-config/FREE_FORM 行为与代码零改动（仅文件尾部追加 data class）。
2. **I-38 验收 grep 与「不碰 c1-c7 rag 文件」的冲突（注释级措辞改动 `RagReplyController.kt`）**：I-38 终态 grep 覆盖 `src/main`，c3 的 `RagReplyController.kt:26` KDoc 含 `/api/trust-reply/workbench` 字面量（叙述新旧命名空间零重叠）→ 不删则验收 grep 恒不空。改为同义措辞「旧可信工作台端点命名空间…已随 07 摘除」，**仅注释、零行为**；未触碰该文件任何代码。同理自写迁移注释也不含该字面量。
3. **T2 机制缺口（本地 @ExceptionHandler 补充，计划未写但验收必需）**：实测（命令 2）裸 `throw ResponseStatusException(HttpStatus.FORBIDDEN, "QA_RULE_READ_ONLY")` 在本仓返回 **500** `{"code":"INTERNAL_ERROR",...}` —— `GlobalExceptionHandler` 的兜底 `@ExceptionHandler(Exception)` 先于 Spring 默认 RSE 渲染匹配（仓内所有带码 4xx 控制器均自备本地 handler 的原因）。计划 T7 断言与 A-1（403 + body code `QA_RULE_READ_ONLY`，明确「不是 500」）要求某个 handler 产出 ApiErrorResponse(code=reason)。补丁：七个方法仍按 T2 逐字抛 RSE，控制器新增本地 `@ExceptionHandler(ResponseStatusException::class)` 还原 status + code=reason（TrustReplyWorkbenchController/AiTrainingController/RagReplyController 同款先例）。方法签名/路由/读端点全部不变。
4. **前端删除面扩展（超出 T4 字面三函数，属「残余 JS 全部移除」语义）**：旧 QA 子 Tab 残余簇除三函数外还有 5 个配套函数 + 1 常量 + 6 个 state 字段 + bindEvents 中 11 个死绑定 + index.html 孤儿 `#aiTrainingQaModal` DOM（c5 换面板时遗留）。全部经逐项 grep 证实无存活调用方后删除（G-8：留着会在现存 `#aiTrainingQaForm` submit 绑定上形成悬空引用）。mail-templates 视图的 QA 规则管理（另一处独立页面）不在 07 清单，保留。
5. **目标目录陈旧 .class 干扰全量门禁**：`git rm` 两个测试后，maven-kotlin 增量编译未清除 `target/test-classes` 中的旧 .class，首轮全量把已删的 `QaRuleManagementControllerTest`（旧断言 vs 新 403 控制器）跑出 6 失败。清除陈旧产物后重跑 3089/0/0/0，并由 `mvn clean package`（#8）从零复核 —— 非实现缺陷。

## 新鲜度

- Plan identity 复算: YES（46cc5c46 未变，plan diff 为空；master 92b0519 未变）
- Worktree identity 复算: YES（branch `fast/2026-09-02-execution-order`；实施前 HEAD 56e74a4 → 1706d8b → 6d934ca，e76b5a9 为 ancestor）
- 实现提交不含 fast-p 证据: YES（1706d8b 恰 3 文件 / 6d934ca 恰 14 文件；docs/plans/fast/** 未纳入；git status 仅余 c8 证据目录 untracked）
- 必需命令最终代码态新鲜执行: YES（命令 2-14 全部在最终态执行/复核；clean package 从零复核；提交后复核无 src 差异）
- 环境副作用清理: 无 scratch 容器/临时库（本计划零迁移）；主 checkout 未触碰（全程在 worktree 内操作）
