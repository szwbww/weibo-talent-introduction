import json,pathlib,collections,sys
from extract_supplemental import assess
p=pathlib.Path('/tmp/deep-discovery-recheck-2688');rows=json.load(open(p/'pending.json'));out=[];lookup=json.load(open(p/'doi-pmc-lookup.json')) if (p/'doi-pmc-lookup.json').exists() else {}
for r in rows:
 pmc=r.get('pmcId') or r.get('externalIds',{}).get('pmcId') or lookup.get((r.get('doi') or '').lower());path=None
 for folder in [p/'public_xml',pathlib.Path('/tmp/deep-discovery-name-audit/public_xml')]:
  if pmc and (folder/(pmc+'.xml')).exists():path=folder/(pmc+'.xml');break
 v={'verdict':'UNRESOLVED','reason':'NO_XML'}
 if path:v=assess(path,r['email'].lower(),r['name']);v['reason']='XML_RECHECK'
 out.append({'id':r['id'],'email':r['email'],'oldName':r['name'],'source':r['source'],'oldVerdict':r['verdict'],'pmcId':pmc,'contacts':r['contacts'],**v})
(p/'assessments.json').write_text(json.dumps(out,ensure_ascii=False,indent=2));print(json.dumps({'counts':collections.Counter(r['verdict'] for r in out),'wrongMethods':collections.Counter(e['method'] for r in out if r['verdict']=='CONFIRMED_OTHER_AUTHOR' for e in r['evidence']),'variants':[{'email':r['email'],'old':r['oldName'],'expected':r.get('expectedName')} for r in out if r['verdict']=='NAME_VARIANT_REVIEW']},ensure_ascii=False))
