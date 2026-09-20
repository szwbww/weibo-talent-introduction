const test = require('node:test');
const assert = require('node:assert/strict');
const { emailsFromText, parseProfileText, mergeRows, toCSV, parseEmploymentText, ignoredTitleReason, defaultIgnoreConfig, validateIgnoreConfig, classifyProfileJobs, defaultEnterpriseConfig, validateEnterpriseConfig, enterpriseScore, matchEnterprise, titleLevel } = require('../core.js');
const company = (id, name, developedEconomy, global500, highSalaryIndustryPoints, priorityIndustryPoints, aliases = []) => ({
  id, name, aliases, headquartersCountry: developedEconomy ? 'Advanced' : 'Other', developedEconomy, global500,
  ...(global500 ? { global500Year: 2026 } : {}), highSalaryIndustryPoints, priorityIndustryPoints,
  priorityIndustry: priorityIndustryPoints ? '重点产业' : '非重点产业', checkedAt: '2026-09-19', sources: ['https://example.com/' + id]
});
const enterprises = () => validateEnterpriseConfig({ schemaVersion: 1, revision: 'test', companies: [
  company('g500', 'Global Corp', true, true, 0, 0, ['Global Corporation']),
  company('score100', 'Score 100 Corp', true, false, 20, 60),
  company('score80', 'Score 80 Corp', true, false, 0, 60),
  company('score70', 'Score 70 Corp', true, false, 10, 40),
  company('score60', 'Score 60 Corp', false, false, 10, 40),
  company('score50', 'Score 50 Corp', false, false, 0, 40)
] });
test('enterprise scores set distinct gates and keep title/company evidence on the same job', () => {
  const config = defaultIgnoreConfig();
  const registry = enterprises();
  const assess = jobs => classifyProfileJobs(jobs, config, { enterpriseConfig: registry });
  assert.equal(config.schemaVersion, 3);
  assert.deepEqual(registry.companies.map(enterpriseScore), [100, 100, 80, 70, 60, 50]);
  assert.equal(matchEnterprise(' global corporation ', registry).id, 'g500');
  assert.equal(titleLevel('Engineer', config), 1);
  assert.equal(titleLevel('Senior Scientist II', config), 2);
  assert.equal(titleLevel('Staff Engineer', config), 3);
  assert.equal(titleLevel('Chief Engineer', config), 4);
  for (const [title, employer] of [['Engineer', 'Global Corp'], ['Senior Engineer', 'Score 100 Corp'], ['Staff Engineer', 'Score 60 Corp'], ['Chief Engineer', 'Score 50 Corp']]) {
    assert.equal(assess([{ title, company: employer, current: true }]).status, 'keep', title + ' at ' + employer);
  }
  for (const [title, employer] of [['Engineer', 'Score 100 Corp'], ['Senior Engineer', 'Score 70 Corp'], ['Principal Engineer', 'Score 50 Corp']]) {
    assert.equal(assess([{ title, company: employer, current: true }]).status, 'exclude', title + ' at ' + employer);
  }
  assert.equal(assess([{ title: 'Chief Scientist', company: 'Unlisted Corp', current: true }]).status, 'keep');
  assert.equal(assess([{ title: 'Staff Engineer', company: 'Unlisted Corp', current: true }]).status, 'review');
  assert.equal(assess([{ title: 'Sales Manager', company: 'Global Corp', current: true }, { title: 'Engineer', company: 'Score 50 Corp', current: false }]).status, 'exclude', 'must not combine Global Corp with another job title');
  assert.equal(assess([{ title: 'Sales Manager', company: 'Global Corp', current: true }, { title: 'Staff Engineer', company: 'Score 70 Corp', current: false }]).status, 'keep');
  assert.equal(assess([{ title: 'R&D Scientist', company: 'Score 50 Corp', current: true }, { title: 'Senior R&D Scientist', company: 'Unlisted History Corp', current: false }]).status, 'exclude', 'an unlisted historical role must not override the current scored employer below its gate');
  for (const title of ['Research Manager', 'Engineering Director']) assert.ok(titleLevel(title, config) >= 3, title);
  for (const title of ['Manager', 'Director', 'Sales Engineer', 'Associate Scientist', 'Postdoctoral Research Fellow', 'Junior Engineer']) assert.equal(titleLevel(title, config), 0, title);
});
test('enterprise configuration rejects ambiguous or untraceable records', () => {
  assert.deepEqual(defaultEnterpriseConfig(), { schemaVersion: 1, revision: 'empty', companies: [] });
  const valid = enterprises();
  assert.throws(() => validateEnterpriseConfig({ ...valid, unknown: true }), /未知字段/);
  assert.throws(() => validateEnterpriseConfig({ ...valid, companies: [...valid.companies, { ...valid.companies[0], id: 'duplicate', name: 'Different', aliases: ['Global Corp'] }] }), /名称或别名重复/);
  assert.throws(() => validateEnterpriseConfig({ ...valid, companies: [{ ...valid.companies[0], highSalaryIndustryPoints: 15 }] }), /highSalaryIndustryPoints/);
  const { global500Year, ...missingYear } = valid.companies[0];
  assert.throws(() => validateEnterpriseConfig({ ...valid, companies: [missingYear] }), /global500Year/);
  assert.throws(() => validateEnterpriseConfig({ ...valid, companies: [{ ...valid.companies[0], sources: [] }] }), /sources/);
  assert.throws(() => validateEnterpriseConfig({ ...valid, companies: [{ ...valid.companies[0], checkedAt: '19-09-2026' }] }), /checkedAt/);
});
test('generic seniority has keep, review and exclude without an industry gate', () => {
  const classify = (title, past = [], incomplete = false) => classifyProfileJobs([
    { title, current: true }, ...past.map(title => ({ title, current: false }))
  ], defaultIgnoreConfig(), { incomplete }).status;
  for (const title of ['Senior Scientist II', 'Technical Staff Engineer', 'Principal Engineer', 'R&D Manager', 'Senior Manufacturing Chemist', 'Chief Engineer', 'Senior Fragrance Scientist', 'Senior Data Scientist', '主任工程师', 'Technical Fellow', 'CTO']) assert.equal(classify(title), 'keep', title);
  for (const title of ['Engineer', 'Scientist', 'Associate Scientist', 'Scientist II', 'Scientist III', 'Principal Associate Scientist', 'Assistant Research Director', 'Postdoctoral Research Fellow', 'SeniorScientist', 'Senior Technician', 'Finance Manager']) assert.equal(classify(title), 'review', title);
  for (const title of ['Junior Engineer', 'Intern', 'Manufacturing Operator III', 'Senior Sales Manager']) assert.equal(classify(title), 'exclude', title);
  assert.equal(classify('Junior Engineer', ['Senior Scientist']), 'keep');
  assert.equal(classify('Sales Manager', ['Senior Process Engineer']), 'keep');
  assert.equal(classify('Junior Engineer', [], true), 'review');
  assert.equal(classify('Senior Scientist', [], true), 'keep');
  assert.equal(classifyProfileJobs([{ title: 'Junior Engineer', current: true }, { title: 'Scientist', current: true }]).status, 'review');
  assert.equal(classifyProfileJobs([]).status, 'review');
});
test('keeps all full emails and rejects whole masked tokens, not just asterisks', () => {
  assert.deepEqual(emailsFromText('***@amat.com j***smith@gmail.com •••a@gmail.com john…smith@gmail.com abc..d@gmail.com work@EXAMPLE.com\nuser+tag@gmail.com\nwork@example.com'), ['work@example.com', 'user+tag@gmail.com']);
});
test('parses job text broken into DOM text nodes without inventing a title', () => {
  assert.deepEqual(parseProfileText('Test Person\nin\nSanta Clara, California, United States\nTechnical\nStaff Engineer\nat\nExample Corp\nin 2012 -\nPresent\nPh.D\nuser@example.com'), { name: 'Test Person', location: 'Santa Clara, California, United States', jobTitle: 'Technical Staff Engineer', company: 'Example Corp' });
});
test('merge captures retains previous and new emails, rejects empty rows', () => {
  const row = { name: 'Test Person', company: 'Example Corp', profileText: 'Test Person', emails: ['a@example.com'], firstCapturedAt: 'first' };
  const combined = mergeRows([row], [{ ...row, emails: ['a@example.com', 'b@gmail.com'], firstCapturedAt: 'second' }, { ...row, name: 'Masked', emails: ['***@gmail.com'] }]);
  assert.equal(combined.length, 1);
  assert.deepEqual(combined[0].emails, ['a@example.com', 'b@gmail.com']);
  assert.equal(combined[0].firstCapturedAt, 'first');
  assert.deepEqual(row.emails, ['a@example.com']);
});
test('CSV includes every address and neutralizes spreadsheet formulas', () => {
  const csv = toCSV([{ name: '=WEBSERVICE("x")', emails: ['a@gmail.com', 'b@example.com'], profileText: 'Two,"lines"\nNext' }]);
  assert.ok(csv.startsWith('\uFEFF'));
  assert.ok(csv.includes('a@gmail.com; b@example.com'));
  assert.ok(csv.includes('"\'=WEBSERVICE(""x"")"'));
  assert.ok(csv.includes('"Two,""lines""\nNext"'));
});
test('review parses complete employment blocks and does not merge adjacent experiences', () => {
  assert.deepEqual(parseEmploymentText('Senior\nData Scientist\nat\nExample Corp\nin 2024 -\nPresent'), { title: 'Senior Data Scientist', company: 'Example Corp', current: true });
  assert.equal(parseEmploymentText('Data Scientist at Example Corp in 2020 - 2023').current, false);
  assert.equal(parseEmploymentText('Data Scientist at Example Corp - Present').current, true);
  assert.equal(parseEmploymentText('Data Scientist at Example Corp in 2020 - 2023 Senior Chemist at Materials Corp in 2023 - Present'), null);
  assert.equal(parseEmploymentText('Skills: Data Scientist, polymers'), null);
});
test('conservative ignore rules retain ambiguous and senior roles, require evidence for patent-information role', () => {
  const legacy = { schemaVersion: 1, scope: '旧规则', revision: 'v1', rules: [
    { id: 'exact', enabled: true, reason: '旧排除', currentTitleEquals: ['Senior Data Scientist', 'Manufacturing Operator III', 'Scientist - Hair Care Formulation', 'Fragrance Scientist'] },
    { id: 'patent', enabled: true, reason: '专利信息', currentTitleEquals: ['Senior Information Scientist'], pastTitleEqualsAny: ['Expert Patent Information'] }
  ] };
  for (const title of ['Senior Scientist II', 'Scientist III', 'Associate Scientist', 'Principal Associate Scientist', 'Senior Manufacturing Chemist', 'Manufacturing Chemist', 'Senior Materials Engineer', 'Sr Scientific Associate', 'Data Scientist - Materials Informatics', 'Data Scientist / Principal Scientist']) assert.equal(ignoredTitleReason(title), '', title);
  for (const title of ['Senior Data Scientist', 'Manufacturing Operator III', 'Scientist - Hair Care Formulation', 'Fragrance Scientist']) assert.ok(ignoredTitleReason(title, [], legacy), title);
  assert.equal(ignoredTitleReason('Senior Information Scientist'), '');
  assert.ok(ignoredTitleReason('Senior Information Scientist', ['Expert Patent Information'], legacy));
  assert.deepEqual(validateIgnoreConfig(legacy), legacy);
  assert.equal(classifyProfileJobs([{ title: 'Senior Data Scientist', current: true }], legacy).status, 'exclude');
  assert.equal(classifyProfileJobs([{ title: 'Senior Data Scientist', current: true }], legacy, { incomplete: true }).status, 'review');
  assert.throws(() => validateIgnoreConfig({ ...legacy, classification: {} }), /未知字段/);
});
test('ignore configuration validates before use and supports exact custom titles, disabled rules and empty lists', () => {
  const config = validateIgnoreConfig(defaultIgnoreConfig());
  config.rules = [{ id: 'custom', enabled: true, reason: 'Custom reason', currentTitleEquals: ['Custom Role - R&D'] }];
  assert.equal(ignoredTitleReason(' custom  ROLE — R&D ', [], config), 'Custom reason');
  assert.equal(ignoredTitleReason('Senior Custom Role - R&D', [], config), '');
  config.rules[0].enabled = false;
  assert.equal(ignoredTitleReason('Custom Role - R&D', [], config), '');
  assert.deepEqual(validateIgnoreConfig({ ...config, rules: [] }).rules, []);
  const { classification, ...withoutClassification } = config;
  assert.throws(() => validateIgnoreConfig({ ...withoutClassification, schemaVersion: 3 }), /classification/);
  assert.throws(() => validateIgnoreConfig(withoutClassification), /classification/);
  for (const technicalTerms of [undefined, 'Engineer', [''], [1], ['Engineer*'], Array(101).fill('Engineer')]) {
    assert.throws(() => validateIgnoreConfig({ ...config, classification: { ...classification, technicalTerms } }), /classification/);
  }
  assert.throws(() => validateIgnoreConfig({ ...config, classification: { ...classification, extra: [] } }), /未知字段/);
  assert.deepEqual(validateIgnoreConfig({ ...config, classification: { ...classification, technicalTerms: ['Engineer', 'engineer'] } }).classification.technicalTerms, ['engineer']);
  const empty = validateIgnoreConfig({ ...config, rules: [], classification: Object.fromEntries(Object.keys(classification).map(key => [key, []])) });
  assert.equal(classifyProfileJobs([{ title: 'Senior Scientist', current: true }], empty).status, 'review');
  const protectedConfig = { ...config, rules: [{ id: 'senior', enabled: true, reason: '排除', currentTitleEquals: ['Senior Scientist'] }] };
  assert.equal(classifyProfileJobs([{ title: 'Senior Scientist', current: true }], protectedConfig).status, 'keep');
  assert.throws(() => validateIgnoreConfig({ ...config, regex: '.*' }), /未知字段/);
  assert.throws(() => validateIgnoreConfig({ ...config, rules: [config.rules[0], config.rules[0]] }), /不重复/);
  assert.throws(() => validateIgnoreConfig({ ...config, rules: [{ ...config.rules[0], currentTitleEquals: ['.*Scientist.*'] }] }), /正则/);
});
