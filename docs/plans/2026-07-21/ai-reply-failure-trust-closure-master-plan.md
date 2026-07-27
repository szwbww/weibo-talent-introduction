# AI 回复失败可见性与信任闭环总计划（方案 2）

## 目标

解决当前真实邮件暴露的四个问题：LLM 超时后页面仍像成功、fallback 可被采用、问题依据显示“未命名事实”、生成上下文与收件人身份不够可信。最终只允许 `usedLlm=true && generationState=LLM_USED` 的结果进入“采用到人工回复”；失败结果保留为内部 QA 参考，不得伪装成可发送邮件。

## 顺序子计划

| 顺序 | 计划 | 独立可见结果 | 数据库 |
|---|---|---|---|
| 1 | [ai-reply-08-llm-failure-workbench-contract.md](ai-reply-08-llm-failure-workbench-contract.md) | 精确识别超时/限流/网络/空响应；红色失败提示；失败禁用采用；修复“未命名事实”前端解析 | 无 |
| 2 | [ai-reply-09-fallback-reference-intent-parity.md](ai-reply-09-fallback-reference-intent-parity.md) | fallback 改为不可发送的 QA 参考；7 问场景按原子意图正确区分完整/部分/缺失 | V81 仅幂等追加关键词 |
| 3 | [ai-reply-10-history-context-recipient-identity.md](ai-reply-10-history-context-recipient-identity.md) | 有界引用历史邮件；当前来信不重复；历史不充当事实；不再出现 `Dear EMAIL-*` | 无 |

## 顺序约束

1. 必须先执行、复验、部署子计划 1。
2. 子计划 2 依赖子计划 1 的“失败不可采用”门禁；迁移前必须完成线上关键词基线核对。
3. 子计划 3 最后执行；它与前两项都修改 `AiReplyDraftService.kt`，禁止并行开发或并行合并。
4. 每个子计划单独提交、单独跑 `fix-v`、单独人工验收；前一项未签发，不进入下一项。

## 总体完成标准

- LLM 超时页面固定显示失败原因、QA 参考属性和不可采用状态。
- 失败结果不写入成功会话轮次，不可通过旧草稿按钮绕过。
- 页面不出现“未命名事实”或内部 rule ID。
- 示例 7 问得到 `完整 4 / 部分 1 / 缺失 2`，没有依据的问题不硬答。
- LLM 成功时读取有界历史用于上下文连续性，但所有事实仍只来自当前审核事实。
- 收件人无真人姓名时使用模板 fallback（通常为 `Professor`），绝不渲染 `EMAIL-*`、ORCID 或邮箱为称呼。
- 自动发送现有 `usedLlm + generationState + readiness` fail-closed 门禁不放宽；人工从编辑器自行撰写并发送的链路不新增历史草稿状态门禁。

