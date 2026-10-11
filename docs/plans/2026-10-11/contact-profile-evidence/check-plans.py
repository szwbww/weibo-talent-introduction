from pathlib import Path
import json,re,hashlib
R=Path('/Users/lukai/IdeaProjects/weibo-talent-introduction');D=R/'docs/plans/2026-10-11';E=D/'contact-profile-evidence'
manifest=json.loads((E/'plan-scope.json').read_text());checks=[];errors=[]
required=['需求描述','关键不变量','现状审计','实现方案','变更文件清单','验收标准','人工验收清单']
for item in manifest:
 p=D/item['plan']; s=p.read_text(); heads=re.findall(r'^## (.+)$',s,re.M)
 want=required.copy()
 fe=any(x.startswith('src/main/resources/static/') for x in item['files'])
 if fe:want.insert(2,'样式契约')
 okay=heads==want
 if not okay:errors.append(f'{p.name}: section order {heads}')
 if len(item['files'])>10:errors.append(f'{p.name}: >10 files')
 if len(item['files'])!=len(set(item['files'])):errors.append(f'{p.name}: duplicate file')
 for f in item['files']:
  if f'`{f}`' not in s:errors.append(f'{p.name}: missing manifest path {f}')
 invariants=re.findall(r'^### Invariant I-(\d+):',s,re.M)
 if list(map(int,invariants))!=list(range(1,len(invariants)+1)):errors.append(f'{p.name}: noncontiguous invariants')
 accepts=re.findall(r'^### A-(\d+):',s,re.M)
 if len(accepts)<3:errors.append(f'{p.name}: insufficient A items')
 for a in re.split(r'^### A-\d+:',s,flags=re.M)[1:]:
  for label in ['前置条件:','操作步骤:','预期结果:','覆盖:']:
   if label not in a:errors.append(f'{p.name}: A missing {label}')
 for link in re.findall(r'\]\(([^)]+)\)',s):
  if not link.startswith(('http','#')) and not (p.parent/link).exists():errors.append(f'{p.name}: bad link {link}')
 checks.append({'plan':p.name,'files':len(item['files']),'frontend':fe,'section_order':okay,'invariants':len(invariants),'manual_cases':len(accepts)})
original=json.loads((E/'source-sha256.json').read_text())
changed=[f for f,h in original.items() if not (R/f).exists() or hashlib.sha256((R/f).read_bytes()).hexdigest()!=h]
if changed: errors.append('source changed since audit: '+str(changed))
# Every new CSS class used by prescribed HTML must be declared in the prescribed CSS.
alltext='\n'.join((D/i['plan']).read_text() for i in manifest)
html='\n'.join(re.findall(r'```html\n(.*?)```',alltext,re.S));css='\n'.join(re.findall(r'```css\n(.*?)```',alltext,re.S))
used={c for group in re.findall(r'class="([^"]+)"',html) for c in group.split() if c.startswith('ec-')}
declared=set(re.findall(r'\.(ec-[a-z-]+)',css))
if used-declared:errors.append('undeclared ec classes: '+str(used-declared))
for p in D.glob('contact-profile-0*.md'):
 if 'style="' in p.read_text():errors.append(p.name+': inline style')
fields=json.loads((E/'field-contract.json').read_text())
if len(fields)!=19:errors.append('wrong field count')
result={'status':'PASS' if not errors else 'FAIL','scope':'Plan structural checks only; implementation and 1GiB tests NOT RUN','children':checks,'new_business_fields':len(fields),'declared_new_css_classes':sorted(declared),'source_files_checked':len(original),'source_files_changed':changed,'errors':errors,'manual_review':['identity versus contact boundary','new private tables / zero existing shared columns','all new writers described','per-field semantics I-5 through I-23','API errors / concurrent updates / upload unknown outcome','cross-module acceptance mapping','1GiB real HTTP release gate','no production edit or deployment']}
(E/'plan-self-check.json').write_text(json.dumps(result,ensure_ascii=False,indent=2));print(json.dumps(result,ensure_ascii=False,indent=2))
if errors:raise SystemExit(1)
