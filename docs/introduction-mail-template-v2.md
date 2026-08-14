# 介绍邮件模板 v2（推荐稿）

> 依据：`docs/qa提炼-完整版.md` 第二部分归纳 + `MailPlaceholderService` 现有占位符白名单。
> 核心约束：**发介绍邮件时我方尚未匹配企业**，先拿专家信息 → 再找有意向的企业 → 再撮合。
> 模板必须诚实反映这个顺序，不能暗示"已经有企业在等你"。

---

## 一、主题行

**推荐（个性化）**

```
Remote advisory collaboration with Chinese industry — ${primaryResearchField|your research area}
```

**备选（完全静态，零渲染风险）**

```
Remote advisory collaboration with Chinese industry
```

不用感叹号、不用全大写、不用 "Opportunity" / "Invitation" / "Urgent"，这三类词是投递侧的高风险词。

---

## 二、正文（可直接粘贴进模板编辑器）

```
Dear Dr. ${expertFamilyName|Colleague},

I came across your paper "${recentWorkTitle|your recent work}" and wanted to write to you directly.

I work with ${teamName}, a Chinese agency supporting a national-level talent programme that places experienced researchers as technical advisors to Chinese companies. The programme is government-initiated, and funded jointly by government and the partner company.

Let me be straightforward about the sequence, because it is the opposite of what emails like this usually claim: I do not have a company waiting for you. Matching happens afterwards — we first understand what a researcher actually works on, then approach companies whose technical problems fit, and introduce you only to the ones that are genuinely interested. Today I know your published work and nothing more than that.

If it does lead somewhere, the usual arrangement is that you keep your current position and advise remotely, travelling to China once or twice a year only where the work calls for it. Travel costs are ours. No relocation, and no exclusivity asked of you at this stage.

I am not asking for documents or for any commitment today — only whether this is worth a conversation. There is no cost to you at any point in this programme.

If you are open to it, a reply is enough. A sentence or two on the problems you are working on now would help me look in the right direction. Email is fine throughout; a call only if you would prefer one.

Best regards,
${senderName}
${senderTitle}, ${teamName}
${senderEmail}
```

**页脚（独立于签名，放在最后）**

```
You received this message because your work is publicly indexed in academic literature databases. If you would prefer not to hear from us again, unsubscribe here: ${unsubscribeUrl}
```

---

## 三、逐段取舍理由

| 段落 | 写了什么 | 为什么 |
|---|---|---|
| 称呼 | `Dr. ${expertFamilyName}` | 池子里既有教授也有企业顾问（如 2143 Sven Gohla 自 2012 年起做顾问），`Prof.` 会用错；`Dr.` 对博士群体全域安全 |
| 开场 | `came across your paper "标题"` | 系统只持有标题不持有全文，`came across` 是能兑现的措辞（沿用 `personalization-gate-p1-send-gate.md` 的既定结论）。**不写** "your work on XXX"——那是主题不是标题，泛化即模板感 |
| 我方身份 | 国家级、政府发起、政府+企业共同出资 | QA 2077-2：专家必问"谁出钱"。首封就答掉一个高频问题 |
| **不匹配企业的诚实交代** | "I do not have a company waiting for you" | 这是整封信的信任支点。QA 2143 整条对话都在问"你们是不是正规的"；一封承认自己此刻不知道什么的邮件，比一封声称已匹配的邮件可信得多。旧模板那句 "It appears relevant to technical needs we are currently evaluating with industrial partners" 是我们兑现不了的暗示 |
| 合作形式 | 保留现职 / 远程 / 一年 1-2 次 / 差旅我方承担 / 不需搬迁 | QA 规则 6、7 的原话，是专家最关心的落地条件 |
| "no exclusivity **at this stage**" | 预埋 | 单一申报承诺是后续必提的（QA B-2 高频）。先说"此阶段不要求"，后面再提就不是变卦 |
| 不要材料、不收费 | 绑在"我现在什么都不要"后面 | QA B-4 标注"信任关键"。单独一句 "we never charge" 反而像骗子话术；挂在"今天不向你要任何东西"之后才成立 |
| CTA | 回一封邮件 + 一句研究方向即可 | QA 2077-3：现阶段只需 research topic。QA 2285-1：专家明确表示国家级项目敏感、初期只愿邮件沟通 → **不能把电话会当默认动作** |
| 页脚 | 说明来源 + 退订 | 降低举报率；退订放最后，不再插在正文和落款之间 |

### 首封**不写**的内容

- **资助金额（300万–1200万 RMB）**：QA 里这是对话第 3 轮才出现的问题。冷邮件里出现大额数字，垃圾特征拉满。等对方问。
- **成功率 10%**：真实但劝退，且对方还没进入决策阶段。
- **护照、学位证、在职证明**：`qa提炼` 明确结论——重资料不出现在任何自动回复中，走人工 `MATERIAL_REMINDER`。
- **承诺视频**：更靠后，首封提会吓退人。
- **`${institution}`**：解析层脏数据未修前禁止入正文（见 `affiliation-raw-jats-text-in-mail`）。
- **`${hIndex}` / `${worksCount}`**：把人当指标念出来，冒犯风险高于个性化收益。

---

## 四、配套设置（不做这些，模板本身救不了）

**1. 打开人格化闸门，让占位符兜底值变成"拦截信号"而不是"替代文案"**

`PersonalizationGateService.evaluate()` 的 `requiredKeys` 建议设为：

```
["expertFamilyName", "recentWorkTitle"]
```

`MailPlaceholderService.validatePlaceholders()` 强制每个 nullable 占位符必须带 `|兜底值`，所以模板里必须写 `|Colleague`、`|your recent work`。但这两个兜底值一旦真的渲染出来，这封信就退化成群发件了。把它们放进 `requiredKeys`，数据缺失时直接 blocked，而不是发一封"Dear Dr. Colleague"。

**2. 目标池硬排除中国境内机构**

模板里"来中国"的整套叙事对已在中国的专家不成立。不能只靠运营在配置里勾 `regions`（该字段在手动执行面板会被静默丢弃）。

**3. 发送前脏值闸**

`institution` / `employment` 含 `http`、`ror.org`、`grid.`、ISNI 式数字串或以数字开头 → 判脏拦截。即使本模板不用 `institution`，别的模板会用。

**4. 签名里加一条可验证信息**

公司官网或办公地址。`qa提炼` 里"项目无对外官网"指的是**项目**，不是**代理机构**——机构本身是可以露出的，这是成本最低的信任升级。QA 2143-3 正是卡在这里。

---

## 五、渲染示例

假设专家 = Dr. Anders Salvatori，近期论文 *Fatigue Crack Growth in Welded Offshore Structures*，方向 Structural Health Monitoring：

> **Subject:** Remote advisory collaboration with Chinese industry — Structural Health Monitoring
>
> Dear Dr. Salvatori,
>
> I came across your paper "Fatigue Crack Growth in Welded Offshore Structures" and wanted to write to you directly.
>
> I work with Qingfei Tech Talent Team, a Chinese agency supporting a national-level talent programme that places experienced researchers as technical advisors to Chinese companies. The programme is government-initiated, and funded jointly by government and the partner company.
>
> Let me be straightforward about the sequence, because it is the opposite of what emails like this usually claim: I do not have a company waiting for you. Matching happens afterwards — we first understand what a researcher actually works on, then approach companies whose technical problems fit, and introduce you only to the ones that are genuinely interested. Today I know your published work and nothing more than that.
>
> If it does lead somewhere, the usual arrangement is that you keep your current position and advise remotely, travelling to China once or twice a year only where the work calls for it. Travel costs are ours. No relocation, and no exclusivity asked of you at this stage.
>
> I am not asking for documents or for any commitment today — only whether this is worth a conversation. There is no cost to you at any point in this programme.
>
> If you are open to it, a reply is enough. A sentence or two on the problems you are working on now would help me look in the right direction. Email is fine throughout; a call only if you would prefer one.
>
> Best regards,
> Chen Jingjing
> Customer Care Officer, Qingfei Tech Talent Team
> chenjj@qftechtalent.com
>
> ---
> You received this message because your work is publicly indexed in academic literature databases. If you would prefer not to hear from us again, unsubscribe here: https://qingfei.szwbww.com/talent/u/unsubscribe?token=...

---

## 六、回复层需要准备的（本模板刻意留给下一封）

按 `qa提炼` 第二部分 B 表优先级，首封收到回复后最可能立刻被问到：

1. 资助多少 / 谁发薪 → 规则 8 SALARY
2. 你们是官方机构吗、能证明吗 → **B-5，目前无自动回复覆盖**
3. 材料保密吗、要不要收费 → **B-4，目前无自动回复覆盖**
4. 时间线、什么时候出结果 → 规则 9 / 10
5. 具体是哪家企业 → 只能答"匹配在后"，与首封口径必须一致

第 2、3 条是首封诚实交代之后最可能被追问的，建议优先补这两条规则，否则首封建立的信任在第二封断掉。
