# Arquitetura — 0.3.0

O entrypoint dedicado Fabric compõe serviços com dependências explícitas. Não há código de cliente nem APIs Bukkit/Paper/Vault.

| Pacote | Responsabilidade |
| --- | --- |
| `config` | Configurações versionadas, módulos/funcionalidades/comandos, validação e defaults novos sem reativar flags antigas |
| `permissions` | LuckPerms opcional, contextos atuais, negações explícitas, tiers e limites |
| `commands` | Brigadier, controle comum de permissão e encaminhamento para serviços |
| `conflicts` | Planejamento anterior ao registro, políticas, auditoria posterior e relatório |
| `persistence` | Escrita atômica, backups, schema 1→2 dos dados, snapshots e diretório por UUID |
| `teleport` | Warmup, cooldown, pedidos, segurança, chunks assíncronos, tickets e autorização revalidada |
| `chat` | Mensagens, correio, ignore, respostas, apelidos, atividade e AFK |
| `utilities` | Modos, inventários e telas vanilla de bancada/bigorna |
| `moderation` | Punições próprias, duração, hierarquia, freeze/jail e integração com bans vanilla |
| `kits` | Codec de itens, planejamento de capacidade, persistência de cooldown e recibos |
| `integrations` | Consulta pública de batalha Cobblemon por UUID, com verificação da assinatura e invocação |
| `migrations` | Adaptador NBT do Essential Commands 0.35.2 |

## Persistência

Os dados gerais/jogadores usam schema 2; schema 1 é promovido em memória e gravado com backup quando houver uma atualização real. Schema desconhecido é rejeitado. Configurações e punições próprias continuam com schema 1. Não se deve alterar manualmente o schema para fazer downgrade.

O padrão de mutação é copiar, validar, gravar e só então publicar o novo estado. Cada arquivo é substituído por rename atômico, após sincronizar seu conteúdo, mantendo `.bak` da revisão anterior. Sistemas de arquivos sem rename atômico falham sem substituir o original. Não há restauração destrutiva automática de backups.

Operações comuns usam leituras pequenas em cache. A moderação percorre apenas IDs com freeze/jail ativos, sem copiar listas de avisos por tick. Nomes têm índice; leitura de warp/spawn e preferências de mensagens evita serializar todo o diretório de jogadores. Escritas JSON ainda são síncronas na thread do servidor; devem ser medidas em servidores grandes/discos lentos. Nenhum processamento de mundo é executado em callbacks de worker.

## Teleporte e integrações

O padrão recusa chunks descarregados. Com `asyncChunkLoading=true`, a thread do servidor agenda futures de chunks completos, sem chamar `join`, esperar ou executar um loop de espera. A conclusão retorna à thread do servidor; então são revalidados jogador, mundo, configuração, permissão, destino e restrições. Tickets têm prazo, orçamento e remoção no sucesso, cancelamento, desconexão e reload. O RTP escolhe a superfície após a carga quando necessário.

Um pedido é limitado por remetente/destinatário; warmups e cargas têm limite global. Movimento/dano cancelam sem executar a ação posterior. Dimensões são IDs completos; a API vanilla resolve também mundos do Multiworld. Warps privados/nomeados são reconsultados no fim do warmup e da carga, impedindo acesso após revogação ou mudança do destino.

O Cobblemon é carregado opcionalmente por sua API pública `BattleRegistry.getBattleByParticipatingPlayerId(UUID)`. A assinatura exata e uma consulta sem efeitos colaterais são verificadas. API incompatível ou falha de consulta bloqueiam conservadoramente os teleportes quando a proteção está ativada. O mod não modifica batalhas, armazenamento de Pokémon ou NPCs.

## Mensagens, inventários e kits

MiniMessage interpreta apenas templates de configuração. Valores dinâmicos usam `Placeholder.unparsed`, depois são convertidos para texto vanilla. O formatador público só substitui o chat quando explicitamente habilitado; formatação e automações respeitam o estado efetivo do módulo, inclusive `FAIL_MODULE`.

Invsee e prévias de kits usam snapshots com handler que rejeita `onSlotClick` e `quickMove`. Inventários reais não são usados como fonte retirável da prévia. Outros menus usam handlers vanilla.

Kits simulam a capacidade em cópias do inventário, respeitando componentes e limites de pilha. Antes de dar itens, o mod grava um recibo com inventários anterior/posterior e o bloqueio persistente da entrega. Depois altera o inventário, solicita a gravação vanilla e encerra o recibo. Como não existe transação única entre JSON, NBT e comandos de recompensa externos, qualquer entrega incerta permanece bloqueada para revisão, sem replay automático. Recuperação administrativa não reenvia itens. O salvamento vanilla de jogadores usa a API pública de salvar todos; esse custo deve ser considerado em servidores grandes.

## Mixin limitado à versão suportada

Há um mixin em `CommandManager.execute(ParseResults, String)` de Minecraft 1.21.1. Os eventos Fabric de chat não cobrem `/msg` vanilla. O hook verifica as raízes de mensagem configuradas para jogadores mutados e registra atividade para AFK; não remove nós nem altera comandos de terceiros. É inerte com o recurso desligado. A aplicação é obrigatória na versão suportada, com teste em JAR remapeado e nos Loaders 0.16.14/0.17.2; Minecraft diferente é rejeitado pelo metadata do mod.

## Limites deliberados

O módulo econômico e as dependências de API do Impactor foram removidos na 0.2.2. Teleporte e kits não consultam nem alteram saldos. Ao carregar configurações de versões anteriores, as entradas do módulo/comandos econômicos são descartadas apenas em memória; o antigo `economy.json` é ignorado.

O importador cobre o formato identificado 0.35.2 e não adivinha outros schemas. Não há vanish, editor remoto de inventário ou paridade declarada com todos os recursos do EssentialsX. A validação Cobblemon inclui PvP real até o turno 1 e bloqueios de teleporte, sem testar a interface gráfica nem vitória/derrota.

## API de integração Core

EssentialsBridge protocolo1 usa UUID e tipos Java padrão. Attach exige dono único; detach não remove o dono de outro consumidor. Leituras usam somente perfis carregados. O Core consulta a API opcional por reflexão e nunca lê JSONs privados. Sem o Core, os serviços próprios permanecem ativos.
