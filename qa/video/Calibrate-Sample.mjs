import fs from 'node:fs';
import path from 'node:path';
const dir=path.resolve(process.argv[2]);
const lead=Number(process.argv[3]);
if(!Number.isFinite(lead))throw new Error('Pass the measured capture lead-in in seconds.');
const cutsPath=path.join(dir,'cuts.json'),filmPath=path.join(dir,'film.html');
const cuts=JSON.parse(fs.readFileSync(cutsPath,'utf8'));
const delta=lead-(cuts.captureLeadIn??2);
// Preserve Studio edits; only source trim times of the selected capture recording change.
let film=fs.readFileSync(filmPath,'utf8');
if(/\blocked\b/.test(film))throw new Error('Review locked tracks before calibration.');
const captureName=path.basename(cuts.capture.media);
const num=x=>Number(x.toFixed(6));
film=film.replace(/src="(assets\/video\/[^"#]+)#t=([\d.]+),([\d.]+)"/g,(all,src,a,b)=>
 src.endsWith('/'+captureName)?`src="${src}#t=${num(Number(a)+delta)},${num(Number(b)+delta)}"`:all);
for(const key of ['worldIn','selectionIn','selectionEnd','previewIn'])cuts[key]=num(cuts[key]+delta);
cuts.captureLeadIn=lead;
cuts.calibration={method:'Actual 60fps corner-field changes vs recorded input events',
 firstCornerSourceSecond:18.1,secondCornerSourceSecond:21.033333,
 measuredLeadIn:1.64,maxResidualSeconds:0.006};
fs.writeFileSync(filmPath,film,'utf8');
fs.writeFileSync(cutsPath,JSON.stringify(cuts,null,2)+'\n','utf8');
console.log('Capture source trims shifted by',delta,'seconds. Refresh cursor effects next.');
