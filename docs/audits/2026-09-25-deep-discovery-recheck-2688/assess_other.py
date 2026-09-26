import json,pathlib,collections,sys,re,copy
from bs4 import BeautifulSoup
sys.path.insert(0,'/tmp/deep-discovery-name-audit');from extract_strict import norm,mail_re
p=pathlib.Path('/tmp/deep-discovery-recheck-2688');rs=json.load(open(p/'pending.json'));out=[];raw=json.load(open('tmp/sbir-full-current/raw/sbir.json'));sbir=collections.defaultdict(list)
for row in raw:
 for role in ['pi','contact']:
  email=(row.get(role+'_email') or '').strip().lower();name=' '.join((row.get(role+'_name') or '').split())
  if email and name:sbir[email].append((name,role,row))
for r in rs:
 if r['source'] not in ('ORCID','ARXIV','SBIR'):continue
 ev=[]
 if r['source']=='ORCID':
  f=p/'other_sources'/(r['id']+'.json')
  if f.exists():
   d=json.load(open(f));n=d.get('name') or {};name=' '.join((n.get(k) or {}).get('value','') for k in ('given-names','family-name')).strip();emails=[v for v in (d.get('emails') or {}).get('email',[]) if v.get('email','').lower()==r['email'] and v.get('verified') is True]
   if name and emails:ev=[{'name':name,'method':'ORCID_VERIFIED_PUBLIC_EMAIL','snippet':{'name':n,'emails':emails},'url':'https://orcid.org/'+r['id']}]
 elif r['source']=='SBIR':
  awards=set(r['externalIds'].get('sbirAwardIds',[]))
  for name,role,row in sbir[r['email']]:
   if awards & {row.get('agency_tracking_number'),row.get('contract')}:
    ev.append({'name':name,'method':'SBIR_ORIGINAL_AWARD_CONTACT','snippet':{k:row.get(k) for k in ['company','contract','agency_tracking_number','award_year',role+'_name',role+'_email']},'url':'https://www.sbir.gov/awards','localSource':'tmp/sbir-full-current/raw/sbir.json'})
 else:
  aid=r['doi'].split(':',1)[-1];f=p/'other_sources'/(aid+'.html')
  if f.exists():
   doc=BeautifulSoup(f.read_text(),'html.parser')
   for author in doc.select('.ltx_authors .ltx_creator.ltx_role_author'):
    ns=author.select('.ltx_personname')
    if len(ns)!=1 or ns[0].select('.ltx_parbox,.ltx_p,br,div'):continue
    n=copy.copy(ns[0])
    for x in n.select('.ltx_note,.ltx_sup,sup,.ltx_ref,.ltx_author_notes'):x.decompose()
    name=' '.join(n.get_text(' ',strip=True).split()).strip(' *†‡')
    if not 2<=len(name.split())<=7 or re.search(r'[,;@\d]|\band\b',name):continue
    es={m.group(0).lower() for e in author.select('.ltx_role_email') for m in mail_re.finditer(e.get_text(' ',strip=True))}
    if r['email'].lower() in es:ev.append({'name':name,'method':'ARXIV_AUTHOR_EMAIL_NODE','snippet':str(author),'url':'https://arxiv.org/html/'+aid})
 names={norm(e['name']) for e in ev};verdict='UNRESOLVED';expected=None
 if len(names)==1:
  expected=ev[0]['name'];verdict='MATCH' if norm(expected)==norm(r['name']) else 'OTHER_NAME_REVIEW'
 out.append({'id':r['id'],'email':r['email'],'oldName':r['name'],'source':r['source'],'expectedName':expected,'verdict':verdict,'evidence':ev})
(p/'other-assessments.json').write_text(json.dumps(out,ensure_ascii=False,indent=2));print(json.dumps({'counts':collections.Counter(x['verdict'] for x in out),'bySource':{s:dict(collections.Counter(x['verdict'] for x in out if x['source']==s)) for s in ['ORCID','ARXIV','SBIR']},'reviews':[x for x in out if x['verdict']=='OTHER_NAME_REVIEW']},ensure_ascii=False))
