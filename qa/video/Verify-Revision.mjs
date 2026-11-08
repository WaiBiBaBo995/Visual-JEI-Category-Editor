import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

const dir=path.resolve(process.argv[2] || 'video-production/sample-v2');
const captionsModule=await import(pathToFileURL('E:/OpenFilm/resources/app.asar.unpacked/node_modules/openfilm/studio/server/captions.mjs'));
const captions=await captionsModule.filmCaptions(dir,{duration:async src=>src.includes('sample-hazel')?16.44:25});
assert.equal(captions.cues.length,4,'OpenFilm must preserve four complete sentences');
assert.ok(captions.cues[3].text.includes('JEI 分类可视化编辑器，VJCE'));
assert.equal(captions.untranscribed.length,0);
const cuts=JSON.parse(fs.readFileSync(path.join(dir,'cuts.json'),'utf8'));
const events=fs.readFileSync(path.join(cuts.capture.markers,'cursor.jsonl'),'utf8').trim().split(/\r?\n/).map(JSON.parse);
assert.ok(events.every((e,i)=>Number.isFinite(e.x)&&Number.isFinite(e.y)&&e.guiWidth>0&&(!i||e.epochMillis>=events[i-1].epochMillis)));
const clicks=events.filter(e=>e.event==='down').map(e=>({...e,source:(e.epochMillis-cuts.capture.recorderReadyEpochMillis)/1000+(cuts.captureLeadIn??2)})).filter(e=>e.source>=cuts.selectionIn&&e.source<cuts.selectionEnd);
assert.equal(clicks.length,4,'Two real corner selections, import, and apply must be represented');
console.log(JSON.stringify({captions:captions.cues.map(c=>({text:c.text,startMs:c.startMs,endMs:c.endMs})),traceEvents:events.length,visibleClicks:clicks.length},null,2));
