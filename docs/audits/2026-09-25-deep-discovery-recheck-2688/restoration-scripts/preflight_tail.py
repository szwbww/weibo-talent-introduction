import pathlib,hashlib
from datetime import datetime
from collections import Counter
root=pathlib.Path('/opt/talent/backups/expert-identity-20260925-restore-04')
assert not root.exists(),'Backup directory already exists; inspect it before resuming'
hits=[];missing=[];indexlist=indices.split(',')
requests=[{'_index':idx,'_id':r['id']} for r in targets for idx in indexlist]
for start in range(0,len(requests),300):
 for h in es('_mget',{'docs':requests[start:start+300]})['docs']:
  if h.get('found'):hits.append(h)
  else:missing.append({'index':h['_index'],'id':h['_id']})
byid={r['id']:r for r in targets};seen=Counter()
for h in hits:
 src=h['_source'];r=byid[h['_id']];assert (src.get('email') or '').lower()==r['email']
 assert ' '.join(str(src.get(k) or '') for k in ['givenNames','familyNames']).strip()==r['name'],h['_id']
 seen[h['_id']]+=1
assert len(seen)==193 and all(n>=1 for n in seen.values())
cols=[l.split('\t')[0] for l in sql('SHOW COLUMNS FROM expert_contact')['stdout'].splitlines()]
expr='JSON_OBJECT('+','.join("'"+c+"',`"+c+"`" for c in cols)+')'
contacts=[];jobs=[]
jobcols=[l.split('\t')[0] for l in sql('SHOW COLUMNS FROM expert_academic_enrichment_job')['stdout'].splitlines()]
jobexpr='JSON_OBJECT('+','.join("'"+c+"',`"+c+"`" for c in jobcols)+')'
for start in range(0,len(targets),300):
 batch=targets[start:start+300];qemails=','.join("'"+r['email'].replace("'","''")+"'" for r in batch);qids=','.join("'"+r['id']+"'" for r in batch)
 result=sql('SELECT '+expr+' FROM expert_contact WHERE expert_email IN ('+qemails+')');assert result['exit']==0,result['stderr'];contacts.extend(json.loads(l) for l in result['stdout'].splitlines())
 result=sql('SELECT '+jobexpr+' FROM expert_academic_enrichment_job WHERE expert_doc_id IN ('+qids+')');assert result['exit']==0,result['stderr'];jobs.extend(json.loads(l) for l in result['stdout'].splitlines())
idlist=','.join(str(c['id']) for c in contacts) or '0'
mail=sql("SELECT expert_contact_id,COUNT(*),COALESCE(SUM(direction='OUTBOUND' AND sent_at IS NOT NULL),0),COALESCE(SUM(direction='INBOUND'),0),BIT_XOR(CRC32(CONCAT_WS('|',id,direction,COALESCE(subject,''),COALESCE(body,''),COALESCE(sent_at,''),COALESCE(received_at,''),COALESCE(send_status,'')))) FROM mail_record WHERE expert_contact_id IN ("+idlist+") GROUP BY expert_contact_id")
assert mail['exit']==0,mail['stderr']
active=sql("SELECT id,task_type,status FROM task_execution WHERE status='RUNNING'")
backup={'createdAt':datetime.now().isoformat(),'targets':targets,'es':hits,'contacts':contacts,'jobs':jobs,'mailBaseline':mail,'activeTasks':active}
root.mkdir(mode=0o700);data=json.dumps(backup,ensure_ascii=True,indent=2).encode();fd=os.open(str(root/'before.json'),os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
with os.fdopen(fd,'wb') as f:f.write(data)
print(json.dumps({'kind':'summary','path':str(root),'sha256':hashlib.sha256(data).hexdigest(),'targetCount':len(targets),'esDocs':len(hits),'layers':Counter(h['_index'] for h in hits),'contactCount':len(contacts),'jobs':Counter(j['status'] for j in jobs),'activeTasks':active}),flush=True)
fields=['email','givenNames','familyNames','country','employment','institution','institutionType','externalIds','hIndex','citationCount','worksCount','researchFields','disciplineCategory','lastPublicationYear','recentWorkTitles','patentTitles','enrichedAt','enrichmentSource','keyword','degree','age','nationality']
for h in hits:
 if h['_index']==setting('ES_RAW_INDEX_NAME'):print(json.dumps({'kind':'expert','id':h['_id'],'source':{k:v for k,v in h['_source'].items() if k in fields}}),flush=True)
print(json.dumps({'kind':'contacts','rows':[{'id':c['id'],'email':c['expert_email'],'name':c['expert_name'],'country':c['country']} for c in contacts]}))
print(json.dumps({'kind':'jobs','rows':[{'id':j['expert_doc_id'],'status':j['status'],'leaseUntil':j.get('lease_until')} for j in jobs]}))
