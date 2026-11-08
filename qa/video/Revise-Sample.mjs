import fs from 'node:fs';
import path from 'node:path';

// Run after recording and Edit-Sample.ps1 -FilmDirectory <new folder>.
const root = process.cwd();
const dir = path.resolve(root, process.argv[2] || 'video-production/sample-v2');
const cuts = JSON.parse(fs.readFileSync(path.join(dir, 'cuts.json'), 'utf8').replace(/^\uFEFF/, ''));
const write = (name, text) => fs.writeFileSync(path.join(dir, name), text, 'utf8');
const filmPath = path.join(dir, 'film.html');
let film = fs.readFileSync(filmPath, 'utf8'); // Read immediately before changes.
const refreshCursor = process.argv.includes('--refresh-cursor');
if (!refreshCursor && (film.includes('VJCE_REVISION_2') || /\blocked\b|\boverrides=/.test(film))) {
  throw new Error('Already revised or manually edited: preserve this project and use a new folder.');
}
const trace = fs.readFileSync(path.join(cuts.capture.markers, 'cursor.jsonl'), 'utf8')
  .trim().split(/\r?\n/).map(line => JSON.parse(line)).map(p => ({
    t: (p.epochMillis - cuts.capture.recorderReadyEpochMillis) / 1000 + (cuts.captureLeadIn ?? 2),
    x: p.x / p.guiWidth * 1920, y: p.y / p.guiHeight * 1080, event: p.event,
  }));
write('cursor-data.js', `window.cursorTake=${JSON.stringify({trace, sourceIn:cuts.selectionIn,speed:cuts.selectionSpeed})};\n`);
write('cursor.html', `<!doctype html><html><head><meta charset="utf-8"><style>
body{margin:0;background:transparent;overflow:hidden}#fx{position:absolute;inset:0}
#pointer{position:absolute;width:38px;height:48px;filter:drop-shadow(0 2px 3px #0009);transform-origin:0 0}
</style></head><body><svg id="fx" width="1920" height="1080"></svg>
<svg id="pointer" viewBox="-4 -4 38 48"><path d="M0 0 L0 31 L8 24 L15 39 L22 36 L15 22 L27 22 Z" fill="#fff" stroke="#17241f" stroke-width="2.7" stroke-linejoin="round"/></svg>
<script src="cursor-data.js"></script><script>
const {trace,sourceIn,speed}=window.cursorTake,fx=document.querySelector('#fx'),pointer=document.querySelector('#pointer');
function point(s){
 let lo=0,hi=trace.length;while(lo<hi){const m=(lo+hi)>>1;if(trace[m].t<=s)lo=m+1;else hi=m;}
 const a=trace[Math.max(0,lo-1)],b=trace[Math.min(trace.length-1,lo)];
 const f=b.t-a.t>0.12||b.t===a.t?0:Math.max(0,Math.min(1,(s-a.t)/(b.t-a.t)));
 return {x:a.x+(b.x-a.x)*f,y:a.y+(b.y-a.y)*f};
}
window.film={width:1920,height:1080,duration:7,frame(t){
 const source=sourceIn+t*speed,p=point(source);let drawing='';
 // Short trail samples actual input; source speed follows the footage cut.
 let length=0;
 for(let k=1;k<=14;k++){
  const a=point(source-k*.009*speed),b=point(source-(k-1)*.009*speed),segment=Math.hypot(a.x-b.x,a.y-b.y);
  length+=segment;if(length>190)break;if(segment<.3)continue;
  const fade=(1-k/15)*(1-length/230);
  drawing+='<line x1="'+a.x+'" y1="'+a.y+'" x2="'+b.x+'" y2="'+b.y+'" stroke="#7aedbb" stroke-opacity="'+(fade*.78)+'" stroke-width="'+(3+fade*3)+'" stroke-linecap="round"/>';
 }
 for(const e of trace){if(e.event!=='down')continue;const age=(source-e.t)/speed;
  if(age<0||age>.46)continue;const f=age/.46;
  drawing+='<circle cx="'+e.x+'" cy="'+e.y+'" r="'+(12+f*42)+'" fill="none" stroke="#8effba" stroke-opacity="'+(1-f)+'" stroke-width="'+(4*(1-f)+1)+'"/><circle cx="'+e.x+'" cy="'+e.y+'" r="13" fill="#8effba" fill-opacity="'+(.2*(1-f))+'"/>';
 }
 fx.innerHTML=drawing;pointer.style.left=(p.x-4)+'px';pointer.style.top=(p.y-4)+'px';
}};
</script></body></html>`);
if (refreshCursor) { console.log('Cursor effect refreshed; film and titles preserved.'); process.exit(0); }

const icons = [['world','Crafting_Table.png'],['preview','Iron_Ingot.png'],['ending','Writable_Book.png']];
for (const [page,icon] of icons) {
 const file=path.join(dir,page+'.html');
 let html=fs.readFileSync(file,'utf8');
 html=html.replace('<div class="message">', `<div class="message"><img class="topic-icon" src="assets/honghe/${icon}" alt="">`);
 fs.writeFileSync(file,html,'utf8');
}
fs.appendFileSync(path.join(dir,'identity.css'), `
.message{display:flex;align-items:center;gap:18px;min-height:78px}
.topic-icon{width:54px;height:54px;object-fit:contain;image-rendering:pixelated;flex:none}
`, 'utf8');
film=film.replace('<!-- VJCE_GENERATED_SAMPLE -->',`<!-- VJCE_GENERATED_SAMPLE --><!-- VJCE_REVISION_2 -->
<section>
 <video id="honghe-copper-transition" src="assets/honghe/146-copper-checker-alpha-60.webm#t=0,2.48" at="3.61" speed="2.48" muted></video>
</section>
<section><iframe id="input-cursor-effects" src="cursor.html#t=0,7" at="8"></iframe></section>`);
film=film.replace(/(id="world-capture"[^>]*speed=")[^"]+/, '$1'+(Math.ceil(cuts.selectionSpeed*1e12)/1e12));
write('film.html',film);
console.log('Revision effects and semantic icon plates written:', dir);
