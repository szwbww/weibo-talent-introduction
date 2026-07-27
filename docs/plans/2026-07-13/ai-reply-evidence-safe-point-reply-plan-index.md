# AI 回复逐点作答与依据隔离修复总计划

## 目标

修复本次专家邮件暴露出的四个链路问题：跨行问题被截断并移到末尾、研究匹配问题丢失 QA 依据、`PARTIAL/UNSUPPORTED` 内部状态进入邮件正文、前端只显示笼统缺口而无法定位到具体问题。

最终可观察结果：系统按专家原邮件顺序识别 7 个请求；有审核依据的请求逐点回答；无完整依据的状态只在操作端提示，不写入外发正文；未人工修改的缺口草稿不能被直接外发。

## 根因摘要

1. `QaMatchService.extractRequestItems()` 先拼 bullet、后拼问号句，天然打乱来源顺序；问句正则禁止跨换行，三行研究匹配问题仅保留最后一行。
2. `AiReplyDraftService.resolveQaRules()` 对研究请求强制把 `factRuleIds` 置空，只按画像 warning 判定 `GROUNDED`，无法完成“专家画像 × 项目范围”的双边比较。
3. `AiReplyPointByPointComposer` 将 `PARTIAL_CONFIRMATION` 与 `UNSUPPORTED_TEXT` 直接拼进邮件，内部审核状态泄漏。
4. LLM 返回整封自由文本；后端没有 request index 级结构校验，无法阻止漏项、额外项或内部状态复述。
5. `requestCoverage` 已由两个接口返回，但前端只消费 `unsupportedRequests`，没有逐项显示 `PARTIAL`，也没有在采用后保留“需人工补充”状态。

## 顺序计划

| 顺序 | 计划 | 独立结果 | 依赖 |
|---|---|---|---|
| 1 | [ai-reply-01-source-order-request-extraction.md](./ai-reply-01-source-order-request-extraction.md) | 跨行问题完整提取，所有请求按原邮件 offset 排序 | 无 |
| 2 | [ai-reply-02-grounding-evidence-semantics.md](./ai-reply-02-grounding-evidence-semantics.md) | 每项状态由真实 QA 正文与研究画像共同确定 | 计划 1 |
| 3 | [ai-reply-03-structured-answer-materialization.md](./ai-reply-03-structured-answer-materialization.md) | LLM 先输出 request-index JSON，后端组装正文；内部状态永不进入正文 | 计划 2 |
| 4 | [ai-reply-04-coverage-warning-ui.md](./ai-reply-04-coverage-warning-ui.md) | 训练模拟与收发件逐项提示缺口；原样缺口草稿不得直接发送 | 计划 3 |

## 全局不变量

- `sendQaRuleIds` 仍只代表真实匹配/显式选择的发送审计规则，不得被 prompt fallback 全集扩大。（来源: K-ai-reply-prompt-vs-send-rule-ids）
- 训练模拟与收发件 AI 回复继续共用 `AiReplyDraftService.generate()`，不得在 controller 各复制一套生成逻辑。（来源: K-ai-generate-single-freeform-seam）
- `QA_MATCHED` 单问题 verbatim、`FREE_FORM` fallback、模型选择、CTA 最终拦截、raw/rendered 变量边界保持不变。
- 不调用 Google Scholar、Scopus 或其他外部资料抓取；研究信息只读现有专家画像，缺失时提示。（来源: K-ai-reply-profile-absence-warning）
- 不修改 QA 表结构、邮件表结构、自动外发策略和 QA 审计表。

## 总体验收样例

使用本次 Pracheta Janmeda 完整邮件，`requestCoverage` 必须依次为：

1. `Based on my research profile ... enterprise projects your team manages?`（三行合并为完整一句）
2. company full name and registered location
3. programme purpose and structure
4. researcher selection and enterprise matching
5. responsibilities and deliverables
6. contractual, financial and IP arrangements
7. next stages

生成正文不得包含：`This still needs confirmation`、`not covered by the approved information`、`STATUS:`、`PARTIAL`、`UNSUPPORTED`。第 3 与原错误第 7 不得复制同一段；若同一审核事实确实覆盖两个问题，后项只能使用短交叉引用。

## 执行与验证顺序

每个子计划单独开发、单独运行其定向测试、单独使用 `fix-v` 验证；前一计划通过后再进入下一计划。全部完成后运行：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
```

人工验收开始时，分别从四个子计划的 `## 人工验收清单` 导出对应 `-acceptance.md`；本阶段不创建勾选文件。
