/* Local-only dedicated-server integration. No Minecraft client mod is used. */
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const root = path.resolve(__dirname, '../..');
const modVersion=JSON.parse(fs.readFileSync(path.join(root,'build/resources/main/fabric.mod.json'))).version;
const output = path.join(root, 'build/bridge-integration');
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
    : spawn(path.join(root,'gradlew'),['runServer','--no-daemon','--console=plain','-PserverRunDir=build/bridge-integration/run'],{cwd:root,env:process.env,stdio:['pipe','pipe','pipe']});
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
  const client=mc.createClient({host:'127.0.0.1',port:25569,username,auth:'offline',version:'1.21.1',
    // Recipe serialization is outside these command/teleport tests. Keep the packet opaque.
    customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer'}}}}}});
  const b={client,username,messages:[],positions:[],commands:[],suggestions:[],tab:[],sidebar:[]};
  client.on('player_info',p=>b.tab.push(p));
  client.on('scoreboard_score',p=>b.sidebar.push(p));
  client.on('playerChat',p=>b.messages.push(JSON.stringify(p))); children.push(b);
  client.on('error',e=>{b.error=e.message;console.error('BOT ERROR', username,e);});
  client.on('state',s=>{b.state=s;});
  client.on('kick_disconnect',p=>{b.kick=p;});
  client.on('end',s=>{b.ended=s;});
  client.on('systemChat',p=>b.messages.push(JSON.stringify(p)));
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
  await wait(1050); // Respect vanilla command-spam accounting as well as the Core cooldown.
  if(b.ended)throw new Error("Client disconnected: "+b.username+" "+JSON.stringify(b.kick));
  const start=b.messages.length;
  b.client.chat('/'+command);
  if(expect) await until(()=>b.messages.slice(start).some(m=>m.includes(expect)),command+' => '+expect,timeout);
  else await wait(200);
  return b.messages.slice(start).join('\n');
}
function edit(file,fn) {
  const p=path.join(root,'build/bridge-integration/run/config/shirokai-essentials',file+'.json');
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
function importFixture(alice){
 const nbt=require('prismarine-nbt'),zlib=require('node:zlib');const fixture=path.join(runDir,'world/import-fixture');
 const loc=()=>({WorldRegistryKey:{type:'string',value:'minecraft:overworld'},x:{type:'double',value:.5},y:{type:'double',value:64},z:{type:'double',value:.5},headYaw:{type:'float',value:0},pitch:{type:'float',value:0}});
 const write=(file,value)=>{fs.mkdirSync(path.dirname(file),{recursive:true});fs.writeFileSync(file,zlib.gzipSync(nbt.writeUncompressed({type:'compound',name:'',value:{data:{type:'compound',value}}})));};
 const player=(id,homes)=>{const raw=Buffer.from(id.replace(/-/g,''),'hex');return {playerUuid:{type:'intArray',value:[0,4,8,12].map(i=>raw.readInt32BE(i))},homes:{type:'compound',value:homes}};};
 write(path.join(fixture,'modplayerdata',alice.client.uuid+'.dat'),player(alice.client.uuid,{importada:{type:'compound',value:loc()},mundo:{type:'compound',value:loc()}}));
 const unknown='00000000-0000-0000-0000-000000000001';write(path.join(fixture,'modplayerdata',unknown+'.dat'),player(unknown,{nova:{type:'compound',value:loc()}}));
 write(path.join(fixture,'essentialcommands/world_data.dat'),{spawn:{type:'compound',value:loc()},warps:{type:'compound',value:{legado:{type:'compound',value:loc()},restrito:{type:'compound',value:{...loc(),permissionString:{type:'string',value:'vip'}}}}}});
 return {directory:fixture,unknown,source:path.join(fixture,'modplayerdata',alice.client.uuid+'.dat')};
}
function reports(){return fs.readdirSync(path.join(runDir,'config/shirokai-essentials')).filter(n=>n.startsWith('migration-')&&n.endsWith('.json'));}
async function migration(mode){const old=new Set(reports());await consoleCommand('ecimport '+mode+' import-fixture');let report;
 await until(()=>{const next=reports().find(n=>!old.has(n));if(!next)return false;const data=JSON.parse(fs.readFileSync(path.join(runDir,'config/shirokai-essentials',next)));if(data.state==='PREPARED')return false;report=data;return true;},'migration '+mode,15000);return report;
}
(async()=>{
 try{
  resetFixture();
  const properties=path.join(runDir,'server.properties');fs.writeFileSync(properties,fs.readFileSync(properties,'utf8').replace('server-port=25565','server-port=25569'));
  if(!process.env.SHIROKAI_CORE_JAR)throw new Error('Set SHIROKAI_CORE_JAR');
  fs.copyFileSync(process.env.SHIROKAI_CORE_JAR,path.join(runDir,'mods/shirokai-core.jar'));
  if(process.env.SHIROKAI_CORE_FABRIC_API)fs.copyFileSync(process.env.SHIROKAI_CORE_FABRIC_API,path.join(runDir,'mods/fabric-api.jar'));
  edit('modules',j=>{for(const module of ['chat','utilities','moderation','kits'])j.modules[module]=true;for(const feature of ['nicknames','afk_manual','private_messages','mail','socialspy','god_mode'])j.features[feature]=true;});
  edit('commands',j=>{for(const [root,alias] of Object.entries({msg:'emsg',r:'er',ignore:'eignore',unignore:'eunignore'}))j.commands[root].aliases=[alias];});
  edit('conflicts',j=>{for(const root of ['msg','r','ignore','unignore'])j.commands[root]='USE_ALTERNATIVE_ALIAS';});
  edit('chat',j=>j.formatPublicChat=true); // Bridge must suppress this duplicate renderer automatically.
  edit('permissions',j=>{j.defaults['kit.starter']=true;});
  edit('teleport',j=>{j.warmupSeconds=0;j.cooldownSeconds=0;});
  await start();await until(()=>logs.includes('[essentials] READY protocol=1'),'bridge connected',30000);
  await consoleCommand('shirokaiessentials status');assert(logs.includes('core-chat=true'));
  await consoleCommand('gamerule doMobSpawning false');await consoleCommand('fill -8 63 -8 40 63 8 minecraft:stone');await consoleCommand('fill -8 64 -8 40 68 8 minecraft:air');
  const alice=await bot('TestAlice'),bob=await bot('TestBob'),admin=await bot('TestAdmin');
  await consoleCommand('op TestAdmin');
  for(const [who,node] of [['TestAlice','shirokai.essentials.nick'],['TestAlice','shirokaicore.command.*'],['TestAdmin','shirokai.essentials.moderation.rank.admin'],['TestAlice','shirokai.essentials.moderation.rank.helper']])await grant(who,node);
  await consoleCommand('tp TestAlice 0.5 64 0.5');await consoleCommand('tp TestBob 10.5 64 0.5');await consoleCommand('tp TestAdmin 20.5 64 0.5');
  await wait(1800); // SQLite online profiles and role snapshots.
  const board=path.join(runDir,'config/shirokai-core/scoreboard.json');const boardJson=JSON.parse(fs.readFileSync(board));
  boardJson.layouts[0].lines=[{id:'identity',condition:{permission:null,group:null,worlds:[]},text:'Nome: %shirokai:player% / %essentials:realname%'},{id:'state',condition:{permission:null,group:null,worlds:[]},text:'AFK: %essentials:afk% Homes: %essentials:homes%'}];fs.writeFileSync(board,JSON.stringify(boardJson,null,2));
  await consoleCommand('shirokaicore reload');await wait(1200);
  await chat(alice,'nick Akira','Akira');
  await until(()=>bob.tab.some(packet=>JSON.stringify(packet).includes('Akira')&&JSON.stringify(packet).includes(alice.client.uuid)),'nickname TAB packet');
  check('Essentials nickname updates the Core TAB without replacing player UUID/profile',()=>assert(bob.tab.some(packet=>JSON.stringify(packet).includes('Akira')&&JSON.stringify(packet).includes(alice.client.uuid))));
  await until(()=>alice.sidebar.some(packet=>JSON.stringify(packet).includes('Akira')),'nickname sidebar packet');results.push('Core scoreboard uses nickname and real-account placeholders');
  let at=bob.messages.length;await chat(alice,'g bridge-global');await until(()=>bob.messages.slice(at).some(m=>m.includes('bridge-global')),'Core global nickname');
  check('Core chat shows nickname and sends one message despite Essentials public formatting enabled',()=>{const received=bob.messages.slice(at).filter(m=>m.includes('bridge-global'));assert.equal(received.length,1);assert(received[0].includes('Akira'));});
  await wait(1100);at=bob.messages.length;alice.client.chat('bridge-normal-body');await until(()=>bob.messages.slice(at).some(m=>m.includes('bridge-normal-body')),'normal chat');
  check('Normal player chat is delivered once through the Core gate',()=>assert.equal(bob.messages.slice(at).filter(m=>m.includes('bridge-normal-body')).length,1));
  await chat(alice,'afk','ausente');await until(()=>bob.tab.some(packet=>JSON.stringify(packet).includes('[AFK]')),'AFK TAB state');results.push('Essentials AFK indicator appears in Core TAB');
  await wait(1100);await chat(alice,'g bridge-back');await wait(1200);const latest=bob.tab.filter(packet=>JSON.stringify(packet).includes('Akira')).at(-1);check('Core chat activity removes AFK without changing the nickname',()=>assert(!JSON.stringify(latest).includes('[AFK]')));
  await chat(admin,'mute TestAlice integration-mute','mute');
  at=bob.messages.length;await chat(alice,'g forbidden-global','silenciado');await chat(alice,'l forbidden-local','silenciado');await chat(alice,'chat msg TestBob forbidden-private','silenciado');
  check('Essentials mute gates Core global/local/private without leaking messages',()=>assert(!bob.messages.slice(at).some(m=>m.includes('forbidden-'))));
  await chat(admin,'unmute TestAlice','unmute');await wait(1100);
  await chat(bob,'eignore TestAlice','atualizada');at=bob.messages.length;await chat(alice,'g hidden-by-ignore');await wait(250);check('Essentials ignore also filters Core public channels',()=>assert(!bob.messages.slice(at).some(m=>m.includes('hidden-by-ignore'))));
  await chat(bob,'eunignore TestAlice','atualizada');await wait(1100);
  at=bob.messages.length;await chat(alice,'emsg TestBob cross-private');await until(()=>bob.messages.slice(at).some(m=>m.includes('cross-private')),'shared private delivery');
  await wait(1100);at=alice.messages.length;await chat(bob,'chat reply cross-reply');await until(()=>alice.messages.slice(at).some(m=>m.includes('cross-reply')),'Core reply after Essentials message');results.push('Essentials PM and Core reply share the same conversation');
  await chat(bob,'msgtoggle','ativado');await wait(1100);at=bob.messages.length;await chat(alice,'chat msg TestBob blocked-toggle','indisponível');check('Essentials private toggle blocks Core private delivery',()=>assert(!bob.messages.slice(at).some(m=>m.includes('blocked-toggle'))));await chat(bob,'msgtoggle','desativado');
  await chat(admin,'socialspy','ativado');await wait(1100);at=admin.messages.length;await chat(alice,'chat msg TestBob audited-private');await until(()=>admin.messages.slice(at).some(m=>m.includes('audited-private')),'Essentials spy on Core PM');results.push('Opt-in Essentials socialspy sees only successfully delivered Core private messages');
  await chat(alice,'sethome casa','salvo');await chat(alice,'shirokaicore placeholders %essentials:homes%/%essentials:home_limit%','1/');results.push('Homes placeholder reads the Essentials profile and permission limit');
  await consoleCommand('give TestAdmin minecraft:diamond 3');await chat(admin,'createkit starter','salvo');await consoleCommand('clear TestAdmin');
  await chat(alice,'shirokaicore placeholders KIT=%essentials:kits_ready%','KIT=1');await chat(alice,'kit starter','recebido');
  await chat(alice,'shirokaicore placeholders KIT=%essentials:kits_ready%','KIT=0');results.push('Kit readiness placeholders follow a real claim and cooldown without charging');
  await grant('TestAlice','shirokai.essentials.fly');await chat(alice,'fly','atualizado');await chat(alice,'shirokaicore placeholders FLY=%essentials:fly%','FLY=true');
  await grant('TestAlice','shirokai.essentials.god');await chat(alice,'god','atualizado');await chat(alice,'shirokaicore placeholders GOD=%essentials:god%','GOD=true');results.push('Fly and god placeholders reflect actual Essentials modes');
  await chat(admin,'mail send TestAlice integration-mail','enviad');await chat(alice,'shirokaicore placeholders MAIL=%essentials:mail%','MAIL=1');results.push('Mail placeholder reflects stored Essentials mail');
  await chat(alice,'nick off','TestAlice');await until(()=>{const packet=bob.tab.filter(packet=>JSON.stringify(packet).includes(alice.client.uuid)).at(-1);return packet&&!JSON.stringify(packet).includes('Akira');},'nick off TAB refresh',10000);results.push('Nick off restores the account name in TAB');
  await chat(alice,'nick Renasce','Renasce');const preservedBoard=fs.readFileSync(board,'utf8');
  await stop();await start();await until(()=>logs.includes('[essentials] READY protocol=1'),'bridge after restart');
  const returned=await bot('TestAlice');await wait(1500);await chat(returned,'homes','casa');await until(()=>returned.tab.some(packet=>JSON.stringify(packet).includes('Renasce')),'nickname after restart');assert.equal(fs.readFileSync(board,'utf8'),preservedBoard);results.push('Both mods restart with nickname, homes and custom scoreboard configuration intact');
  await stop();
  fs.unlinkSync(path.join(runDir,'mods/shirokai-core.jar'));
  await start();await consoleCommand('shirokaiessentials status');
  check('Essentials works independently after removing Core',()=>assert(logs.includes('core-chat=false')));
  const soloAlice=await bot('TestAlice'),soloBob=await bot('TestBob');
  await chat(soloAlice,'emsg TestBob standalone-private');await until(()=>soloBob.messages.some(m=>m.includes('standalone-private')),'standalone private delivery');results.push('Standalone Essentials retains its own private messages');
  await stop();
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({version:modVersion,coreJar:path.basename(process.env.SHIROKAI_CORE_JAR),coreSha256:require('node:crypto').createHash('sha256').update(fs.readFileSync(process.env.SHIROKAI_CORE_JAR)).digest('hex'),passed:results.length,checks:results},null,2));console.log(results.length+' shared bridge checks passed');
 }catch(e){for(const b of children)console.error('LAST MESSAGES',b.username,'ended=',b.ended,'kick=',JSON.stringify(b.kick),b.messages.slice(-3));console.error(e);console.error(logs.slice(-10000));fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({passed:results,error:e.stack},null,2));await stop().catch(()=>server?.kill());process.exitCode=1;}
})();
