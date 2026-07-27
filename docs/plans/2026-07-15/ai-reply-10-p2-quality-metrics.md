# P2-10：AI 回复覆盖质量指标

## 需求描述

在现有 QA 规则审计时间范围内展示 AI 初稿质量：完整数、部分覆盖数、遗漏数、遗漏率、部分覆盖率、直发拦截次数、人工确认发送次数。指标直接来自计划 8 的 action type。

Out of scope：草稿正文留存、按模型/操作员下钻、自动质量评分、修改历史 action 日志。

## 关键不变量

### I-1：分母固定
- `totalGenerated = READY + NEEDS_REVIEW + BLOCKED`，且仅 mailbox 首轮已记录 action。
- 续轮与训练模拟不进入分母。

### I-2：指标定义固定
- 完整率 = READY / totalGenerated。
- 部分覆盖率 = NEEDS_REVIEW / totalGenerated。
- 遗漏率 = BLOCKED / totalGenerated（至少一个 request/intent 完全无据）。
- 分母为 0 时所有 rate=0，不返回 NaN/null。

### I-3：计数走数据库聚合
- 不把 operator logs 全量拉到内存；复用 repository `countSearch()` 按 actionType/time range 计数。
- 现有 QA rule usage 的 selected source 逻辑不变。

### I-4：API additive
- 扩展 `QaRuleUsageAuditReport.aiReplyQuality`；不新增第二套日期筛选和 endpoint。
- 旧前端忽略新字段仍可工作。

## 现状审计

- `loadQaAuditReport()` 请求 `/api/qa/audit/rule-usage?from&to`。
- `QaRuleAuditService` 当前读取 SEND_MANUAL_COMPOSED_REPLY 日志并用 `mail_record_qa_rule` 解析实际 selected rule。
- `OperatorActionLogRepository.countSearch()` 已支持 action type + 时间范围，可直接聚合五类新 action。
- 前端 audit panel 已复用 `.metadata-grid/.metadata-card/.compose-gap-list`。

## 后端实现任务

### T1：扩展 QaRuleAuditService
文件：`src/main/kotlin/com/weibo/talentintroduction/qa/service/QaRuleAuditService.kt`

- 新增 `AiReplyQualityMetrics` DTO：readyCount、needsReviewCount、blockedCount、totalGenerated、readyRate、partialRate、omissionRate、directSendBlockedCount、reviewConfirmedCount。
- 对五个 action type 分别调用 countSearch，long→response 数值保持 Long。
- rate 使用 Double，统一四位小数或后端原始比例，前端格式化百分比。
- 返回 report nested 字段；现有 removed/added/freeText 逻辑不动。

### T2：服务测试
文件：`src/test/kotlin/com/weibo/talentintroduction/qa/service/QaRuleAuditServiceTest.kt`

- 3 READY/2 NEEDS/1 BLOCKED → total 6，rates 精确。
- 0 分母。
- blocked attempt/confirmed 不进入 generation 分母。
- 原 QA selected association 测试保持。

## 前端实现任务

### T3：审计卡片
文件：`src/main/resources/static/app.js`

- `renderQaAuditPanel()` 在现有 QA 组装卡片下增加 AI reply quality 分组。
- 卡片：AI 初稿总数、完整率、部分覆盖率、遗漏率、直发拦截、人工确认。
- count/rate 缺字段时兼容显示 0/`-`，不得报错。
- 百分比统一 `Intl.NumberFormat` 或 helper，最多 1 位小数。

### T4：JS 测试
文件：`src/test/js/qaAiReplyQualityMetrics.test.js`

- 正常、零分母、缺字段兼容。
- 文案与字段映射正确。
- HTML escape/纯数值渲染。

## 前端样式契约

- 复用 `.metadata-grid`：auto-fill min 160px、gap 8px。
- 复用 `.metadata-card/header/value`；不新增颜色、尺寸、hover。
- 分组标题沿用当前 inline h4 规格 `margin:8px 0 4px;font-size:13px`，后续可统一但本计划不扩样式范围。
- 不改 index.html/styles.css；响应式沿用 metadata grid auto-fill。

## 变更文件清单（4）

1. `QaRuleAuditService.kt`
2. `QaRuleAuditServiceTest.kt`
3. `src/main/resources/static/app.js`
4. `src/test/js/qaAiReplyQualityMetrics.test.js`

## 工作区冲突保护

- app.js 仅修改 `renderQaAuditPanel` 与百分比 helper；保留 batch-send 未提交代码。
- 不修改 audit endpoint 日期语义，避免扩大范围。

## 验收标准

- 指标可由 operator_action_log 对应 action count 手工复算。
- 训练模拟不会使数字变化；mailbox 首轮生成一次只增加一个 readiness action。
- 现有 QA rule usage 统计结果不变。
- 定向测试：

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home mvn -Dtest=QaRuleAuditServiceTest test
node --test src/test/js/qaAiReplyQualityMetrics.test.js src/test/js/batchSendTaskConsoleVisualFix.test.js
```

## 人工验收清单

### A-1：混合样本
- 生成 READY/NEEDS/BLOCKED 各一封 mailbox 初稿，加载同日期审计。
- 预期：total=3，各 rate=33.3%。

### A-2：模拟排除
- 训练模拟生成 5 次。
- 预期：指标不变。

### A-3：拦截与确认
- 对 BLOCKED 草稿尝试发送一次，再完整确认发送。
- 预期：直发拦截 +1、人工确认 +1；generation total 不变。
