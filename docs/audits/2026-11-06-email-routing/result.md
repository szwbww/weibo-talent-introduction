# 微软收信服务标记：交付记录

本地 main 实现完成；按用户后续授权提交本地，未发布、未修改生产数据。依据用户已确认的 MX 识别/证据/缓存/发现接入/历史补查/展示筛选方案核验。

- 新发现的论文与 ORCID 档案，在统一 toIndexMap 写 RAW 前保存 emailRouting；不加入资格快照计算。
- Outlook/Hotmail/Live/MSN 公共域名，或最高优先级 MX 全部指向微软官方域名，标记 MICROSOFT。其他网关、混合主 MX、无 MX、DNS 失败均为 UNKNOWN，绝不声称已证明“非微软”。无历史证据为未检测。
- 证据保存域名、MX、原因、UTC 检测时间。缓存按域名，最多 5,000 个，成功六小时、DNS 错误一分钟；重启仅清除内存缓存，ES 证据保留。
- 审核列表提供三类筛选、证据和本页补查。补查捕获本页 ID 并逐条串行请求；离开页面停止后续请求。每条只做现有三层文档的 emailRouting CAS 更新，不创建文档、不改变准入或发信状态。
- 列表、审核所有页、重审当前筛选共用服务端筛选。IDS 不带筛选；ALL_PENDING 保留“全部待审核”的明确语义。

## 验证证据

Java 11 / Kotlin 1.9.25：Maven 主代码与测试编译通过。最后邮箱格式收紧的单文件及其测试经同版本 Kotlin 定向编译；最终 Surefire 对 13 个相关测试类运行 309 项，0 失败、0 错误、0 跳过。全量 Node：2,058 项，全部通过。app.js / discovery-recheck.js 语法及 git diff --check 通过。

实现过程中修正：缓存 Entry 类型命名冲突；旧 mock 参数及 mapping 字段数量断言；历史补查 API 前缀；重审前端过滤白名单漏传 emailRouting。后者先获得红灯回归，再修复并全量 Node 通过。3 项本地 HTTP 测试初次因沙箱限制端口失败，允许回环端口后重跑通过。

## 只读核验

- I1 ✅ EmailRoutingService.kt:9、EmailRoutingServiceTest：官方后缀、主备/混合 MX、伪装后缀、错误、格式与缓存并发/淘汰。
- I2 ✅ EmailValidationService 保留 validate/hasMxRecord 原判定；23 项既有校验测试通过。DiscoveryVerificationRegistrationService.kt:338 排除非资格字段，续跑事实比较测试通过。
- I3 ✅ ExpertDiscoveryService.kt:2750；三层 ES mapping、ExpertProfile、ExpertSearchService 和 writer.discoveryProfile 全部读写闭合。晋升沿用全量 _source 复制。
- I4 ✅ DiscoveryEmailRoutingService.kt:18、ExpertIndexWriterService.kt:703；缺层不创建、冲突不冒充成功、域名变化不覆盖。认证及 HTTP 请求体测试通过。
- I5 ✅ DiscoveryReviewScanService.kt:223、DiscoveryReviewController/Service、DiscoveryRecheckService 与 discovery-recheck.js:147；页面读操作不访问 DNS。
- I6 ✅ 与开发前源文件哈希比对，无关的国家补全等已有改动保持。ExpertIndexWriterService 原有改动上仅追加本功能。

缓存累积/并发 ✅；页面请求生命周期 ✅；筛选读写闭环 ✅；删除代码兼容 ✅；无额外业务规则 ✅。无未解决阻断项。相关源码哈希及逐测试类统计见 verification.json；哈希对应验证时工作区，包含 writer 中原有国家补全改动，该部分不纳入本次提交。

限制：ES 查询与 CAS 用 HTTP 边界测试验证；本次未连接真实线上 ES，也未进行线上 UI 验收。部署后需人工核对真实新发现标记、历史本页补查及筛选结果；第三方网关背后的实际邮箱托管商仍可能无法确认。
