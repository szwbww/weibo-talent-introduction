# TUM 机构别名修复

授权：当前会话明确要求直接在 main 修改并发布。仅补机构英德名称对应；保留既定准入规则、资格判断、原始字段及留存。没有授权历史记录批量重跑。

## 依据与边界

官方来源（2026-10-20 核对）：
- https://www.tum.de/en/about-tum/contact-directions — Technical University of Munich
- https://www.tum.de/ueber-die-tum/kontakt-und-anfahrt — Technische Universität München

同一官网英德页面、同一机构及联系地址。只纳入这两个完整名称；不使用自动翻译、模糊相似、裸缩写 TUM 或从邮箱域名猜测机构。其他机构尚未覆盖。

原始文本完整命中，或现有提取器明确提取单一主体后命中，才能使用别名。明确机构 ID 的比较优先级不变；同命名空间不同 ID 不被别名覆盖。未改变原有主体提取结果及其持久化版本，避免历史上下文校验漂移。

## 持久化与日志

新任务 settings_json 冻结 institutionAliasVersion=1；Worker 首次进入 Apollo 时复制到 search 证据。旧任务与旧 search 缺省为 0，不自动升级。Worker 候选选择、Rules 最终判定、Gate 复算与 QueryService 共用冻结版本。现有跨任务档案去重不变，已完成旧条目不会因新任务启动自动重核。

使用别名的准入保留 APOLLO_CURRENT_INSTITUTION_MATCH / APOLLO_HISTORICAL_INSTITUTION_MATCH，并额外保存 APOLLO_INSTITUTION_ALIAS_TUM_V1。现有日志的匹配标签追加“TUM 官网英德名称对应”；无需新增页面、CSS 或数据库字段。

## 对照样本

线上任务 27083：
- item 152：Patrick Ebel，目标 Apollo 63a47d8d3160610001a93c00；旧档案 TUM 英文名，Apollo 历史任职德文名，结束 2023-01-01。
- item 170：Nils Quetschlich，目标 Apollo 5e84b846833c0e00011be45b；相同英德差异，历史任职结束 2025-08-01。

预期：旧版本仍 PENDING / IDENTITY_NOT_MATCHED；仅对照计算启用别名时变为 APOLLO_CLEAR / APOLLO_HISTORICAL_INSTITUTION_MATCH，资格状态保持不变。两条实际载荷仅服务器内读取与计算，不下载到本地，不触发 Apollo 请求，不写业务数据。

## 验证和发布

本次发布完成：main 产品提交 `56829c97b7380848b73c61466dec8ea6279e3741`。

- 专项测试 51 项通过；完整后端 5290 项，失败 0、错误 0、既有 opt-in 跳过 21；前端 1725 项全部通过。详见 build.json。
- 服务器内真实留存对照 2/2 通过：正确候选、历史机构匹配，资格仍 UNKNOWN；业务写入 0，Apollo 请求 0。详见 replay.json。原始载荷没有下载。
- 新 WAR SHA-256：`2abe76427b635f363461d637c236e1b49c60c22c05f7953b60bc9c532bc9373e`。
- 2026-10-20 16:44（服务器时间）发布完成；新 Tomcat PID 14437；HTTP 200；6 个关键类与 WAR 校验一致；外部配置仍被加载且未变更，国家补全保持开启。详见 production.json。
- 旧包备份：`/opt/apache-tomcat-9.0.71/deploy/backups/tum-alias-20261020-164313-56829c97b738`。无数据库迁移，可恢复旧 WAR。

仅发布后新任务冻结别名版本 1；旧任务、旧已完成档案不自动重核。对照计算不是线上晋升写入。
