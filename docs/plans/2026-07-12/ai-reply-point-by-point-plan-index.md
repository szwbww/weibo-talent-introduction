# AI 回复格式保真与逐点回答开发计划包

> 日期：2026-07-12  
> 状态：待执行  
> 背景：最新草稿被动作清理器压成一行；模型不可用时按 QA rule 扁平拼接；模型可用时虽然有 request checklist，但没有“问题→事实”显式映射。按 create-p 上限拆成 6 个顺序子计划。

## 总体结果

1. 安全草稿的换行、空行、编号、签名逐字保留；删除违规 CTA 时仅删除违规句，不重排其他内容。
2. 每个专家请求都形成独立的“问题—依据—状态”记录，并按原邮件顺序交给生成和 fallback。
3. 多问题邮件固定逐点回复；单问题保持自然短邮件；无依据项在对应编号下说明待确认。
4. DeepSeek 不可用时仍输出结构化逐点草稿，而不是 QA 段落堆叠。
5. 前端明确展示 `LLM 已使用` 或具体降级状态，不再只显示笼统“DeepSeek 不可用”。
6. AI 草稿预览与最终人工富文本发送使用同一变量渲染语义，避免 `${expertName|Professor}` 字面展示或外发。

## 执行顺序

1. `ai-reply-01-format-preservation.md`
2. `ai-reply-02-request-fact-matrix.md`
3. `ai-reply-03-structured-generation-fallback.md`
4. `ai-reply-04-generation-state-feedback.md`
5. `ai-reply-05-rendered-preview.md`
6. `ai-reply-06-rich-send-variable-rendering.md`

## 全局不变量

- 不修改自动外发 `QaMatchService.match()` 的 supersede、gap、handoff、autoReplyEnabled 语义。
- `sendQaRuleIds` 仍只是真实匹配/运营选择；问题矩阵不得扩大邮件审计关联。
- 不访问 Scholar/Scopus/URL，不触发 enrichment。
- CTA 授权规则、Flash/Pro 选择、loading、requestSeq、mailRecordId 精确选择保持。
- style few-shot 只影响表达，不作为事实来源。
- 任何预览变量替换都从 raw template 开始；最终外发再次按实际 sender account/contact 渲染。

## 跨计划接口

- 计划 2 产出 `RequestFactItem` 列表；计划 3 是唯一正文消费者。
- 计划 3 产出保留模板变量的 raw `draftText`；计划 5 只生成 rendered preview，计划 6 为最终外发兜底渲染。
- 计划 4 的 `generationState` 只描述生成路径，不改变 `usedLlm/mode/qaRuleIds`。
- 每个子计划独立跑定向测试；全部完成后跑 `mvn test` 与 `node --test src/test/js/*.test.js`。
