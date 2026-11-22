# Comandos e permissões — 0.5.0-guard.1

Todos os nós próprios começam com `shirokai.essentials.`. As tabelas mostram o sufixo. Por padrão, comandos administrativos exigem OP nível 2 se `opFallback=true`; console segue nível 4. Com `requireLuckPerms=true`, jogadores precisam de concessões LuckPerms explícitas, sem fallback OP. Uma negação explícita do LuckPerms prevalece sobre OP.

Aliases configurados usam a permissão do comando principal. As raízes `tell`, `whisper`, `reply`, `bal` e `money`, quando habilitadas separadamente, usam seus respectivos nós. Comandos externos preservados continuam usando suas próprias permissões.

## Destinos e teleporte

| Comando | Permissão | Padrão |
| --- | --- | --- |
| `/home [nome]`, `/homes`, `/sethome [nome]`, `/delhome <nome>`, `/renamehome <nome> <novo>` | `home`, `homes`, `sethome`, `delhome`, `renamehome` | jogador |
| `/spawn`, `/warp <nome>`, `/warps` | `spawn`, `warp`, `warps` | jogador |
| `/setspawn`, `/setwarp <nome> [public]`, `/delwarp <nome>` | `setspawn`, `setwarp`, `delwarp` | administrativo |
| `/tpa <jogador>`, `/tpahere <jogador>` | `tpa`, `tpahere` | jogador |
| `/tpaccept [jogador]`, `/tpdeny [jogador]`, `/tpacancel`, `/tpatoggle` | nome do comando | jogador |
| `/back` | `back` | jogador |
| `/tp <jogador>`, `/tppos <x> <y> <z>`, `/top`, `/jump`, `/near`, `/rtp` | nome do comando | administrativo |
| `/tphere <jogador>`, `/tpall` | nome do comando + `.others` | administrativo |

`bypass.cooldown`, `bypass.warmup` e `bypass.homelimit` controlam exceções. Limites por tier: `home.multiple.<tier>` conforme `permissions.json`; vence o maior limite explicitamente concedido. Warps individuais usam `warp.<nome>` quando habilitado; `warp.private` permite acesso aos privados de terceiros. Permissões e alterações do warp são revalidadas no warmup e após carregamento de chunks.

Com Shirokai-tab/Core 0.18.0-dev, apelidos/AFK e controles de chat usam a [ponte opcional](INTEGRACAO-SHIROKAI-TAB.md).

## Chat e informações

| Comando | Permissão | Padrão |
| --- | --- | --- |
| `/msg`, `/tell`, `/whisper <jogador> <mensagem>` | `msg`, `tell`, `whisper` | jogador |
| `/r`, `/reply <mensagem>` | `r`, `reply` | jogador |
| `/ignore`, `/unignore <nome ou UUID conhecido>` | nome do comando | jogador |
| `/msgtoggle` | `msgtoggle` | jogador |
| `/mail`, `/mail send <jogador> <mensagem>`, `/mail read [página]`, `/mail clear` | `mail` | jogador |
| `/afk` | `afk` | jogador |
| `/motd`, `/rules`, `/info`, `/help`, `/list`, `/seen <jogador>`, `/realname <apelido>` | nome do comando | jogador |
| `/nick <apelido ou off> [jogador]` | `nick`; terceiros: `nick.others` | administrativo |
| `/whois <jogador>`, `/broadcast <mensagem>`, `/me <mensagem>`, `/socialspy` | nome do comando | administrativo |

Correio offline resolve UUID/nomes já conhecidos pelo mod; não inventa UUID a partir de um nome desconhecido. Há limite de tamanho, capacidade e intervalo entre envios. Ignore e bloqueio são persistentes. A espionagem vale para as mensagens privadas deste mod e exige permissão atual e ativação explícita; não intercepta mensagens privadas de outros mods.

## Utilidades e administração

| Comando | Permissão |
| --- | --- |
| `/fly`, `/god`, `/heal`, `/feed`, `/repair`, `/hat`, `/clearinventory`, `/invsee`, `/enderchest [jogador]` | nome do comando; terceiros: `<comando>.others` |
| `/speed <1..10> [jogador]`, `/speed reset [jogador]` | `speed`; terceiros: `speed.others` |
| `/workbench`, `/anvil` | nome do comando |
| `/ping` | `ping` — jogador por padrão |
| `/gc` | `gc` — informa memória/jogadores, sem forçar coleta |
| `/sudo <jogador> <comando sem barra>` | `sudo` + `sudo.others` + `sudo.command.<raiz>` |
| `/time <ticks de 0 a 24000>`, `/weather <clear/rain/thunder>` | `time`, `weather` |

Sudo exige `allowSudo=true`, raiz explicitamente permitida, hierarquia e auditoria. O comando é executado no contexto do jogador alvo com nível vanilla 0. A lista permitida deve conter apenas ações administrativas cuidadosamente escolhidas; o padrão é `me`. OP não deve usar sudo como substituto de uma configuração de permissões adequada.

GUIs exigem um jogador como emissor; `invsee` é somente leitura e bloqueia ações de clique/quick-move. Ender chest é o inventário real do jogador online. Os demais comandos com alvo podem ser usados pelo console quando não dependem de uma GUI ou posição pessoal. Alvos de utilidades devem estar online.

## Moderação

| Comando | Permissão |
| --- | --- |
| `/kick`, `/ban`, `/mute`, `/warn <jogador> <motivo>` | nome do comando |
| `/tempban`, `/tempmute <jogador> <duração> <motivo>` | nome do comando |
| `/unban`, `/unmute`, `/warnings`, `/clearwarnings <jogador>` | nome do comando |
| `/setjail <nome>`, `/deljail <nome>` | nome do comando |
| `/jail <jogador> <prisão> [duração]`, `/unjail <jogador>`, `/freeze <jogador>` | nome do comando |

Durações: `30s`, `10m`, `2h`, `7d`, até 365 dias. Bans exigem `manageVanillaBans=true` e usam a lista vanilla. As ações próprias têm auditoria e exigem tier superior ao do alvo. No LuckPerms, use `moderation.rank.helper`, `.moderator` e `.admin`; `moderation.exempt` protege o alvo. Console administrativo pode operar offline. Se o usuário LuckPerms offline não estiver em cache, a ação de um jogador falha de forma conservadora; use console para essa manutenção.

Mute bloqueia chat público, mensagens próprias e as raízes configuradas em `mutedCommandRoots` — incluindo os aliases vanilla conhecidos. Para comandos de chat de outros mods, configure a raiz correspondente e confirme a compatibilidade. O módulo não substitui o sistema de punição de terceiros.

## Kits gratuitos

| Comando | Permissão |
| --- | --- |
| `/kit <nome>`, `/kits`, `/showkit <nome>` | `kit`, `kits`, `showkit` + `kit.<nome>` para acesso |
| `/createkit <nome>`, `/delkit <nome>` | nome do comando |
| `/resetkit <jogador> <nome>` | `resetkit` |
| `/kitrecover <jogador> <id do recibo>` | `kitrecover` |

Kits aceitam `permission` como sufixo alternativo do nó Shirokai. `kit.bypass.cooldown` é separado do bypass de teleporte. A entrega recusa inventário sem espaço; um recibo pendente bloqueia novas entregas. `kitrecover` encerra a pendência **sem reenviar itens** e preserva o cooldown; confira inventário, NBT e recibo antes de usá-lo. Recompensas por comando exigem ativação administrativa e lista de raízes permitidas, sem garantia transacional entre comandos externos.

Teleporte e kits não têm cobrança. A economia foi removida do Essentials; comandos, saldos e administração do Impactor continuam independentes.

## Diagnóstico e migração

`/shirokaiessentials status`, `version`, `reload`, `conflicts` e `conflicts report` usam, respectivamente, `status`, `version`, `reload`, `conflicts` e `conflicts.report`. Cada subcomando pode ser desabilitado em `commands.json`.

`/ecimport dry-run <cópia relativa ao mundo>` exige `ecimport`; `/ecimport apply <cópia>` também exige `ecimport.apply`, console, servidor vazio e Essential Commands inativo. Veja [MIGRACAO.md](MIGRACAO.md).

### Ender Chest: próprio e de terceiros

`shirokai.essentials.enderchest` libera `/enderchest` para o próprio jogador. Para `/enderchest <outro jogador>`, conceda também `shirokai.essentials.enderchest.others` apenas à administração. Desde a 0.3.3-guard.1, shorthand do nó principal não libera terceiros. Wildcards administrativos e fallback OP configurado continuam válidos; negações explícitas prevalecem. Aliases seguem a mesma verificação.

### Bancadas, visão noturna e fundição

`/craft` e `/bancada` usam `workbench`; `/bigorna` usa `anvil`; `/rebolo` usa `grindstone`; `/reparar` usa `repair`. Prefixo das permissões: `shirokai.essentials.`. `/luz` e `/esquentar` usam os nós com seus próprios nomes e atuam somente no próprio jogador. `/esquentar` tem recarga persistente de 300 segundos. [Detalhes e configuração](UTILIDADES-0.4.0-guard.1.md).

### Lixeira, compactação e outras bancadas

`/lixeira`, `/compactar`, `/cortador` e `/tear` pertencem ao módulo `utilities` e usam as permissões `shirokai.essentials.<comando>`. A lixeira exige revisão e confirmação do descarte. Compactação usa apenas a pilha na mão, preservando as sobras. [Funcionamento, configuração e limites](UTILIDADES-0.5.0-guard.1.md).
