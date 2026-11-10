// Misst die Widget-Wolkenschleife wie SkyBitmap.clouds: Schrittweiten, Uebergang Ende->Anfang, Kontrast je Bild.
const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
const N=+(process.argv[2]||14), LOOP=+(process.argv[3]||20);
(async()=>{
 const b=await chromium.launch({args:['--use-angle=swiftshader','--enable-webgl','--ignore-gpu-blocklist','--enable-unsafe-swiftshader']});
 const p=await b.newPage();
 const src=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/shaders/sky.agsl'),'utf8');
 const gl=fs.readFileSync(path.join(__dirname,'gl.js'),'utf8').replace('export function','function');
 const scenes=JSON.parse(fs.readFileSync(path.join(__dirname,'scenes.json')));
 const out=await p.evaluate(({src,gl,scenes,N,LOOP})=>{
  const compileSky=new Function(gl+';return compileSky')();
  const c=document.createElement('canvas');c.width=240;c.height=112;
  const draw=compileSky(c,src); const ctx=c.getContext('webgl');
  const read=()=>{const px=new Uint8Array(c.width*c.height*4);ctx.readPixels(0,0,c.width,c.height,ctx.RGBA,ctx.UNSIGNED_BYTE,px);const l=new Float32Array(c.width*c.height);for(let i=0;i<l.length;i++)l[i]=.3*px[i*4]+.59*px[i*4+1]+.11*px[i*4+2];return l};
  const res={};
  for(const name of ['Teils bewölkt','Bedeckt','Bewölkte Nacht','Sonnenuntergang']){
   const s={...scenes[name],uRain:0,uSnow:0,uStorm:0,uLoopT:LOOP};
   const fr=[];
   for(let i=0;i<=N;i++){const w=i/N; draw({...s,uLoopW:w,uCloudTime:41+w*LOOP},41); fr.push(read());}
   const diff=(a,b)=>{let d=0;for(let i=0;i<a.length;i++)d+=Math.abs(a[i]-b[i]);return d/a.length};
   const sd=a=>{let m=0;for(const v of a)m+=v;m/=a.length;let q=0;for(const v of a)q+=(v-m)*(v-m);return Math.sqrt(q/a.length)};
   const steps=[];for(let i=0;i<N;i++)steps.push(diff(fr[i],fr[(i+1)%N]));
   res[name]={steps:steps.map(x=>+x.toFixed(2)),closure:+diff(fr[N],fr[0]).toFixed(3),contrast:fr.slice(0,N).map(f=>+sd(f).toFixed(1))};
  }
  return res;
 },{src,gl,scenes,N,LOOP});
 for(const [k,v] of Object.entries(out)) console.log(k,'\n  Schritte',v.steps.join(' '),'\n  Bild N gegen Bild 0',v.closure,'\n  Kontrast',v.contrast.join(' '));
 await b.close();
})();
