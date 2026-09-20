# 可更新的通用职级规则（1.3.0）

## 启用与更新

1. 先导出名单和「已保存规则」备份；更新扩展使用原目录并重新加载，不卸载。
2. 打开「编辑忽略列表」→「恢复默认到编辑框」→「保存并应用规则」，启用 schemaVersion 2 通用三态。
3. 后续编辑 JSON 或导入新 JSON，再保存；保存替换整份配置，不是追加。导入、恢复默认仅填框，不保存不生效。
4. 当前 ContactOut 标签页会应用已保存配置，其他标签页需再次点击「标记职级（红／黄）」。导出的是已保存配置，不是未保存的编辑内容。

已有 v1 自定义配置不会自动升级；弹窗会提示旧模式。v1 继续按旧完整职位排除规则工作，不提供新版黄色与历史高级保护。若继续沿用旧方向规则，仍可能标红数据、香精等人员。启用通用筛选必须显式恢复默认并保存。文档不会被自动读取，规则由你更新。

## 三态与优先级

1. 当前或历史任职有明确高级技术线索 → 无色保留；优先于负向规则、未展开提示。
2. 没有可靠当前任职、more 未展开、可见履历解析不完整 → 黄色待核实。
3. 每一个当前任职都命中初级词或启用的负向完整职位规则 → 红色可忽略候选。
4. 其余 → 黄色待核实，不作为确定排除。

高级技术线索：高级词与技术词同时命中，或命中高级完整职位白名单；同时不能含初级、歧义、非研发职能词。只判断任职 title，不根据公司、技能、年龄、学历判断。

| 示例 | 默认结果（可见履历完整、无其他职务时） |
| --- | --- |
| Senior Scientist II、Staff Engineer、Principal Engineer、R&D Manager、Senior Manufacturing Chemist | 无色 |
| Senior Fragrance Scientist、Senior Data Scientist | 无色；不按行业排除 |
| Junior Engineer、Intern、Manufacturing Operator III、Senior Sales Manager | 红色 |
| Engineer、Scientist、Scientist II/III、Associate Scientist | 黄色 |
| Principal Associate Scientist、Assistant Research Director、Postdoctoral Research Fellow | 黄色 |
| 当前 Junior Engineer，历史 Senior Scientist | 无色，历史高级保护 |
| 当前 Junior Engineer，仍有 more 未展开 | 黄色 |

无色表示值得保留复核，不是正式高级人才资格；红色也不是确定不存在未展示的高级经历。

## v2 配置示例

下面是可保存的简化示例，不是完整默认词表。直接保存会替换全部现有规则；建议先恢复默认并导出，再增删词条。

```json
{
  "schemaVersion": 2,
  "scope": "通用高级生产技术／研发人才",
  "revision": "custom-2026-09-18",
  "classification": {
    "seniorTerms": ["Senior", "Sr", "Staff", "Principal", "Distinguished", "Lead", "Chief", "Manager", "Director", "Head", "VP", "高级", "资深", "首席"],
    "technicalTerms": ["Engineer", "Engineering", "Scientist", "Chemist", "Research", "R&D", "Technical", "Process", "Production", "Manufacturing", "研发", "工程", "工艺", "生产"],
    "seniorTitleEquals": ["Fellow", "Technical Fellow", "Corporate Fellow", "Distinguished Fellow", "CTO", "Chief Technology Officer", "Chief Scientific Officer"],
    "juniorTerms": ["Junior", "Intern", "Internship", "Trainee", "Apprentice", "初级", "实习"],
    "ambiguousTerms": ["Associate", "Assistant", "Postdoctoral", "Postdoc", "助理", "博士后"],
    "nonResearchTerms": ["Sales", "Recruiter", "Recruitment", "Human Resources", "Payroll", "Operator", "Technician", "销售", "招聘", "人事", "操作工", "技工"]
  },
  "rules": [
    {
      "id": "operator",
      "enabled": true,
      "reason": "当前偏生产操作，未见高级技术／研发任职线索，请复核",
      "currentTitleEquals": ["Manufacturing Operator", "Manufacturing Operator III", "Production Operator"]
    }
  ]
}
```

## 字段约束

- `schemaVersion`：数字 2；兼容数字 1。v2 必须有 `classification`；v1 禁止该字段。
- `scope`：1–100 字符，用途说明，不是企业过滤条件。`revision`：1–80 字符，自定义版本。
- `classification`：上例六个数组全部必填，各 0–100 个词条，每条 1–200 字符，规范化去重。高级完整职位数组为精确匹配，其他五组为有边界的关键词匹配。非研发词仅阻止自动保留，不会单独触发红色。
- `rules`：0–100 条负向精确匹配规则；空数组不会关闭 v2 的初级判断或黄色待核实。
- 每条规则的 `id`：1–80 字符，字母、数字、下划线、短横线，不得重复；`enabled` 必须为布尔；`reason` 为 1–200 字符纯文本。
- `currentTitleEquals`：1–100 个完整当前职位；可选 `pastTitleEqualsAny`：1–100 个完整历史职位。单项 1–200 字符。若设置历史条件，当前与历史条件都满足才命中。
- 未知字段、错误类型、非法版本、重复 id 拒绝保存；不支持 `protectTitleKeywords`、代码、远程规则地址或通配／正则控制字符 `* ^ $ | \`。导入文件最大 100 KB。

## 匹配细则与边界

- 大小写不敏感，规范化全角、横线、重复空格。英文词有字母数字边界：`SeniorScientist` 不算 `Senior`；`Sr.` 可命中 `Sr`。中文词按规范化包含匹配。
- `seniorTitleEquals` 只匹配完整职位，所以 `Postdoctoral Research Fellow` 不会因为含 `Fellow` 而自动保留。
- 多个当前任职只要有一个不明确，就不能仅凭其他初级职位标红；任何可见当前／历史高级技术职务均优先保护。
- 仅基于当前页面可见证据。页面改版、缺少日期、未展开履历可能产生黄色，需要手动查看，不能猜测。
- 标记不点击 more、邮箱、电话、翻页，不请求平台接口。页面文字变化时重算；刷新网页需重新启用；「取消标记」清除红黄颜色和原因。
- 颜色不影响采集／导出。所有已显示完整邮箱仍保留，提示文字不进入履历。原采集操作仍只展开已显示完整邮箱的卡片，不获取隐藏邮箱。
- 专家缓存键仍为 `contactout-visible-export-v1`，规则键仍为 `contactout-ignore-rules-v1`。保存规则不改名单；清空名单不清空规则。
