import json,sys,collections,hashlib,csv
from pathlib import Path
def load(path):return [json.loads(x[2:]) for x in Path(path).read_text().splitlines() if x.startswith('E ')]
def fp(s):return hashlib.sha256(s.encode()).hexdigest()[:24]
def tokens(a):
 r,e,n,x,o,*_=a
 return {('mail',e),('name',n)}|({('alex',fp(x.split('/')[-1]))} if x else set())|({('orcid',fp(o.split('/')[-1]))} if o else set())
def group(rows):
 ps=[]
 for row in rows:
  t=tokens(row); match=[p for p in ps if p[0]&t]; rs=[row]
  for p in match:t|=p[0];rs+=p[1];ps.remove(p)
  ps.append((t,rs))
 return ps
def key(a):return (a[0],a[1],a[2])
def dump(rows):
 bs=collections.defaultdict(set)
 for e,n,x,o in (s.split() for s in Path(__file__).with_name('baseline-hashes.txt').read_text().splitlines()):
  bs['pair'].add((e,n));bs['alex'].add(x);bs['orcid'].add(o);bs['name'].add(n)
 ps=group(rows); allpairs=set(map(key,rows));rids=set(r[0] for r in rows); known=[]; uncertain=[]
 for i,(ts,items) in enumerate(ps,1):
  if any((a[1],a[2]) in bs['pair'] or any(k in ('alex','orcid') and v in bs[k] for k,v in tokens(a)) for a in items): known.append(i)
  elif any(a[2] in bs['name'] for a in items):uncertain.append(i)
 print('PERSON',len(ps),'PAPER',len(rids),'PAPER_BINDINGS',len(allpairs),'prior_known',len(known),'other_same_name',len(uncertain))
 return ps,known,uncertain

if __name__ == '__main__':
 dump(json.loads(Path(__file__).with_name('source-evidence.json').read_text()))
