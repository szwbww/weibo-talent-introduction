import tempfile,pathlib
from extract_strict import extract
root=pathlib.Path('/tmp/deep-discovery-name-audit')
cases=[('continuous','Alpha One <email>alpha@x.edu</email> Beta Two <email>beta@x.edu</email>',{'alpha@x.edu':['Alpha One'],'beta@x.edu':['Beta Two']}),('multi','Alpha One <email>alpha@x.edu</email>, <email>alias@x.edu</email>; Beta Two <email>beta@x.edu</email>',{'alpha@x.edu':['Alpha One'],'alias@x.edu':['Alpha One'],'beta@x.edu':['Beta Two']}),('unlabeled','<email>alpha@x.edu</email>; <email>beta@x.edu</email>',{}),('semicolon_boundary','Alpha One <email>alpha@x.edu</email>; <email>unassigned@x.edu</email>',{'alpha@x.edu':['Alpha One']}),('initials','<email>alpha@x.edu</email> (A.O.); <email>beta@x.edu</email> (B.T.)',{'alpha@x.edu':['Alpha One'],'beta@x.edu':['Beta Two']}),('competing_names','Alpha One <email>alpha@x.edu</email> Beta Two <email>beta@x.edu</email> (A.O.)',{'alpha@x.edu':['Alpha One'],'beta@x.edu':['Beta Two']})]
for label,note,expected in cases:
 xml='<article><front><article-meta><contrib-group><contrib><name><given-names>Alpha</given-names><surname>One</surname></name><xref ref-type="corresp" rid="c"/></contrib><contrib><name><given-names>Beta</given-names><surname>Two</surname></name><xref ref-type="corresp" rid="c"/></contrib></contrib-group><author-notes><corresp id="c">'+note+'</corresp></author-notes></article-meta></front></article>'
 with tempfile.NamedTemporaryFile(suffix='.xml') as f:
  f.write(xml.encode());f.flush();m,_,_=extract(f.name);actual={e:r['names'] for e,r in m.items()};assert actual==expected,(label,actual)
checks={'PMC13269110':{'biedh@nus.edu.sg':'Dean Ho','tgekyen@dso.org.sg':'Gladys Gek Yen Tan'},'PMC13275667':{'nardeen.hammad321@gmail.com':'Nardeen Hammad'},'PMC13280628':{'hlmarkc@gmail.com':'Chien-Chin Chen'},'PMC13279394':{'xuxiaoliang1983@163.com':'Xiaoliang Xu'},'PMC13269353':{'raguvarma@gmail.com':'Durairaj Ragu Varman'}}
for pmc,want in checks.items():
 p=root/'public_xml'/(pmc+'.xml')
 if not p.exists():p=pathlib.Path('/tmp/expert-identity-audit/public_xml')/p.name
 m,_,_=extract(p)
 for email,name in want.items():assert m[email]['names']==[name],(pmc,email,m[email])
print('PASS: 6 ambiguity/segmentation fixtures and 6 independently checked paper bindings')
