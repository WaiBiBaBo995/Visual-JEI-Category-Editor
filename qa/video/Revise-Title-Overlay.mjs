import fs from 'node:fs';
import path from 'node:path';

const dir = path.resolve(process.argv[2] || 'video-production/sample-v2');
// Only title pages change; film.html, footage speed, and other Studio tracks stay as read.
const pages = ['opening', 'world', 'capture', 'preview', 'ending'];
const backup = path.resolve('build/video-tools/title-backup-' + Date.now());
fs.mkdirSync(backup, {recursive:true});
const previousMotion=path.join(dir,'title-motion.js');
if(fs.existsSync(previousMotion))fs.copyFileSync(previousMotion,path.join(backup,'title-motion.js'));
for (const name of pages) {
  const file = path.join(dir, `${name}.html`);
  let html = fs.readFileSync(file, 'utf8');
  fs.writeFileSync(path.join(backup, `${name}.html`), html);
  if (!html.includes('class="title-popover"')) {
    html = html.replace(/(<div class="name">.*?<\/div><div class="message">.*?<\/div>)/s,
      '<div class="title-popover">$1</div>');
  }
  if (name === 'capture') html = html.replace(/<div class="note">[^<]*(?:加速|倍速)[^<]*<\/div>/, '');
  html = html.replace(/<script>window\.film=.*?<\/script>/s, '<script src="title-motion.js"></script>');
  const holdSeconds=(name==='capture'||name==='ending')?3.2:2.4;
  html = html.replace(/<body(?:\s+data-title-hold="[^"]*")?>/, `<body data-title-hold="${holdSeconds}">`);
  fs.writeFileSync(file, html, 'utf8');
}
const cssFile=path.join(dir,'identity.css');
const css=fs.readFileSync(cssFile,'utf8');
fs.writeFileSync(path.join(backup,'identity.css'),css);
if(!css.includes('.title-popover{')) fs.appendFileSync(cssFile, `
body{overflow:hidden}
.title-popover{position:absolute;left:0;top:0;width:1100px;height:220px;opacity:0;pointer-events:none;will-change:transform,opacity}
`);
fs.writeFileSync(path.join(dir,'title-motion.js'), `// Title timing: 0.8s in, 2.4s/3.2s hold, 0.8s out. Positions use actual content width.
const panel=document.querySelector('.title-popover');
const clamp=x=>Math.max(0,Math.min(1,x));
const smooth=x=>{const p=clamp(x);return p*p*(3-2*p);};
const inSeconds=.8,outSeconds=.8,holdSeconds=Number(document.body.dataset.titleHold||2.4);
window.film={width:1920,height:1080,ready:document.fonts.ready,frame(t){
 const enter=smooth(t/inSeconds),exit=smooth((t-inSeconds-holdSeconds)/outSeconds);
 const show=enter*(1-exit);
 // offsetLeft/offsetWidth ignore the previous transform, so shuffled frame requests stay deterministic.
 const travel=Math.max(...Array.from(panel.children,e=>e.offsetLeft+e.offsetWidth))+24;
 panel.style.transform='translateX('+(-travel*(1-show))+'px)';
 panel.style.opacity=String(show);
 panel.style.visibility=t>=inSeconds+holdSeconds+outSeconds?'hidden':'visible';
}};
`, 'utf8');
console.log('Titles: 0.8s in/out, 2.4s short / 3.2s long hold, measured travel. Backup:',backup);
