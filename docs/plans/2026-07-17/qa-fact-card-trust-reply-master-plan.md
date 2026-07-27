# QA 事实卡与可信回复重构总计划

## 目标

把现有“关键词 + 完整邮件正文 + 覆盖标签 + 内容变体”的 QA 规则，重构为单一事实源：一条规则只描述一个可核验问题及其标准事实；人工工作台选择事实，LLM 负责自然表达，服务端负责事实引用、风险校验和发送权限。

本文件是执行索引。因改造横跨模板、QA 数据、LLM、自动回复和人工工作台，且 `qa_rule` 需要分两次新增共享字段，按 `create-p` 硬限制拆成 6 个可独立发布、独立验证的子计划。

## 已确认产品决策

1. QA 管“能说什么”，不再保存完整邮件、称呼、致谢、签名或 CTA。
2. QA 规则不再维护 `coverageKeys`；问题与事实的关联由原子化规则、匹配短语和服务端 request→fact 解析产生。
3. QA 规则不再拥有内容变体；回复片段 `REPLY_SNIPPET` 的变体继续保留。
4. `replyPolicy` 只有 `AUTO / REVIEW / NEVER`：
   - `AUTO`：事实可进入自动回复；
   - `REVIEW`：事实只可进入人工草稿；
   - `NEVER`：内部保留，不可进入外发正文。
5. 只要命中 QA 事实，统一走 grounded 生成；不再存在“单规则直接外发/多规则拼接”的产品分支。
6. LLM 失败、结构化响应非法、事实引用非法或高风险声明不受支持时，自动回复必须 fail closed 转人工；人工端只展示确定性事实摘要，不自动发送。
7. 组装台改名“可信回复工作台”：展示专家请求、候选事实、草稿与校验；不直接拼接 `answerBody`。
8. 项目介绍邮件后续单独调整；本改造必须先把现有 `INTRODUCTION` 模板与 QA 解耦，保证其当前输出不变。

## 当前线上基线（2026-07-17，只读核验）

- `qa_rule`：29 条，启用 28 条。
- 旧开关组合：27 条 `(auto_reply_enabled=1, handoff_required=0)`；2 条 `(0,1)`。
- `coverage_keys` 非空：15 条。
- `content_variant`：0 条；其中 QA 变体 0 条、回复片段变体 0 条。
- `mail_compose_template_block`：1 个 `QA_RULE` 块，属于启用的 `INTRODUCTION` 模板，引用规则 `About the talent program`。
- `mail_record_qa_rule`：12 条历史关联，涉及 9 条 QA 规则；历史关联必须保留，不能删除被引用规则。
- 工作区已有用户改动：`docs/releases.json`；全部子计划不得覆盖或格式化该文件。

## 全局关键不变量

### G-1：事实正文与邮件表达分离
- `answerBody` 只能保存事实；称呼、感谢、段落衔接、签名和低压力下一步由回复片段/生成器负责。
- 违反后果：机械拼接、重复致谢、AI 感和不可追溯承诺重新出现。

### G-2：事实选择与发送审计分离但一致
- prompt 可使用的事实集合与实际外发关联集合不能混淆；只有实际被草稿引用且最终采用的规则 ID 可写入 `mail_record_qa_rule`。
- `mail_record_qa_rule.ordinal` 按专家原问题顺序、再按候选事实稳定顺序写入，不再表示运营手工排列邮件段落。
- 来源：K-ai-reply-prompt-vs-send-rule-ids、K-audit-selected-source、K-request-facts-not-flat-pool。

### G-3：自动回复 fail closed
- 自动外发必须同时满足：全部请求有事实、全部事实 policy=`AUTO`、LLM 实际成功、结构化引用合法、风险校验通过。
- deterministic fallback 只供人工查看，绝不自动发送。

### G-4：人工发送仍走唯一安全 seam
- 最终发送统一走 `sendManualRichReply()` 的占位符校验、最终账号/专家变量渲染、邮件记录与审计；工作台不得建立第二条 SMTP 路径。
- 来源：K-manual-rich-render-before-send、K-rich-reply-qa-audit-reuse。

### G-5：回复片段变体不受影响
- 只移除 `owner_type=QA_RULE` 的能力；`REPLY_SNIPPET` 主体、变体、稳定 seed 和 frame 顺序保持现状。
- 来源：K-content-variant-input-read-contract、K-manual-frame-three-consumers。

### G-6：项目介绍邮件输出不变
- 在 QA `reply_body/answer_body` 演进前，所有模板 `QA_RULE` 块先快照为 `CUSTOM_TEXT`；变量在实际 render 时仍按原逻辑解析。
- 当前生产 QA 变体数必须保持 0；若部署前不为 0，停止发布并人工选择需要快照的具体变体。

### G-7：兼容采用 expand→switch→contract
- V79 新增 `answer_body`，V80 新增 `reply_policy`；不能在同一子计划增加两个共享字段。
- 旧 `reply_body/auto_reply_enabled/handoff_required/coverage_keys` 至少保留两个稳定发布周期，只作为兼容影子，不作为新逻辑事实源。

### G-8：人工状态与自动发送状态分权
- `READY/NEEDS_REVIEW/BLOCKED` 对人工端是校验结果和操作提示；对自动回复是发送闸门。
- 人工最终发送仍必须通过高风险事实校验和模板变量校验；非高风险措辞编辑不因旧草稿 readiness 被永久阻断。
- 对 K-ai-generation-observability-not-send-gate 的处理：保留其“历史草稿状态不能单独阻断人工发送”规则；本改造新增的是对最终正文的实时事实校验，不以历史状态作权威。

## 执行顺序

| 顺序 | 子计划 | 独立可验收结果 | 依赖 |
|---|---|---|---|
| 1 | [01 模板边界](./qa-refactor-01-template-boundary.md) | 介绍信模板不再实时读取 QA，输出保持一致 | 无 |
| 2 | [02 事实卡基础](./qa-refactor-02-fact-card-foundation.md) | QA 后台可维护 `answerBody`，不再维护覆盖标签和 QA 变体 | 1 |
| 3 | [03 回复策略](./qa-refactor-03-reply-policy.md) | 单一 `replyPolicy` 取代两个运营开关，兼容旧运行时 | 2 |
| 4 | [04 Grounded 引擎](./qa-refactor-04-grounded-engine.md) | request→fact 取代 coverageKeys；所有命中事实的 AI 草稿统一 grounded | 3；事实正文已人工抽查 |
| 5 | [05 自动回复切换](./qa-refactor-05-auto-reply-rollout.md) | 自动回复使用 grounded 草稿，任何生成/校验失败均转人工 | 4 |
| 6 | [06 可信回复工作台](./qa-refactor-06-trust-workbench.md) | 组装台变为事实控制台，取消拼接、QA 变体和事实自由文本 | 4；建议在 5 后发布 |

## 发布门与回滚

### 发布门 R-1：模板解耦
- 线上 `QA_RULE` 模板块数为 0。
- `INTRODUCTION` 同一专家、同一账号、同一 seed 的 subject/text/html 与发布前快照逐字一致。

### 发布门 R-2：事实卡准备
- 29 条存量 `answer_body` 均非空。
- policy=`AUTO` 的规则必须人工抽查并满足：无称呼、无签名、无 CTA、无未证实金额/期限/保证。
- 未完成事实清洗的规则先改为 `REVIEW`，不得用“先上线后补内容”绕过。

### 发布门 R-3：Grounded 影子验证
- 在切自动回复前，用历史来信回放比较旧命中与新 request→fact 结果。
- 新引擎不能把无事实项误标为 READY；宁可转人工，不可扩大自动发送。

### 回滚策略
- 子计划 1-3 为 additive/兼容变更，可回滚应用版本；数据库保留新列。
- 子计划 4、6 出现问题时回滚应用版本；旧列、旧模板兼容读取和 additive schema 均保留，不需要回写数据。
- 子计划 5 使用现有 `LLM_AUTO_REPLY_ENABLED` 作为自动 grounded kill switch：先保持 `false` 做预览/人工验证，通过发布门后再开启；异常时先关闭开关，所有 QA 自动候选转人工，再回滚应用版本。
- 本轮不 drop 旧列、不删历史 `mail_record_qa_rule`、不删除 `QA_RULE` 兼容读取分支；破坏性 contract cleanup 另起计划，至少观察两个稳定发布周期。

## 总体验收样例

1. 单一身份质疑：只选择公司身份/核验事实，AI 自然回应并给核验路径，不混入项目总览。
2. 复合问题：selection、matching、responsibilities、deliverables 各自绑定事实；任一缺失则 NEEDS_REVIEW/BLOCKED，不用另一条事实冒充覆盖。
3. 无匹配问题：人工端显示无依据；自动端转人工；不得用 QA 全集兜底并写入审计。
4. 事实存在但 policy=`REVIEW`：可生成草稿，自动端不得发送。
5. LLM 超时：人工端展示只读事实摘要；自动端转人工。
6. 人工修改加入 QA 中不存在的金额、URL、无费用、政府保证、合同/IP 承诺：最终校验阻止发送。
7. 介绍信：QA 事实正文修改后，`INTRODUCTION` 邮件仍保持解耦前快照。

## 总体验证命令

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn test
node --test src/test/js/*.test.js
```

每个子计划完成后单独调用 `fix-v`。机器验证全部通过后，才从对应子计划的 `## 人工验收清单` 导出 acceptance 文件。

## 明确不在本总计划内

- 项目介绍邮件内容改写。
- 新建独立 `TrustProfile` 数据库及其后台；本轮继续把机构身份、核验方式、费用/保密事实作为原子 QA 事实，后续迁移到 TrustProfile 时保持 source ID 审计。
- embedding/vector DB、RAG 平台、自动联网核验。
- 删除旧列和删除历史兼容代码；需两个稳定发布周期后单独立项。
