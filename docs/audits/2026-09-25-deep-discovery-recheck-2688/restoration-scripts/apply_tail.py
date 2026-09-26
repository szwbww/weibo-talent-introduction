assert not (root/'apply-started.json').exists(),'Run already attempted; inspect journal before resuming'
assert contacts()==contactbefore,'Contacts changed after backup'
assert jobs()=={j['id']:j for j in b['jobs']},'Jobs changed after backup'
assert mail()==b['mailBaseline']['stdout'],'Mail changed after backup'
active=sql("SELECT id,task_type,status FROM task_execution WHERE status='RUNNING'");assert active['exit']==0 and not active['stdout'],active
live=all_docs();assert len(live)==386
for k,h in live.items():assert h['_source']==before[k]['_source'] and h['_seq_no']==before[k]['_seq_no'] and h['_primary_term']==before[k]['_primary_term'],('Concurrent ES change',k)
# Compare all backed-up contact columns under row locks inside one MySQL transaction.
commands=['CREATE TEMPORARY TABLE restore_guard(id INT PRIMARY KEY);','INSERT INTO restore_guard VALUES(1);','START TRANSACTION;']
for c in m['contacts']:
 old=contactbefore[c['id']];guard=' AND '.join('`'+k+'` <=> '+literal(v) for k,v in old.items())
 commands+=['SELECT id FROM expert_contact WHERE id='+str(c['id'])+' FOR UPDATE;', 'UPDATE expert_contact SET expert_name='+literal(c['expert_name'])+',country='+literal(c['country'])+',updated_at=NOW() WHERE '+guard+';', 'SET @restore_affected=ROW_COUNT();','INSERT INTO restore_guard SELECT 1 WHERE @restore_affected<>1;']
commands+=['COMMIT;'];statement='\n'.join(commands)
(root/'contacts-update.sql').write_text(statement,encoding='utf-8');os.chmod(str(root/'contacts-update.sql'),0o600)
save('apply-started.json',{'time':datetime.now().isoformat(),'manifestSha256':hashlib.sha256((root/'manifest.json').read_bytes()).hexdigest()})
sql_attempted=False
try:
 records=m['es']
 for start in range(0,len(records),200):
  lines=[]
  for r in records[start:start+200]:
   lines+=[{'update':{'_index':r['index'],'_id':r['id'],'if_seq_no':r['seqNo'],'if_primary_term':r['primaryTerm']}},{'script':{'lang':'painless','source':'for (entry in params.patch.entrySet()) { ctx._source[entry.getKey()] = entry.getValue(); }','params':{'patch':r['patch']}}}]
  response=bulk(lines);save('batch-'+str(start)+'.json',response);assert not response.get('errors'),response
  print(json.dumps({'stage':'es_updated','completed':min(start+200,len(records)),'total':len(records)}),flush=True)
 now=all_docs();assert all(now[k]['_source']==expected(k) for k in before),'ES post-write mismatch'
 assert contacts()==contactbefore,'Contact concurrent change before SQL'
 sql_attempted=True;res=sql(statement);save('contacts-update-result.json',res);assert res['exit']==0,res['stderr']
 finalcontacts=contacts();assert set(finalcontacts)==set(contactbefore)
 for c in m['contacts']:
  wanted=dict(contactbefore[c['id']]);wanted.update({'expert_name':c['expert_name'],'country':c['country']});actual=dict(finalcontacts[c['id']]);actual.pop('updated_at',None);wanted.pop('updated_at',None);assert actual==wanted,('Contact mismatch',c['id'])
 assert mail()==b['mailBaseline']['stdout'],'Mail changed'
 save('apply-complete.json',{'time':datetime.now().isoformat(),'experts':193,'esDocs':386,'contacts':5,'mailUnchanged':True});print(json.dumps({'stage':'APPLIED','experts':193,'esDocs':386,'contacts':5,'mailUnchanged':True}),flush=True)
except Exception as exc:
 save('apply-failure.json',{'error':str(exc),'sqlAttempted':sql_attempted,'time':datetime.now().isoformat()})
 # If MySQL committed or its state is uncertain, preserve repaired ES and report for reconciliation.
 if sql_attempted and contacts()!=contactbefore:
  print(json.dumps({'stage':'RECONCILIATION_REQUIRED','error':str(exc)}),flush=True);raise
 rollback=[];conflicts=[];current=all_docs()
 for k,h in current.items():
  if h['_source']==before[k]['_source']:continue
  if h['_source']!=expected(k):conflicts.append(k);continue
  patch=patches[k]['patch'];old=before[k]['_source'];values={x:old[x] for x in patch if x in old};remove=[x for x in patch if x not in old]
  rollback.append([{'update':{'_index':k[0],'_id':k[1],'if_seq_no':h['_seq_no'],'if_primary_term':h['_primary_term']}},{'script':{'lang':'painless','source':'for (entry in params.values.entrySet()) {ctx._source[entry.getKey()] = entry.getValue();} for (key in params.remove) {ctx._source.remove(key);}','params':{'values':values,'remove':remove}}}])
 for start in range(0,len(rollback),200):
  rr=bulk([line for pair in rollback[start:start+200] for line in pair]);save('rollback-'+str(start)+'.json',rr)
  if rr.get('errors'):conflicts.append('rollback_errors_'+str(start))
 current=all_docs();same=sum(current[k]['_source']==before[k]['_source'] for k in before);save('rollback-result.json',{'restoredOrUnchanged':same,'total':386,'conflicts':conflicts});print(json.dumps({'stage':'ROLLED_BACK','restoredOrUnchanged':same,'conflicts':conflicts}),flush=True);raise
