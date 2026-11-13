/* Local-only dedicated-server integration. No Minecraft client mod is used. */
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const root = path.resolve(__dirname, '../..');
const modVersion=JSON.parse(fs.readFileSync(path.join(root,'build/resources/main/fabric.mod.json'))).version;
const output = path.join(root, process.env.SHIROKAI_OUTPUT||'build/integration');
const port=Number(process.env.SHIROKAI_PORT||25565);
const runDir = path.join(output, 'run');
fs.mkdirSync(output, {recursive:true});
const results = [];
const wait = ms => new Promise(r=>setTimeout(r,ms));
function check(name, fn) { fn(); results.push(name); console.log('PASS',name); }
let server, logs='', children=[];
const production=process.env.SHIROKAI_PRODUCTION==='1';
const launcher=production ? JSON.parse(fs.readFileSync(path.join(root,'build/integration/production-launch.json'))) : null;
async function until(fn, label, timeout=30000) {
  const end = Date.now()+timeout;
  while(Date.now()<end) { if(fn()) return; await wait(50); }
  throw new Error('Timeout: '+label);
}
async function start() {
  logs='';
  server=production
    ? spawn(path.join(process.env.JAVA_HOME,'bin/java'),['-Xmx1G','-cp',launcher.classpath,'net.fabricmc.loader.impl.launch.knot.KnotServer','nogui'],{cwd:runDir,env:process.env,stdio:['pipe','pipe','pipe']})
    : spawn(path.join(root,'gradlew'),['runServer','--no-daemon','--console=plain','-PserverRunDir='+path.relative(root,runDir)],{cwd:root,env:process.env,stdio:['pipe','pipe','pipe']});
  const file=fs.createWriteStream(path.join(output,`server-${Date.now()}.log`));
  for(const stream of [server.stdout,server.stderr]) stream.on('data',buf=>{logs+=buf.toString();file.write(buf);});
  server.once('close',()=>file.end());
  await until(()=>{if(server.exitCode!==null)throw new Error('Server exited: '+server.exitCode);return logs.includes('Shirokai Essentials iniciado.');},'server ready',120000);
}
async function consoleCommand(cmd) { server.stdin.write(cmd+'\n'); await wait(180); }
async function stop() {
  for(const b of children) b.client.end(); children=[];
  if(!server || server.exitCode!==null) return;
  await consoleCommand('stop');
  await until(()=>server.exitCode!==null,'server stopped',30000);
}
async function bot(username) {
  const client=mc.createClient({host:'127.0.0.1',port,username,auth:'offline',version:'1.21.1',
    // Recipe serialization is outside these command/teleport tests. Keep the packet opaque.
    customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer'}}}}}});
  const b={client,username,messages:[],positions:[],commands:[],suggestions:[]}; children.push(b);
  client.on('error',e=>{b.error=e.message;console.error('BOT ERROR', username,e);});
  client.on('state',s=>{b.state=s;});
  client.on('kick_disconnect',p=>console.error('BOT KICK',username,p));
  client.on('end',s=>{b.ended=s;});
  client.on('systemChat',p=>b.messages.push(JSON.stringify(p)));
  client.on('declare_commands',p=>{b.commands=(p.nodes[p.rootIndex??0].children||[]).map(id=>p.nodes[id]).filter(n=>n.flags.command_node_type===1).map(n=>n.extraNodeData.name);});
  client.on('tab_complete',p=>b.suggestions.push(p));
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
  const p=path.join(runDir,'config/shirokai-essentials',file+'.json');
  const json=JSON.parse(fs.readFileSync(p,'utf8')); fn(json); fs.writeFileSync(p,JSON.stringify(json,null,2));
}
(async()=>{
  try {
    fs.rmSync(runDir,{recursive:true,force:true}); fs.mkdirSync(runDir,{recursive:true});
    fs.cpSync(path.join(root,'examples/config'),path.join(runDir,'config'),{recursive:true});
    fs.writeFileSync(path.join(runDir,'server.properties'),fs.readFileSync(path.join(__dirname,'server.properties'),'utf8').replace('server-port=25565','server-port='+port));
    fs.writeFileSync(path.join(runDir,'eula.txt'),'eula=true\n');
    if(production) {
      fs.mkdirSync(path.join(runDir,'mods'));
      fs.copyFileSync(path.join(root,`build/libs/shirokai-essentials-${modVersion}.jar`),path.join(runDir,`mods/shirokai-essentials-${modVersion}.jar`));
      fs.copyFileSync(launcher.fabricApi,path.join(runDir,'mods/fabric-api.jar'));
    }
    edit('commands',j=>{j.commands.home.aliases=['casa','lar']; j.commands.home.aliasEnabled={lar:false}; j.commands.near.enabled=false;});
    edit('teleport',j=>{j.warmupSeconds=1;j.cooldownSeconds=3;j.requestExpirySeconds=2;});
    edit('permissions',j=>{j.defaultHomeLimit=2;j.defaults['kit.free']=true;j.defaults['kit.bypass.cooldown']=false;});
    edit('modules',j=>j.modules.kits=true);
    await start();
    await consoleCommand('gamerule doMobSpawning false');
    await consoleCommand('fill -8 63 -8 40 63 8 minecraft:stone');
    await consoleCommand('fill -8 64 -8 40 68 8 minecraft:air');
    const alice=await bot('TestAlice'), bob=await bot('TestBob'), admin=await bot('TestAdmin');
    await consoleCommand('op TestAdmin');
    await consoleCommand('tp TestAlice 0.5 64 0.5'); await consoleCommand('tp TestBob 10.5 64 0.5'); await consoleCommand('tp TestAdmin 20.5 64 0.5');
    await until(()=>Math.abs(alice.position.x-.5)<.1,'initial teleport');
    check('Vanilla /tp preserved and collision reported',()=>assert(logs.includes('Conflito /tp:')));
    check('Non-OP receives allowed roots but not administrative or disabled roots',()=>{
      assert(alice.commands.includes('home'));assert(alice.commands.includes('casa'));
      for(const n of ['lar','setwarp','setspawn','near','tpall','tppos']) assert(!alice.commands.includes(n),n);
    });
    check('No economy module commands or configuration exist without Impactor',()=>{
      for(const root of ['balance','bal','money','pay','baltop','eco'])assert(!alice.commands.includes(root),root);
      assert(!fs.existsSync(path.join(runDir,'config/shirokai-essentials/economy.json')));
    });
    await consoleCommand('give TestAdmin minecraft:diamond 3');
    await chat(admin,'createkit free','salvo');
    await consoleCommand('clear TestAdmin');
    await chat(alice,'kit free','recebido');
    await consoleCommand('save-all');
    const nbt=require('prismarine-nbt');
    const inventory=nbt.simplify((await nbt.parse(fs.readFileSync(path.join(runDir,'world/playerdata',alice.client.uuid+'.dat')))).parsed).Inventory;
    check('Kit delivers the full inventory without any economy mod installed',()=>assert.equal(inventory.filter(item=>item.id==='minecraft:diamond').reduce((n,item)=>n+item.count,0),3));
    await chat(alice,'kit free','Aguarde');results.push('Free kit still enforces its configured cooldown');
    await chat(alice,'sethome one','salvo'); await chat(alice,'sethome two','salvo');
    await chat(alice,'sethome three','limite'); results.push('Home permission limit enforced');
    await chat(alice,'renamehome two renamed','salvo'); await chat(alice,'delhome renamed','removido');
    const homes=await chat(alice,'homes','Destinos:'); check('Home rename/delete persisted',()=>{assert(homes.includes('one'));assert(!homes.includes('renamed'));});
    await consoleCommand('tp TestAlice 5.5 64 0.5');
    await chat(alice,'casa one','Teleporte concluído',6000);
    check('Enabled alias teleports to stored home',()=>assert(Math.abs(alice.position.x-.5)<.1));
    await chat(alice,'home one','Aguarde'); results.push('Teleport cooldown enforced');
    await wait(3100);
    await chat(alice,'back','Teleporte concluído',6000);
    check('/back restores previous location',()=>assert(Math.abs(alice.position.x-5.5)<.1));
    await wait(3100);
    await chat(alice,'home one','Permaneça parado');
    alice.client.write('position',{x:6,y:64,z:.5,onGround:true});
    await until(()=>alice.messages.some(m=>m.includes('Teleporte cancelado')),'movement cancellation');
    results.push('Warmup cancelled by movement');
    const damageStart=alice.messages.length;
    await chat(alice,'home one','Permaneça parado'); await consoleCommand('damage TestAlice 1 minecraft:generic');
    await until(()=>alice.messages.slice(damageStart).some(m=>m.includes('Teleporte cancelado')),'damage cancellation'); results.push('Warmup cancelled by damage');
    await consoleCommand('setblock 0 63 0 minecraft:lava');
    await chat(alice,'home one','inseguro'); results.push('Dangerous destination rejected');
    await consoleCommand('setblock 0 63 0 minecraft:stone');
    await chat(admin,'setwarp publicwarp','salvo'); await chat(admin,'setwarp privatewarp false','salvo');
    const warps=await chat(alice,'warps','Destinos:'); check('Private warps hidden from unauthorized player',()=>{assert(warps.includes('publicwarp'));assert(!warps.includes('privatewarp'));});
    await chat(alice,'warp privatewarp','não encontrado');
    await chat(admin,'setspawn','salvo');
    await chat(alice,'spawn','Teleporte concluído',6000);
    check('/spawn uses configured global destination',()=>assert(Math.abs(alice.position.x-20.5)<.1));
    await wait(3100);
    await chat(alice,'tpa TestBob','Pedido enviado'); await chat(bob,'tpaccept TestAlice');
    await until(()=>Math.abs(alice.position.x-10.5)<.1,'accepted tpa'); results.push('TPA acceptance moves sender');
    await wait(3100);
    await consoleCommand('tp TestBob 15.5 64 0.5');
    await chat(alice,'tpahere TestBob','Pedido enviado'); await chat(bob,'tpaccept TestAlice');
    await until(()=>Math.abs(bob.position.x-10.5)<.1,'accepted tpahere'); results.push('TPAHere consent moves recipient');
    await chat(bob,'tpatoggle','bloqueados'); await chat(alice,'tpa TestBob','bloqueou'); await chat(bob,'tpatoggle','ativados');
    results.push('Persistent TPA block enforced');
    await chat(alice,'tpa TestBob','Pedido enviado'); await wait(2300); await chat(bob,'tpaccept TestAlice','expirado'); results.push('Expired TPA cannot be accepted');
    await consoleCommand('execute in minecraft:the_nether run forceload add 0 0');
    await consoleCommand('execute in minecraft:the_nether run fill 0 63 0 8 63 8 minecraft:stone');
    await consoleCommand('execute in minecraft:the_nether run fill 0 64 0 8 67 8 minecraft:air');
    await consoleCommand('execute as TestAlice in minecraft:the_nether run tp @s 4.5 64 4.5');
    await chat(alice,'sethome nether','salvo');
    await consoleCommand('execute as TestAlice in minecraft:overworld run tp @s 4.5 64 4.5');
    await chat(alice,'home nether','Teleporte concluído',6000);
    check('Cross-dimension home persists full identifier',()=>assert(Math.abs(alice.position.x-4.5)<.1));
    const playerFile=path.join(runDir,'world/shirokai-essentials/players',alice.client.uuid+'.json');
    const saved=JSON.parse(fs.readFileSync(playerFile));
    check('Home file stores minecraft:the_nether',()=>assert.equal(saved.homes.nether.dimension,'minecraft:the_nether'));
    edit('teleport',j=>j.warmupSeconds=-1);
    await chat(admin,'shirokaiessentials reload','Configuração inválida'); results.push('Invalid reload preserves running configuration');
    edit('teleport',j=>j.warmupSeconds=1);
    await chat(admin,'shirokaiessentials reload','recarregadas');
    await stop();
    edit('modules',j=>j.modules.homes=false);
    await start();
    const returning=await bot('TestAlice');
    check('Disabled module removes command and aliases from client tree',()=>{for(const n of ['home','homes','sethome','casa'])assert(!returning.commands.includes(n),n);});
    check('Disabling homes preserves stored player data',()=>assert.deepEqual(JSON.parse(fs.readFileSync(playerFile)).homes,saved.homes));
    await stop();
    edit('modules',j=>j.modules.homes=true);
    await start();
    const restored=await bot('TestAlice');
    check('Re-enabled homes restored after full server restart',()=>assert(restored.commands.includes('home')));
    await chat(restored,'homes','nether'); results.push('Persisted homes accessible after restart');
    edit('permissions',j=>{j.requireLuckPerms=true;j.opFallback=false;});
    await consoleCommand('shirokaiessentials reload');
    await until(()=>!restored.commands.includes('home'),'permission revocation command tree');
    check('LuckPerms required but absent denies player access',()=>assert(!restored.commands.includes('home')));
    edit('permissions',j=>{j.requireLuckPerms=false;j.opFallback=true;});
    await consoleCommand('shirokaiessentials reload');
    await until(()=>restored.commands.includes('home'),'permission restoration command tree');
    check('Permission reload refreshes client command tree',()=>assert(restored.commands.includes('home')));
    await stop();
    console.log(`${results.length} dedicated integration checks passed`);
    fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({version:modVersion,mode:production?'production-jar':'loom-development',passed:results.length,checks:results},null,2));
  } catch(e) {
    console.error(e); console.error(logs.slice(-6000));
    fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({passed:results,error:e.stack},null,2));
    await stop().catch(()=>server?.kill()); process.exitCode=1;
  }
})();
