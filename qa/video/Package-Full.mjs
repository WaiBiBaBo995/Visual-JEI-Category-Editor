import fs from 'node:fs';
import path from 'node:path';
import {execFileSync} from 'node:child_process';
import crypto from 'node:crypto';
const root=process.cwd(),dir=path.resolve('video-production/full');
const read=f=>fs.readFileSync(path.join(dir,f),'utf8').replace(/^\uFEFF/,'');
const write=(f,s)=>fs.writeFileSync(path.join(dir,f),s,'utf8');
let manifest=read('honghe-素材来源.md');
manifest=manifest.replace('# VJCE 样片修订版素材','# VJCE 完整介绍片素材来源')
 .replace('25 秒样片','4 分 40.7 秒完整介绍片')
 .replace('4–8 秒，世界搭建','50.3–55.1 / 112.8–117.6 秒，新建分类与世界框选标题')
 .replace('15–19 秒，材料与结构','70.3–75.1 / 161.9–166.7 秒，材料与预览标题')
 .replace('19–25 秒，整合包说明页','12–16.8 / 225.9–230.7 / 263.7–268.5 秒标题；231.9–241.9 秒导出说明卡')
 .replaceAll('`originals/Crafting_Table.png`','`assets/honghe/Crafting_Table.png`')
 .replaceAll('`originals/Iron_Ingot.png`','`assets/honghe/Iron_Ingot.png`')
 .replaceAll('`originals/Writable_Book.png`','`assets/honghe/Writable_Book.png`')
 .replace('目标：一次约 1 秒的横屏转场，在约 4 秒切换成品和世界实录。','正片用途：两次各约 1 秒的横屏转场，连接新建分类、世界框选两段。')
 .replace('OpenFilm：3.61 秒起，源片 0–2.48 秒，以 `speed=2.48` 播放；底层在 4 秒切镜。','OpenFilm：49.91 秒、112.41 秒起，各取源片 0–2.48 秒，以 `speed=2.48` 播放；底层分别在 50.3 秒、112.8 秒切镜。')
 .replace('正式工程素材副本位于 `video-production/sample-v2/assets/honghe/`，原件与来源信息保留在本目录。','正式工程素材副本位于本工程 `assets/honghe/`；源原件与中间转换文件保留在工作区 `outputs/honghemc-assets/`。工程包内另附 `sources/honghe-originals/` 的站点原件，避免把绿幕原件误用为透明成品。');
write('honghe-素材来源.md',manifest);
const scenes=JSON.parse(read('narration.json'));
const duration=JSON.parse(read('cuts.json')).duration;
const chapterTime=x=>`${String(Math.floor(x/60)).padStart(2,'0')}:${String(Math.floor(x%60)).padStart(2,'0')}`;
const chapters=['成品展示','为什么做这个工具','已有页面改造','新建结构说明页','材料、数量与角色','画布排版','世界框选导入','独立场景快照','播放预览','方块与实体资源','Create 动画预设','保存与实际 JEI','导出整合包配置','运行环境与能力范围','从示例工程开始'];
write('B站发布文案.md',`# 投稿标题\n\n我做了个模组，把世界里的结构放进了 JEI｜VJCE 可视化编辑器\n\n## 简介\n\n我做了一个游戏内的 JEI 分类可视化编辑器：VJCE。\n\n可以重新排版能解析的 JEI 页面，也可以制作自己的展示分类，把世界中框选的结构、方块和实体放进说明页。\n\n模组下载：【发布前填写真实链接】\n同款示例工程：【发布前填写真实链接；本次交付已附 ZIP】\n使用说明与反馈：【发布前填写真实入口】\n\n本视频基于 VJCE 0.11.0。\n环境：Minecraft 1.21.1 / NeoForge 21.1.262。\n必需前置：LDLib2 2.2.44、JEI 19.70.0.464。\n工业案例使用 Mekanism 10.7.19.85；机械预设拍摄使用 Create 6.0.10。\n实际版本兼容范围以模组发布页为准。\n\nVJCE 编辑的是 JEI 展示，实际加工规则由原模组或整合包决定；第三方分类的编辑范围取决于适配情况。\n\n${scenes.map((s,i)=>`${chapterTime(s.start)} ${chapters[i]}`).join('\n')}\n\n模组与示例：pureneko\n配音：Hazel / GPT-SoVITS v5dev\n部分图标与转场：红河素材站 https://honghemc.cn/\n\n## 置顶评论\n\n模组与视频同款示例工程：【发布前填写真实入口】\n\n第一次可以先打开示例工程，改标题和材料，再把自己的结构框选进去。同款加工站示例需要 Mekanism。保存并应用后，退出并重新进入世界查看实际 JEI。\n\n## 投稿文件\n\n视频：VJCE-模组介绍-完整版.mp4（1920×1080 / 60fps / ${duration} 秒，字幕已内嵌）\n封面：VJCE-B站封面.png（1920×1080）\n\n尚未提供公开下载地址，因此成片没有声称链接已上线，也没有将占位链接烧进画面。\n`);
fs.mkdirSync(path.join(dir,'example'),{recursive:true});
fs.copyFileSync('video-production/game/exports/vjce-modpack.zip',path.join(dir,'example/VJCE-视频同款示例.zip'));
fs.copyFileSync('video-production/game/config/vjce/projects/processing-station.vjce',path.join(dir,'example/processing-station.vjce'));
write('example/使用说明.txt',`VJCE 视频同款示例：加工站搭建参考\n\nMinecraft 1.21.1 / NeoForge，安装 VJCE 0.11.0、LDLib2 2.2.44、JEI 19.70.0.464 和 Mekanism 10.7.19.85。\n\n把 VJCE-视频同款示例.zip 解压到独立 Minecraft 实例根目录，保留 config 和 ldlib2 目录层级。进入世界后，使用 JEI 查看“加工站搭建参考”。\n\n编辑器可打开 processing-station.vjce，或 config/vjce/projects/ 下的同名工程；修改后保存并应用，退出并重新进入世界检查实际 JEI。\n\n结构为独立场景快照，共 42 个方块。材料：磨制深板岩35、富集仓1、冶炼炉1、基础流体储罐2、磨制黑石砖3，角色均为 RENDER_ONLY（仅展示）。它是搭建参考，没有新增机器加工规则。\n\nZIP 另外含录制资源拖入镜头时建立的石头与猪资源，两项都在 ldlib2/assets 内。没有附第三方模组 JAR。Create 预设演示需要 Create，本加工站工程不依赖 Create。\n`);
const receipts=JSON.parse(read('voice-receipts.json'));
const totalBytes=receipts.reduce((sum,r)=>sum+r.bytes,0);
write('制作记录.md',`# VJCE 完整介绍片制作记录\n\n成片：${duration} 秒（4:40.7），1920×1080，60fps。原定约4分30秒，完整旁白与自然停顿后延长10.7秒，仍在4–5分钟范围内。\n\n## 配音\n\n逻辑音色 hazel_zh，用户已确认拥有参考录音使用授权。GPT 与 SoVITS 模型都为 Hazel v5dev：\n\n- GPT：GPT_weights_v5dev/hazel-e15.ckpt\n- SoVITS：SoVITS_weights_v5dev/hazel_e2_s220_l32.pth\n\n45句合成均已完成，原始输出合计 ${totalBytes.toLocaleString('en-US')} 字节，MIME 为 audio/wav。具体返回路径、字节数和job ID见 voice-receipts.json；原始输出保留在本机MCP输出目录。15段组装配音是48kHz、16bit、单声道PCM WAV，句间80ms空白。时间线 speed=0.85；VTT依据逐句真实音频时长，OpenFilm保留45条完整句子，未用平均字数估算时间。\n\n## 剪辑\n\n实录采用独立拍摄实例；不修改玩家日常存档。编辑、拖动、导入、应用、重启后实际JEI、导出均来自真实产品。待机被裁去，重复输入与部分操作加速；预览与成品主要保持原速。\n\n鼠标放大、短淡出轨迹与点击圆环是后期特效，来自实际输入日志，随每段源片 trim/speed 同步。世界录像按60fps两次字段变化校准lead=1.677秒；作者录制校准lead=1.730秒，其余take近似1.677秒。\n\n标题进出各0.8秒、静止3.2秒，两端缓起缓停；按实际标题宽度移出，右上角不显示倍速。背景音乐为本地程序生成的原创合成音轨。配音与背景混音测得约-16.3 LUFS、-1.3dBTP。\n\n## 事实验证\n\n有效take的LDLib2场景报告均PASS；画布镜头额外检查真实标题移动、正确选中、实际宽度变化；原版熔炼镜头检查真实slot移动；世界快照检查原世界对应位置已为空、快照仍保留42方块；资源拖入检查实际石头与猪对象；Create三种预设做过代表帧差对照。\n\n分类尺寸330×208；数量35/1/1/2/3，材料均为RENDER_ONLY。真实客户端重新启动后已注册分类并由正常JEI展示；最终导出ZIP中确有分类JSON、可编辑工程、资源及README，文件清单见export-contents.json。\n\n## 实际运行环境\n\nVJCE 0.11.0 / Minecraft 1.21.1 / NeoForge 21.1.262 / LDLib2 2.2.44 / JEI 19.70.0.464 / Mekanism 10.7.19.85。Create拍摄使用本机6.0.10；未声称README表中的6.0.11-320是本次拍摄版本。Create JAR SHA256：ef87fe5709f1ba1f5b8bb20a2925b5afb4669e178fd6d8bf10c167759eefe37a。\n\n红河图标与转场来源、原件与抠像参数见honghe-素材来源.md。公开下载链接尚未提供，投稿文案保留明确占位，成片没有虚构地址。\n`);
write('README.md',`# VJCE 完整介绍片 · OpenFilm 工程\n\n打开本目录的 film.html 所在文件夹即可在 OpenFilm Studio 继续编辑。\n\n成片4:40.7，1920×1080 / 60fps，Hazel中文配音，字幕已内嵌。字幕源在assets/audio/vo/*.vtt，配音在同名WAV。每条cue是完整句子，请保留NOTE标记，避免自动重新切分。\n\n- film.html：36个初始剪辑镜头经节奏调整后的39个实录镜头，分轨保存标题、注释、鼠标、画面、配音、音乐。\n- title-S*.html / title-motion.js：标题0.8秒入场、3.2秒静止、0.8秒收回。\n- *-cursor.html / *-cursor.js：可编辑的输入特效与实际轨迹。\n- assets/video：原始完整录像；每个片段只用#t裁切、speed调速和CSS构图。\n- cover.html / assets/images：真实录像截图制作的封面源。\n- example：同款加工站ZIP、工程及使用说明。\n- B站发布文案.md：与成片时间对应的标题、简介、章节时间点与置顶评论草稿；发布前填写真实链接。\n- 制作记录.md / cuts.json / caption-check.json：制作参数、来源和字幕核对。\n\n复现导出：在本文件夹运行 openfilm render --fps 60 --subtitles --workers 3。低内存机器可改为--workers 1。\n\n本工程不附第三方模组JAR；站点素材原件另存sources，剪辑采用assets下的透明派生。详细来源见honghe-素材来源.md。\n`);
// Create an editable delivery manifest following local HTML/CSS/JS dependencies.
// This excludes failed/unused takes, old renders, and all OpenFilm tool-owned folders.
const files=new Set(),visit=rel=>{
 rel=rel.replaceAll('\\','/');if(files.has(rel)||rel.startsWith('.')||rel.startsWith('../'))return;
 const abs=path.join(dir,rel);if(!fs.existsSync(abs)||!fs.statSync(abs).isFile())throw new Error('Missing '+rel);
 files.add(rel);
 if(/\.(html|css|js)$/.test(rel)){
  const text=fs.readFileSync(abs,'utf8');
  for(const m of text.matchAll(/(?:src|href)="([^"#]+)(?:#[^"]*)?"/g))if(!/^[a-z]+:/i.test(m[1]))visit(path.posix.normalize(path.posix.join(path.posix.dirname(rel),m[1])));
 }
 if(rel.endsWith('.wav')&&fs.existsSync(abs.slice(0,-4)+'.vtt'))visit(rel.slice(0,-4)+'.vtt');
};
visit('film.html');if(fs.existsSync(path.join(dir,'cover.html')))visit('cover.html');
for(const f of ['README.md','制作记录.md','B站发布文案.md','配音稿.txt','narration.json','voice-receipts.json','honghe-素材来源.md','cuts.json','caption-check.json','export-contents.json','cursor-engine.txt','example/VJCE-视频同款示例.zip','example/processing-station.vjce','example/使用说明.txt'])visit(f);
for(const f of ['Crafting_Table.png','Iron_Ingot.png','Writable_Book.png','146-copper-checker.webm']){
 const target=path.join(dir,'sources/honghe-originals',f);fs.mkdirSync(path.dirname(target),{recursive:true});fs.copyFileSync(path.join(root,'outputs/honghemc-assets/originals',f),target);files.add('sources/honghe-originals/'+f);
}
write('delivery-files.json',JSON.stringify([...files].sort(),null,2));
// The ZIP is created from this explicit allowlist by the companion PowerShell step.
const hashes=Object.fromEntries([...files].sort().map(f=>[f,crypto.createHash('sha256').update(fs.readFileSync(path.join(dir,f))).digest('hex')]));
write('sha256-files.json',JSON.stringify(hashes,null,2)+'\n');
console.log(JSON.stringify({files:files.size,voiceOriginalBytes:totalBytes,duration,usedFootage:[...files].filter(f=>f.startsWith('assets/video/'))},null,2));
