(function (root) {
  'use strict';
  function emailsFromText(text) {
    // A masked address must not yield a plausible-looking suffix as an email.
    const candidates = String(text).match(/[^\s<>"(),;:]+@[^\s<>"(),;:]+/g) || [];
    return [...new Set(candidates.map(value => value.replace(/[.。]+$/, ''))
      .filter(value => !/[*•●…\u200b-\u200f\u202a-\u202e\uff0a]/u.test(value))
      .filter(value => /^[a-z0-9!#$%&'+/=?^_`{|}~-]+(?:\.[a-z0-9!#$%&'+/=?^_`{|}~-]+)*@(?:[a-z0-9](?:[a-z0-9-]*[a-z0-9])?\.)+[a-z]{2,63}$/i.test(value))
      .map(value => value.toLowerCase()))];
  }
  function identity(row) {
    if (row.linkedin) return 'linkedin:' + row.linkedin.replace(/\/$/, '').toLowerCase();
    if (row.name && row.company) return 'name-company:' + row.name.toLowerCase() + '|' + row.company.toLowerCase();
    return 'text:' + row.profileText.replace(/[^\s]+@[^\s]+/g, '').replace(/\s+/g, ' ').trim();
  }
  function parseEmploymentText(text) {
    const value = String(text).replace(/\s+/g, ' ').trim();
    const match = value.match(/^(.{2,200}?)\s+at\s+(.{1,180}?)\s+(?:in\s+)?(?:(?:19|20)\d{2}\s*)?[-–—]\s*(Present|(?:19|20)\d{2})$/i);
    if (!match || /\bat\b|\bPresent\b|\b(?:19|20)\d{2}\b/i.test(match[1] + ' ' + match[2])) return null;
    return { title: match[1].trim(), company: match[2].trim(), current: /^present$/i.test(match[3]) };
  }
  function normalizeTitle(value) {
    return String(value).normalize('NFKC').replace(/[–—]/g, '-').replace(/\s+/g, ' ').trim().toLowerCase();
  }
  function defaultIgnoreConfig() {
    return { schemaVersion: 3, scope: '企业评分分层的生产技术／研发人才', revision: '2026-09-19-enterprise-gates-v1', classification: {
      technicalTerms: ['Engineer', 'Engineering', 'Scientist', 'Scientific', 'Chemist', 'Chemistry', 'Research', 'R&D', 'Technical', 'Technology', 'Technologist', 'Materials', 'Process', 'Production', 'Manufacturing', 'Development', '研发', '研究', '工程', '工艺', '生产', '技术', '科学', '化学'],
      level2Terms: ['Senior', 'Sr', '高级', '资深'],
      level3Terms: ['Staff', 'Principal', 'Lead', 'Manager', '主任', '经理', '负责人'],
      level4Terms: ['Distinguished', 'Chief', 'Director', 'Head', 'VP', '首席', '总监', '总工程师'],
      level4TitleEquals: ['Fellow', 'Technical Fellow', 'Corporate Fellow', 'Distinguished Fellow', 'CTO', 'Chief Technology Officer', 'Chief Scientific Officer'],
      juniorTerms: ['Junior', 'Intern', 'Internship', 'Trainee', 'Apprentice', '初级', '实习', '见习', '学徒'],
      ambiguousTerms: ['Associate', 'Assistant', 'Postdoctoral', 'Postdoc', '助理', '博士后'],
      nonResearchTerms: ['Sales', 'Recruiter', 'Recruitment', 'Human Resources', 'Payroll', 'Operator', 'Technician', '销售', '招聘', '人事', '操作工', '技工']
    }, rules: [
      { id: 'operator', enabled: true, reason: '当前偏生产操作，未见高级技术／研发任职线索，请复核', currentTitleEquals: ['Manufacturing Operator', 'Manufacturing Operator I', 'Manufacturing Operator II', 'Manufacturing Operator III', 'Manufacturing Operator IV', 'Production Operator', 'Machine Operator', 'Assembly Operator'] },
      { id: 'sales', enabled: true, reason: '当前偏销售，未见高级技术／研发任职线索，请复核', currentTitleEquals: ['Sales Representative', 'Sales Manager', 'Senior Sales Manager', 'Account Executive'] },
      { id: 'recruitment', enabled: true, reason: '当前偏招聘／人事，未见高级技术／研发任职线索，请复核', currentTitleEquals: ['Recruiter', 'Senior Recruiter', 'Talent Acquisition Manager', 'Human Resources Manager'] },
      { id: 'administration', enabled: true, reason: '当前偏行政，未见高级技术／研发任职线索，请复核', currentTitleEquals: ['Administrative Assistant', 'Office Administrator', 'Receptionist'] }
    ] };
  }
  function validateIgnoreConfig(input) {
    const fail = message => { throw new Error('忽略规则：' + message); };
    const object = (value, allowed, label) => {
      if (!value || typeof value !== 'object' || Array.isArray(value)) fail(label + ' 必须是对象');
      for (const key of Object.keys(value)) if (!allowed.includes(key)) fail(label + ' 含未知字段 ' + key);
    };
    const string = (value, label, max = 200) => {
      if (typeof value !== 'string' || !value.trim() || value.length > max) fail(label + ` 必须是 1–${max} 字符的文本`);
      return value.trim();
    };
    const titles = (value, label, allowEmpty = false) => {
      if (!Array.isArray(value) || (!allowEmpty && !value.length) || value.length > 100) fail(label + (allowEmpty ? ' 必须含 0–100 个词条' : ' 必须含 1–100 个完整职称'));
      const result = value.map(item => string(item, label));
      if (result.some(item => /[*^$|\\]/.test(item))) fail(label + ' 不支持通配符或正则表达式');
      return [...new Map(result.map(item => [normalizeTitle(item), item])).values()];
    };
    object(input, ['schemaVersion', 'scope', 'revision', 'rules', ...([2, 3].includes(input?.schemaVersion) ? ['classification'] : [])], '配置');
    if (![1, 2, 3].includes(input.schemaVersion)) fail('schemaVersion 必须为 1、2 或 3');
    let classification;
    if ([2, 3].includes(input.schemaVersion)) {
      const fields = input.schemaVersion === 2
        ? ['seniorTerms', 'technicalTerms', 'seniorTitleEquals', 'juniorTerms', 'ambiguousTerms', 'nonResearchTerms']
        : ['technicalTerms', 'level2Terms', 'level3Terms', 'level4Terms', 'level4TitleEquals', 'juniorTerms', 'ambiguousTerms', 'nonResearchTerms'];
      object(input.classification, fields, 'classification');
      classification = Object.fromEntries(fields.map(key => [key, titles(input.classification[key], 'classification.' + key, true)]));
    }
    const scope = string(input.scope, 'scope', 100);
    const revision = string(input.revision, 'revision', 80);
    if (!Array.isArray(input.rules) || input.rules.length > 100) fail('rules 必须是最多 100 条规则的数组');
    const ids = new Set();
    const rules = input.rules.map(rule => {
      object(rule, ['id', 'enabled', 'reason', 'currentTitleEquals', 'pastTitleEqualsAny'], '规则');
      const id = string(rule.id, 'id', 80);
      if (!/^[a-z0-9_-]+$/i.test(id) || ids.has(id)) fail('id 须为不重复的英文字母、数字、下划线或短横线');
      ids.add(id);
      if (typeof rule.enabled !== 'boolean') fail(id + '.enabled 必须为 true 或 false');
      const result = { id, enabled: rule.enabled, reason: string(rule.reason, id + '.reason'), currentTitleEquals: titles(rule.currentTitleEquals, id + '.currentTitleEquals') };
      if (rule.pastTitleEqualsAny !== undefined) result.pastTitleEqualsAny = titles(rule.pastTitleEqualsAny, id + '.pastTitleEqualsAny');
      return result;
    });
    return { schemaVersion: input.schemaVersion, scope, revision, ...(classification ? { classification } : {}), rules };
  }
  function ignoredTitleReason(title, pastTitles = [], config = defaultIgnoreConfig()) {
    const value = normalizeTitle(title);
    const past = new Set(pastTitles.map(normalizeTitle));
    const rule = config.rules.find(item => item.enabled && item.currentTitleEquals.some(item => normalizeTitle(item) === value)
      && (!item.pastTitleEqualsAny || item.pastTitleEqualsAny.some(item => past.has(normalizeTitle(item)))));
    return rule?.reason || '';
  }
  function hasTitleTerm(title, terms) {
    const value = normalizeTitle(title);
    return terms.some(term => {
      const normalized = normalizeTitle(term);
      if (/[^\x00-\x7f]/.test(normalized)) return value.includes(normalized);
      const escaped = normalized.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
      return new RegExp('(^|[^a-z0-9])' + escaped + '($|[^a-z0-9])', 'i').test(value);
    });
  }
  function titleLevel(title, config = defaultIgnoreConfig()) {
    if (config.schemaVersion !== 3) return 0;
    const c = config.classification;
    if (hasTitleTerm(title, [...c.juniorTerms, ...c.ambiguousTerms, ...c.nonResearchTerms])) return 0;
    const normalized = normalizeTitle(title);
    if (c.level4TitleEquals.some(item => normalizeTitle(item) === normalized)) return 4;
    if (!hasTitleTerm(title, c.technicalTerms)) return 0;
    if (hasTitleTerm(title, c.level4Terms)) return 4;
    if (hasTitleTerm(title, c.level3Terms)) return 3;
    if (hasTitleTerm(title, c.level2Terms)) return 2;
    return 1;
  }
  function normalizeCompany(value) {
    return String(value).normalize('NFKC').toLowerCase().replace(/[‘’'"“”]/g, '').replace(/[^\p{L}\p{N}]+/gu, ' ').trim();
  }
  function defaultEnterpriseConfig() {
    return { schemaVersion: 1, revision: 'empty', companies: [] };
  }
  function enterpriseScore(company) {
    return company.global500 ? 100 : (company.developedEconomy ? 20 : 10) + company.highSalaryIndustryPoints + company.priorityIndustryPoints;
  }
  function requiredLevelForCompany(company) {
    if (company.global500) return 1;
    const score = enterpriseScore(company);
    return score >= 80 ? 2 : score >= 60 ? 3 : 4;
  }
  function validateEnterpriseConfig(input) {
    const fail = message => { throw new Error('企业评分：' + message); };
    const allowedRoot = ['schemaVersion', 'revision', 'companies'];
    if (!input || typeof input !== 'object' || Array.isArray(input)) fail('配置必须是对象');
    for (const key of Object.keys(input)) if (!allowedRoot.includes(key)) fail('含未知字段 ' + key);
    if (input.schemaVersion !== 1) fail('schemaVersion 必须为 1');
    if (typeof input.revision !== 'string' || !input.revision.trim() || input.revision.length > 80) fail('revision 必须是 1–80 字符文本');
    if (!Array.isArray(input.companies) || input.companies.length > 5000) fail('companies 必须是最多 5000 家企业的数组');
    const ids = new Set();
    const names = new Set();
    const allowedCompany = ['id', 'name', 'aliases', 'headquartersCountry', 'developedEconomy', 'global500', 'global500Year', 'highSalaryIndustryPoints', 'priorityIndustryPoints', 'priorityIndustry', 'checkedAt', 'sources'];
    const text = (value, label, max = 200) => {
      if (typeof value !== 'string' || !value.trim() || value.length > max) fail(label + ' 必须是有效文本');
      return value.trim();
    };
    const companies = input.companies.map((company, index) => {
      const label = 'companies[' + index + ']';
      if (!company || typeof company !== 'object' || Array.isArray(company)) fail(label + ' 必须是对象');
      for (const key of Object.keys(company)) if (!allowedCompany.includes(key)) fail(label + ' 含未知字段 ' + key);
      const id = text(company.id, label + '.id', 80);
      if (!/^[a-z0-9_-]+$/i.test(id) || ids.has(id)) fail('id 必须唯一且仅含英文字母、数字、下划线或短横线');
      ids.add(id);
      const name = text(company.name, label + '.name');
      if (!Array.isArray(company.aliases) || company.aliases.length > 100) fail(label + '.aliases 必须是最多 100 项的数组');
      const aliases = company.aliases.map((item, aliasIndex) => text(item, label + '.aliases[' + aliasIndex + ']'));
      for (const candidate of [name, ...aliases]) {
        const normalized = normalizeCompany(candidate);
        if (!normalized || names.has(normalized)) fail('名称或别名重复：' + candidate);
        names.add(normalized);
      }
      const headquartersCountry = text(company.headquartersCountry, label + '.headquartersCountry', 100);
      if (typeof company.developedEconomy !== 'boolean' || typeof company.global500 !== 'boolean') fail(label + ' 的 developedEconomy/global500 必须为布尔值');
      if (company.global500 && (!Number.isInteger(company.global500Year) || company.global500Year < 2000 || company.global500Year > 2100)) fail(label + '.global500Year 必须为有效年度');
      if (!company.global500 && company.global500Year !== undefined) fail(label + '.global500Year 仅用于世界五百强');
      if (![0, 10, 20].includes(company.highSalaryIndustryPoints)) fail(label + '.highSalaryIndustryPoints 只能是 0、10、20');
      if (![0, 40, 60].includes(company.priorityIndustryPoints)) fail(label + '.priorityIndustryPoints 只能是 0、40、60');
      const priorityIndustry = text(company.priorityIndustry, label + '.priorityIndustry');
      const checkedTime = typeof company.checkedAt === 'string' && /^\d{4}-\d{2}-\d{2}$/.test(company.checkedAt) ? Date.parse(company.checkedAt + 'T00:00:00Z') : NaN;
      if (Number.isNaN(checkedTime) || new Date(checkedTime).toISOString().slice(0, 10) !== company.checkedAt) fail(label + '.checkedAt 必须为有效 YYYY-MM-DD');
      if (!Array.isArray(company.sources) || !company.sources.length || company.sources.length > 20 || company.sources.some(source => typeof source !== 'string' || !/^https?:\/\/\S+$/i.test(source))) fail(label + '.sources 必须含有效 http(s) 来源');
      return { id, name, aliases, headquartersCountry, developedEconomy: company.developedEconomy, global500: company.global500,
        ...(company.global500 ? { global500Year: company.global500Year } : {}), highSalaryIndustryPoints: company.highSalaryIndustryPoints,
        priorityIndustryPoints: company.priorityIndustryPoints, priorityIndustry, checkedAt: company.checkedAt, sources: [...company.sources] };
    });
    return { schemaVersion: 1, revision: input.revision.trim(), companies };
  }
  function matchEnterprise(name, config = defaultEnterpriseConfig()) {
    const normalized = normalizeCompany(name);
    if (!normalized) return null;
    return config.companies.find(company => [company.name, ...company.aliases].some(item => normalizeCompany(item) === normalized)) || null;
  }
  function classifyProfileJobs(jobs, config = defaultIgnoreConfig(), options = {}) {
    const { incomplete = false } = options;
    const current = jobs.filter(job => job.current);
    const past = jobs.filter(job => !job.current).map(job => job.title);
    const uncertain = reason => ({ status: 'review', reason });
    if (config.schemaVersion === 1) {
      if (incomplete || !current.length) return uncertain('当前任职无法可靠识别');
      const reasons = current.map(job => ignoredTitleReason(job.title, past, config));
      return reasons.every(Boolean) ? { status: 'exclude', reason: [...new Set(reasons)].join('；') } : { status: 'keep', reason: '未命中旧版排除规则' };
    }
    const c = config.classification;
    if (config.schemaVersion === 3 && Object.prototype.hasOwnProperty.call(options, 'enterpriseConfig')) {
      const enterpriseConfig = options.enterpriseConfig || defaultEnterpriseConfig();
      let unknownTechnical = null;
      let knownBelow = null;
      for (const job of jobs) {
        const level = titleLevel(job.title, config);
        if (!level) continue;
        const enterprise = matchEnterprise(job.company, enterpriseConfig);
        if (level === 4 && !enterprise) return { status: 'keep', reason: (job.current ? '当前' : '历史') + ' L4 技术／研发岗位：' + job.title, jobLevel: 4, requiredLevel: 4, matchedCompany: '' };
        if (!enterprise) {
          unknownTechnical ||= { job, level };
          continue;
        }
        const requiredLevel = requiredLevelForCompany(enterprise);
        const score = enterpriseScore(enterprise);
        if (level >= requiredLevel) return { status: 'keep', reason: `${job.current ? '当前' : '历史'} ${enterprise.name} ${enterprise.global500 ? '世界五百强' : score + '分'}，岗位 L${level} 达到 L${requiredLevel}`, jobLevel: level, requiredLevel, score, global500: enterprise.global500, matchedCompany: enterprise.name };
        knownBelow ||= { job, level, enterprise, requiredLevel, score };
      }
      if (incomplete || !current.length) return uncertain('履历未展开或任职信息不完整，请展开 more 后核实');
      if (knownBelow) return { status: 'exclude', reason: `${knownBelow.enterprise.name} ${knownBelow.enterprise.global500 ? '世界五百强' : knownBelow.score + '分'}，岗位 L${knownBelow.level} 低于 L${knownBelow.requiredLevel}`, jobLevel: knownBelow.level, requiredLevel: knownBelow.requiredLevel, score: knownBelow.score, global500: knownBelow.enterprise.global500, matchedCompany: knownBelow.enterprise.name };
      if (unknownTechnical) return { ...uncertain('技术／研发岗位所在企业未收录，需补充企业评分'), jobLevel: unknownTechnical.level, matchedCompany: '' };
      const reasons = current.map(job => hasTitleTerm(job.title, c.juniorTerms)
        ? '当前为初级／实习岗位，未见达标技术／研发经历'
        : ignoredTitleReason(job.title, past, config));
      if (reasons.every(Boolean)) return { status: 'exclude', reason: [...new Set(reasons)].join('；') };
      return uncertain('当前职务不是明确的技术／研发职级，需人工复核');
    }
    if (config.schemaVersion === 3) {
      const senior = jobs.find(job => titleLevel(job.title, config) >= 2);
      if (senior) return { status: 'keep', reason: (senior.current ? '当前' : '历史') + '有高级技术／研发职务线索：' + senior.title, jobLevel: titleLevel(senior.title, config) };
      if (incomplete || !current.length) return uncertain('履历未展开或任职信息不完整，请展开 more 后核实');
      const reasons = current.map(job => hasTitleTerm(job.title, c.juniorTerms)
        ? '当前为初级／实习岗位，未见历史高级技术／研发职务，请复核'
        : ignoredTitleReason(job.title, past, config));
      if (reasons.every(Boolean)) return { status: 'exclude', reason: [...new Set(reasons)].join('；') };
      return uncertain('职级或技术职责未明确；普通／Associate／II／III 等不能直接判为非高级');
    }
    const senior = jobs.find(job => {
      const title = job.title;
      if (hasTitleTerm(title, [...c.juniorTerms, ...c.ambiguousTerms, ...c.nonResearchTerms])) return false;
      return c.seniorTitleEquals.some(item => normalizeTitle(item) === normalizeTitle(title)) ||
        (hasTitleTerm(title, c.seniorTerms) && hasTitleTerm(title, c.technicalTerms));
    });
    if (senior) return { status: 'keep', reason: (senior.current ? '当前' : '历史') + '有高级技术／研发职务线索：' + senior.title };
    if (incomplete || !current.length) return uncertain('履历未展开或任职信息不完整，请展开 more 后核实');
    const reasons = current.map(job => hasTitleTerm(job.title, c.juniorTerms)
      ? '当前为初级／实习岗位，未见历史高级技术／研发职务，请复核'
      : ignoredTitleReason(job.title, past, config));
    if (reasons.every(Boolean)) return { status: 'exclude', reason: [...new Set(reasons)].join('；') };
    return uncertain('职级或技术职责未明确；普通／Associate／II／III 等不能直接判为非高级');
  }
  function parseProfileText(profileText, linkedName = '') {
    const lines = profileText.split('\n').map(line => line.trim()).filter(Boolean);
    const name = linkedName || lines.find(line => !/@/.test(line) && !/^(view |save|select|copy|\d|\+|more|\.\.\.)/i.test(line) && line.length > 2 && line.length < 100) || '';
    const nameIndex = lines.indexOf(name);
    const afterName = lines.slice(Math.max(0, nameIndex + 1));
    while (afterName.length && /^(in|linkedin|github|twitter|facebook)$/i.test(afterName[0])) afterName.shift();
    // Location is normally the first line after the name. Do not mistake a
    // comma-containing job title further down the profile for a location.
    const location = afterName[0]?.includes(',') && !/\bat\b|@|engineer|manager|director|architect|scientist/i.test(afterName[0]) ? afterName[0] : '';
    const experience = (location ? afterName.slice(1) : afterName).join(' ').replace(/\s+/g, ' ');
    const current = experience.match(/^(.*?)\s+at\s+(.+?)\s+in\s+(?:19|20)\d{2}\s*[-–—]\s*Present\b/i);
    return { name, location, jobTitle: current && current[1].length < 150 ? current[1].trim() : '', company: current ? current[2].trim() : '' };
  }
  function mergeRows(previous, incoming) {
    const result = previous.map(row => ({ ...row, emails: [...row.emails] }));
    for (const row of incoming) {
      const emails = emailsFromText(row.emails.join('\n'));
      if (!emails.length) continue;
      const existing = result.find(item => identity(item) === identity(row));
      if (!existing) result.push({ ...row, emails });
      else {
        const combined = [...new Set([...existing.emails, ...emails])];
        Object.assign(existing, row, { emails: combined, firstCapturedAt: existing.firstCapturedAt });
      }
    }
    return result;
  }
  function csvCell(value) {
    let text = String(value ?? '');
    // Prevent profile text from being interpreted as spreadsheet formulas.
    if (/^[\s\uFEFF]*[=+@-]/.test(text)) text = "'" + text;
    return '"' + text.replace(/"/g, '""') + '"';
  }
  function toCSV(rows) {
    const columns = ['name', 'company', 'jobTitle', 'location', 'linkedin', 'emails', 'emailCount', 'source', 'sourceUrl', 'firstCapturedAt', 'capturedAt', 'needsReview', 'profileText'];
    return '\uFEFF' + [columns.map(csvCell).join(','), ...rows.map(row => columns.map(key => {
      if (key === 'emails') return csvCell(row.emails.join('; '));
      if (key === 'emailCount') return csvCell(row.emails.length);
      return csvCell(row[key]);
    }).join(','))].join('\r\n') + '\r\n';
  }
  const api = { emailsFromText, parseProfileText, mergeRows, toCSV, parseEmploymentText, ignoredTitleReason, defaultIgnoreConfig, validateIgnoreConfig, classifyProfileJobs,
    defaultEnterpriseConfig, validateEnterpriseConfig, enterpriseScore, matchEnterprise, titleLevel, requiredLevelForCompany };
  root.ContactOutExport = api;
  if (typeof module !== 'undefined') module.exports = api;
})(globalThis);
