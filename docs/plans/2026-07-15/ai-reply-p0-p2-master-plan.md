# AI 回复逐点覆盖、事实约束与人工审核 P0-P2 总计划

## 目标

修复最新专家回信模拟暴露出的系统性问题：完整问题被省略导致正文从 `2.` 开始、复合问题被误标为完整、公司信息问题混入信任说明、原始问题直接充当机械标题、人工只要随便编辑即可绕过缺口拦截，以及缺少可审计的质量指标。

最终结果：系统先把每条专家请求拆成原子意图，再以 QA 规则的审核能力标签逐项绑定证据；模型只生成带意图与来源引用的结构化答案；编号、标题、称呼和签名由后端统一组装；缺失依据不会伪装成完整邮件，操作员必须逐项确认后才能发送，全部动作可审计。

## 当前根因

1. `AiReplyDraftService.isPartialCoverage()` 仅按整条 request 的少量短语检查 QA 正文，无法识别“selection + matching”“responsibilities + deliverables”“contract + finance + IP”等复合意图。
2. `RequestFactItem` 只有 request 级 `factRuleIds/status`，没有 intent 级所需能力、命中依据和缺失原因。
3. grounded LLM 协议是“一条 request 一个 answer”，无法证明同一 request 内所有子问题均被回答。
4. `AiReplyPointByPointComposer` 跳过 UNSUPPORTED request 并保留原 index，故第一项缺失时正文直接从 `2.` 开始；标题仍来自原问题清理，不是稳定的邮件标题。
5. V68 把公司名称/注册地址关键词和正文追加到“Agency credentials”大规则，精确公司信息问题会带出保密、政府合作和信任说明。
6. 前端发送闸门只阻止“正文完全未改”的缺口草稿，任意编辑即可绕过；后端也不知道该富文本回复来源于缺口 AI 草稿。
7. 现有审计只记录实际选用 QA 规则，不记录 AI 初稿覆盖质量、阻止直发次数和人工缺口确认。

## 全局不变量

- `sendQaRuleIds` 仅是实际匹配/显式选择的外发审计子集；prompt 全集兜底不得进入 `mail_record_qa_rule`。（K-ai-reply-prompt-vs-send-rule-ids）
- `GROUNDED/PARTIAL/UNSUPPORTED`、缺失原因、coverage key 只属于操作端控制数据，不得进入外发正文。（K-grounding-status-ui-only）
- 研究匹配结论必须同时有专家画像与项目范围依据；不触发 Scholar/Scopus/外网抓取，不新增“自动获取 CV/资料”。（K-research-fit-dual-evidence）
- AI 训练模拟保持只读；审计写入仅发生在收发件 AI 初稿、发送拦截和成功人工确认链路。
- `QA_MATCHED` 单问题、`FREE_FORM`、模型下拉选择、loading 遮罩、CTA 拦截、raw/rendered 模板变量边界保持现状。
- 对话范例仍只影响风格和表达策略，不能成为公司、资金、合同、IP、项目或专家研究事实来源。
- 自动回复 `match()/detectGap()` 不消费新增 intent coverage；改动只进入人工组装/AI `gapItems` 链路。（K-gap-items-compose-only）
- 不修改已应用迁移；当前工作区已有 V74，新增迁移固定从 V75 开始。

## 执行顺序

| 顺序 | 优先级 | 子计划 | 可独立验收结果 | 依赖 |
|---|---|---|---|---|
| 1 | P0 | [01-readiness-and-compound-coverage](./ai-reply-01-p0-readiness-and-compound-coverage.md) | 4/5/6 等复合项不再误报完整，response 有权威 readiness | 无 |
| 2 | P0 | [02-company-identity-rule-split](./ai-reply-02-p0-company-identity-rule-split.md) | 公司名称/注册地址只命中精简事实，不混入信任长文 | 1 可并行验证，迁移先于 4 |
| 3 | P0 | [03-readiness-ui](./ai-reply-03-p0-readiness-ui.md) | 两个入口明确显示 READY/NEEDS_REVIEW/BLOCKED，缺口原稿不可直发 | 1 |
| 4 | P1 | [04-qa-coverage-keys-backend](./ai-reply-04-p1-qa-coverage-keys-backend.md) | QA 规则具备可校验、可管理的覆盖能力标签 | 2（V75→V76） |
| 5 | P1 | [05-qa-coverage-keys-ui](./ai-reply-05-p1-qa-coverage-keys-ui.md) | 运营可查看/编辑 QA 覆盖能力 | 4 |
| 6 | P1 | [06-intent-coverage-matrix](./ai-reply-06-p1-intent-coverage-matrix.md) | request 拆为 intent，4/5/6 按子问题精确标缺失 | 4 |
| 7 | P1 | [07-intent-output-and-claim-validation](./ai-reply-07-p1-intent-output-and-claim-validation.md) | 模型逐 intent 引用来源，后端统一标题/编号/签名并拦截高风险增写 | 6 |
| 8 | P2 | [08-review-audit-backend](./ai-reply-08-p2-review-audit-backend.md) | 后端要求 AI 缺口确认，并记录生成/拦截/确认审计 | 6、7 |
| 9 | P2 | [09-review-confirmation-ui](./ai-reply-09-p2-review-confirmation-ui.md) | 操作员逐项勾选、备注，任意编辑不再绕过 | 8 |
| 10 | P2 | [10-quality-metrics](./ai-reply-10-p2-quality-metrics.md) | QA 审计页展示遗漏率、部分覆盖率和直发拦截数 | 8 |

## 发布切片

- P0 发布门：计划 1-3 全部通过。允许继续保留 request 级旧协议，但必须醒目标识不完整草稿。
- P1 发布门：计划 4-7 全部通过。P0 临时短语启发式必须删除，coverage key + intent catalog 成为唯一覆盖判定。
- P2 发布门：计划 8-10 全部通过。邮件发送必须以后端 review payload 校验为准，前端编辑差异不再是审核依据。

## 迁移与运行时数据约束

- V75 只拆分公司身份规则；上线前导出目标线上规则，合并运营对 `keywords/reply_body` 的修改，禁止盲目覆盖。（K-qa-rule-runtime-vs-migration-writes）
- V76 新增 `coverage_keys` 并按 `reply_subject/id` 回填已知能力；未知规则保持空标签，由运营补录，不推断不存在的业务事实。
- coverage key 表示“该 QA 正文已审核覆盖的能力”，不表示关键词命中，也不表示模型可以跨 request 借用该规则。

## 工作区保护

当前 `app.js/index.html/styles.css` 存在未提交的批量发送控制台改动，且有 V74 与相关测试未跟踪。执行前逐文件查看 diff；所有前端计划按窄锚点修改，不覆盖、不格式化整个文件，不改 V74；每个前端子计划同时回归 `batchSendTaskConsoleVisualFix.test.js`。

## 总体验收样例

使用 Pracheta Janmeda 邮件应形成 7 个 request group：

1. Research fit and enterprise projects
2. Company details
3. Programme purpose and structure
4. Selection and enterprise matching
5. Responsibilities and deliverables
6. Contractual, financial and IP arrangements
7. Next stages

其中 selection、deliverables、enterprise project types 或 financial detail 若无审核能力标签，必须在 intent coverage 中明确缺失；不得用匹配流程重复代替项目类型，不得用“use your expertise”代替 deliverables。公司信息段只能输出公司法定名称与注册地址。正文不得含内部状态、缺失 key、确认提示、Markdown 粗体或原始长问题标题。

## 总体验证

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
```

每个子计划开发后单独调用 `fix-v`；前一计划通过才进入下一计划。人工验收时从各子计划的 `## 人工验收清单` 导出 acceptance 文件，本阶段不创建勾选文件。
