// Testet die Karten-Seite im Browser; alle Netzantworten sind hier nachgestellt.
const {chromium}=require('playwright'),path=require('path');
const png=Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfz0AAAwAB/aurH8kAAAAASUVORK5CYII=','base64');
(async()=>{
 const out=process.argv[2];
 const b=await chromium.launch();
 const p=await b.newPage({viewport:{width:412,height:880}});
 const errs=[];p.on('pageerror',e=>errs.push(e.message));
 const seen={radar:0,grid:0,tiles:0};
 await p.route(/^https:/,r=>{
  const u=r.request().url();
  const cors={'access-control-allow-origin':'*'};
  if(u.includes('weather-maps.json')){const now=1793660000;return r.fulfill({headers:cors,contentType:'application/json',body:JSON.stringify({host:'https://tilecache.rainviewer.com',radar:{past:[...Array(13)].map((_,i)=>({time:now+i*600,path:'/v2/radar/'+i}))}})})}
  if(u.includes('open-meteo')){seen.grid++;const q=new URL(u).searchParams;const la=q.get('latitude').split(',');const lo=q.get('longitude').split(',');
    return r.fulfill({headers:cors,contentType:'application/json',body:JSON.stringify(la.map((x,i)=>({current:{temperature_2m:6+(+x-46)*-1.4+Math.sin(+lo[i])*5,wind_speed_10m:12+(i%7)*5,wind_direction_10m:(240+i*3)%360}})))})}
  if(u.includes('tilecache'))seen.radar++;else seen.tiles++;
  return r.fulfill({contentType:'image/png',body:png});
 });
 await p.goto('file://'+path.resolve(__dirname,'../../app/src/main/assets/map/map.html'));
 await p.evaluate(()=>start(50.11,8.68,24,16));
 await p.waitForTimeout(1500);
 const frames=await p.evaluate(()=>({n:frames.length,idx:index,when:document.getElementById('when').textContent,fill:document.getElementById('fill').style.width}));
 await p.screenshot({path:out+'-rain.png'});
 await p.click('button[data-l=temp]');await p.waitForTimeout(1200);
 const temp=await p.evaluate(()=>({grid:!!grid,labels:labels.length,overlay:!!overlay,cells:grid&&grid.t.length}));
 await p.screenshot({path:out+'-temp.png'});
 await p.click('button[data-l=wind]');await p.waitForTimeout(2200);
 const wind=await p.evaluate(()=>{const c=document.getElementById('wind');const d=c.getContext('2d').getImageData(0,0,c.width,c.height).data;let n=0;for(let i=3;i<d.length;i+=4)if(d[i]>0)n++;return{particles:particles.length,painted:n,sample:windAt(50,9)}});
 await p.screenshot({path:out+'-wind.png'});
 await p.click('button[data-l=rain]');await p.waitForTimeout(800);
 const back=await p.evaluate(()=>({overlay:!!overlay,labels:labels.length,timeline:document.getElementById('timeline').style.display}));
 console.log(JSON.stringify({frames,temp,wind,back,seen,errs}));
 await b.close();
})();
