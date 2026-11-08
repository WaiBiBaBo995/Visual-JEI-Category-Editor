import fs from 'node:fs';
import path from 'node:path';
const dir='video-production/full';
const scenes=JSON.parse(fs.readFileSync(path.join(dir,'narration.json'),'utf8'));
const receipts=JSON.parse(fs.readFileSync(path.join(dir,'voice-receipts.json'),'utf8'));
function readWav(file){const b=fs.readFileSync(file);let fmt,data;for(let p=12;p+8<=b.length;){const name=b.toString('ascii',p,p+4),n=b.readUInt32LE(p+4);if(name==='fmt ')fmt=b.subarray(p+8,p+8+n);if(name==='data')data=b.subarray(p+8,p+8+n);p+=8+n+(n%2);}if(!fmt||!data||fmt.readUInt16LE(0)!==1)throw new Error('Expected PCM WAV');return{fmt,data,rate:fmt.readUInt32LE(4),bytesPerSecond:fmt.readUInt32LE(8)};}
function wav(fmt,data){const h=Buffer.alloc(44);h.write('RIFF');h.writeUInt32LE(data.length+36,4);h.write('WAVEfmt ',8);h.writeUInt32LE(16,16);fmt.copy(h,20,0,16);h.write('data',36);h.writeUInt32LE(data.length,40);return Buffer.concat([h,data]);}
const stamp=s=>{const ms=Math.round(s*1000);return String(Math.floor(ms/3600000)).padStart(2,'0')+':'+String(Math.floor(ms/60000)%60).padStart(2,'0')+':'+String(Math.floor(ms/1000)%60).padStart(2,'0')+'.'+String(ms%1000).padStart(3,'0');};
let start=0;
for(const scene of scenes){
 const parts=[],cues=[];let pos=0,format;
 for(let i=0;i<scene.sentences.length;i++){
  const receipt=receipts.find(r=>r.scene===scene.id&&r.index===i);if(!receipt)throw new Error('Missing '+scene.id+'/'+i);
  const clip=readWav(receipt.path);format??=clip.fmt;
  if(!format.equals(clip.fmt))throw new Error('Inconsistent WAV format');
  const duration=clip.data.length/clip.bytesPerSecond;parts.push(clip.data);
  cues.push({start:pos,end:pos+duration,text:scene.sentences[i]});pos+=duration;
  if(i<scene.sentences.length-1){const pause=Buffer.alloc(Math.round(.08*clip.bytesPerSecond/2)*2);parts.push(pause);pos+=pause.length/clip.bytesPerSecond;}
 }
 scene.audioDuration=pos;scene.voiceSpeed=.85;
 scene.duration=Math.max(scene.duration,Math.ceil((pos/.85+1.2)*10)/10);
 scene.start=start;start+=scene.duration;
 fs.writeFileSync(path.join(dir,'assets/audio/vo',scene.id+'.wav'),wav(format,Buffer.concat(parts)));
 const vtt='WEBVTT\n\nNOTE Each cue is one subtitle, as cut by hand in OpenFilm Studio.\n\n'+cues.map(c=>stamp(c.start)+' --> '+stamp(c.end)+'\n'+c.text+'\n').join('\n');
 fs.writeFileSync(path.join(dir,'assets/audio/vo',scene.id+'.vtt'),vtt);
}
fs.writeFileSync(path.join(dir,'narration.json'),JSON.stringify(scenes,null,2));
console.log(JSON.stringify({duration:start,scenes:scenes.map(s=>({id:s.id,start:s.start,duration:s.duration,audio:s.audioDuration}))},null,2));
