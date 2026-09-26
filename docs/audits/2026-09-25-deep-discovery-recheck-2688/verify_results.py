import json,pathlib,collections,hashlib,sys
from extract_supplemental import supplemental,norm
p=pathlib.Path('/tmp/deep-discovery-recheck-2688');r=json.load(open(p/'final-assessments.json'));s=json.load(open(p/'summary.json'));live=json.load(open(p/'live-verification.json'))
assert len(r)==2688 and len({x['id'] for x in r})==2688
assert dict(collections.Counter(x['verdict'] for x in r))==s['counts']=={'MATCH':571,'UNRESOLVED':1924,'CONFIRMED_OTHER_AUTHOR':193}
prior=json.load(open('docs/audits/2026-09-25-deep-discovery-duplicate-names/confirmed-wrong.json'));assert not ({x['id'] for x in prior}&{x['id'] for x in r})
assert live['uniqueFound']==764 and live['changedNames']==live['changedEmails']==0 and live['docs']==1528
# Independently audit each new direct-author proof from its exact XML node.
import xml.etree.ElementTree as E
checked=0
for x in r:
 for e in x.get('evidence',[]):
  if e['method']=='AUTHOR_ADDRESS_EMAIL':
   node=E.fromstring(e['snippet']);name=' '.join(''.join(node.find('name/'+f).itertext()).strip() for f in ['given-names','surname']);emails=[''.join(n.itertext()).strip().lower() for n in node.findall('./address/email')]
   assert x['email'] in emails and norm(name)==norm(x['expectedName']);checked+=1
# New evidence methods must never disagree with previous 2,102 confirmed source mappings.
cache={};conflicts=[]
corrections={r['email']:r['expectedName'] for r in json.load(open(p/'prior-three-corrections.json'))}
for x in prior:
 if x['email'] in corrections:x['expectedName']=corrections[x['email']]
for x in prior:
 pmc=x['pmcId']
 if pmc not in cache:cache[pmc]=supplemental(pathlib.Path('/tmp/deep-discovery-name-audit/public_xml')/(pmc+'.xml'))[0]
 ev=cache[pmc].get(x['email'],[])
 for e in ev:
  if norm(e['name'])!=norm(x['expectedName']):conflicts.append({'email':x['email'],'expected':x['expectedName'],'extra':e})
assert not conflicts,conflicts[:10]
checks={'status':'PASS','totalRows':2688,'resolvedRows':764,'newWrong':193,'sourceMatches':571,'stillUnresolved':1924,'directAuthorProofsChecked':checked,'previousConfirmedRecordsRegressionChecked':len(prior),'previousEvidenceConflicts':len(conflicts),'historicalRestorationErrorsIdentifiedAndCorrected':3,'liveNamesAndEmailsUnchanged':True,'liveDocs':1528}
(p/'quality-verification.json').write_text(json.dumps(checks,indent=2));print(json.dumps(checks))
