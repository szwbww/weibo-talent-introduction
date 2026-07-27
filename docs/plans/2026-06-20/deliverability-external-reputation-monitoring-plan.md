# 计划：外部信誉监控接入（Postmaster / SNDS / FBL）

> 用 create-p skill 编写。**注意：本项以运营/配置为主，代码改动极小。** 与其它计划不同，主体是 DNS 与第三方控制台注册，因此本文件以「运营运行手册 + 可选最小代码」组织，并标注真正的代码面。

## 需求描述

- 可观察结果：运营方能在外部权威面板看到本域/发件 IP 的信誉、垃圾投诉率、认证通过率趋势；收件方的投诉（FBL）能回流并自动进入退订抑制名单。
- 必须不变：不改发送链路与抑制逻辑（仅复用子计划 01 的 `EmailSuppressionService.suppress`）。
- 不做（明确推迟）：自建 DMARC `rua` 聚合报告（XML）解析入库与可视化（数据量与解析复杂度高，单列计划）；自动调速联动外部信誉（先人工看板）。

## 关键不变量

### Invariant I-1：FBL 投诉等同退订
- 规则：任何经 FBL 回流识别到的投诉地址，调用 `EmailSuppressionService.suppress(email, SuppressionSource.FBL, reason)` 加入抑制名单（归一化、幂等，复用 01 的 G-1/G-2）。
- 适用于：若实现 FBL 回流处理（见可选任务 C）。
- 违反后果：投诉者继续被发信，信誉持续恶化。

### Invariant I-2：仅新增来源值，不改既有
- 规则：如启用任务 C，给 `SuppressionSource` 增加枚举值 `FBL`，不改动既有 `INBOUND_REPLY/ONE_CLICK/MAILTO/MANUAL` 的语义与处理。
- 适用于：`SuppressionSource`。
- 违反后果：既有来源统计/处理回归。

## 现状审计

- 认证现状（已查 DNS）：域 `qftechtalent.com`，MX=腾讯企业邮，SPF 已有，**DMARC/DKIM 待补**（见 `docs`/认证处理事项，由用户负责）。Postmaster/SNDS 的有效数据**依赖认证先就绪**，故本项排在认证之后。
- 内部已有 `monitoring/service/MailMonitoringService` 与 `mail/service/BounceRateMonitorService`（内部退信率），与外部信誉互补，不重叠。
- 抑制能力来自子计划 01。

## 实现方案

### A. 运营配置（无代码，主体工作）

1. **Google Postmaster Tools**
   - 访问 postmaster.google.com，添加域 `qftechtalent.com`，按提示在 DNSPod 加一条 TXT 验证记录。
   - 验证后可看：域信誉、IP 信誉、垃圾率、SPF/DKIM/DMARC 通过率、加密占比。
   - 前提：DKIM/DMARC 已就绪且有一定发往 Gmail 的量才出数据。

2. **Microsoft SNDS + JMRP**
   - SNDS（sendersupport.olc.protection.outlook.com/snds）：登记发件 IP，看到达/陷阱/投诉数据（IP 由腾讯企业邮承载，需确认能否取得授权数据；若 IP 非自有，此项可能受限，作记录）。
   - JMRP（垃圾报告反馈环）：注册后微软把投诉回流到指定邮箱。

3. **其它 FBL**：雅虎等按需注册，回流到统一投诉邮箱。

> 产出：一份《外部信誉监控接入运行手册》文档（见任务 B），登记各面板账号、验证记录、负责人、检查频率（建议每周）。

### B. 文档（唯一必做的「交付物」，非代码）

文件：`docs/runbooks/external-reputation-monitoring.md`（新增）
- 记录上述各面板的接入步骤、验证记录值、查看口径（垃圾率<0.3%、认证通过率目标）、异常时的处置（降速/排查认证/清列表）。

### C.（可选）FBL 投诉回流自动抑制（唯一的代码面）

仅当投诉能以邮件形式回流到某个 IMAP 信箱时实现：
- `SuppressionSource` 增加 `FBL`（I-2）。
- 在退信/入站收集链路（参考 `BounceCollectionService` 的 IMAP 扫描范式）识别 ARF（`message/feedback-report`，Content-Type `multipart/report; report-type=feedback-report`）格式邮件，提取被投诉地址 → `suppress(email, FBL, ...)`（I-1）。
- 文件（若实施）：`mail/service/FeedbackLoopCollectionService.kt`（新增）、`mail/service/InboundIntentClassifier` 不动、`SuppressionSource`（在 01 文件内）增值、对应测试。
- **本期默认不实施**任务 C，先完成 A/B；待 FBL 真有数据回流再单独排期。

## 变更文件清单

| # | 文件 | 类型 | 是否本期 |
|---|---|---|---|
| 1 | `docs/runbooks/external-reputation-monitoring.md` | 新增 | 是 |
| 2 | `mail/service/FeedbackLoopCollectionService.kt` | 新增 | 否（任务 C，按需） |
| 3 | `mail/service/EmailSuppressionService.kt`（`SuppressionSource` 加 `FBL`） | 修改 | 否（任务 C，按需） |
| 4 | `test/.../FeedbackLoopCollectionServiceTest.kt` | 新增 | 否（任务 C，按需） |

本期代码改动 = 0（仅文档）。任务 C 若启用：3 文件，单一子系统（投诉回流），届时按 create-p 复核。

## 验收标准
- A：各面板验证通过、能看到数据（认证就绪后）。
- B：运行手册可被他人据以复现接入与处置。
- C（若实施）：ARF 投诉邮件被识别并以 `FBL` 来源加入抑制名单（I-1）；既有来源处理不回归（I-2）。

## 依赖与排期
- 依赖：DMARC/DKIM 认证就绪（用户处理中）；任务 C 依赖子计划 01。
- 建议顺序：认证就绪 → A/B（运营）→ 观察数据 → 视情况再排任务 C。
