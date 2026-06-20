# 外部信誉监控接入运行手册

> 对应计划：`docs/plans/2026-06-20-deliverability-external-reputation-monitoring-plan.md`（投递率 #9）
> 域：`qftechtalent.com`（腾讯企业邮 MX，SPF 已配置）
> 本期交付：**运营配置 + 本文档**；代码改动 = 0。FBL 自动抑制（任务 C）暂不实施。

## 1. 目标与范围

**可观察结果**

- 运营方能在 Google / Microsoft 等权威面板查看域/IP 信誉、垃圾投诉率、认证通过率趋势。
- （可选，后续）FBL 投诉回流后自动进入抑制名单（见 §8）。

**不在本期**

- 自建 DMARC `rua` XML 聚合报告解析与可视化。
- 外部信誉与发件限速的自动联动（先人工看板决策）。

## 2. 前置条件（必须先完成）

| 项 | 现状（2026-06-20） | 目标 | 负责 |
|---|---|---|---|
| SPF | ✅ 已有 | 保持，勿删改 | 运维 |
| DKIM | ❌ 待补 | 腾讯企业邮控制台开启，DNS 加 CNAME/TXT | 运维 |
| DMARC | ❌ 待补 | `_dmarc.qftechtalent.com` TXT，`p=none` 起步，含 `rua`/`ruf`（可选） | 运维 |
| PTR/rDNS | 腾讯侧 | 确认发件 IP 反向解析正常 | 腾讯企业邮 |
| 发信量 | 视业务 | Gmail/Outlook 面板需一定真实投递量才有趋势 | 运营 |

> **Postmaster / SNDS 有效数据依赖 DKIM + DMARC 就绪。** 认证未完成前可先做域名验证与账号登记，指标可能为空或延迟。

### 2.1 DMARC 起步示例（DNSPod）

```
主机记录：_dmarc
记录类型：TXT
记录值：v=DMARC1; p=none; rua=mailto:dmarc-reports@qftechtalent.com; pct=100
```

验证：`dig TXT _dmarc.qftechtalent.com +short`

## 3. 内外监控分工

| 来源 | 看什么 | 入口 |
|---|---|---|
| **外部（本手册）** | 域/IP 信誉、用户举报率、SPF/DKIM/DMARC 通过率 | Postmaster、SNDS、FBL 邮箱 |
| **内部** | 硬退信率、账号暂停、外发/入站明细 | 管理后台监控页、`/api/mail-monitoring/bounce-stats` |
| **内部自动动作** | 7 日硬退信率 > 5% 且样本 ≥ 20 封 → 暂停账号自动发信 | `BounceRateMonitorService` |

外部与内部互补，不替代：外部反映收件方感知，内部反映列表质量与 SMTP 结果。

## 4. 接入登记总表

运营完成后填写（敏感账号勿提交 git，可放内部密码库）：

| 面板 | URL | 登录账号 | 验证方式 | 验证记录/状态 | 负责人 | 检查频率 |
|---|---|---|---|---|---|---|
| Google Postmaster Tools | https://postmaster.google.com | _待填_ | DNS TXT | _待填_ | _待填_ | 每周 |
| Microsoft SNDS | https://sendersupport.olc.protection.outlook.com/snds/ | _待填_ | IP 登记 | _待填_ | _待填_ | 每周 |
| Microsoft JMRP (FBL) | https://sendersupport.olc.protection.outlook.com/snds/JMRP.aspx | _待填_ | 投诉回流邮箱 | _待填_ | _待填_ | 每周 |
| Yahoo FBL（按需） | https://senders.yahooinc.com/ | _待填_ | 域/IP + 回流邮箱 | _待填_ | _待填_ | 每周 |
| 统一 FBL 收件箱 | IMAP | _待填_ | — | _待填_ | _待填_ | 每日（有回流时） |

**建议统一 FBL 回流地址**：`fbl@qftechtalent.com`（或专用邮箱，需 IMAP 可读；任务 C 实施时再接入系统）。

## 5. Google Postmaster Tools

### 5.1 接入步骤

1. 使用 Google 账号登录 [Postmaster Tools](https://postmaster.google.com)。
2. **添加域** → 输入 `qftechtalent.com`。
3. 按提示在 **DNSPod** 添加 **TXT** 验证记录（主机名通常为 `@` 或 Google 指定子域）。
4. 回到 Postmaster 点击验证；通过后状态为 **Verified**。
5. 等待 24–72h 及足够 Gmail 投递量后查看 Dashboard。

### 5.2 验证记录（填写区）

```
主机记录：_待 Google 控制台生成后填写_
记录类型：TXT
记录值：_待填_
添加时间：____-__-__
验证通过时间：____-__-__
```

### 5.3 关注指标与口径

| 指标 | 健康参考 | 说明 |
|---|---|---|
| Domain reputation | High / Medium | Low 需立即降速并查认证与列表 |
| IP reputation | High / Medium | 与腾讯出口 IP 相关 |
| User-reported spam rate | **< 0.3%** | 超过则排查内容、频率、退订路径 |
| SPF / DKIM / DMARC success rate | **≥ 95%**（理想接近 100%） | 任一项偏低先修 DNS/签名，勿加量 |
| TLS encryption | 越高越好 | 一般腾讯 SMTP 已支持 |

### 5.4 无数据时

- 确认 DKIM/DMARC 已生效（§2）。
- 确认近期有发往 `@gmail.com` / Google Workspace 的邮件。
- 新验证域常需数天累积。

## 6. Microsoft SNDS + JMRP

### 6.1 SNDS（发件人数据服务）

1. 访问 [SNDS](https://sendersupport.olc.protection.outlook.com/snds/)。
2. 注册并登记**发件 IP**（腾讯企业邮出口 IP，向腾讯支持或邮件头 `Received` 获取）。
3. 若 IP 为腾讯共享池、无法单独授权，SNDS 数据可能**受限**——在登记总表备注「共享 IP / 仅 JMRP」。

**可看数据**：到达量、陷阱命中、投诉量、颜色标识（绿/黄/红）。

### 6.2 JMRP（垃圾邮件投诉反馈环）

1. 在 SNDS 站点进入 [JMRP 注册](https://sendersupport.olc.protection.outlook.com/snds/JMRP.aspx)。
2. 登记域 `qftechtalent.com` 与投诉回流邮箱（建议 §4 统一 FBL 邮箱）。
3. 微软将用户「举报垃圾邮件」的 ARF 格式通知发到该邮箱。

### 6.3 关注指标与口径

| 指标 | 健康参考 | 说明 |
|---|---|---|
| Complaint rate（投诉率） | **< 0.3%** | 与 Postmaster 用户举报率同量级警戒 |
| Trap hits | 0 或极低 | 突增说明列表含陈旧/陷阱地址 |
| Green / Yellow / Red | 保持 Green | Yellow 降速；Red 停量并排查 |

## 7. 其它 FBL（雅虎等）

按业务覆盖的收件域按需注册，投诉统一回流到 §4 邮箱：

- **Yahoo / AOL**： [Sender Hub](https://senders.yahooinc.com/) 注册 FBL。
- **其它 ESP**：查阅对方 Postmaster 文档，同样指向统一 FBL 邮箱。

**人工处理（本期）**：收到 ARF/投诉邮件后，在管理后台将投诉地址加入抑制名单（来源选手动 `MANUAL`，原因注明 FBL）。
**自动处理（任务 C，未实施）**：见 §8。

## 8. 任务 C（可选，本期不做）

当 FBL 邮件稳定回流且需自动化时，单独排期：

- `SuppressionSource` 增加 `FBL`（不改既有来源语义）。
- 新增 `FeedbackLoopCollectionService`，参照 `BounceCollectionService` 的 IMAP 扫描，识别 ARF（`multipart/report; report-type=feedback-report`），提取被投诉地址 → `EmailSuppressionService.suppress(email, FBL, reason)`。
- 不变量：FBL 投诉等同退订（与一键退订、回复退订同一抑制表）。

计划文件：`docs/plans/2026-06-20-deliverability-external-reputation-monitoring-plan.md` §C。

## 9. 查看口径汇总

| 维度 | 目标/警戒 | 数据源 |
|---|---|---|
| 用户垃圾举报率 | < **0.3%** | Postmaster、SNDS/JMRP |
| SPF 通过率 | ≥ **95%** | Postmaster |
| DKIM 通过率 | ≥ **95%** | Postmaster |
| DMARC 通过率 | ≥ **95%** | Postmaster |
| 内部硬退信率（7 日） | < **5%**（系统自动暂停） | 内部监控 / `BounceRateMonitorService` |
| 域信誉 | High | Postmaster |
| IP 信誉 | High | Postmaster / SNDS |

样本不足时（日发 < 数百）外部比率波动大，结合绝对投诉封数判断。

## 10. 异常处置 playbook

按优先级执行，可并行：

### 10.1 认证通过率下降（SPF/DKIM/DMARC）

1. `dig` / [MXToolbox](https://mxtoolbox.com/) 检查 DNS 是否被误改。
2. 腾讯企业邮控制台确认 DKIM 开关与 selector 一致。
3. 抽一封外发邮件看原始头 `Authentication-Results`。
4. **在认证修复前不要提高日发量。**

### 10.2 垃圾举报率 / 投诉率 ≥ 0.3%

1. **立即降速**：调低各发件账号 `dailySendLimit`，确认 `warmup` 策略（`talent-introduction.warmup.enabled`）。
2. 检查退订：List-Unsubscribe 头、一键退订端点、抑制名单是否生效。
3. 检查内容与列表：是否重复触达已退订/无互动地址。
4. 人工将 FBL 回流中的投诉地址加入抑制名单（本期）。

### 10.3 域/IP 信誉降为 Low 或 SNDS Red

1. 暂停增量外发（保留必要人工跟进）。
2. 完成 §10.1 + §10.2。
3. 清理高风险 segment（长期无打开、高频硬退信域名）。
4. 信誉恢复通常需 **2–4 周** 低量优质发送，勿急于拉回峰值。

### 10.4 内部硬退信率触发暂停

系统已自动 `pauseAutoSend`；运营在管理后台查看暂停原因（`BOUNCE_RATE_HIGH:xx%`），修复列表后手动恢复账号。

### 10.5 陷阱命中（Trap hits）

1. 停止向对应批次/来源继续发送。
2. 审计地址来源与验证流程（`email-validation` 配置）。
3. 将相关地址段加入抑制或从 ES 候选层剔除。

## 11. 每周检查清单

- [ ] Postmaster：域信誉、IP 信誉、用户举报率、三项认证通过率
- [ ] SNDS：IP 颜色、投诉、陷阱（若已授权）
- [ ] FBL 邮箱：有无新 ARF，人工 suppress（本期）
- [ ] 内部监控：各账号 bounce-stats、暂停账号数量
- [ ] 登记总表（§4）验证状态与负责人是否仍有效
- [ ] 异常是否按 §10 记录处置与结论

建议固定每周一上午执行，结果写入团队共享文档（日期 + 截图 + 动作）。

## 12. 相关代码与文档

| 资源 | 说明 |
|---|---|
| `docs/plans/2026-06-20-deliverability-INDEX.md` | 投递率计划总索引 |
| `docs/plans/2026-06-20-deliverability-external-reputation-monitoring-plan.md` | 本项计划原文 |
| `mail/service/BounceCollectionService.kt` | 退信 IMAP 收集（FBL 任务 C 参考范式） |
| `mail/service/EmailSuppressionService.kt` | 抑制名单（FBL 任务 C 复用 `suppress`） |
| `mail/service/BounceRateMonitorService.kt` | 内部硬退信率自动暂停 |
| `monitoring/service/MailMonitoringService.kt` | 内部监控 API |

## 13. 修订记录

| 日期 | 修订 | 作者 |
|---|---|---|
| 2026-06-20 | 初版：A/B 运营手册，任务 C 仅文档预留 | _待填_ |
