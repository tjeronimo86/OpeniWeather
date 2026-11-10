// Simuliert die Widget-Wolkenschleife: rendert die Bilder jeder Szene mit dem echten Shader
// und mischt sie so, wie der ViewFlipper es tut (auch am Sprung vom letzten aufs erste Bild).
// Ausgabe je Szene: Verhaeltnis groesste zu mittlerer Aenderung zwischen zwei Zeitschritten.
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const cfg=JSON.parse(process.argv[2]||'{}');
(async()=>{
 const b=await chromium.launch({args:['--use-angle=swiftshader','--enable-webgl','--ignore-gpu-blocklist','--enable-unsafe-swiftshader']});
 const p=await b.newPage();
 const src=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/shaders/sky.agsl'),'utf8');
 const scenes=JSON.parse(fs.readFileSync(path.join(__dirname,'scenes.json')));
 const out=await p.evaluate(({src,scenes,cfg})=>{
  const W=169,H=79,N=cfg.frames||14,LOOP=cfg.loop||20;
  const c=document.createElement('canvas');c.width=W;c.height=H;
  const gl=c.getContext('webgl',{preserveDrawingBuffer:true});
  const frag='precision highp float;\n#define half4 vec4\n#define half3 vec3\n'+src.replace('half4 main(vec2 frag) {','vec4 skyMain(vec2 frag) {')+'\nvoid main(){gl_FragColor=skyMain(vec2(gl_FragCoord.x,uRes.y-gl_FragCoord.y));}';
  const sh=(t,s)=>{const o=gl.createShader(t);gl.shaderSource(o,s);gl.compileShader(o);if(!gl.getShaderParameter(o,gl.COMPILE_STATUS))throw new Error(gl.getShaderInfoLog(o));return o};
  const pr=gl.createProgram();gl.attachShader(pr,sh(gl.VERTEX_SHADER,'attribute vec2 a;void main(){gl_Position=vec4(a,0.,1.);}'));gl.attachShader(pr,sh(gl.FRAGMENT_SHADER,frag));gl.linkProgram(pr);gl.useProgram(pr);
  gl.bindBuffer(gl.ARRAY_BUFFER,gl.createBuffer());gl.bufferData(gl.ARRAY_BUFFER,new Float32Array([-1,-1,3,-1,-1,3]),gl.STATIC_DRAW);
  const a=gl.getAttribLocation(pr,'a');gl.enableVertexAttribArray(a);gl.vertexAttribPointer(a,2,gl.FLOAT,false,0,0);
  const u=n=>gl.getUniformLocation(pr,n);
  const phases=cfg.phases||[...Array(N)].map((_,i)=>i/N);
  const res={};
  for(const [name,s] of Object.entries(scenes)){
   const frames=[];
   for(let i=0;i<N;i++){
    gl.viewport(0,0,W,H);gl.uniform2f(u('uRes'),W,H);gl.uniform1f(u('uTime'),41);gl.uniform1f(u('uSunVis'),1);
    for(const [k,v] of Object.entries(s))Array.isArray(v)?gl.uniform2f(u(k),v[0],v[1]):gl.uniform1f(u(k),v);
    gl.uniform1f(u('uRain'),0);gl.uniform1f(u('uSnow'),0);gl.uniform1f(u('uStorm'),0);
    gl.uniform1f(u('uLoopT'),LOOP);gl.uniform1f(u('uCloudTime'),41+phases[i]*LOOP);gl.uniform1f(u('uLoopW'),phases[i]);
    gl.drawArrays(gl.TRIANGLES,0,3);
    const px=new Uint8Array(W*H*4);gl.readPixels(0,0,W,H,gl.RGBA,gl.UNSIGNED_BYTE,px);
    const f=new Float32Array(W*H*3);for(let j=0;j<W*H;j++){f[j*3]=px[j*4];f[j*3+1]=px[j*4+1];f[j*3+2]=px[j*4+2]}
    frames.push(f);
   }
   // Zeitreihe: S Schritte je Wechselzeit
   const S=8,series=[];
   const inA=cfg.inA||'t',outB=cfg.outB||'1';
   const A=new Function('t','return '+inA),B=new Function('t','return '+outB);
   const under=cfg.under?frames[N>>1]:null;
   for(let k=0;k<N;k++){           // Wechsel von Bild k-1 auf Bild k
    const neu=frames[k],alt=frames[(k+N-1)%N],wrap=k===0&&!cfg.noWrap;
    for(let s2=0;s2<S;s2++){
     const t=s2/S,a=Math.min(1,Math.max(0,A(t))),bb=Math.min(1,Math.max(0,B(t)));
     const o=new Float32Array(W*H*3);
     // normal liegt das neue Bild oben, am Schleifensprung das alte
     const wTop=wrap?bb:a,wBot=(1-wTop)*(wrap?a:bb),wU=(1-wTop)*(1-(wrap?a:bb));
     const top=wrap?alt:neu,bot=wrap?neu:alt;
     for(let j=0;j<o.length;j++)o[j]=wTop*top[j]+wBot*bot[j]+wU*(under?under[j]:40);
     series.push(o);
    }
   }
   const d=[];for(let i=0;i<series.length;i++){const x=series[i],y=series[(i+1)%series.length];let sum=0;for(let j=0;j<x.length;j++)sum+=Math.abs(x[j]-y[j]);d.push(sum/x.length)}
   const sorted=[...d].sort((x,y)=>x-y),med=sorted[d.length>>1],mx=Math.max(...d),mn=Math.min(...d);
   if(cfg.dump===name)res['_'+name]=d.slice(0,32).map(x=>+x.toFixed(2)).join(' ');
   res[name]={median:+med.toFixed(3),max:+mx.toFixed(3),min:+mn.toFixed(3),ratio:+(mx/(med||1e-9)).toFixed(1),wo:d.indexOf(mx),wrapMax:+Math.max(...d.slice(0,S)).toFixed(3)};
  }
  return res;
 },{src,scenes,cfg});
 for(const [k,v] of Object.entries(out))console.log(k.padEnd(20),JSON.stringify(v));
 await b.close();
})();
