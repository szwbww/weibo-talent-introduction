---
name: expert-discovery
description: Use when operating the expert_discovery Python workflow to find production or R&D experts at a known benchmark company, then selectively enrich and verify work emails.
---

# 对标企业专家搜索

执行前阅读同目录 `README.md`。默认输入为 Excel 数据行、对标企业名称和官网域名。

1. `plan --excel ... --row ... --benchmark ... --domain ...` 只生成提案，不调用 API。
2. 人工核对提案后，批准并执行 `discover`。该阶段只调用 DeepSeek 解析、Apollo People Search、DeepSeek 排序；不查询企业，不获取或验证邮箱。
3. `review` 展示名单、匹配理由和限制。职位与当前企业信息是筛选线索，不充当项目技术证据。
4. 只对人工选中的人员单独提出 `enrich`，默认最多 1 人；再查看邮箱来源。
5. 只对人工选中的明确邮箱单独提出 `verify`，默认最多 1 个。

每项付费动作必须绑定具体提案、对象、理由和调用上限。已有批准只适用于同一快照。遇到 `PENDING`、`UNCERTAIN` 或 `PENDING_PROVIDER` 时停止自动重试，先查提供商记录。

密钥只放在忽略提交的 `keys.properties` 或环境变量中，不打印、不写报告。DEMO 不回退到真实请求。脚本不发送邮件、不写线上人才库。
