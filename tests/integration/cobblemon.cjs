/* Local-only real Cobblemon battle integration. */
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const root = path.resolve(__dirname, '../..');
const modVersion=JSON.parse(fs.readFileSync(path.join(root,'build/resources/main/fabric.mod.json'))).version;
const output = path.join(root, 'build/cobblemon-integration');
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
const launcher=production ? JSON.parse(fs.readFileSync(path.join(root,'build/integration/cobblemon-launch.json'))) : null;
async function until(fn, label, timeout=30000) {
  const end = Date.now()+timeout;
  while(Date.now()<end) { if(fn()) return; await wait(50); }
  throw new Error('Timeout: '+label);
}
async function start() {
  logs='';
  server=production
    ? spawn(path.join(process.env.JAVA_HOME,'bin/java'),['-Xmx3G',...javaProxy(),'-cp',launcher.classpath,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],{cwd:runDir,env:process.env,stdio:['pipe','pipe','pipe']})
    : spawn(path.join(root,'gradlew'),['runServer','--no-daemon','--console=plain','-PserverRunDir=build/expanded-integration/run'],{cwd:root,env:process.env,stdio:['pipe','pipe','pipe']});
  const file=fs.createWriteStream(path.join(output,`server-${Date.now()}.log`));
  for(const stream of [server.stdout,server.stderr]) stream.on('data',buf=>{logs+=buf.toString();file.write(buf);});
  server.once('close',()=>file.end());
  await until(()=>{if(server.exitCode!==null)throw new Error('Server exited: '+server.exitCode);return logs.includes('Shirokai Essentials iniciado.');},'server ready',240000);
}
async function consoleCommand(cmd) { server.stdin.write(cmd+'\n'); await wait(180); }
async function stop() {
  for(const b of children) b.client.end(); children=[];
  if(!server || server.exitCode!==null) return;
  await consoleCommand('stop');
  await until(()=>server.exitCode!==null,'server stopped',30000);
}
async function bot(username) {
  const client=mc.createClient({host:'127.0.0.1',port:25566,username,auth:'offline',version:'1.21.1',
    // Recipe serialization is outside these command/teleport tests. Keep the packet opaque.
    customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer',packet_entity_metadata:'restBuffer'}}}}}});
  const b={client,username,messages:[],positions:[],commands:[],suggestions:[]}; children.push(b);
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
  const start=b.messages.length;
  b.client.chat('/'+command);
  if(expect) await until(()=>b.messages.slice(start).some(m=>m.includes(expect)),command+' => '+expect,timeout);
  else await wait(200);
  return b.messages.slice(start).join('\n');
}
function edit(file,fn) {
  const p=path.join(root,'build/cobblemon-integration/run/config/shirokai-essentials',file+'.json');
  const json=JSON.parse(fs.readFileSync(p,'utf8')); fn(json); fs.writeFileSync(p,JSON.stringify(json,null,2));
}

function resetFixture(){
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
  resetFixture();
  const settings=path.join(runDir,'server.properties');fs.writeFileSync(settings,fs.readFileSync(settings,'utf8').replace('server-port=25565','server-port=25566'));
  if(!process.env.SHIROKAI_COBBLEMON_MOD)throw new Error('Set SHIROKAI_COBBLEMON_MOD to the official 1.8.3 JAR');
  fs.copyFileSync(process.env.SHIROKAI_COBBLEMON_MOD,path.join(runDir,'mods/cobblemon.jar'));
  fs.copyFileSync(path.join(root,`build/libs/shirokai-battle-fixture-${modVersion}.jar`),path.join(runDir,'mods/battle-fixture.jar'));
  edit('teleport',j=>{j.warmupSeconds=0;j.cooldownSeconds=0;j.asyncChunkLoading=true;});
  edit('commands',j=>{j.commands.pay={enabled:true,aliases:['spay'],aliasEnabled:{}};j.commands.eco={enabled:true,aliases:['seco'],aliasEnabled:{}};});
  await start();
  await consoleCommand('shirokaiessentials status');
  check('Dedicated server boots with Cobblemon 1.8.3 and Fabric Loader 0.17.2',()=>{assert(logs.includes('Fabric Loader 0.17.2'));assert(logs.includes('cobblemon=1.8.3+1.21.1'));});
  check('Cobblemon public UUID battle lookup passes ABI and invocation probe',()=>assert(logs.includes('cobblemon-guard=consulta pública por UUID disponível')));
  check('LuckPerms and Multiworld coexist in the Cobblemon server',()=>{for(const version of ['luckperms=5.4.140','multiworld=1.14.2'])assert(logs.includes(version),version);});
  const alice=await bot('BattleAlice'),bob=await bot('BattleBob');
  await consoleCommand('gamerule doMobSpawning false');
  await consoleCommand('fill -8 63 -8 40 63 8 minecraft:stone');
  await consoleCommand('fill -8 64 -8 40 68 8 minecraft:air');
  await consoleCommand('tp BattleAlice 0.5 64 0.5');
  await consoleCommand('tp BattleBob 10.5 64 0.5');
  await consoleCommand('op BattleAlice');
  await grant('BattleAlice','shirokai.essentials.bypass.warmup',false);
  await chat(alice,'sethome anchor','salvo');
  await chat(alice,'setspawn','salvo');
  await chat(alice,'setwarp arena','salvo');
  await consoleCommand('tp BattleAlice 20.5 64 0.5');
  await chat(alice,'home anchor','concluído');
  await until(()=>Math.abs(alice.position.x-.5)<.1,'home before battle');
  results.push('Home teleport works before battle');
  await consoleCommand('tp BattleAlice 20.5 64 0.5');
  await consoleCommand('givepokemonother BattleAlice pikachu level=50');
  await consoleCommand('givepokemonother BattleBob eevee level=50');
  await consoleCommand('shirokai_test_battle start');
  for(let i=0;i<30&&!logs.includes(';turn1=true');i++){await wait(500);await consoleCommand('shirokai_test_battle status');}
  check('Real Cobblemon PvP battle reaches Showdown turn 1 with two connected protocol clients',()=>{assert(logs.includes(';ended=false;turn1=true'));assert(!alice.error&&!bob.error&&!alice.ended&&!bob.ended);});
  const beforeBattle=await snapshot(alice);
  const before=alice.positions.length;
  await chat(alice,'home anchor','inseguro');
  await wait(500);
  check('Home teleport is blocked during a real battle without changing position',()=>{assert.equal(alice.positions.length,before);assert(Math.abs(alice.position.x-20.5)<.1);});
  for(const command of ['spawn','warp arena','back','tppos 0.5 64 0.5']){
    await chat(alice,command,'inseguro');
    check(command+' is blocked during a real battle',()=>assert.equal(alice.positions.length,before));
  }
  const requestStart=alice.messages.length;
  await chat(alice,'tpa BattleBob','Pedido enviado');
  await chat(bob,'tpaccept BattleAlice');
  await until(()=>alice.messages.slice(requestStart).some(m=>m.includes('inseguro')),'accepted TPA rejected for battler');
  check('Accepted TPA cannot move a battle participant',()=>assert.equal(alice.positions.length,before));
  await consoleCommand('shirokai_test_battle systemteleport');
  check('System teleport cannot bypass the battle guard',()=>{assert(logs.includes('BATTLE_FIXTURE_SYSTEM_TELEPORT=0'));assert.equal(alice.positions.length,before);});
  const afterBlocked=await snapshot(alice);
  check('Rejected battle teleport does not update history or cooldown',()=>{assert.deepEqual(afterBlocked.back,beforeBattle.back);assert.equal(afterBlocked.nextTeleportAt,beforeBattle.nextTeleportAt);});
  await consoleCommand('shirokai_test_battle stop');
  await consoleCommand('shirokai_test_battle status');
  await until(()=>logs.includes('BATTLE_FIXTURE_STATUS=absent'),'battle deregistration');
  await chat(alice,'home anchor','concluído');
  await until(()=>Math.abs(alice.position.x-.5)<.1,'home after battle');
  results.push('Home teleport resumes when the battle ends');

  // Battle begins while a teleport warmup is pending. It must cancel immediately,
  // so ending the battle before the original deadline cannot resurrect the job.
  edit('teleport',j=>{j.warmupSeconds=5;});
  await consoleCommand('shirokaiessentials reload');
  await consoleCommand('tp BattleAlice 20.5 64 0.5');
  await chat(alice,'home anchor','Permaneça parado');
  const cancellationStart=alice.messages.length;
  await consoleCommand('shirokai_test_battle start');
  await until(()=>alice.messages.slice(cancellationStart).some(m=>m.includes('Teleporte cancelado')),'battle cancels warmup');
  await consoleCommand('shirokai_test_battle stop');
  await wait(5500);
  check('Entering battle cancels warmup permanently even if battle ends before its deadline',()=>assert(Math.abs(alice.position.x-20.5)<.1));
  await chat(alice,'home anchor','Permaneça parado');
  await until(()=>Math.abs(alice.position.x-.5)<.1,'new warmup after battle',8000);
  results.push('Cancelled warmup releases its slot for the next teleport');
  check('Removed financial aliases never register even in legacy configuration',()=>{assert(!alice.commands.includes('spay'));assert(!alice.commands.includes('seco'));});
  await stop();
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({version:modVersion,mode:'production-jar-real-battle',passed:results.length,checks:results},null,2));console.log(results.length+' battle checks passed');
 }catch(e){console.error(e);console.error(logs.slice(-10000));fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({passed:results,error:e.stack},null,2));await stop().catch(()=>server?.kill());process.exitCode=1;}
})();
