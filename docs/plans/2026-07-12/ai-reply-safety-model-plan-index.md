# AI 回复安全、依据可读性与模型选择开发计划包

> 日期：2026-07-12  
> 状态：待执行  
> 原则：这是一个需求包，按 create-p 的文件/子系统上限拆成 6 个顺序子计划；每个子计划独立部署、验证。不得合并跳序实施。

## 总体可观察结果

1. Google Scholar/Scopus URL 内的 `?` 不再被识别成专家请求；“缺少已审核依据”只显示人能读懂的完整请求。
2. AI 只注入与本封邮件明确请求相关的已审核知识；不再把全量训练 QA 放入 prompt。
3. 专家未提出或运营未明确授权时，草稿禁止索要 CV/材料、提议会议、预约通话或扩展到其他下一步动作。
4. 已有专家画像不足时只返回可读提示，不访问外部 URL、不触发 enrichment、不让 AI 自行补资料。
5. AI 回复训练与收发件箱共用模型选择契约，可选 `DeepSeek V4 Flash`、`DeepSeek V4 Pro`；默认 Flash。
6. 两入口生成期间继续显示现有 loading 遮罩；模型选择器在请求期间禁用，旧响应不得覆盖新邮件/新模型结果。
7. 对话范例保留为 style-only few-shot；不作为事实来源，并移除会锚定 CV/会议的负向字面示例。

## 执行顺序

1. `ai-reply-url-safe-request-extraction.md`
2. `ai-reply-content-boundary-curation.md`
3. `ai-reply-targeted-knowledge-context.md`
4. `ai-reply-action-policy-runtime.md`
5. `ai-reply-model-selection-backend.md`
6. `ai-reply-model-picker-frontend.md`

## 全局不变量

- 不修改自动收信/自动外发 `QaMatchService.match()` 的 supersede、handoff、autoReplyEnabled 语义。
- 不新增 Scholar/Scopus/URL 抓取；现有只读画像查询仍是研究依据唯一来源。
- `sendQaRuleIds` 仍只表示真实匹配/运营显式选择，不因 prompt 知识扩大。
- 模拟接口不写邮件、审计、联系人状态或任何业务表。
- 模型选择仅影响当前 AI 草稿请求，不持久化为联系人/邮件属性。
- 现有 loading、反馈、requestSeq、mailRecordId 精确选择能力必须保留。

## 发布门禁

- 每个子计划先跑其定向测试，再跑完整 `mvn test` 与 `node --test src/test/js/*.test.js`。
- 子计划 2 上线前必须导出线上 `qa_rule.id IN (23,24)` 与 `ai_training_qa.source_ref='MATERIALS_LIGHT'` 当前值；有运营修改则先合并，禁止迁移盲覆盖。（来源: K-qa-rule-runtime-vs-migration-writes）
- 子计划 5 实施前由部署配置确认两个真实 provider model id；浏览器/API 只使用稳定枚举，不直接暴露 provider id。

