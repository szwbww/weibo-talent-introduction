# 会议确认：国家／UTC 展示与无姓名日历附件

状态：待评审开发计划；本轮未改业务代码、未部署、未发送邮件。日期：2026-10-04。

## 需求描述

会议确认按国家/地区选择，用 `巴西（UTC-3）` 区分同国不同时间；一个有效选项自动使用，多个才展示时区选择。邮件写 `Brazil (UTC-3)`；新日历附件改为 `meeting-2026-10-07-0900-a7f3c2d1.ics`，日期时间取会议当地起点，尾部取现有语义摘要前 8 位。

必须保留：真实会议 UTC 起止、北京时间、DST 跳时/回拨拒绝、预览与发送重建校验、发送幂等与排期事务、旧附件原件下载、会议模板/称呼/签名、世界时钟原有城市目录。文件名去姓名不扩大为删除 ICS 标题里的专家姓名。

不做：数据库迁移/回填、线上历史邮件改写、自动重发、操作专家国家资料、改会议日历独立排期功能、全局时区目录重构、运行时联网、升级 JDK/tzdb、添加新的 UI 样式系统。

### 顺序与范围

| 顺序 | 计划 | 实施文件 | 交付 |
|---|---|---:|---|
| 1 | [01 后端](meeting-country-timezone-01-backend.md) | 10 | 国家元信息、实际时刻目录模式、英文会议文案、新文件名与历史兼容 |
| 2 | [02 前端](meeting-country-timezone-02-frontend.md) | 6 | 国家选择、按本场会议偏移分组、单项自动选择、异步与改期保护、统一静态键 |

文件清单逐项见各子计划；10+6 为清单计数，不是猜测。拆分原因是 create-p 每个计划最多 10 个文件。01 可独立部署：旧前端仍调用旧目录模式，提前获得新正文/附件名；02 必须在 01 验证通过后执行。两个子计划不得并行修改/验收。不额外拆通用平台或新服务。

## 关键不变量

- **M-1 时间为准**：展示分组基于本场会议实际起止的合法偏移；内部与请求保留真实 `zoneId`。同国相同起止偏移可合并展示，不把 ZoneRules 永久合并。
- **M-2 国家有凭据**：国家归属来自现有 `contact-country-timezones.json` 和固定 IANA 2026c 的明确别名链，不解析城市中文，不按相同 offset/ZoneRules 猜国家。
- **M-3 共用调用方兼容**：`time-zones?date=` 的原字段/顺序/中午偏移语义与 `filterZones` 纯函数不变；会议新增参数使用另一模式。世界时钟不被去重。
- **M-4 原件兼容**：新附件名由本次起始当地日期/分钟+`semanticSha256.take(8)` 派生；既有 codec 正则/schema 和历史存档保持可读。短码仅为文件区分，不承担唯一标识。
- **M-5 同一生成链**：邮件、ICS、北京时间沿 `validateAndBuild`；发送仍重建验证；UID/语义哈希算法、状态机、排期表不改。
- **M-6 前端不猜**：原选项因 DST 分拆或无效时重新选择/改时间；目录失败与过期响应不能放行旧预览。

详细规则、适用路径与后果分别在子计划 I-1～I-8、F-1～F-7。

## 样式契约

遵循 02 的 S-1～S-4。复用 `meeting-confirmation.css`，不新增或修改 CSS；新 country `<select>` 复用 `.meeting-form select`。改前 DOM/CSS 原文在 [code-baseline](meeting-country-timezone-evidence/code-baseline.md) 的 frontend/styles 段，目标 DOM 在 02 中逐字列出。

## 现状审计

基线 HEAD：`e28e53fd898edd62905a0d45a6bf90396b18b1bf`；开始时工作树已有其他任务变更，不覆盖。证据按工作树内容采集，执行前比较 [source-hashes.json](meeting-country-timezone-evidence/source-hashes.json)，发生变化只复核受影响事实，不直接套旧行号。

| 事实 | 代码证据 | 设计约束 |
|---|---|---|
| `East Time` 是截取 zone ID 尾部再拼 ` Time` | `MeetingConfirmationService.kt:404–407` | 改为查已证实英文国家名 |
| 旧文件名直接用称呼 | 同文件 `163,529–536` | 复用现有摘要，替换该生成方法 |
| 摘要不含 filename/generatedAt；UID 已来自摘要 | 同文件 `130–146,539–566` | 不另造随机号/数据库编号 |
| 列表偏移取日期 UTC 中午；会议预览用两端 getValidOffsets | 同文件 `63–80,356–365` | 列表增加会议起止模式，不用中午值做会议分组 |
| 世界时钟读取同一 API、复用 filterZones | `world-clock.js:3–10`；[grep 回执](meeting-country-timezone-evidence/grep-receipts.md) | 保留原目录模式与纯函数 |
| 已有离线国家目录、ISO code、canonical ID | `ExpertContactLocationCatalog.kt:12–17,31–71`，`contact-country-timezones.json` | 复用资料，不从标签猜国家 |
| 发送前重新生成并核对正文/ICS hash | `PendingMailOperationService.kt:590–639` | 新文案同源更新，旧草稿提示重新预览 |
| filename 存入附件 JSON，SMTP/时间线/下载直接读取 | `ManualReplySendAttemptService.kt:353–393,451–492`；`SmtpMailDeliveryService.kt:101–117`；`MailboxConversationService.kt:391–410`；`CalendarAttachmentController.kt:59–86` | 生成处改名可贯通链路，不分别改五套命名 |

详细写读路径与 schema 见 01；前端内存状态与异步见 02。命令和完整命中位置见 [grep-receipts.md](meeting-country-timezone-evidence/grep-receipts.md)，原文摘录见 [code-baseline.md](meeting-country-timezone-evidence/code-baseline.md)。

### 实测而非推测

- [JDK 探针](meeting-country-timezone-evidence/jdk11-timezone-probe.txt)：JDK 11.0.15/tzdb 2021e；`Brazil/East` 和 `America/Sao_Paulo` 在 2026-10-07 09:00 均为 12:00Z、北京 20:00。纽约 2026-03-08 01:30 为 UTC-5，但当天 UTC 中午已是 UTC-4；直接用旧列表值会错。
- [国家映射审计](meeting-country-timezone-evidence/country-mapping-audit.json)：532 个资源 ID 中 519 个可映射（含 UTC），13 个 SystemV 项没有国家归属；247 个国家/地区在映射里可见。计数由同目录 `country-audit.py` 产生，JSON 附完整映射与源 SHA256。
- IANA `backward` 对 `Pacific/Ponape` 的目标是 `Pacific/Guadalcanal`，但 `#= Pacific/Pohnpei` 保留原属地区。先用现有国家目录精确匹配，再沿 `#=` 原目标，最后普通 link，可避免把 FM 错标为 SB。不能简化成“所有别名追到最终合并目标”。
- 国家目录实际含 `CN: Asia/Shanghai, Asia/Urumqi`；本轮沿目录呈现，不按常识擅删条目。单选项隐藏规则用土耳其等实测国家验收。
- 原目录源版本为 2026c。本轮曾读取滚动站点为 2026e，已放弃混用，证据固定为 [IANA 2026c 官方包](https://data.iana.org/time-zones/releases/tzdata2026c.tar.gz) 中的 backward/iso3166.tab/zone.tab。这些是名称归属材料；运行时计算继续使用现有 JVM 规则，不宣称 JVM 已升级到 2026c。

### 知识使用

已重验并更新命中计数：K-meeting-confirmation-generic-template-boundary、K-meeting-timezone-offset-is-noon-metadata、K-frontend-cache-key-triad、K-dom-stub-tests-hide-dangling-refs、K-calendar-not-expert-material-owner、K-download-context-path-host-injection、K-plan-quantified-claims-need-grep-receipts。均在 90 天窗口内；本轮不归档。通用入站附件 metadata 知识已阅读但本次不改变其链路，不引入材料同步任务。

## 实现方案

1. 按 01 完成离线国家映射、兼容目录模式、国家文案、文件名及对应验证；字段契约冻结后交给 02。
2. 按 02 在会议组件内分组并复用现有样式；模拟数据必须补后端真实新增字段，禁止用空字段兼容分支掩盖接口未部署。
3. 对新命名执行预览→发送重建→SMTP 快照→存档→下载回归；对旧附件保留旧名下载。
4. 最终人工按 02 A-1～A-10 检查整体体验；验收开始时再从子计划导出 acceptance.md，本轮不提前生成。

## 变更文件清单

业务实施清单以两个子计划各自的表为准。本主计划不授权额外代码文件。计划阶段生成主计划/子计划、代码与官方数据证据、审计脚本，更新命中知识并补充可复用知识；它们不是已经实现的业务改动。

## 验收标准

- M-1/M-2：01 I-1/I-2/I-3 测试+02 F-1/F-2 改期分拆场景。
- M-3：世界时钟回归保持原 ID/城市搜索；无参数目录旧字段逐项相同。
- M-4/M-5：01 I-4～I-8，含真实生成器重建与存档下载，不以字符串 fixture 冒充全链路。
- M-6：02 异步倒序/失败重试/关闭重开/切专家测试，旧预览不可应用。
- S-1～S-4：02 样式源文本断言与人工目测。
- 本次计划已运行的是审计脚本和只读 JDK 探针；未运行“新功能测试”，也不将计划自检当作实现验收。

## 人工验收清单

### A-M1：整体流程
- 前置条件：两个子计划完成，在本地或测试环境准备联系人及受控测试收件箱。
- 操作步骤：执行 02 A-1～A-10；执行 01 A-4/A-5 的发送与历史下载。
- 预期结果：巴西会议 09:00 对应北京 20:00；选择项/邮件无城市名与 East Time；附件文件名符合新规则；历史文件仍返回存档原名和原字节。
- 覆盖：M-1～M-6、S-1～S-4。

### A-M2：边界确认
- 前置条件：使用同一测试版本。
- 操作步骤：打开世界时钟、专家位置设置与独立会议日历；仅查看，再执行 02 A-10。
- 预期结果：世界时钟仍能搜城市与 IANA ID；专家位置目录未去重；原排期 UTC 起止不被这次展示改造改写。
- 覆盖：M-3/M-5、不变项及范围边界。
