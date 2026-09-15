# 对标企业人才池盘点

判断「按国际对标企业反查工程师」这条路，对每一家具体值不值得建数据源。
只读 OpenAlex 公开学术元数据，不碰任何雇员名录、社交网站或非公开数据。

## 前置

* Python 3.7+，**无需 pip**（只用标准库）
* 机器要能访问 `api.openalex.org`（我这边的容器和你本机都被网关 403 挡了，
  用生产服务器或任意能出网的机器跑）

## 跑

```bash
cd scripts/benchmark-scan

# 阶段一：企业名 -> OpenAlex 机构候选
python3 openalex_benchmark_scan.py resolve --email wuwei@qftechtalent.com

# ↓ 人工核验（别跳）：打开 out/01_institution_candidates.csv，
#   确认哪几行是真正要的企业，把 keep 列改成 1

# 阶段二：扫已确认的机构
python3 openalex_benchmark_scan.py scan --email wuwei@qftechtalent.com
```

常用参数：`--from-year 2021 --to-year 2026`、`--max-works 400`（每机构抽样上限）、
`--candidates 5`（每个搜索词返回几个候选）、`--delay 0.15`（请求间隔）。

## 产出

| 文件 | 内容 |
|---|---|
| `out/01_institution_candidates.csv` | 机构候选，**人工把 keep 改成 1**；`risk` 列已自动标出可疑行 |
| `out/02_institution_scan.csv` | 每机构：论文数 / 开放全文数 / 作者数 / ORCID 数 / 学科分布 / 建议 |
| `out/03_author_sample.csv` | 抽样到的具体作者（姓名 + ORCID + 最近论文） |

## 怎么读

* **`works_open_access` 才是邮箱来源的分母**，不是论文总数 —— 你系统的邮箱抽取
  只在有开放全文的论文上跑得动。
* `distinct_authors_in_sample` 是抽样去重数，不是该机构总人数，只用来判断
  「这条源值不值得建」。
* `authors_with_orcid` 这批人能直接接进现有 ORCID 链路，是最省事的一批。
* `verdict` 三档：论文路可行 / 论文路薄（走专利） / 放弃论文路。

## 已知坑（两轮实测踩出来的，脚本已修）

1. **搜索词不能带法人后缀。** OpenAlex 把企业统一存成「裸名 (国家)」，
   `search=` 是 AND 语义 —— `Corning Incorporated` / `Gentex Corporation` /
   `AGC Inc` 全部零匹配。种子清单现在一律用裸名。
2. **必须排掉非论文记录。** OpenAlex 现在也收 GitHub 仓库、数据集，
   它们 OA 率近 100%，会把 `works_open_access` 打成废数。
   脚本用 `WORK_TYPES` 常量限定只统计 article/review/preprint/会议论文/书章。
3. **OpenAlex 对企业的挂靠消歧本身就很脏 —— 这是最要命的一条。**
   第二轮实测：Lumentum (US) 95 个作者，抽样标题是《古兰经教学法》《乳腺癌凝血级联》；
   OFS (US) 75 个作者，抽样标题是食草动物演化、丹麦肉鸡死亡原因；
   Corning (US) 561 个作者里混着艾滋病过量死亡、推荐系统综述。
   原因是企业名会撞地名（Corning, NY）、常用词（Coherent / Vitro / View / NKT），
   以及方法学里提到的耗材品牌。**全量 works 越大越可疑，不是越好。**
   反过来 TE SubCom 只有 6 篇，但 4 篇里 3 篇精准（EDFA、双芯高非线性光纤、海底设备）。

   **唯一有效的办法是「机构 × 学科关键词」求交集**，脚本用 `SECTOR_KEYWORDS` 做这件事，
   只看 `works_ondomain` 列，`works_all_*` 仅用于算对口率。

4. **通用短词会撞医学机构。** `Vitro`（墨西哥玻璃厂）撞出一堆 "in vitro" 医学机构，
   `NKT` 撞出 NKT Therapeutics。scan 现在输出 `medical_ratio` 和 `top_field`，
   医学占比过半直接判「疑似认错机构」。

## 阶段一必须人工核验的原因

OpenAlex 的机构搜索会把同名的博物馆、医院、地名、基金会和大学一起返回
（搜 Corning 会同时给出 Corning Incorporated 和 Corning Museum of Glass）。
勾错一行，后面整条源的数据全是脏的。

一家企业可以勾多行 —— 母公司和研发子公司在 OpenAlex 里常是独立机构，
脚本用 `lineage` 过滤，勾母公司通常已经能覆盖子公司，但不保证。

## 学科关键词在哪改

脚本顶部 `SECTOR_KEYWORDS`，按板块给一组英文关键词（OpenAlex 的 `title_and_abstract.search`，
`|` 是 OR）。**新增板块必须同时加关键词**，否则该板块退化成不带闸门的全量扫描，
结果就是第二轮那种一半噪声。

## 种子清单在哪改

脚本顶部的 `SEEDS` 常量，格式：`(板块, 国内源企业, 国际企业搜索词, 业务线备注)`。
仁硕 / 聚富 确认全称后照格式加进去即可，末尾有注释掉的占位行。
