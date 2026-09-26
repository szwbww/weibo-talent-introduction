import sys,collections,re,xml.etree.ElementTree as E
sys.path.insert(0,'/tmp/deep-discovery-name-audit')
from extract_base_corrected import extract,text,norm,mail_re,initials

def supplemental(path):
 doc=E.parse(path).getroot();meta=doc.find('./front/article-meta');out=collections.defaultdict(list)
 if meta is None:return out,[]
 nodes={n.get('id'):n for n in meta.iter() if n.get('id')};authors=[]
 for group in meta.findall('./contrib-group'):
  if group.get('content-type') in ('editor','editors'):continue
  for c in group.findall('./contrib'):
   if c.get('contrib-type') not in (None,'author'):continue
   name=c.find('name')
   if name is None:continue
   g=text(name.find('given-names')).strip();f=text(name.find('surname')).strip()
   if not g or not f:continue
   a={'name':g+' '+f,'node':c,'shortNames':{norm(initials(g)+' '+f),norm(initials(g+' '+f))},'refs':set(x for n in c.findall('./xref') if n.get('ref-type') in ('corresp','author-notes','fn') for x in n.get('rid','').split())};authors.append(a)
 def add(em,a,method,snippet):
  em=em.strip().lower()
  if mail_re.fullmatch(em):out[em].append({'name':a['name'],'method':method,'snippet':snippet})
 for a in authors:
  for node in a['node'].findall('./address/email'):
   add(text(node),a,'AUTHOR_ADDRESS_EMAIL',E.tostring(a['node'],encoding='unicode'))
 for rid in {r for a in authors for r in a['refs']}:
  node=nodes.get(rid)
  if node is None:continue
  claimants=[a for a in authors if rid in a['refs']];txt=text(node);emails=list(dict.fromkeys(m.group(0).lower() for m in mail_re.finditer(txt)))
  # Author-linked, email-only footnote. Reject additional prose beyond email/contact labels.
  residual=mail_re.sub('',txt);residual=re.sub(r'(?i)\b(?:e-?mail|address|correspondence|corresponding author)\b','',residual)
  if len(claimants)==1 and len(emails)==1 and not norm(residual):add(emails[0],claimants[0],'EXCLUSIVE_EMAIL_FOOTNOTE',txt)
 # Read explicit full name immediately before parenthesized email in correspondence notes.
 for node in meta.findall('./author-notes/corresp')+meta.findall('./author-notes/fn'):
  txt=text(node);ems=list(mail_re.finditer(txt));previous=0
  for em in ems:
   prefix=txt[previous:em.start()]
   if previous>0:prefix=re.sub(r'^\s*\([^()]*\)\s*[,;]?\s*','',prefix)
   previous=em.end()
   # Demand a NAME ( EMAIL ) shape, with exact suffix after punctuation/whitespace normalization.
   if not re.search(r'\(\s*$',prefix):continue
   prefix=re.sub(r'\(\s*$','',prefix).strip();candidates=[a for a in authors if norm(prefix).endswith(norm(a['name']))]
   if len(candidates)!=1:continue
   if not re.match(r'\s*\)',txt[em.end():]):continue
   add(em.group(0),candidates[0],'EXPLICIT_NAME_PAREN_EMAIL',prefix+' ('+em.group(0)+')')
 # Every email gets its OWN adjacent parenthesized author label, not a shared segment label.
 for node in meta.findall('./author-notes/corresp')+meta.findall('./author-notes/fn'):
  txt=text(node);ems=list(mail_re.finditer(txt));previous=0
  for i,em in enumerate(ems):
   suffix=txt[em.end():ems[i+1].start() if i+1<len(ems) else len(txt)]
   label=re.match(r'\s*\(([^()]+)\)',suffix)
   if label:
    matches=[a for a in authors if norm(label.group(1)) in a['shortNames'] or norm(label.group(1))==norm(a['name'])]
    if len(matches)==1:add(em.group(0),matches[0],'ADJACENT_AUTHOR_LABEL',em.group(0)+label.group(0))
   prefix=txt[previous:em.start()]
   if previous>0:prefix=re.sub(r'^\s*\([^()]*\)\s*[,;]?\s*','',prefix)
   previous=em.end()
   prefix=prefix.lstrip(' \n\t,;.*✉#†‡)')
   prefix=re.sub(r'(?i)^(?:correspondence(?:\s+to)?|corresponding\s+authors?|address\s+correspondence|co-authors?)\s*[:：]?\s*','',prefix).strip()
   pn=norm(prefix)
   # A correspondence segment starts with exactly one full author name, followed by affiliation/contact text.
   mentioned=[a for a in authors if norm(a['name']) in pn]
   if len(mentioned)==1 and pn.startswith(norm(mentioned[0]['name'])):
    add(em.group(0),mentioned[0],'NAMED_CORRESPONDENCE_SEGMENT',prefix+em.group(0))
   # Explicit coauthor initials colon email; only adjacent label, unique among all authors.
   token=re.search(r'(?:^|[,;])\s*([A-Za-z. -]{2,18})\s*[:：]\s*$',prefix)
   if token:
    matches=[a for a in authors if norm(token.group(1)) in a['shortNames']]
    if len(matches)==1:add(em.group(0),matches[0],'ADJACENT_INITIALS_COLON',token.group(0)+em.group(0))
 return dict(out),[a['name'] for a in authors]

def assess(path,email,oldname):
 base,doi,oldauthors=extract(path);extra,authors=supplemental(path);ev=extra.get(email,[]);names={x['name'] for x in ev};original=base.get(email)
 if original:names.update(original['names'])
 result={'evidence':ev,'originalMapping':original,'doi':doi,'paperAuthors':authors}
 if len({norm(n) for n in names})!=1:result['verdict']='UNRESOLVED';return result
 expected=next(iter(names));result['expectedName']=expected
 if norm(expected)==norm(oldname):result['verdict']='MATCH'
 elif norm(oldname) in {norm(n) for n in authors}:result['verdict']='CONFIRMED_OTHER_AUTHOR'
 else:result['verdict']='NAME_VARIANT_REVIEW'
 return result
