# 企业研发人员发现：独立试验脚本

输入企业名称，例如 `Prysmian Group`；输出该企业的海外研发人员线索、邮箱验证记录及人工审核清单。

运行环境：Python 3.10+，macOS/Linux；只用标准库，无需安装依赖。脚本不连接本项目数据库，不发邮件。

## 当前最小测试范围

- 默认公司搜索最多 1 次、人员搜索最多 1 页（25 条线索，不代表补全 25 人）。
- **默认人员补全最多 1 次，NeverBounce 验证最多 1 次。**
- 姓名、当前任职、个人所在地不足时保留待核实状态，可能不会调用 NeverBounce。
- 不获取手机号、私人邮箱，不启用 Apollo waterfall 多供应商补全。
- 限额按同一输出目录中的累计请求计算。失败和中断也占请求上限，避免重跑重复消耗。
- 公司名称搜索需 Apollo `api/v1/mixed_companies/search` 权限；人员搜索需 `api/v1/mixed_people/api_search`，补全需 `api/v1/people/match`。Master Key 只涵盖账号本来具有的接口权限，不会解除套餐或账号限制。

## Key 配置

在本地 properties/env 文件写入下面两个配置项（把占位符替换为各自密钥）：

```properties
APOLLO_API_KEY=你的Apollo密钥
NEVERBOUNCE_API_KEY=你的NeverBounce密钥
```

支持空行、`#`/`!` 注释、`export KEY=...`、完整包围值的单/双引号。按普通文本读取，不执行文件内容。已设置的同名环境变量优先于文件。密钥不写入日志或 checkpoint。

本次使用的文件为 `scripts/properties/apllo.properties`（文件名沿用现有拼写）；已添加精确 Git 忽略规则。

## 亨通光电需求、Prysmian 对标案例

先预览参数；不读取密钥、不联网、不花额度：

```bash
python3 scripts/probe_corporate_experts.py --company "Prysmian Group" --dry-run
```

正式最小测试，在项目根目录运行：

```bash
python3 scripts/probe_corporate_experts.py \
  --company "Prysmian Group" \
  --env-file scripts/properties/apllo.properties \
  --limit 1 \
  --max-verifications 1 \
  --max-pages 1 \
  --output-dir exports/prysmian-smoke
```

实际发往 Apollo 的企业名查询会去除 Group 等后缀以兼容命名变体；只有归一化后唯一匹配的公司才继续。公司候选及 Apollo ID 保存到 `organizations.csv`。子公司、其他品牌不自动扩展进来。

默认搜索 R&D、Research、Scientist、Product Development、Process Development，并优先排序研发主管、首席/资深科学家。补全后用当前公司 ID 再核实任职；历史任职不能用于放行。排除所在地为中国或未知的人员，不根据所在地推断国籍。

只输入公司名称时，返回该公司各方向研发线索。若需求进一步明确为光纤/光缆，可在**后续另一次获准测试**中加：

```text
--keywords "optical fiber" "optical fibre" "cable" "materials"
```

关键词只用于人员职位/简介排序及标记匹配依据，不能证明个人技术能力；不会把“公司做光纤”当成“每个人都做光纤”。原计划中的个人专利、产品和项目证据尚需人工核实，首版不抓取官网、LinkedIn 或专利库。

## 邮箱策略

1. 优先使用 Apollo 补全返回的非已知免费邮箱地址。
2. 公司邮箱域名与官网不同，仍保留提供商地址，但标记 `DIFFERENT_DOMAIN_REVIEW`，需核实历史域名/关联关系。
3. 缺邮箱时，仅以完整、可明确拆分的英文 first_name 和 last_name 生成候选；不根据脱敏姓氏、缩写、复姓或自行音译生成。
4. 默认候选格式依次为 `first.last`、`首字母last`、`firstlast`。默认总验证上限为 1，因此只会验证第一个可用地址。
5. 可用 `--patterns '{f}{last}'` 指定已知公司格式；不自动把通用格式当成已证实的公司命名规律。
6. `valid`：进入待审核清单。猜测邮箱的 `email_ownership` 始终为 `UNCONFIRMED`。
7. `invalid`/`disposable`：仅在还有验证预算时尝试下一个候选。
8. `catchall`/`unknown`/请求超时：停止该人的其他格式尝试。已有效的地址也不继续验证其他格式。
9. 同一邮箱关联多个人时标记 `CONFLICT`。角色邮箱、免费邮箱或风险标记单列复核，不进入常规待审核清单。

## 输出文件

| 文件 | 内容 |
|---|---|
| `organizations.csv` | 搜索得到的公司、域名及 Apollo ID |
| `people.csv` | 搜索线索及未选中、任职冲突、所在地、邮箱状态等原因 |
| `email_checks.csv` | 实际尝试验证的地址、来源/猜测格式、结果、时间 |
| `review.csv` | 邮箱有效但研发资格/邮箱归属仍需人工审核的线索 |
| `summary.json` | 累计/本次请求数、结果统计、错误和额度估算 |
| `checkpoint.json` | 请求预留、已返回数据、错误状态，用于断点恢复 |

CSV 使用 UTF-8 BOM，便于 Excel 打开。`qualification_status=NEEDS_RND_EVIDENCE` 表示待补个人生产研发证据；本脚本不会输出“已确认生产研发专家”或可自动发送名单。

## 费用、恢复与错误

- 当前 Apollo 文档中，公司搜索为每页 1 credit；人员搜索为 0 credit；普通人员资料/邮箱补全最多按每人 1 credit 估算（本脚本关闭手机和 waterfall）。因此默认正常走完约为 **Apollo 最多 2 credits、NeverBounce 最多 1 次验证**。最终以账户实际扣费为准。
- 请求发出前先原子保存 PENDING。中断后相同命令可复用成功响应；PENDING/超时/错误请求不会自动重试。输出目录使用进程锁防止两个进程同时消费预算。
- 提高 `--limit` / `--max-verifications` / `--max-pages` 会允许**新增真实请求**，仅在愿意继续消费额度时使用。
- 换公司、关键词、国家、格式或运行模式时，需要新 `--output-dir`，防止混用旧结果。新目录是独立新预算，不能用来规避同一测试的消费限制。
- 401/403：检查对应 Key、接口权限、账号及套餐。报错缓存后，即使修正 Key，原目录仍不会自动重试；确认允许再测后使用新输出目录。
- 429/额度不足：停止，不循环重试。未知结果不能作为有效邮箱。
- NeverBounce 的 `general_failure` 是通用请求错误，具体原因以响应 `message` 为准，不能仅凭状态码判断余额或密钥问题。脚本会保存脱敏后的完整错误响应及详细提示；旧版已经丢失的 message 无法从旧 checkpoint 恢复，也不会为恢复消息而自动重发验证请求。
- 公司重名：查看 `organizations.csv`，用 `--domain` 指定已确认的域名并另开输出目录。`--domain` 仍调用公司搜索，不绕过接口权限。

## 本次真实测试记录

`exports/prysmian-smoke/summary.json` 记录首次实测结果：公司搜索 HTTP 403；公司查询请求 1 次、人员搜索 0 次、补全 0 次、NeverBounce 验证 0 次。

没有找到真实人员或验证真实邮箱；公司接口权限问题尚待处理。**请求数不等于扣费数，本次是否扣 Apollo credit 以后台为准。**

后续用户更新 Master Key 后，`exports/prysmian-master-test/summary.json` 显示：公司搜索、人员搜索、人员补全各 1 次，找到 25 条线索；NeverBounce 请求 1 次，返回 `general_failure`。原版未保留该次服务端详细 message，不能从旧记录恢复具体文案。

随后仅调用只读 `/v4.2/account/info`，没有重跑邮箱验证。脱敏结果保存在 `exports/prysmian-master-test/neverbounce-account-diagnostic.json`：账户查询成功，免费/付费剩余额度以及已用额度均为 0。账户当时没有可用验证额度，且未显示有已消耗的赠送额度；先向后台/客服确认试用赠送或补充余额，再安排下一次验证。旧失败 checkpoint 仍不会自动重试。

## 离线验证

```bash
python3 -m unittest discover -s scripts -p test_probe_corporate_experts.py
```

测试全部使用合成人员和 `.example` 域名，不访问 API、不消耗额度。`--fixture-file` 提供显式离线重放入口，输出 `run_mode=OFFLINE_FIXTURE`；样本结构可参考测试文件。不要把离线样本当成真实搜索结果。

参考：[Apollo 公司搜索](https://docs.apollo.io/reference/organization-search)、[人员搜索](https://docs.apollo.io/reference/people-api-search)、[补全](https://docs.apollo.io/reference/people-enrichment)、[API 计费](https://docs.apollo.io/docs/api-pricing)、[NeverBounce 验证](https://developers.neverbounce.com/reference/single-check)。
