import json,sys,os
d=os.path.dirname(os.path.abspath(__file__))
t=open(d+'/page.template.html',encoding='utf8').read()
t=t.replace('/*SHADER*/',open(d+'/../../app/src/main/assets/shaders/sky.agsl',encoding='utf8').read())
t=t.replace('/*SCENES*/',json.dumps(json.load(open(d+'/scenes.json',encoding='utf8')),ensure_ascii=False))
open(sys.argv[1],'w',encoding='utf8').write(t)
