(function (root) {
  'use strict';
  const RULE_VERSION = 'online-v4-unverified-non-global500';
  const TTL = 30 * 86400000;
  const STRATEGIES = { 1:'普通 Engineer / Scientist 等生产技术研发岗起', 2:'Senior / Sr 等高级技术研发岗起', 3:'Staff / Principal / Lead / 技术经理起', 4:'Chief / Distinguished / 技术总监等岗位' };
  const SECTORS = { semiconductors: 20, ai: 20, software: 20, biopharma: 20, quantum: 20, advanced_materials: 10, automation: 10, aerospace: 10, other: 0 };
  const PRIORITIES = {
    ic_lithography: 'EUV/BEUV/纳米压印', ic_materials: '集成电路先进材料与工艺', ic_metrology: '集成电路量检测设备', ic_devices: '新型器件架构', ic_physics: '半导体物理基础理论',
    ai_models: '算法模型', ai_architecture: 'AI处理架构', ai_chips: 'AI芯片', ai_embodied: '具身智能', ai_brain: '脑科学与类脑计算', ai_theory: 'AI基础理论',
    quantum_communication: '量子通信', quantum_computing: '超导/硅基/离子阱量子计算', quantum_measurement: '量子精密测量', quantum_theory: '量子信息基础理论',
    bio_drugs: '新药创制', bio_cell: '细胞治疗', bio_gene: '基因编辑', bio_synthetic: '合成生物学', bio_medical: '生物医学工程', bio_clinical: '临床医师科学家', bio_theory: '生命科学基础理论',
    ic: '集成电路（未证实细分方向）', ai: '人工智能（未证实细分方向）', quantum: '量子科技（未证实细分方向）', bio: '生物科技（未证实细分方向）', other: '非重点产业'
  };
  const key = name => String(name || '').normalize('NFKC').toLowerCase().replace(/\s+/g, ' ').trim();
  const httpURL = value => { try { const u = new URL(value); return /^https?:$/.test(u.protocol) && !u.username && !u.password; } catch { return false; } };
  function review(query, reason, sources = [], now = Date.now()) {
    return { query, ruleVersion: RULE_VERSION, status: 'review', reason, score: null, requiredLevel: null, company: null, sources,
      checkedAtMs: now, expiresAt: now + 600000 };
  }
  function assess(query, facts, sources, now = Date.now()) {
    const uncertain = message => review(query, message, sources, now);
    if (!Array.isArray(sources) || !sources.length || sources.some(s => !httpURL(s.url))) return uncertain('缺少有效联网来源');
    const supported = f => f && typeof f.evidence === 'string' && f.evidence.trim().length >= 8 && Array.isArray(f.sources) && f.sources.length && f.sources.every(i => Number.isInteger(i) && i >= 0 && i < sources.length);
    for (const field of ['identity', 'sameEntity']) {
      if (!supported(facts?.[field]) || facts[field].value === null) return uncertain('待核实：' + field + ' 缺少明确事实或来源');
    }
    if (facts.sameEntity.value !== true) return uncertain('企业重名或母子公司实体未确认');
    if (typeof facts.identity.value !== 'string' || !facts.identity.value.trim()) return uncertain('企业身份不完整');
    const global500 = facts.global500?.value === true;
    let year;
    if (global500) {
      if (!supported(facts.global500) || !supported(facts.global500Year) || facts.global500Year.value === null) return uncertain('待核实：global500 缺少明确事实或来源');
      year = facts.global500Year.value;
      if (!Number.isInteger(year) || year < new Date(now).getUTCFullYear() - 1 || year > new Date(now).getUTCFullYear()) return uncertain('五百强核验年度已过期或无效');
      const officialFortune = facts.global500.sources.some(i => {
        const source = sources[i];
        const host = new URL(source.url).hostname;
        return /(^|\.)fortune\.com$/.test(host);
      });
      if (!officialFortune) return uncertain('五百强身份需 Fortune 官方名单证据，不能用美国500强或母公司身份替代');
    }
    if (!global500) {
      for (const field of ['country','developedEconomy','sector','priority']) if (!supported(facts[field]) || facts[field].value === null) return uncertain('待核实：' + field + ' 缺少明确事实或来源');
      if (typeof facts.country.value !== 'string' || !facts.country.value.trim() || typeof facts.developedEconomy.value !== 'boolean') return uncertain('国家分类不明确');
      if (!Object.prototype.hasOwnProperty.call(SECTORS, facts.sector.value) || !Object.prototype.hasOwnProperty.call(PRIORITIES, facts.priority.value)) return uncertain('产业分类尚未明确');
    }
    const sector = facts.sector?.value;
    const priority = facts.priority?.value;
    // Global-500 eligibility is sufficient; unused fields do not become asserted facts.
    const countryPoints = global500 ? null : facts.developedEconomy.value ? 20 : 10;
    const industryPoints = global500 ? null : SECTORS[sector];
    const priorityPoints = global500 ? null : priority === 'other' ? 0 : ['ic', 'ai', 'quantum', 'bio'].includes(priority) ? 40 : 60;
    const score = global500 ? 100 : countryPoints + industryPoints + priorityPoints;
    const requiredLevel = global500 ? 1 : score >= 80 ? 2 : score >= 60 ? 3 : 4;
    // Persist only the exact requested employer, never inferred subsidiaries or group aliases.
    const company = { id: 'online-' + encodeURIComponent(key(query)).replace(/[^a-z0-9]/gi, '').slice(0,60), name: query.trim(), aliases: [],
      headquartersCountry: global500 ? '不参与评分（五百强直通）' : facts.country.value, developedEconomy: global500 ? false : facts.developedEconomy.value, global500,
      ...(global500 ? { global500Year: year } : {}), highSalaryIndustryPoints: industryPoints ?? 0, priorityIndustryPoints: priorityPoints ?? 0,
      priorityIndustry: global500 ? '不参与评分（五百强直通）' : PRIORITIES[priority], checkedAt: new Date(now).toISOString().slice(0,10), sources: sources.map(s => s.url).slice(0,20) };
    return { query, ruleVersion: RULE_VERSION, status: 'rated', score, requiredLevel, company, facts, sources,
      breakdown: { country: countryPoints, industry: industryPoints, priority: priorityPoints },
      reason: `${global500 ? '世界五百强直通' : '世界五百强未证实，按非五百强 0 分（低置信）；国家' + countryPoints + '＋产业' + industryPoints + '＋重点方向' + priorityPoints}：${score}分，要求L${requiredLevel}（${STRATEGIES[requiredLevel]}）`,
      checkedAtMs: now, expiresAt: Math.min(now + TTL, Date.UTC(new Date(now).getUTCFullYear() + 1, 0, 1)) };
  }
  function fresh(result, now = Date.now()) {
    return Boolean(result && result.ruleVersion === RULE_VERSION && Number.isFinite(result.checkedAtMs) && result.checkedAtMs <= now && Number.isFinite(result.expiresAt) && now < result.expiresAt && result.expiresAt <= result.checkedAtMs + TTL);
  }
  function enterpriseConfig(results, now = Date.now()) {
    const companies = new Map();
    for (const r of results) if (fresh(r, now) && r.status === 'rated' && r.company) companies.set(key(r.company.name), r.company);
    return { schemaVersion: 1, revision: RULE_VERSION, companies: [...companies.values()].map((c, i) => ({ ...c, id: 'online-' + i })) };
  }
  const api = { RULE_VERSION, TTL, SECTORS, PRIORITIES, key, httpURL, review, assess, fresh, enterpriseConfig };
  root.ContactOutResearch = api;
  if (typeof module !== 'undefined') module.exports = api;
})(globalThis);
