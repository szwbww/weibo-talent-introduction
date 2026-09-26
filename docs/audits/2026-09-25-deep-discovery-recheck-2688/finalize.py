import json,pathlib,collections,sys
sys.path.insert(0,'/tmp/deep-discovery-name-audit');from extract_strict import norm
p=pathlib.Path('/tmp/deep-discovery-recheck-2688');rows=json.load(open(p/'assessments.json'));other={r['id']:r for r in json.load(open(p/'other-assessments.json'))};manual=[]
match_variants={'irp2@le.ac.uk','cooperm2@dal.ca'}
wrong_variants={'jyotismita.c@gmail.com','neelanjan.dey@gmail.com','cap8@le.ac.uk','andrzejj@anl.gov','dickinson@uchicago.edu'}
shared={'bwoods@nanosonic.com','bwoods@mainstream-engr.com','proposals1@hedgefogresearch.com','mjones@systemstech.com'}
for r in rows:
 if r['verdict']=='NAME_VARIANT_REVIEW':
  assert r['email'] in match_variants|wrong_variants
  r['verdict']='MATCH' if r['email'] in match_variants else 'CONFIRMED_OTHER_AUTHOR';r['manualReview']='Middle initial omitted; same author' if r['email'] in match_variants else 'Explicit individual author/email node identifies another named coauthor; old stored name is an abbreviated/expanded variant of a different coauthor'
  manual.append({'id':r['id'],'email':r['email'],'oldName':r['oldName'],'expectedName':r['expectedName'],'verdict':r['verdict'],'reason':r['manualReview']})
 if r['id'] in other:
  o=other[r['id']]
  if o['verdict']=='MATCH' and r['email'] not in shared:
   assert r['verdict']=='UNRESOLVED';r.update({'verdict':'MATCH','expectedName':o['expectedName'],'evidence':o['evidence'],'reason':'OTHER_SOURCE_RECHECK'})
  elif o['evidence']:
   r['supplementaryEvidence']=o['evidence'];r['reason']='CROSS_AWARD_NAME_VARIANTS_OR_CONFLICT' if r['email'] in shared or r['source']=='SBIR' else 'ARXIV_SOURCE_ATTRIBUTION_NEEDS_REVIEW'
 r['evidenceUrl']='https://pmc.ncbi.nlm.nih.gov/articles/'+r['pmcId']+'/' if r.get('pmcId') else next((e.get('url') for e in r.get('evidence',[]) if e.get('url')),None)
counts=collections.Counter(r['verdict'] for r in rows);assert sum(counts.values())==2688 and len({r['id'] for r in rows})==2688
summary={'total':len(rows),'counts':dict(counts),'bySource':{s:dict(collections.Counter(r['verdict'] for r in rows if r['source']==s)) for s in sorted({r['source'] for r in rows})},'wrongWithSentMail':sum(any(c.get('sent',0)>0 for c in r['contacts']) for r in rows if r['verdict']=='CONFIRMED_OTHER_AUTHOR'),'wrongSentMailCount':sum(c.get('sent',0) for r in rows if r['verdict']=='CONFIRMED_OTHER_AUTHOR' for c in r['contacts']),'legacyBusinessIdsInWrong':sum(not r['id'].startswith('EMAIL-') for r in rows if r['verdict']=='CONFIRMED_OTHER_AUTHOR'),'unresolvedByReason':dict(collections.Counter(r['reason'] for r in rows if r['verdict']=='UNRESOLVED'))}
(p/'final-assessments.json').write_text(json.dumps(rows,ensure_ascii=False,indent=2));(p/'summary.json').write_text(json.dumps(summary,ensure_ascii=False,indent=2));(p/'manual-review.json').write_text(json.dumps(manual,ensure_ascii=False,indent=2));print(json.dumps(summary,ensure_ascii=False))
