import pathlib,hashlib,copy
from datetime import datetime
root=pathlib.Path('/opt/talent/backups/expert-identity-20260925-restore-02')
backupBytes=(root/'before.json').read_bytes();assert hashlib.sha256(backupBytes).hexdigest()=='af717b4d6058f92ddf5d6ab9cec4e817c9f6556a5ef60bfb766786e1e6eb6489'
b=json.loads(backupBytes);byid={r['id']:r for r in plan};assert len(byid)==2102
assert not (root/'manifest.json').exists()
(root/'ClassifyRestore.class').write_bytes(base64.b64decode(helperClass));os.chmod(str(root/'ClassifyRestore.class'),0o600)
java=None
for pid in os.listdir('/proc'):
 if not pid.isdigit():continue
 try:
  if b'org.apache.catalina.startup.Bootstrap' in open('/proc/'+pid+'/cmdline','rb').read():java=os.readlink('/proc/'+pid+'/exe');break
 except OSError:pass
assert java
patched=[]
for h in b['es']:
 r=byid[h['_id']];assert (h['_source']['email'] or '').lower()==r['email'];z=copy.deepcopy(h['_source']);z.update(r['patch']);patched.append(z)
proc=subprocess.run([java,'-cp',str(root)+':/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/classes:/opt/apache-tomcat-9.0.71/webapps/talent/WEB-INF/lib/*','ClassifyRestore'],input=('\n'.join(json.dumps(z) for z in patched)+'\n').encode(),stdout=subprocess.PIPE,stderr=subprocess.PIPE)
assert proc.returncode==0,proc.stderr.decode();classes=[json.loads(l) for l in proc.stdout.splitlines()];assert len(classes)==4204
manifest=[]
for h,classification in zip(b['es'],classes):
 if classification.get('classifiedAt'):classification['classifiedAt']=classification['classifiedAt'][:19].replace('T',' ')
 patch=copy.deepcopy(byid[h['_id']]['patch']);patch['expertClassification']=classification
 manifest.append({'index':h['_index'],'id':h['_id'],'patch':patch,'seqNo':h['_seq_no'],'primaryTerm':h['_primary_term']})
# Python 3.6 has no datetime.fromisoformat, parse normalized representation instead.
cp=[];byemail={r['email']:r for r in plan}
for c in b['contacts']:
 r=byemail[c['expert_email'].lower()];cp.append({'id':c['id'],'expert_name':r['expectedName'],'country':r['patch']['country']})
result={'createdAt':datetime.now().isoformat(),'es':manifest,'contacts':cp,'plan':plan}
data=json.dumps(result,ensure_ascii=True,indent=2).encode();(root/'manifest.json').write_bytes(data);os.chmod(str(root/'manifest.json'),0o600)
print(json.dumps({'stage':'prepared','experts':len(plan),'esDocs':len(manifest),'contacts':len(cp),'manifestSha256':hashlib.sha256(data).hexdigest(),'fields':sorted({k for m in manifest for k in m['patch']}),'classificationCount':len(classes)}))
