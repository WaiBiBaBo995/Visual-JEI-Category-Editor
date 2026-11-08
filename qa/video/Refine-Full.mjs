import fs from 'node:fs';
import path from 'node:path';
const dir=path.resolve('video-production/full'),read=f=>fs.readFileSync(path.join(dir,f),'utf8').replace(/^\uFEFF/,'');
const write=(f,s)=>fs.writeFileSync(path.join(dir,f),s,'utf8');
let film=read('film.html');
if(/\blocked\b|\boverrides=/.test(film))throw new Error('Preserve manual edits.');
if(film.includes('world-corner-actions'))throw new Error('Refinement already applied.');
const data=JSON.parse(read('cuts.json')),W=data.takes.world;
const fmt=x=>Number(x.toFixed(6)),speed=(a,b,d)=>Math.ceil((b-a)/d*1e12)/1e12;
const tag=(id,replacement)=>{const re=new RegExp(`<video id="${id}"[^>]*><\\/video>`);if(!re.test(film))throw new Error(id);film=film.replace(re,replacement);};
const worldFirst=data.cuts.find(c=>c.id==='world-structure');worldFirst.out=fmt(W.markers.world+6.2);worldFirst.duration=6;worldFirst.speed=1;
tag(worldFirst.id,`<video id="${worldFirst.id}" src="${W.src}#t=${worldFirst.in},${worldFirst.out}" at="${worldFirst.at}" muted></video>`);
const trace=fs.readFileSync(path.join(W.root,'cursor.jsonl'),'utf8').trim().split(/\r?\n/).map(JSON.parse).map(p=>({t:(p.epochMillis-W.recorderReadyEpochMillis)/1000+W.lead,x:p.x/p.guiWidth*1920,y:p.y/p.guiHeight*1080,event:p.event}));
const replacements=[
 ['world-open-selection',118.8,4,W.markers.world+9.1,W.markers.selection+.15,true],
 ['world-corner-actions',122.8,6,W.markers.selection+.15,W.markers['corner-second']+1.8,true],
 ['world-import-apply',128.8,6,W.markers['corner-second']+5.5,W.markers.preview+1,true],
 ['world-import-result',134.8,8,W.markers.preview+1.1,W.markers.preview+9.1,false],
];
const vids=[],pointers=[];
for(const [id,at,duration,a,b,pointer] of replacements){
 const s=speed(a,b,duration);
 vids.push(`<video id="${id}" src="${W.src}#t=${fmt(a)},${fmt(b)}" at="${at}" speed="${s}" muted></video>`);
 if(pointer){
  write(id+'-cursor.js','window.cursorTake='+JSON.stringify({trace,sourceIn:a,speed:s})+';');
  const engine=read('world-import-actions-cursor.html').replace('world-import-actions-cursor.js',id+'-cursor.js').replace('duration:25','duration:'+duration);
  write(id+'-cursor.html',engine);
  pointers.push(`<iframe id="${id}-input" src="${id}-cursor.html#t=0,${duration}" at="${at}"></iframe>`);
 }
 data.cuts.push({id,take:W.take,in:fmt(a),out:fmt(b),at,duration,speed:s,style:'',pointer});
}
tag('world-import-actions',vids.join('\n'));
film=film.replace(/<iframe id="world-import-actions-input"[^>]*><\/iframe>/,pointers.join('\n'));
film=film.replace(/(id="world-selection-guide"[^>]*at=")[^"]+/, '$1122.8');
data.cuts=data.cuts.filter(c=>c.id!=='world-import-actions').sort((a,b)=>a.at-b.at);
for(const id of ['native-before','native-real-drag']){
 const c=data.cuts.find(c=>c.id===id);c.style='width:2688px;height:1512px;left:-450px;top:-280px';
 tag(id,`<video id="${id}" src="${data.takes.native.src}#t=${c.in},${c.out}" at="${c.at}" speed="${c.speed}" style="${c.style}" muted></video>`);
 if(c.pointer)film=film.replace(new RegExp(`(<iframe id="${id}-input"[^>]*)(></iframe>)`),`$1 style="${c.style}"$2`);
}
const runtime=data.cuts.find(c=>c.id==='environment-jei');runtime.style='width:2400px;height:1350px;left:220px;top:-160px';
tag(runtime.id,`<video id="${runtime.id}" src="${data.takes['showcase-full'].src}#t=${runtime.in},${runtime.out}" at="${runtime.at}" speed="1" style="${runtime.style}" muted></video>`);
write('film.html',film);write('cuts.json',JSON.stringify(data,null,2)+'\n');
console.log('World capture now lands with narration; native edit enlarged; runtime card clears real scene.');
