const {chromium}=require('playwright'),fs=require('fs'),path=require('path');
(async()=>{
 const b=await chromium.launch({args:['--use-gl=swiftshader','--use-angle=swiftshader','--enable-webgl','--ignore-gpu-blocklist','--enable-unsafe-swiftshader']});
 const p=await b.newPage({viewport:{width:1800,height:1500}});
 const errs=[];p.on('pageerror',e=>errs.push(e.message));p.on('console',m=>m.type()=='error'&&errs.push(m.text()));
 await p.setContent('<body style="margin:0;background:#111;color:#fff;font:13px sans-serif"><div id=g style="display:grid;grid-template-columns:repeat(5,1fr);gap:6px;padding:6px"></div>');
 const src=fs.readFileSync(path.join(__dirname,'../../app/src/main/assets/shaders/sky.agsl'),'utf8');
 const gl=fs.readFileSync(path.join(__dirname,'gl.js'),'utf8').replace('export function','function');
 const scenes=JSON.parse(fs.readFileSync(path.join(__dirname,'scenes.json')));
 const r=await p.evaluate(({src,gl,scenes,times})=>{
  const compileSky=new Function(gl+';return compileSky')();
  const t0=performance.now();
  for(const [n,s] of Object.entries(scenes)){
   const c=document.createElement('canvas');c.width=540;c.height=1170;
   const d=document.createElement('div');d.textContent=n;c.style.cssText='width:100%;display:block';d.prepend(c);g.append(d);
   compileSky(c,src)(s,times[n]??41.3);
  }
  return performance.now()-t0;
 },{src,gl,scenes,times:JSON.parse(process.argv[3]||'{}')}).catch(e=>'ERR '+e.message);
 console.log(r,errs.slice(0,5));
 await p.screenshot({path:process.argv[2],fullPage:true});
 await b.close();
})();
