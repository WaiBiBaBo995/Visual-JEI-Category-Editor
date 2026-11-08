// Original quiet instrumental bed, made from oscillators; no third-party recordings.
import { writeFileSync, mkdirSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
const rate=44100, seconds=Number(process.argv[2]||25), frames=Math.round(rate*seconds), beat=60/102;
const chords=[[57,60,64,67],[53,57,60,64],[48,55,59,64],[55,57,62,67]];
const hz=n=>440*2**((n-69)/12);
const data=Buffer.alloc(44+frames*4);
data.write('RIFF',0);data.writeUInt32LE(data.length-8,4);data.write('WAVEfmt ',8);
data.writeUInt32LE(16,16);data.writeUInt16LE(1,20);data.writeUInt16LE(2,22);
data.writeUInt32LE(rate,24);data.writeUInt32LE(rate*4,28);data.writeUInt16LE(4,32);data.writeUInt16LE(16,34);
data.write('data',36);data.writeUInt32LE(frames*4,40);
for(let i=0;i<frames;i++){
  const t=i/rate, phrase=Math.floor(t/(8*beat)), notes=chords[phrase%chords.length];
  const u=t%(8*beat), gate=Math.min(1,u/.4)*Math.min(1,(8*beat-u)/.65);
  let pad=0;
  for(const note of notes)pad+=Math.sin(2*Math.PI*hz(note)*t)*.017;
  pad*=gate;
  const pulse=t%(beat/2), step=Math.floor(t/(beat/2));
  const pluck=Math.sin(2*Math.PI*hz(notes[step%4]+12)*pulse)*Math.exp(-pulse*15)*.018;
  const b=t%(2*beat), kick=Math.sin(2*Math.PI*(46*b+3*(1-Math.exp(-b*35))))*Math.exp(-b*22)*.075;
  const fade=Math.min(1,t/1.1)*Math.min(1,(seconds-t)/1.5);
  const lift=t>seconds-6?1.3:1;
  const value=(pad+pluck+kick)*fade*lift;
  data.writeInt16LE(Math.round(Math.max(-1,Math.min(1,value))*32767),44+i*4);
  data.writeInt16LE(Math.round(Math.max(-1,Math.min(1,value+pad*.06*Math.sin(t*1.3)))*32767),46+i*4);
}
const output=resolve(process.argv[3]||'video-production/sample/assets/audio/sample-bed.wav');
mkdirSync(dirname(output),{recursive:true});
writeFileSync(output,data);
process.stdout.write(output+'\n');
