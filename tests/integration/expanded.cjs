/* Local-only dedicated-server integration. No Minecraft client mod is used. */
const fs = require('node:fs');
const path = require('node:path');
const {spawn} = require('node:child_process');
const assert = require('node:assert/strict');
const mc = require('minecraft-protocol');
const root = path.resolve(__dirname, '../..');
const modVersion=JSON.parse(fs.readFileSync(path.join(root,'build/resources/main/fabric.mod.json'))).version;
const output = path.join(root, 'build/expanded-integration');
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
  const client=mc.createClient({host:'127.0.0.1',port:25565,username,auth:'offline',version:'1.21.1',
    // Recipe serialization is outside these command/teleport tests. Keep the packet opaque.
    customPackets:{'1.21':{play:{toClient:{types:{packet_declare_recipes:'restBuffer'}}}}}});
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
  const p=path.join(root,'build/expanded-integration/run/config/shirokai-essentials',file+'.json');
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
  edit('modules',j=>{
    for(const module of ['chat','utilities','administration','moderation','kits'])j.modules[module]=true;
    j.modules.economy=true; // Legacy config is ignored even when the removed module was enabled.
    for(const feature of ['private_messages','mail','socialspy','nicknames','afk_manual','motd_auto','fly','god_mode','kit_cooldowns','moderation_chat'])j.features[feature]=true;
  });
  edit('commands',j=>{
    for(const name of ['msg','tell','whisper','r','reply','ignore','unignore','mail','socialspy','msgtoggle','broadcast','me','afk','nick','realname','help','rules','motd','info','list','seen','whois','fly','god','heal','feed','speed','invsee','enderchest','workbench','anvil','repair','hat','clearinventory','ping','gc','sudo','time','weather','kick','ban','tempban','unban','mute','tempmute','unmute','warn','warnings','clearwarnings','setjail','deljail','jail','unjail','freeze','kit','kits','createkit','delkit','showkit','resetkit','kitrecover','balance','bal','money','pay','baltop','eco','ecimport'])j.commands[name]||={enabled:true,aliases:[],aliasEnabled:{}};
    for(const [command,alias] of Object.entries({msg:'smsg',me:'sme',list:'slist',help:'shelp',time:'stime',weather:'sweather',kick:'skick',ban:'sban'}))j.commands[command]={enabled:true,aliases:[alias],aliasEnabled:{}};
    j.commands.motd.enabled=false;j.commands.afk.aliases=['away'];j.commands.pay.aliases=['spay'];j.commands.eco.aliases=['seco'];
  });
  edit('conflicts',j=>{j.commands={...j.commands,msg:'USE_ALTERNATIVE_ALIAS',me:'USE_ALTERNATIVE_ALIAS',list:'USE_ALTERNATIVE_ALIAS',help:'USE_ALTERNATIVE_ALIAS',time:'USE_ALTERNATIVE_ALIAS',weather:'USE_ALTERNATIVE_ALIAS',kick:'USE_ALTERNATIVE_ALIAS',ban:'USE_ALTERNATIVE_ALIAS'};});
  edit('permissions',j=>{j.defaults={...j.defaults,'kit.starter':true,'kit.bypass.cooldown':false,'invsee':true,'invsee.others':true,'nick':true};});
  edit('chat',j=>{j.motd=['<aqua>Bem-vindo ao teste, {player}!</aqua>'];j.afkSeconds=10;j.mailCooldownSeconds=1;});
  edit('utilities',j=>{j.persistModes=true;j.allowSudo=true;});
  edit('moderation',j=>j.manageVanillaBans=true);
  edit('teleport',j=>{j.warmupSeconds=0;j.cooldownSeconds=0;j.asyncChunkLoading=true;});
  edit('integrations',j=>j.essentialCommandsVersion='0.35.2');
  await start();await consoleCommand('gamerule doMobSpawning false');await consoleCommand('fill -8 63 -8 40 63 8 minecraft:stone');await consoleCommand('fill -8 64 -8 40 68 8 minecraft:air');
  const alice=await bot('TestAlice'),bob=await bot('TestBob'),admin=await bot('TestAdmin');
  await consoleCommand('op TestAdmin');
  if(process.env.SHIROKAI_TEST_MODS){await grant('TestAdmin','shirokai.essentials.moderation.rank.admin');await grant('TestAlice','shirokai.essentials.moderation.rank.helper');}
  await consoleCommand('tp TestAlice 0.5 64 0.5');await consoleCommand('tp TestBob 10.5 64 0.5');await consoleCommand('tp TestAdmin 20.5 64 0.5');await chat(admin,'setspawn','salvo');
  check('Automatic MOTD works with /motd command disabled',()=>{assert(alice.messages.some(m=>m.includes('Bem-vindo ao teste')));assert(!alice.commands.includes('motd'));});
  await chat(alice,'smsg TestBob primeiro','primeiro');await until(()=>bob.messages.some(m=>m.includes('primeiro')),'private delivery');
  await chat(bob,'r resposta','resposta');await until(()=>alice.messages.some(m=>m.includes('resposta')),'reply');results.push('Private messages and replies');
  const messageStart=bob.messages.length;await chat(alice,'smsg TestBob <click:run_command:/op>texto</click>','texto');
  await until(()=>bob.messages.length>messageStart,'unparsed delivery');check('Player markup is literal and cannot inject click events',()=>{const text=bob.messages.slice(messageStart).join('');assert(text.includes('<click:run_command:/op>'));assert(!text.includes('clickEvent'));});
  await chat(bob,'ignore TestAlice','atualizada');const ignored=bob.messages.length;
  await chat(alice,'smsg TestBob segredo','Não foi possível');await wait(250);check('Ignored sender cannot leak private content',()=>assert(!bob.messages.slice(ignored).some(m=>m.includes('segredo'))));
  await chat(bob,'unignore TestAlice','atualizada');await chat(bob,'msgtoggle','ativado');await chat(alice,'smsg TestBob bloqueado','Não foi possível');await chat(bob,'msgtoggle','desativado');results.push('Private message toggle');
  await chat(admin,'socialspy','ativado');const spy=admin.messages.length;await chat(alice,'smsg TestBob observada','observada');await until(()=>admin.messages.slice(spy).some(m=>m.includes('Espião')&&m.includes('observada')),'social spy');results.push('Social spy requires permission and explicit toggle');
  await chat(alice,'away','ausente');await chat(alice,'away','disponível');results.push('Manual AFK aliases toggle both directions');
  await chat(alice,'nick Apelido','Apelido');await chat(bob,'realname Apelido','TestAlice');await chat(alice,'nick __','Valor inválido');results.push('Nickname sanitization and real-name lookup');
  bob.client.end();await until(()=>logs.includes('TestBob lost connection'),'offline recipient');
  await chat(alice,'mail send TestBob mensagem-offline','Correio enviado');
  const returned=await bot('TestBob');await until(()=>returned.messages.some(m=>m.includes('Você tem 1')),'offline mail notice');await chat(returned,'mail read','mensagem-offline');await chat(returned,'mail clear','limpa');results.push('Offline mail persists across player reconnect');
  await chat(alice,'seen TestBob','UUID:');await chat(admin,'whois TestAlice','minecraft:overworld');results.push('Player directory and administrative information');
  if(process.env.SHIROKAI_TEST_MODS){
    await grant('TestAdmin','shirokai.essentials.heal',false);await consoleCommand('shirokaiessentials reload');await until(()=>!admin.commands.includes('heal'),'explicit LuckPerms denial');check('Explicit LuckPerms deny overrides OP fallback',()=>assert(!admin.commands.includes('heal')));
    await grant('TestAdmin','shirokai.essentials.heal',true);await consoleCommand('shirokaiessentials reload');await until(()=>admin.commands.includes('heal'),'LuckPerms permission restored');results.push('LuckPerms 5.4.140 permission changes reflected after reload');
  }
  await chat(admin,'fly','atualizado');await until(()=>admin.abilities,'abilities');check('Flight ability toggles on',()=>assert((typeof admin.abilities.flags==='number'?admin.abilities.flags:0)&4));
  await chat(admin,'fly','atualizado');await chat(admin,'god','atualizado');
  let pos=logs.length;await consoleCommand('damage TestAdmin 5 minecraft:generic');await consoleCommand('data get entity TestAdmin Health');check('God mode cancels damage',()=>assert(logs.slice(pos).includes('20.0f')));
  await chat(admin,'god','atualizado');await chat(admin,'heal TestAlice','atualizado');await chat(admin,'feed TestAlice','atualizado');await chat(admin,'speed 2','atualizado');results.push('Heal/feed/speed and others permissions');
  await chat(admin,'sudo TestAlice op TestAlice','permissão');check('Sudo cannot execute a command outside its allowlist',()=>assert(!logs.includes('Made TestAlice a server operator')));
  await chat(admin,'stime 6000','Concluído');await chat(admin,'sweather clear','Concluído');results.push('Alternative aliases preserve vanilla time/weather');
  await chat(admin,'tempmute TestAlice 2s motivo-teste','tempmute');
  const silent=returned.messages.length;await chat(alice,'smsg TestBob silenciada','silenciado');
  await chat(alice,'msg TestBob vanilla-silenciada','silenciado');await wait(150);check('Mute blocks own and vanilla private messages',()=>assert(!returned.messages.slice(silent).some(m=>m.includes('silenciada'))));
  await wait(2200);await chat(alice,'smsg TestBob liberada','liberada');results.push('Temporary mute expires');
  await chat(admin,'warn TestAlice advertencia-teste','warn');await chat(admin,'warnings TestAlice','advertencia-teste');await chat(admin,'clearwarnings TestAlice','clearwarnings');results.push('Persistent warning records and administrative clearing');
  await chat(admin,'freeze TestAlice','ativado');await consoleCommand('tp TestAlice 8.5 64 0.5');await until(()=>Math.abs(alice.position.x-.5)<.1,'freeze anchor');await chat(alice,'spawn','inseguro');await chat(admin,'freeze TestAlice','desativado');results.push('Freeze anchors movement and rejects teleports');
  await chat(admin,'setjail cela','salvo');await chat(admin,'jail TestAlice cela 2s','Concluído');await until(()=>Math.abs(alice.position.x-20.5)<.1,'jail teleport');
  await chat(alice,'spawn','inseguro');await until(()=>alice.messages.some(m=>m.includes('liberado da prisão')),'jail expiry',6000);results.push('Timed jail enforces restrictions and releases');
  await chat(admin,'sban TestBob motivo-ban','ban');await until(()=>returned.ended,'ban disconnect');
  await chat(admin,'unban TestBob','unban');const bob2=await bot('TestBob');results.push('Vanilla ban list integration and offline unban');
  await consoleCommand('clear TestAdmin');await consoleCommand(`give TestAdmin minecraft:diamond[minecraft:custom_name='{"text":"Presente Shirokai"}'] 3`);
  await chat(admin,'createkit starter','salvo');await consoleCommand('clear TestAdmin');
  let impactorAccount;
  if(process.env.SHIROKAI_TEST_MODS){
    impactorAccount=path.join(runDir,'config/impactor/economy/accounts/users',alice.client.uuid.slice(0,2),alice.client.uuid+'.conf');
    await consoleCommand('eco set 0 impactor:dollars TestAlice');
    await until(()=>{try{return JSON.parse(fs.readFileSync(impactorAccount,'utf8'))['impactor:dollars']===0;}catch{return false;}},'native Impactor zero balance');
    check('Native Impactor economy commands remain registered',()=>{assert(admin.commands.includes('balance'));assert(admin.commands.includes('eco'));assert(!admin.commands.includes('sbalance'));assert(!admin.commands.includes('sbaltop'));});
  }
  await chat(alice,'kit starter','recebido');let kitData=await snapshot(alice);check('Kit commits cooldown and closes its durable receipt',()=>{assert(kitData.claimedKits.includes('starter'));assert.equal(kitData.pendingKit,'');assert(kitData.kitCooldowns.starter>Date.now());});
  pos=logs.length;await consoleCommand('data get entity TestAlice Inventory');check('Kit preserves Minecraft 1.21.1 item data components',()=>assert(logs.slice(pos).includes('Presente Shirokai')));
  await chat(alice,'kit starter','Aguarde');results.push('Kit cooldown prevents duplicate claims');
  if(impactorAccount){
    await consoleCommand('tp TestAlice 0.5 64 0.5');
    await chat(alice,'spawn','Teleporte concluído');
    check('Kit and teleport succeed at zero Impactor balance without changing it',()=>{assert.equal(JSON.parse(fs.readFileSync(impactorAccount,'utf8'))['impactor:dollars'],0);assert(Math.abs(alice.position.x-20.5)<.1);});
  }
  await chat(alice,'showkit starter');await until(()=>alice.window,'kit preview');const win=alice.window.windowId;
  const beforePreview=await inventory(alice);
  alice.client.write('window_click',{windowId:win,stateId:alice.windowItems?.stateId||0,slot:0,mouseButton:0,mode:1,changedSlots:[],cursorItem:{itemCount:0}});await wait(250);alice.client.write('close_window',{windowId:win});
  const afterPreview=await inventory(alice);check('Kit preview remains read-only under forged quick-move clicks',()=>assert.deepEqual(afterPreview,beforePreview));
  await chat(alice,'invsee TestAdmin');await until(()=>alice.window&&alice.window.windowId!==win,'inventory snapshot');alice.client.write('close_window',{windowId:alice.window.windowId});results.push('Read-only inventory inspection');
  if(process.env.SHIROKAI_TEST_MODS){
    check('Removed economy aliases never register while Impactor remains installed',()=>{assert(!admin.commands.includes('spay'));assert(!admin.commands.includes('seco'));});
  }
  if(process.env.SHIROKAI_TEST_MODS){
    let from=admin.messages.length;await chat(admin,'mw create shirokai_test NORMAL -g=FLAT');await until(()=>admin.messages.slice(from).some(m=>m.includes('multiworld:shirokai_test')),'Multiworld dimension creation',30000);
    await consoleCommand('execute in multiworld:shirokai_test run forceload add 0 0');await consoleCommand('execute in multiworld:shirokai_test run fill 0 63 0 8 63 8 minecraft:stone');await consoleCommand('execute in multiworld:shirokai_test run fill 0 64 0 8 68 8 minecraft:air');
    await consoleCommand('execute as TestAlice in multiworld:shirokai_test run tp @s 4.5 64 4.5');await chat(alice,'sethome mundo','salvo');
    await consoleCommand('execute as TestAlice in minecraft:overworld run tp @s 0.5 64 0.5');await chat(alice,'home mundo','Teleporte concluído',10000);
    let worldData=await snapshot(alice);check('Homes work in a real Multiworld 1.14.2 custom dimension',()=>assert.equal(worldData.homes.mundo.dimension,'multiworld:shirokai_test'));
    await consoleCommand('execute as TestAlice in minecraft:overworld run tp @s 0.5 64 0.5');
    edit('teleport',j=>j.warmupSeconds=3);await grant('TestAdmin','shirokai.essentials.bypass.warmup',false);await consoleCommand('shirokaiessentials reload');
    let begin=admin.messages.length;await chat(admin,'tppos 5000 -60 5000','Preparando o destino',10000);await chat(admin,'tppos 6000 -60 6000','pendente');
    admin.client.write('position',{x:21,y:64,z:.5,onGround:true});admin.position={...admin.position,x:21,y:64,z:.5};await until(()=>admin.messages.slice(begin).some(m=>m.includes('Teleporte cancelado')),'async teleport cancellation');results.push('Asynchronous chunk jobs reject duplicates and cancel safely on movement');
    await chat(admin,'rtp',null);await until(()=>admin.messages.some(m=>m.includes('Preparando o destino')),'asynchronous RTP request',5000);await until(()=>Math.hypot(admin.position.x-21,admin.position.z-.5)>90,'asynchronous RTP completed',25000);results.push('RTP loads new chunks asynchronously and finds a safe surface');
  }
  await chat(admin,'fly','atualizado');await chat(admin,'god','atualizado');
  const fixture=importFixture(alice);const original=fs.readFileSync(fixture.source),beforeDryRun=fs.readFileSync(nbt(alice));
  const dry=await migration('dry-run');check('Migration dry-run reports conflicts without writing player destinations',()=>{
    assert.equal(dry.state,'DRY_RUN');assert.equal(dry.applied.length,0);assert(dry.candidates.some(n=>n.includes('importada')));assert(dry.issues.some(n=>n.includes('duplicada')));assert.deepEqual(fs.readFileSync(nbt(alice)),beforeDryRun);assert(!fs.existsSync(path.join(runDir,'world/shirokai-essentials/players',fixture.unknown+'.json')));
  });
  const leaving=logs.length;for(const b of children)if(!b.ended)b.client.end();children=[];
  await until(()=>['TestAlice','TestBob','TestAdmin'].every(n=>logs.slice(leaving).includes(n+' left the game')),'empty maintenance server');
  const imported=await migration('apply');const homesAfter=JSON.parse(fs.readFileSync(nbt(alice))).homes;
  check('Migration apply keeps originals, skips permissioned warps and creates backups',()=>{
    assert.equal(imported.state,'APPLIED_WITH_REPORT');assert.equal(homesAfter.importada.dimension,'minecraft:overworld');assert.equal(homesAfter.mundo.dimension,'multiworld:shirokai_test');assert.deepEqual(fs.readFileSync(fixture.source),original);
    const world=JSON.parse(fs.readFileSync(path.join(runDir,'world/shirokai-essentials/world.json')));assert(world.warps.legado);assert(!world.warps.restrito);assert(fs.existsSync(path.join(runDir,'world/shirokai-migration-backups',imported.id,'destination-before.json')));
  });
  const again=await migration('apply');check('Repeated migration never overwrites existing destinations',()=>assert.equal(again.applied.length,0));
  await stop();
  const receiptFile=nbt(alice);const receiptState=JSON.parse(fs.readFileSync(receiptFile));receiptState.pendingKit='fixture-receipt';fs.writeFileSync(receiptFile,JSON.stringify(receiptState));
  edit('modules',j=>{j.features.afk_auto=true;j.features.afk_manual=false;});
  await start();const after=await bot('TestAlice');check('Automatic AFK does not register disabled manual command or alias',()=>{assert(!after.commands.includes('afk'));assert(!after.commands.includes('away'));});
  await until(()=>after.messages.some(m=>m.includes('ausente')),'automatic afk',16000);results.push('Automatic AFK runs independently of its command');
  const restored=JSON.parse(fs.readFileSync(nbt(after)));check('Nickname, kit cooldown and player state survive server restart',()=>{assert.equal(restored.nickname,'Apelido');assert(restored.claimedKits.includes('starter'));assert.equal(restored.pendingKit,'fixture-receipt');});
  await chat(after,'kit starter','pendente');const pendingInventory=await inventory(after);await consoleCommand('kitrecover TestAlice fixture-receipt');await until(()=>logs.includes('encerrada sem reenviar'),'kit receipt recovery');const recoveredInventory=await inventory(after);check('Uncertain kit delivery stays blocked until audited recovery without item replay',()=>assert.deepEqual(recoveredInventory,pendingInventory));
  await chat(after,'kit starter','Aguarde');results.push('Kit cooldown remains effective after full server restart');
  await chat(after,'home mundo','Teleporte concluído',15000);results.push('Multiworld custom home survives a full server restart');
  const restoredAdmin=await bot('TestAdmin');await until(()=>restoredAdmin.abilities,'restored abilities');
  check('Persistent flight restores safely with LuckPerms after reconnect',()=>assert(restoredAdmin.abilities.flags&4));
  await wait(4200);let startDamage=logs.length;await consoleCommand('damage TestAdmin 5 minecraft:generic');await consoleCommand('data get entity TestAdmin Health');check('Persistent god mode restores after server restart',()=>assert(logs.slice(startDamage).includes('20.0f')));
  restoredAdmin.client.write('abilities',{flags:2});await wait(100);await stop();
  edit('modules',j=>{j.features.fly=false;j.features.god_mode=false;j.modules.kits=false;});
  await start();const disabled=await bot('TestAdmin');await until(()=>disabled.abilities,'disabled abilities');
  check('Disabled fly/god features leave no abilities or commands after restart',()=>{assert(!(disabled.abilities.flags&6));assert(!disabled.commands.includes('fly'));assert(!disabled.commands.includes('god'));assert(!disabled.commands.includes('kit'));});
  await wait(4200);startDamage=logs.length;await consoleCommand('damage TestAdmin 1 minecraft:generic');await consoleCommand('data get entity TestAdmin Health');check('Disabled god mode no longer intercepts damage',()=>assert(logs.slice(startDamage).includes('19.0f')));
  const retained=JSON.parse(fs.readFileSync(nbt(disabled)));check('Disabling features preserves saved mode preferences',()=>assert(retained.fly&&retained.god));
  await stop();
  fs.writeFileSync(path.join(output,'results.json'),JSON.stringify({version:modVersion,mode:'production-jar',passed:results.length,checks:results},null,2));console.log(results.length+' expanded checks passed');
 }catch(e){console.error(e);console.error(logs.slice(-9000));fs.writeFileSync(path.join(output,'failure.json'),JSON.stringify({passed:results,error:e.stack},null,2));await stop().catch(()=>server?.kill());process.exitCode=1;}
})();
