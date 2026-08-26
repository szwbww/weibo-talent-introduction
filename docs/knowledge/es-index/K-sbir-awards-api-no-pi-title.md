---
id: K-sbir-awards-api-no-pi-title
domain: es-index
created: 2026-08-25
last_used: 2026-08-25
hit_count: 0
source: create-p:00-rnd-gate-master
---

SBIR/STTR Awards API（`https://api.www.sbir.gov/public/api/awards`）的字段全集里
**没有 PI 的职位/头衔**。PI 只有三个字段：`pi_name` / `pi_phone` / `pi_email`。

官方文档（`https://www.sbir.gov/api`，2026-08-25 读取）的完整返回字段：

```
firm, award_title, agency, branch, phase, program, agency_tracking_number, contract,
proposal_award_date, contract_end_date, solicitation_number, solicitation_year, topic_code,
award_year, award_amount, duns, uei, hubzone_owned, socially_economically_disadvantaged,
women_owned, number_employees, company_url, address1, address2, city, state, zip,
poc_name, poc_title, poc_phone, poc_email,
pi_name, pi_phone, pi_email,
ri_name, ri_poc_name, ri_poc_phone,
research_area_keywords, abstract, award_link
```

查询参数：`agency` / `firm` / `year` / `ri` / `rows`（默认 100）/ `start`（偏移分页）/ `format`。

## 三个易踩的坑

1. **`poc_title` 不是 PI 的头衔。** POC 是公司业务联系人，`poc_email` 与 `pi_email` 是两个地址。
   把 POC 头衔写进专家的 `employment`（对外标签是「职位」，会进邮件正文）＝张冠李戴，
   同 [[K-mail-placeholder-labels-are-semantic-contracts]]。
2. **`ri_name` 是 STTR 的研究机构合作方**，不是 PI 的雇主。写进 `institution` 会让
   `RESEARCH_INSTITUTION_TERMS` 误命中 +20。
3. **`award_title` 不是论文标题、`award_year` 不是发表年份。** 写进 `recentWorkTitles` /
   `lastPublicationYear` 会让对外邮件说假话。

## 对分类打分的直接后果

只用可正当写入的字段（`firm`→employment/institution、`research_area_keywords`→keyword/researchFields）：
`COMPANY_TERMS` +15 + `PRODUCTION_THEME_TERMS` +20 = **35 < 50 阈值** → 恒为 `UNKNOWN`。
叠加 [[K-enrichment-excludes-email-id-experts]]（无 ORCID 者永不 enrich），这个结果永远不会变。

因此接入 SBIR 必须同时决定「是否为它增加一条生产分证据」，否则捞回来的人一封信也发不出去。

## 可用性

官网长期挂着「The SBIR.gov APIs are currently undergoing maintenance」。
2026-08-25 通过抓取工具直连 `api.www.sbir.gov` 返回 **403**（工具侧被拒，未能判断服务端真实状态）。
实测方案见 `docs/plans/2026-08-25/00-research-checkpoints.md` 的 CP-1。
