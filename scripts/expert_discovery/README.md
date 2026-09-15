# 对标企业专家搜索

默认流程：Excel 一行人才需求 + 已确认的对标企业名称/域名 → DeepSeek 生成职位筛选条件 → Apollo People Search 返回人员名单 → DeepSeek 按需求排序 → 人工查看名单。发现阶段不查询企业、不解锁邮箱、不调用 Emailable。

## 安装与密钥

```bash
cd scripts/expert_discovery
python3 -m pip install -r requirements.txt
cp keys.example.properties keys.properties
chmod 600 keys.properties
```

在 `keys.properties` 中填写 `DEEPSEEK_API_KEY`、`APOLLO_API_KEY`、`EMAILABLE_API_KEY`。前两个只在执行发现时读取；Emailable Key 只在单独批准邮箱验证后读取。密钥文件已被 `.gitignore` 排除。

## 1. 生成计划：0 次 API

```bash
python3 cli.py --job runs/prysmian \
  plan \
  --excel '/绝对路径/企业高端人才需求表.xlsx' \
  --row 4 \
  --benchmark 'Prysmian Group' \
  --domain 'prysmiangroup.com'
```

`--row` 是 Excel 中实际数据行号；默认读取第一个工作表，也可传 `--sheet '工作表名'`。脚本在前 20 行识别“企业名称、岗位名称、专业领域”表头，并读取同一行的研究方向、难点、职责、经验等字段。

输出 `discover` 提案及 ID。此时没有联网，也没有任何 API 调用。

## 2. 批准并搜索人员

先看提案中的企业、域名及最大调用数，然后执行：

```bash
python3 cli.py --job runs/prysmian approve --proposal '提案ID' --confirm
python3 cli.py --job runs/prysmian discover --proposal '提案ID'
python3 cli.py --job runs/prysmian review
```

发现阶段最多执行：

- DeepSeek 需求转换 1 次；
- Apollo People Search 1 页，最多 25 条；
- DeepSeek 名单排序 1 次。

Apollo 查询只使用对标企业域名、职位、职级和明确的地域条件。Apollo 的企业域名筛选可能匹配历史雇主；脚本会排除返回结果中明确属于其他当前雇主的人。缺少当前企业信息的结果会保留供人工判断。搜索响应中的邮箱即使出现也不会写入名单。

截至 2026-09-09，Apollo 官方文档说明 People API Search 为 0 credits，且该接口不返回邮箱或电话；账号仍须具备接口权限。实际权限与计费以账号控制台为准：<https://docs.apollo.io/reference/people-api-search>。

## 3. 人工查看名单

`review` 和自动导出的 `review.txt` 展示：姓名、职位、所在地、当前企业线索、DeepSeek 匹配分、匹配理由、命中需求和证据限制。职位匹配只是筛选线索，不证明候选人的具体项目能力。

任务目录包含：

- `job.json`：配置、审批、请求日志及缓存；
- `review.txt`：人工审核名单；
- `candidates.csv`：可筛选名单；
- `evidence.jsonl`：来源线索；
- `summary.json`：调用次数和模型 usage。

## 4. 只对选中的人补邮箱

看完名单后，明确选择一人：

```bash
python3 cli.py --job runs/prysmian propose enrich \
  --ids '人员ID' \
  --reason '已确认该候选人值得联系，仅补全此人的工作邮箱'
```

查看新提案后再批准、执行：

```bash
python3 cli.py --job runs/prysmian approve --proposal '补全提案ID' --confirm
python3 cli.py --job runs/prysmian enrich --proposal '补全提案ID'
python3 cli.py --job runs/prysmian review
```

默认最多补全 1 人，不请求手机号、私人邮箱或 waterfall。Apollo People Enrichment 可能消耗额度，因此始终与人员搜索分开审批。

## 5. 只验证选中的邮箱

名单和邮箱均人工确认后再提出 Emailable 验证：

```bash
python3 cli.py --job runs/prysmian propose verify \
  --ids '人员ID' \
  --emails '选定邮箱' \
  --reason '人员已通过审核，需要确认该工作邮箱的投递性'
```

批准后执行：

```bash
python3 cli.py --job runs/prysmian approve --proposal '验证提案ID' --confirm
python3 cli.py --job runs/prysmian verify --proposal '验证提案ID'
```

脚本先查 Emailable 余额，再验证最多 1 个明确邮箱。验证结果只说明投递性，不证明身份或在职情况。

## 安全与恢复

每次请求会先写 `PENDING` 再联网。网络异常标记为 `UNCERTAIN`，不会自动重试；应先在提供商控制台核对。已完成的相同请求会复用本地缓存。脚本不发送邮件、不写线上人才库。

旧的公开来源流程和离线 `--demo` 仍保留用于兼容，但 Excel 入口默认使用 Apollo People Search。

## 测试

```bash
python3 -m unittest discover -s . -p 'test_*.py' -v
```

测试全部使用生成的 Excel 与本地模拟响应，不访问真实 API。
