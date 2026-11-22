# Shirokai Essentials 0.5.0-guard.1

Mod **Minecraft Java 1.21.1 / Fabric / Java 21, somente servidor**. A linha 0.2 acrescenta chat, correio offline, AFK, informações, utilidades, administração, moderação, kits e migração ao núcleo de homes/warps/spawn/teleporte da 0.1.0.

O projeto segue [a especificação fornecida](docs/ESPECIFICACAO.txt), com as limitações descritas abaixo. Não usa Bukkit, Paper, Spigot ou Vault. Conforme a alteração solicitada, desde a versão 0.2.2 não há economia do Essentials: **teleportes e kits são gratuitos**, mantendo permissões, cooldowns e proteções. O Impactor funciona de forma independente.

## Novas utilidades 0.5.0-guard.1

`/lixeira` tem 18 espaços, revisão e confirmação antes do descarte; cancelar ou fechar devolve os itens. `/compactar` transforma grupos de nove barras na mão em blocos, preservando sobras e recusando a operação se faltar espaço. `/cortador` e `/tear` abrem interfaces nativas de trabalho.

[Permissões, funcionamento e limites](docs/UTILIDADES-0.5.0-guard.1.md). Pacotes em [releases/v0.5.0-guard.1](releases/v0.5.0-guard.1). O hotfix do Ender Chest e as utilidades da 0.4.0 foram mantidos.

## Comandos de utilidades 0.4.0-guard.1

`/craft` e `/bancada`, `/bigorna`, `/rebolo`, `/reparar`, `/luz` e `/esquentar`. Visão noturna liga/desliga pelo mesmo comando. Fundição processa a pilha na mão, com recarga persistida de **5 minutos**, configurável em `utilities.json`. Nomes ingleses antigos continuam disponíveis; os aliases não mudam as permissões existentes.

[Comandos, permissões, configuração e limites](docs/UTILIDADES-0.4.0-guard.1.md). Pacotes em [releases/v0.4.0-guard.1](releases/v0.4.0-guard.1).

## Hotfix do Ender Chest

A 0.3.3-guard.1 impede que a expansão automática do LuckPerms transforme `shirokai.essentials.enderchest` em acesso aos baús de outros jogadores. Mantenha essa permissão para o baú próprio; acesso a terceiros exige `.enderchest.others` ou privilégios administrativos configurados. IDs e configurações existentes foram preservados.

Build e 51 testes automatizados aprovados, mais 17 verificações em servidor com LuckPerms real e jogadores simulados. [Reprodução, resultados e limites](docs/ENDERCHEST-0.3.3-guard.1.md). Pacotes em [releases/v0.3.3-guard.1](releases/v0.3.3-guard.1).

## Instalar e atualizar

1. Faça backup do mundo e das configurações.
2. Substitua o JAR antigo pelo `shirokai-essentials-0.5.0-guard.1.jar` em `mods/`. Não mantenha duas versões do mesmo mod.
3. Use Fabric API para Minecraft 1.21.1 e Java 21. **Com Cobblemon 1.8.3, Fabric Loader deve ser 0.17.2 ou superior.** O mod também foi testado sem Cobblemon no Loader 0.16.14.
4. Reinicie o servidor. Clientes não precisam instalar **Shirokai Essentials**; continuam precisando dos mods cliente exigidos pelo restante do servidor, como Cobblemon.
5. Ative os módulos novos que desejar em `modules.json`. Eles vêm **desligados por padrão** para preservar o comportamento da instalação anterior.

As configurações mantêm schema 1. Os **dados do Shirokai passam ao schema 2**, com leitura do schema 1 e backup na primeira gravação. Uma versão antiga rejeita schema 2, evitando apagar correio e demais campos novos. Para voltar à 0.1.0, restaure o backup completo correspondente; não altere o número do schema manualmente.

Na atualização das versões antigas, entradas antigas de economia em `modules.json`, `commands.json`, `permissions.json` e `conflicts.json` são ignoradas em memória; seus arquivos são preservados. `economy.json` não é lido nem gerado. Não é necessário editar configurações antigas ou importar dados de outro mod.

## Compilar

Requer **JDK 21 completo**, incluindo `javac`, e acesso aos repositórios Gradle/Maven:

```sh
./gradlew build
```

No Windows: `gradlew.bat build`. O wrapper fixa Gradle 8.12.1 e verifica a distribuição por SHA-256.

Artefato instalável: `build/libs/shirokai-essentials-0.5.0-guard.1.jar`. O arquivo `-sources.jar` contém fontes. Adventure/MiniMessage são incluídos no JAR; LuckPerms é uma integração opcional e não é redistribuído. O Essentials não depende do Impactor. Nenhuma tarefa de build publica release ou faz deploy.

Integração opcional com **Shirokai-tab/Core 0.18.0-dev**: apelidos, AFK, proteção de chat e placeholders de homes/kits. Veja [instalação e opções](docs/INTEGRACAO-SHIROKAI-TAB.md).

## Recursos

| Módulo | Recursos |
| --- | --- |
| Teleporte | TPA/TPAHere, aceite/recusa/cancelamento/bloqueio, cooldown, warmup, cancelamento por movimento/dano, `/back`, top, jump, near, RTP e chunks assíncronos opcionais |
| Homes, warps e spawn | UUID, dimensão completa, orientação, limites, homes nomeadas, warps privados, permissões individuais e spawn global/por mundo |
| Chat | Mensagens privadas, respostas, ignore persistente, correio offline paginado, bloqueio de mensagens, espionagem administrativa optativa |
| Informações | MOTD, regras, ajuda, info, listagem, seen/whois, apelidos sanitizados e consulta ao nome real |
| AFK | Manual e automático independentes; retorno por movimento ou atividade de chat/comando |
| Utilidades | Fly/god opcionais e persistência configurável, heal/feed, speed, invsee somente leitura, ender chest, bancada, bigorna, repair, hat, clearinventory, ping e memória |
| Administração | Time/weather quando não colidem; sudo desligado por padrão, com lista de comandos permitidos, hierarquia, auditoria e execução no nível vanilla 0 |
| Moderação | Kick, ban/tempban/unban via lista vanilla, mute/tempmute/unmute, avisos, prisões temporárias, freeze e hierarquia de permissões |
| Kits | Captura do inventário, JSON de itens com data components 1.21.1, prévia somente leitura, entrega integral, cooldown, recibos e recuperação sem reenvio automático |
| Migração | Importador NBT do **Essential Commands 0.35.2**, com dry-run, conflitos, hashes, backups e aplicação sem sobrescrita |

O Essentials não registra `/balance`, `/bal`, `/money`, `/pay`, `/baltop` ou `/eco`, não consulta saldos e não cobra por teleportes ou kits. Use os comandos do Impactor para gerenciar a economia do servidor.

## Configurar

A pasta `config/shirokai-essentials/` contém 15 arquivos JSON com schema 1: `config`, `modules`, `commands`, `conflicts`, `permissions`, `messages`, `teleport`, `homes`, `warps`, `spawn`, `chat`, `utilities`, `moderation`, `kits` e `integrations`.

[Exemplos completos](examples/config/shirokai-essentials) acompanham o projeto. Para gerar em outra pasta:

```sh
./gradlew generateExampleConfig -PexampleDir=build/exemplos
```

Exemplo de ativação dos módulos novos:

```json
"modules": {
  "teleport": true, "homes": true, "warps": true, "spawn": true,
  "chat": true, "utilities": true, "administration": true,
  "moderation": true, "kits": true
}
```

Use `features` para controlar funções individuais. `motd_auto` e `afk_auto` são independentes de `/motd` e `/afk`. `god_mode`, `nicknames`, automações de mensagens e formatação pública são optativos. Mute, freeze, jail e avisos têm as flags independentes `moderation_chat`, `moderation_freeze`, `moderation_jail` e `moderation_warnings`; desativá-las preserva os registros. A persistência de fly/god fica em `utilities.json`; gerenciar bans vanilla exige `manageVanillaBans=true` em `moderation.json`.

Cada comando tem `enabled`, `aliases` e `aliasEnabled` em `commands.json`. Um comando desligado não registra seus aliases. Aliases compartilham a permissão da ação correspondente. Para funções novas em configurações antigas, os defaults são adicionados em memória; consulte os exemplos para tornar as opções explícitas. Flags antigas, inclusive comandos/permissões antigos omitidos, não são reativadas pela atualização.

**Reinicie após alterar módulos, funcionalidades, comandos, aliases, políticas de conflito, ativação de sudo, gerência de bans vanilla ou formatação pública.** O reload preserva a estrutura de registro, cancela pendências de teleporte, recarrega opções dinâmicas e atualiza a árvore enviada aos clientes. JSON inválido mantém a configuração anterior.

## Conflitos e mensagens

A política padrão preserva raízes existentes. Isso inclui `/tp`, `/msg`, `/help`, `/list`, `/me`, `/time`, `/weather`, `/kick` e `/ban` vanilla e comandos do Impactor. Para usar a implementação Shirokai em uma raiz ocupada, configure um alias disponível e a política `USE_ALTERNATIVE_ALIAS`, por exemplo:

```json
// commands.json, dentro de commands
"msg": {"enabled": true, "aliases": ["smsg"], "aliasEnabled": {}}
```

```json
// conflicts.json, dentro de commands
"msg": "USE_ALTERNATIVE_ALIAS"
```

Os comentários acima são explicativos; remova comentários ao editar JSON. `FAIL_MODULE` desliga o módulo inteiro. Colisões posteriores ao registro são diagnosticadas, sem remover nós de terceiros. Relatório: `config/shirokai-essentials/conflicts-report.json`.

Mensagens e títulos ficam em `messages.json`, com templates MiniMessage. Placeholders com conteúdo de jogadores são inseridos como texto não interpretado. A formatação pública é preservada por padrão; só é substituída com `chat.formatPublicChat=true`. Metadados de prefixo LuckPerms são tratados como texto e não concedem permissões. `chat.timeZone` usa `America/Sao_Paulo` por padrão.

## Dados e limites

Dados em `<mundo>/shirokai-essentials/`: `players/<UUID>.json`, `world.json`, `moderation-data.json`, `audit.jsonl` e `kit-receipts/`. Configuração e dados permanecem separados; desligar módulos não apaga seus arquivos. Escritas são atômicas e mantêm `.bak`; erros de leitura nunca são silenciosamente substituídos por defaults.

A segurança de teleporte é conservadora: valida borda, altura, dimensão, chão, colisão e perigos próximos. Chunks descarregados são recusados no padrão. Ative `asyncChunkLoading` para carregá-los sem aguardar futures na thread do servidor; pedidos têm limite, prazo de espera e tickets liberados no cancelamento. O RTP pode falhar se o candidato não tiver superfície segura.

A integração Cobblemon 1.8.3 consulta a API pública de batalhas por UUID. Se o mod estiver presente e a consulta for incompatível, o bloqueio é conservador enquanto `blockCobblemonBattles=true`. A versão 0.3.0 cancela imediatamente teleportes pendentes ao entrar em batalha. O teste inicia PvP real pelo BattleBuilder, chega ao turno 1 no Showdown e verifica bloqueio/retomada de teleportes com dois clientes de protocolo. Esses clientes não renderizam a interface Cobblemon.

`invsee` é uma fotografia somente leitura, não um editor de inventário remoto. Apelidos afetam as mensagens/listas do mod e o chat público quando a formatação estiver explicitamente ativada. Não há vanish nesta versão. O mod não promete paridade total com toda a cobertura do EssentialsX.

Leia [comandos/permissões](docs/COMANDOS.md), [arquitetura](docs/ARQUITETURA.md), [migração](docs/MIGRACAO.md) e [testes](docs/TESTES.md).

## Integração Guard 0.4.0 — entrada por comando

A versão 0.3.2-guard.1 captura a autorização por comando exato e a mantém somente
na operação de teleporte correspondente, inclusive durante warmup e carga de chunks.
Para autorizar apenas `/warp loja` na região `loja`, use no Guard atualizado:

```mcfunction
/sg flag loja entry-mode command-only
/sg entry-command loja add warp loja
```

Outros warps, TPA aceito e `/back` continuam sujeitos à proteção do destino e não
herdam o token. Aliases precisam de cadastro pelo comando completo digitado.
Teleporte de sistema não usa a autorização de um warmup pendente. Recusas do Guard
ocorrem antes de gravar histórico/cooldown. Sem Guard, os fluxos comuns permanecem.
A integração de `/raid entrar` usa a API anterior e continua independente.

## Cosméticos simples (0.6.7-staggered-trails.1)

`/cosmeticos`: cor do nick, rastro contínuo interpolado e escalonado no tempo, aura em espiral, entrada, vitória e raid. A personalização permite ajustar velocidade, tamanho, densidade e direção com prévia e limites administrativos. Funciona somente no servidor; o rastro distribui seu lote por uma janela de 6 ticks para não desaparecer em blocos. [Configuração, permissões e limites](docs/cosmetics/README.md).
