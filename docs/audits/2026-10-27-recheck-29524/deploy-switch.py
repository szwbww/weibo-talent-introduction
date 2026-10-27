import os,pathlib,subprocess,sys,re,json,hashlib,time,shutil,signal,datetime
root=pathlib.Path('/opt/apache-tomcat-9.0.71');incoming=root/'deploy/incoming/recheck-29524'
rp=incoming/'receipt.json';r=json.loads(rp.read_text());assert r['stage']=='STAGED'
expected=sys.argv[1];assert re.fullmatch('[a-f0-9]{64}',expected)
pids=[]
for p in pathlib.Path('/proc').glob('[0-9]*'):
 try:
  if b'org.apache.catalina.startup.Bootstrap' in (p/'cmdline').read_bytes().split(b'\0'):pids.append(p)
 except FileNotFoundError:pass
assert len(pids)==1
proc=pids[0];pid=int(proc.name);java=str((proc/'exe').resolve())
values=dict(part.split('=',1) for part in (proc/'environ').read_bytes().decode().split('\0') if '=' in part)
u=re.match(r'jdbc:mysql://([^/:?]+)(?::([0-9]+))?/([^?]+)',values['DB_URL'])
cmd=['mysql','--connect-timeout=5','--quick','--host='+u.group(1),'--port='+(u.group(2) or '3306'),'--user='+values['DB_USERNAME'],'--database='+u.group(3),'--batch','--raw','--skip-column-names']
def query(sql):
 p=subprocess.run(cmd,input='SET SESSION MAX_EXECUTION_TIME=5000; SET SESSION TRANSACTION READ ONLY; START TRANSACTION; '+sql+'; COMMIT;',universal_newlines=True,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=dict(os.environ,MYSQL_PWD=values['DB_PASSWORD']),timeout=10)
 assert p.returncode==0,'DB preflight failed'
 return p.stdout
active=query("SELECT id,task_type FROM task_execution WHERE status IN ('RUNNING','CANCELLING')")
assert all(line.split('\t')==['29524','DISCOVERY_RECHECK_PREPARE'] for line in active.splitlines()),'Other tasks active; restart not performed'
assert query("SELECT revision FROM discovery_verification_run WHERE execution_id=29524").strip()=='8629','Roster changed; inspect first'
def sha(path):
 h=hashlib.sha256()
 with path.open('rb') as f:
  for b in iter(lambda:f.read(1024*1024),b''):h.update(b)
 return h.hexdigest()
assert sha(root/'webapps/talent.war')==r['baseWarSha'],'WAR changed'
assert sha(incoming/'talent-repaired.war')==r['releaseWarSha'],'staged WAR changed'
backup=pathlib.Path(r['backup'])
(backup/'run-before.json').write_text(query("SELECT JSON_OBJECT('execution_id',execution_id,'revision',revision,'input_closed',input_closed,'recheck_json',recheck_json) FROM discovery_verification_run WHERE execution_id=29524"))
(backup/'task-before.json').write_text(query("SELECT JSON_OBJECT('id',id,'status',status,'error_message',error_message,'finished_at',finished_at,'result_summary',result_summary,'success_count',success_count,'failure_count',failure_count) FROM task_execution WHERE id=29524"))
for path in [backup/'run-before.json',backup/'task-before.json']:os.chmod(str(path),0o600)
r.update(oldPid=pid,restartStartedAt=datetime.datetime.now().isoformat(),catalinaLogOffset=(root/'logs/catalina.out').stat().st_size,recoveredHash=expected)
rp.write_text(json.dumps(r,indent=2))
log=open(backup/'restart.log','ab');os.chmod(str(backup/'restart.log'),0o600)
print('STOPPING',pid,flush=True)
subprocess.run(['bash',str(root/'bin/shutdown.sh')],stdout=log,stderr=subprocess.STDOUT,timeout=25)
for _ in range(30):
 if not proc.exists():break
 time.sleep(1)
if proc.exists():
 print('GRACEFUL_TIMEOUT sending SIGTERM to verified Tomcat PID',pid,flush=True)
 os.kill(pid,signal.SIGTERM)
 for _ in range(20):
  if not proc.exists():break
  time.sleep(1)
assert not proc.exists(),'Tomcat did not stop; no files replaced'
try:
 cp=str(incoming)+':'+str(incoming/'patch/WEB-INF/classes')+':'+str(root/'webapps/talent/WEB-INF/classes')+':'+str(root/'webapps/talent/WEB-INF/lib/*')
 recovery=subprocess.run([java,'-Xmx128m','-cp',cp,'Recheck29524Recovery','apply',expected],env=dict(os.environ,**{k:values[k] for k in ['DB_URL','DB_USERNAME','DB_PASSWORD']}),stdout=subprocess.PIPE,stderr=subprocess.PIPE,universal_newlines=True,timeout=180)
 (backup/'recovery.log').write_text(recovery.stdout+recovery.stderr);os.chmod(str(backup/'recovery.log'),0o600)
 assert recovery.returncode==0,'Recovery failed; see private backup log'
 for line in recovery.stdout.splitlines():
  if line.startswith(('CHECK_OK','RECOVERED_READY')):print(line,flush=True)
 os.rename(str(root/'webapps/talent'),str(backup/'talent-expanded'))
 shutil.copy2(incoming/'talent-repaired.war',root/'webapps/.talent-29524.war.tmp')
 os.replace(str(root/'webapps/.talent-29524.war.tmp'),str(root/'webapps/talent.war'))
 r['stage']='RESTARTING';rp.write_text(json.dumps(r,indent=2))
except BaseException:
 # If replacement failed, retain/reinstate the original deployed tree before starting it.
 if not (root/'webapps/talent').exists() and (backup/'talent-expanded').exists():
  shutil.copy2(backup/'talent.war',root/'webapps/talent.war')
  os.rename(str(backup/'talent-expanded'),str(root/'webapps/talent'))
 subprocess.run(['bash',str(root/'bin/startup.sh')],stdout=log,stderr=subprocess.STDOUT,timeout=25)
 raise
start=subprocess.run(['bash',str(root/'bin/startup.sh')],stdout=log,stderr=subprocess.STDOUT,timeout=25)
r['startupExit']=start.returncode;rp.write_text(json.dumps(r,indent=2))
print('RESTARTING startupExit='+str(start.returncode)+' backup='+r['backup'],flush=True)
