/* Local-only dedicated-server integration. No Minecraft client mod is used. */
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const root = path.resolve(__dirname, '../..');
const modVersion=JSON.parse(fs.readFileSync(path.join(root,'build/resources/main/fabric.mod.json'))).version;
const output = path.join(root, 'build/module-failure-integration');
const runDir = path.join(output, 'run');
fs.mkdirSync(output, {recursive:true});
const results = [];
const wait = ms => new Promise(r=>setTimeout(r,ms));
function check(name, fn) { fn(); results.push(name); console.log('PASS',name); }
let server, logs='', children=[];
const production=true;
function javaProxy(){
  if(process.env.SHIROKAI_JAVA_PROXY!=='1')return [];
  const proxy=new URL(process.env.HTTPS_PROXY||process.env.https_proxy);
  return ['-Dhttps.proxyHost='+proxy.hostname,'-Dhttps.proxyPort='+(proxy.port||80),'-Dhttp.proxyHost='+proxy.hostname,'-Dhttp.proxyPort='+(proxy.port||80),'-Dhttp.nonProxyHosts=localhost|127.*','-Djavax.net.ssl.trustStore=/etc/ssl/certs/java/cacerts'];
}
const launcher=production ? JSON.parse(fs.readFileSync(path.join(root,'build/integration/production-launch.json'))) : null;
async function until(fn, label, timeout=30000) {
  const end = Date.now()+timeout;
  while(Date.now()<end) { if(fn()) return; await wait(50); }
  throw new Error('Timeout: '+label);
}
async function start() {
  logs='';
  server=production
    ? spawn(path.join(process.env.JAVA_HOME,'bin/java'),['-Xmx2G',...javaProxy(),'-cp',launcher.classpath,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],{cwd:runDir,env:process.env,stdio:['pipe','pipe','pipe']})
    : spawn(path.join(root,'gradlew'),['runServer','--no-daemon','--console=plain','-PserverRunDir=build/expanded-integration/run'],{cwd:root,env:process.env,stdio:['pipe','pipe','pipe']});
  const file=fs.createWriteStream(path.join(output,`server-${Date.now()}.log`));
  for(const stream of [server.stdout,server.stderr]) stream.on('data',buf=>{logs+=buf.toString();file.write(buf);});
  server.once('close',()=>file.end());
  await until(()=>{if(server.exitCode!==null)throw new Error('Server exited: '+server.exitCode);return logs.includes('Shirokai Essentials iniciado.');},'server ready',180000);
}
async function consoleCommand(cmd) { server.stdin.write(cmd+'\n'); await wait(180); }
async function stop() {
  for(const b of children) b.client.end(); children=[];
  if(!server || server.exitCode!==null) return;
  await consoleCommand('stop');
  await until(()=>server.exitCode!==null,'server stopped',30000);
}
async function bot(username) {
  const client=mc.createClient({host:'127.0.0.1',port:25568,username,auth:'offline',version:'1.21.1',
    // Recipe serialization is outside these command/teleport tests. Keep the packet opaque.
    customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer'}}}}}});
  const b={client,username,messages:[],positions:[],commands:[],suggestions:[]}; children.push(b);
  client.on('error',e=>{b.error=e.message;console.error('BOT ERROR', username,e);});
  client.on('state',s=>{b.state=s;});
  client.on('kick_disconnect',p=>{b.kick=p;});
  client.on('end',s=>{b.ended=s;});
  client.on('systemChat',p=>b.messages.push(JSON.stringify(p)));
  b.publicChats=[];client.on('playerChat',p=>b.publicChats.push(p.plainMessage));
  client.on('declare_commands',p=>{b.commands=(p.nodes[p.rootIndex??0].children||[]).map(id=>p.nodes[id]).filter(n=>n.flags.command_node_type===1).map(n=>n.extraNodeData.name);});
  client.on('tab_complete',p=>b.suggestions.push(p));
  client.on('window_items',p=>{b.windowItems=p;});
  client.on('open_window',p=>{b.window=p;});
  client.on('abilities',p=>{b.abilities=p;});
  client.on('respawn',p=>{b.world=p.worldState?.name||p.worldState?.dimensionName||JSON.stringify(p.worldState);});
  client.on('ping',p=>client.write('pong',{id:p.id}));
  client.on('position',p=>{
    // Apply relative flags from vanilla teleport packets before acknowledging.
    const prev=b.position||{x:0,y:0,z:0,yaw:0,pitch:0}; const flags=p.flags;
    const relative=(key,bit)=>typeof flags==='number' ? !!(flags & bit) : !!flags?.[key];
    p={...p,x:p.x+(relative('x',1)?prev.x:0),y:p.y+(relative('y',2)?prev.y:0),z:p.z+(relative('z',4)?prev.z:0),yaw:p.yaw+(relative('yaw',8)?prev.yaw:0),pitch:p.pitch+(relative('pitch',16)?prev.pitch:0)};
    b.position=p; b.positions.push(p);
    client.write('teleport_confirm',{teleportId:p.teleportId});
    // Teleport confirmation is enough; sending a move in the same server tick causes false speed warnings.
  });
  await until(()=>b.position,'bot '+username,30000);
  if(b.error) throw new Error(b.error);
  await wait(250);
  return b;
}
async function chat(b, command, expect, timeout=5000) {
  const start=b.messages.length;
  b.client.chat('/'+command);
  if(expect) await until(()=>b.messages.slice(start).some(m=>m.includes(expect)),command+' => '+expect,timeout);
  else await wait(200);
  return b.messages.slice(start).join('\n');
}
function edit(file,fn) {
  const p=path.join(root,'build/module-failure-integration/run/config/shirokai-essentials',file+'.json');
  const json=JSON.parse(fs.readFileSync(p,'utf8')); fn(json); fs.writeFileSync(p,JSON.stringify(json,null,2));
}

function resetFixture(){
  for(const name of ['results.json','failure.json'])fs.rmSync(path.join(output,name),{force:true});
  fs.rmSync(runDir,{recursive:true,force:true});fs.mkdirSync(runDir,{recursive:true});
  fs.cpSync(path.join(root,'examples/config'),path.join(runDir,'config'),{recursive:true});
  fs.copyFileSync(path.join(__dirname,'server.properties'),path.join(runDir,'server.properties'));fs.writeFileSync(path.join(runDir,'eula.txt'),'eula=true\n');
  fs.mkdirSync(path.join(runDir,'mods'));fs.copyFileSync(path.join(root,`build/libs/shirokai-essentials-${modVersion}.jar`),path.join(runDir,'mods/shirokai-essentials.jar'));fs.copyFileSync(launcher.fabricApi,path.join(runDir,'mods/fabric-api.jar'));
  if(process.env.SHIROKAI_TEST_MODS)for(const entry of fs.readdirSync(process.env.SHIROKAI_TEST_MODS).filter(n=>n.endsWith('.jar')))fs.copyFileSync(path.join(process.env.SHIROKAI_TEST_MODS,entry),path.join(runDir,'mods',entry));
}
function grant(player,node,value=true){return consoleCommand(`lp user ${player} permission set ${node} ${value}`);}
async function inventory(player){
  await consoleCommand('save-all');
  const parser=require('prismarine-nbt');const result=await parser.parse(fs.readFileSync(path.join(runDir,'world/playerdata',player.client.uuid+'.dat')));
  return parser.simplify(result.parsed).Inventory;
}
function nbt(player){return path.join(runDir,'world/shirokai-essentials/players',player.client.uuid+'.json');}
async function snapshot(player){await consoleCommand('save-all');return JSON.parse(fs.readFileSync(nbt(player)));}
(async()=>{
 try{
  resetFixture();const props=path.join(runDir,'server.properties');fs.writeFileSync(props,fs.readFileSync(props,'utf8').replace('server-port=25565','server-port=25568'));
  edit('modules',j=>{j.modules.chat=true;j.modules.moderation=true;j.features.motd_auto=true;});edit('chat',j=>{j.formatPublicChat=true;j.motd=['NAO_DEVE_APARECER'];});
  edit('warps',j=>j.individualPermissions=true);edit('teleport',j=>{j.warmupSeconds=2;j.cooldownSeconds=0;});
  edit('commands',j=>j.commands.msg={enabled:true,aliases:['smsg'],aliasEnabled:{}});edit('conflicts',j=>j.commands.msg='FAIL_MODULE');
  await start();const alice=await bot('FailureAlice'),bob=await bot('FailureBob');
  check('FAIL_MODULE removes all own chat commands and aliases',()=>{assert(!alice.commands.includes('smsg'));assert(!alice.commands.includes('nick'));});
  check('FAIL_MODULE suppresses automatic MOTD even with feature enabled',()=>assert(!alice.messages.some(m=>m.includes('NAO_DEVE_APARECER'))));
  alice.client.chat('chat-original');await until(()=>bob.publicChats.includes('chat-original'),'vanilla public chat after failed module');results.push('Failed chat module preserves the vanilla chat pipeline and does not format messages');
  await consoleCommand('op FailureBob');await consoleCommand('tp FailureAlice 0.5 -60 0.5');await consoleCommand('tp FailureBob 10.5 -60 0.5');
  await chat(bob,'setwarp secure','salvo');await consoleCommand('lp user FailureAlice permission set shirokai.essentials.warp.secure true');
  await chat(alice,'warp secure','Permaneça parado');await consoleCommand('lp user FailureAlice permission set shirokai.essentials.warp.secure false');
  await until(()=>alice.messages.some(m=>m.includes('Teleporte cancelado')),'warp permission revoked during warmup');check('Specific warp permissions are rechecked before completing warmup',()=>assert(Math.abs(alice.position.x-.5)<.1));
  await consoleCommand('lp user FailureBob permission set shirokai.essentials.moderation.rank.admin true');await chat(bob,'freeze FailureAlice','ativado');
  await stop();edit('modules',j=>{for(const flag of ['moderation_chat','moderation_freeze','moderation_jail','moderation_warnings'])j.features[flag]=false;});
  await start();const unlocked=await bot('FailureAlice'),operator=await bot('FailureBob');
  check('Disabled moderation functions remove their commands independently',()=>{for(const name of ['mute','freeze','jail','warn'])assert(!operator.commands.includes(name),name);});
  await consoleCommand('tp FailureAlice 20.5 -60 0.5');await wait(250);check('Disabled freeze stops anchoring without deleting the saved punishment',()=>{
    assert(Math.abs(unlocked.position.x-20.5)<.1);const data=JSON.parse(fs.readFileSync(path.join(runDir,'world/shirokai-essentials/moderation-data.json')));assert(data.players[unlocked.client.uuid].freeze);
  });
  await stop();fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({version:modVersion,passed:results.length,checks:results},null,2));console.log(results.length+' failed-module integration checks passed');
 }catch(e){console.error(e);console.error(logs.slice(-7000));await stop().catch(()=>server?.kill());process.exitCode=1;}
})();
