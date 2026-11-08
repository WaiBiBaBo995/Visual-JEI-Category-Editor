import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';

const dir=path.resolve('video-production/full');
const read=f=>fs.readFileSync(f,'utf8').replace(/^\uFEFF/,'');
const json=f=>JSON.parse(read(f));
const write=(f,s)=>fs.writeFileSync(path.join(dir,f),s,'utf8');
const filmPath=path.join(dir,'film.html');
// A fresh edit only. Never overwrite Studio edits or tool-owned files.
if(fs.existsSync(filmPath)){
 const current=read(filmPath);
 if(/<section|locked|overrides=|VJCE_FULL/.test(current))throw new Error('Full film already exists; read it and make targeted edits.');
}
const scenes=json(path.join(dir,'narration.json'));
const takes={};
for(const phase of ['world','authoring','native','resources','create','publish','showcase-full']){
 const names=fs.readdirSync('build/video-takes').filter(n=>n.startsWith(phase+'-')).sort().reverse();
 const name=names.find(n=>fs.existsSync(`build/video-takes/${n}/take.json`)&&/status\s*:\s*PASS/.test(read(`build/video-takes/${n}/report.txt`)));
 if(!name)throw new Error(`No passing ${phase} take`);
 const root=path.resolve('build/video-takes',name),r=json(path.join(root,'take.json'));
 const lead=phase==='authoring'?1.73:1.677;
 const markers=Object.fromEntries(fs.readdirSync(root).filter(f=>f.endsWith('.json')&&!['take.json','report.json'].includes(f)).map(f=>{const m=json(path.join(root,f));return[f.slice(0,-5),(m.epochMillis-r.recorderReadyEpochMillis)/1000+lead];}));
 const probe=JSON.parse(execFileSync('E:/ffmpeg-master-latest-win64-gpl-shared/bin/ffprobe.exe',['-v','error','-show_entries','format=duration','-of','json',r.media],{encoding:'utf8'}));
 const trace=fs.existsSync(path.join(root,'cursor.jsonl'))?read(path.join(root,'cursor.jsonl')).trim().split(/\r?\n/).map(JSON.parse).map(p=>({t:(p.epochMillis-r.recorderReadyEpochMillis)/1000+lead,x:p.x/p.guiWidth*1920,y:p.y/p.guiHeight*1080,event:p.event})):[];
 takes[phase]={...r,root,lead,markers,trace,duration:Number(probe.format.duration),src:'assets/video/'+path.basename(r.media)};
}
const W=takes.world,A=takes.authoring,N=takes.native,R=takes.resources,C=takes.create,P=takes.publish,H=takes['showcase-full'];
const n=x=>Number(x.toFixed(6)),clips=[],layers=[],cursors=[],cuts=[];
const page=(file,body,script='',css='')=>write(file,`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><link rel="stylesheet" href="identity.css"><link rel="stylesheet" href="cards.css"><style>${css}</style></head><body>${body}<script>${script||'window.film={width:1920,height:1080,frame(t){}};'}</script></body></html>`);
const crop=(scale,left,top)=>`width:${1920*scale}px;height:${1080*scale}px;left:${left}px;top:${top}px`;
const hero=crop(1.25,-240,-160),preview=crop(1.7,-346,-265);
function clip(id,take,a,b,at,length,style='',pointer=false){
 if(a<0||b>take.duration+.01||a>=b)throw new Error(`${id}: outside source ${a}-${b}/${take.duration}`);
 const speed=Math.ceil((b-a)/length*1e12)/1e12;
 clips.push(`<video id="${id}" src="${take.src}#t=${n(a)},${n(b)}" at="${n(at)}" speed="${speed}" ${style?`style="${style}"`:''} muted></video>`);
 cuts.push({id,take:take.take,in:n(a),out:n(b),at:n(at),duration:length,speed,style,pointer});
 if(pointer){
  const data=`window.cursorTake=${JSON.stringify({trace:take.trace,sourceIn:a,speed})};`;
  write(`${id}-cursor.js`,data);
  const sample=read('video-production/sample-v2/cursor.html').replace('src="cursor-data.js"',`src="${id}-cursor.js"`).replace('duration:7','duration:'+length);
  write(`${id}-cursor.html`,sample);
  cursors.push(`<iframe id="${id}-input" src="${id}-cursor.html#t=0,${length}" at="${n(at)}" ${style?`style="${style}"`:''}></iframe>`);
 }
}
function cut(scene,offset,length,id,take,a,b,style='',pointer=false){clip(id,take,a,b,scene.start+offset,length,style,pointer);}
function overlay(id,at,length,body,css='',script=''){page(id+'.html',body,script,css);layers.push(`<iframe id="${id}" src="${id}.html#t=0,${length}" at="${at}"></iframe>`);}
const topics=[
 ['把世界里的结构，放进 JEI',''],['给整合包，多一种说明方式','Writable_Book.png'],['先从熟悉的原版页面开始',''],['新建一张结构说明页','Crafting_Table.png'],['材料、数量与「仅展示」','Iron_Ingot.png'],['在画布里整理信息',''],['世界框选，一次带进来','Crafting_Table.png'],['保存一份独立场景快照',''],['把结构、材料和说明放在一起','Iron_Ingot.png'],['从资源库，拖入方块和实体',''],['Create：三种机械动画预设',''],['保存、应用，再看真正的 JEI',''],['导出这份整合包配置','Writable_Book.png'],['开始之前，确认运行环境',''],['从这份示例工程开始','Writable_Book.png']
];
for(let i=0;i<scenes.length;i++){
 const s=scenes[i],[title,icon]=topics[i];
 write(`title-${s.id}.html`,`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><link rel="stylesheet" href="identity.css"></head><body data-title-hold="3.2"><div class="title-popover"><div class="name"><b>VJCE</b> JEI 分类可视化编辑器</div><div class="message">${icon?`<img class="topic-icon" src="assets/honghe/${icon}" alt="">`:''}${title}</div></div><script src="title-motion.js"></script></body></html>`);
}
write('cards.css',`body{overflow:hidden} .chip{position:absolute;left:64px;bottom:162px;background:#17231eea;border-left:4px solid #86c66b;padding:12px 20px;font-size:23px;color:#eff3e9}.chip small{color:#b2c6b1;margin-left:16px}.panel{position:absolute;inset:0;background:#17201cf5;background-image:linear-gradient(#9bbb9710 1px,transparent 1px),linear-gradient(90deg,#9bbb9710 1px,transparent 1px);background-size:56px 56px;padding:210px 110px 130px}.eyebrow{font-size:22px;letter-spacing:5px;color:#92be7b;margin-bottom:20px}h1{font-size:62px;line-height:1.25;margin:0 0 28px;font-weight:750}h2{font-size:38px;margin:0 0 24px}p{font-size:27px;line-height:1.65;margin:10px 0;color:#c7d3c6}.rule{height:3px;width:90px;background:#87bf69;margin:32px 0}.rows{display:grid;gap:18px}.row{display:flex;gap:28px;padding:16px 0;border-bottom:1px solid #c1d1bc2b;font-size:27px}.row b{color:#a4c98d;min-width:100px}.mono{font-family:Consolas,monospace;font-size:24px}.muted{color:#b1beb0}.cardIcon{width:65px;height:65px;image-rendering:pixelated;margin-right:22px;vertical-align:middle}`);

// Each cut points at its original, unmodified real product recording.
cut(scenes[0],0,12,'hero-opening',H,H.markers.jei+.5,H.markers.jei+12.5,hero);
cut(scenes[1],0,7,'why-world',W,W.markers.world+.2,W.markers.world+7.2);
cut(scenes[1],7,4.5,'why-original-page',N,N.markers.after+1,N.markers.after+5.5,preview);
cut(scenes[1],11.5,7.4,'introduce-vjce',H,H.markers.jei+14,H.markers.jei+21.4,hero);
overlay('name-reveal',scenes[1].start+11.5,7.4,'<div class="brand"><div class="eyebrow">pureneko / Minecraft 1.21.1</div><h1>Visual JEI<br>Category Editor</h1><div class="rule"></div><p>JEI 分类可视化编辑器</p></div>',`.brand{position:absolute;left:64px;top:250px;padding:40px;background:#18231ef2;width:650px} .brand h1{font-size:46px}`,`const b=document.querySelector('.brand');window.film={width:1920,height:1080,frame(t){const f=Math.min(1,t/.8);b.style.opacity=f;b.style.transform='translateY('+(24*(1-f))+'px)';}};`);
cut(scenes[2],0,4.4,'native-before',N,N.markers.before+.25,N.markers.before+4.65);
cut(scenes[2],4.4,8,'native-real-drag',N,N.markers.before+4.65,N.markers.edit+3.3,'',true);
cut(scenes[2],12.4,7,'native-after',N,N.markers.after+.4,N.markers.after+7.4,preview);
overlay('native-before-label',scenes[2].start,4.4,'<div class="chip">读取原版熔炼分类<small>编辑器中的原生布局</small></div>');
overlay('native-after-label',scenes[2].start+12.4,7,'<div class="chip">输入槽已移动<small>VJCE 预览 · 原配方内容保留</small></div>');
cut(scenes[3],0,5,'new-category',A,A.markers.library+.3,A.markers.new+1,'',true);
cut(scenes[3],5,10,'category-fields',A,A.markers.new+1,A.markers.category+.1,'',true);
cut(scenes[3],15,5,'category-settings-result',A,A.markers.category+.1,A.markers.category+4.8);
cut(scenes[4],0,5,'material-add-slot',A,A.markers.slot-3.5,A.markers.slot+1.5,'',true);
cut(scenes[4],5,7,'ingredient-search',A,A.markers.slot+1.5,A.markers.search+1.5,'',true);
cut(scenes[4],12,5,'material-amount-role',A,A.markers.search+1.5,A.markers.role+.4,'',true);
cut(scenes[4],17,3.5,'material-role-result',A,A.markers.role+.4,A.markers.role+3.9);
cut(scenes[5],0,5,'canvas-overview',A,A.markers.layout+.3,A.markers.layout+3);
cut(scenes[5],5,8,'canvas-title-move',A,A.markers.layout+3,A.markers.resize+.15,'',true);
cut(scenes[5],13,5,'canvas-title-resize',A,A.markers.resize+.15,A.markers['layout-done']+.05,'',true);
cut(scenes[5],18,4,'canvas-clean-layout',A,A.markers['layout-done']+.25,A.markers['layout-done']+4.25);
cut(scenes[6],0,5,'world-structure',W,W.markers.world+.2,W.markers.world+5.2);
cut(scenes[6],5,25,'world-import-actions',W,W.markers.selection-.4,W.markers.preview+1,'',true);
overlay('world-selection-guide',scenes[6].start+7.5,8,'<div class="chip">Shift + 点击两个角点<small>选区：7 × 4 × 5 · 导入 42 个方块</small></div>');
cut(scenes[7],0,8,'snapshot-details',W,W.markers.preview+2,W.markers.preview+10,preview);
cut(scenes[7],8,11.1,'snapshot-after-world-cleared',W,W.markers['snapshot-proof']+.15,W.markers['snapshot-proof']+11.25,preview);
overlay('snapshot-proof-note',scenes[7].start+8,11.1,'<div class="chip">独立快照保留<small>本次录制已清空原选区，保存内容仍为 42 方块</small></div>');
cut(scenes[8],0,15,'completed-preview',W,W.markers.preview+.4,W.markers.preview+15.4,preview);
overlay('preview-label',scenes[8].start+5,10,'<div class="chip">播放预览<small>结构 + 材料 + 搭建说明</small></div>');
cut(scenes[9],0,5.7,'resource-block-drag',R,R.markers.block+1.7,R.markers.block+7.4,'',true);
cut(scenes[9],5.7,7.3,'resource-entity-drag',R,R.markers.entity+1.7,R.markers.placed+1.7,'',true);
cut(scenes[9],13,2,'resource-result',R,R.markers.preview+.5,R.markers.preview+2.5,preview);
cut(scenes[10],0,3.4,'create-press',C,C.markers.press+2,C.markers.press+5.4,preview);
cut(scenes[10],3.4,3.3,'create-mixer',C,C.markers.mixer+2,C.markers.mixer+5.3,preview);
cut(scenes[10],6.7,3.3,'create-wheels',C,C.markers.crushing_wheels+2,C.markers.crushing_wheels+5.3,preview);
for(const [i,label] of [[0,'压机'],[1,'搅拌器'],[2,'粉碎轮']])overlay('create-label-'+i,scenes[10].start+[0,3.4,6.7][i],[3.4,3.3,3.3][i],`<div class="chip">Create · ${label}<small>实际动画预设</small></div>`);
cut(scenes[11],0,6,'project-save',P,P.markers.save+.2,P.markers.save+6.2,'',true);
cut(scenes[11],6,5.5,'category-publish',P,P.markers.published-2.2,P.markers.published+3.3,'',true);
cut(scenes[11],11.5,12.5,'registered-normal-jei',H,H.markers.jei+24,H.markers.jei+36.5,hero);
overlay('restart-evidence',scenes[11].start+11.5,5,'<div class="chip">重新启动客户端后<small>已注册的真实 JEI 页面</small></div>');
cut(scenes[12],0,6,'export-actions',P,P.markers.export-3,P.markers.export+3,'',true);
const contents=json(path.join(dir,'export-contents.json'));
if(!contents.some(e=>e.FullName.startsWith('config/vjce/categories/'))||!contents.some(e=>e.FullName.endsWith('.vjce')))throw new Error('Archive content missing');
overlay('export-contents',scenes[12].start+6,10,`<div class="panel"><div class="eyebrow">实际导出内容 / vjce-modpack.zip</div><h1><img class="cardIcon" src="assets/honghe/Writable_Book.png">一份配置，带进整合包</h1><div class="rule"></div><div class="rows"><div class="row"><b>分类配置</b><span class="mono">config/vjce/categories/</span></div><div class="row"><b>可编辑工程</b><span class="mono">config/vjce/projects/processing-station.vjce</span></div><div class="row"><b>安装说明</b><span class="mono">config/vjce/README.txt</span></div></div><p>使用到的外部资源会一并打包；对应模组仍需安装。</p></div>`);
cut(scenes[13],0,21.8,'environment-jei',H,H.markers.jei+38,H.markers.jei+59.8,hero);
overlay('runtime-card',scenes[13].start,21.8,`<div class="runtime"><div class="eyebrow">VJCE 0.11.0 / pureneko</div><h2>在你的整合包里试一试</h2><div class="rows"><div class="row"><b>Minecraft</b><span>1.21.1 · NeoForge</span></div><div class="row"><b>必需前置</b><span>JEI / LDLib2</span></div></div><div class="rule"></div><p>编辑 JEI 展示<br>实际加工规则由原模组或整合包决定</p><p class="muted">第三方页面的编辑范围取决于适配情况</p><div class="versions">本次录制：NeoForge 21.1.262<br>JEI 19.70.0.464 · LDLib2 2.2.44<br>案例：Mekanism 10.7.19.85 · Create 6.0.10</div></div>`,`.runtime{position:absolute;left:60px;top:225px;width:810px;padding:36px;background:#18231ff5;border-left:5px solid #8bc06e}.runtime p{font-size:24px}.runtime .row{font-size:24px}.versions{font-size:18px;line-height:1.6;color:#9eb59d;margin-top:25px}.runtime .eyebrow{font-size:18px}`);
cut(scenes[14],0,4,'closing-world',W,W.markers.world+4,W.markers.world+8);
cut(scenes[14],4,4,'closing-jei',H,H.markers.jei+9,H.markers.jei+13,hero);
overlay('ending-card',scenes[14].start+8,9,`<div class="panel"><div class="eyebrow">Visual JEI Category Editor</div><h1 style="font-size:110px;letter-spacing:14px">VJCE</h1><h2>从这份示例工程开始，做成自己的页面。</h2><div class="rule"></div><p>JEI 分类可视化编辑器 · Minecraft 1.21.1</p><p style="font-size:21px;margin-top:35px">模组与示例：pureneko &nbsp; / &nbsp; 配音：Hazel · GPT-SoVITS v5dev<br>部分图标与转场：红河素材站 honghemc.cn</p></div>`,`.panel{padding-top:170px}`);
// Modest, brief copper transitions. Source transparency is retained.
const transitions=[scenes[3].start,scenes[6].start].map((t,i)=>`<video id="copper-${i}" src="assets/honghe/146-copper-checker-alpha-60.webm#t=0,2.48" at="${n(t-.39)}" speed="2.48" muted></video>`);
const titles=scenes.map(s=>`<iframe id="title-${s.id}" src="title-${s.id}.html#t=0,${s.duration}" at="${s.start}"></iframe>`);
// Overlays share one track where possible; separate overlapping annotations from scene titles.
const overlayTracks=[];
for(const item of layers){const at=Number(item.match(/at="([\d.]+)"/)[1]),length=Number(item.match(/#t=0,([\d.]+)/)[1]);let tr=overlayTracks.find(tr=>tr.end<=at+.000001);if(!tr){tr={end:0,items:[]};overlayTracks.push(tr);}tr.items.push(item);tr.end=at+length;}
const audio=scenes.map(s=>`<audio id="hazel-${s.id}" src="assets/audio/vo/${s.id}.wav" at="${n(s.start+.35)}" speed="${s.voiceSpeed}" volume="1"></audio>`);
write('film.html',`<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=1920, height=1080"></head><body>\n<!-- VJCE_FULL_REAL_PRODUCT_EDIT -->\n<section>${transitions.join('\n')}</section>\n<section>${titles.join('\n')}</section>\n${overlayTracks.map(tr=>`<section>${tr.items.join('\n')}</section>`).join('\n')}\n<section>${cursors.join('\n')}</section>\n<section>${clips.join('\n')}</section>\n<section>${audio.join('\n')}</section>\n<section><audio id="original-music-bed" src="assets/audio/full-bed.wav" volume="0.55"></audio></section>\n</body></html>`);
write('cuts.json',JSON.stringify({duration:280.7,cuts,takes:Object.fromEntries(Object.entries(takes).map(([k,{trace,...r}])=>[k,r]))},null,2)+'\n');
write('cursor-engine.txt','Cursor effects derive from actual input logs. Authoring lead: 1.730s; world: 1.677s (two 60fps field changes); other phases: approx 1.677s. Pointer, short fading trail and click rings share the source trim and speed of each recording.\n');
console.log(JSON.stringify({scenes:scenes.length,cuts:cuts.length,sourceTakes:Object.keys(takes),overlayTracks:overlayTracks.length,duration:280.7},null,2));
