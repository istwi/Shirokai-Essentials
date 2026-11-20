# Utilidades e aliases — 0.4.0-guard.1

Esta versão parte do hotfix 0.3.3-guard.1 e mantém a proteção do Ender Chest. As branches anteriores são preservadas.

| Comando | Função | Permissão |
| --- | --- | --- |
| `/craft`, `/bancada`, `/workbench` | Bancada virtual 3×3 | `shirokai.essentials.workbench` |
| `/bigorna`, `/anvil` | Bigorna virtual, com receitas e custo normal de níveis | `shirokai.essentials.anvil` |
| `/rebolo`, `/grindstone` | Rebolo virtual, com o funcionamento vanilla | `shirokai.essentials.grindstone` |
| `/reparar`, `/repair` | Repara o item na mão principal | `shirokai.essentials.repair` |
| `/luz` | Alterna visão noturna, sem partículas | `shirokai.essentials.luz` |
| `/esquentar` | Funde a pilha de minério na mão principal | `shirokai.essentials.esquentar` |

Os comandos pertencem ao módulo `utilities`, que precisa estar ativo em `config/shirokai-essentials/modules.json`. As permissões não são concedidas automaticamente a jogadores comuns; regras de OP/LuckPerms existentes continuam aplicadas. Os aliases usam as permissões dos comandos originais. `/luz` e `/esquentar` atuam somente no próprio jogador.

## Fundição

A recarga escolhida foi **5 minutos**. Em `config/shirokai-essentials/utilities.json`:

```json
"smeltCooldownSeconds": 300
```

A lista `smeltItems` permite ferro, ouro e cobre brutos; seus blocos de minério, incluindo ardósia; minério de ouro do Nether; e detritos ancestrais. O resultado vem da receita de forno registrada no servidor, inclusive de datapacks: detritos viram fragmentos de netherita, não barras de netherita. Para minérios de mods, acrescente o ID à lista; é necessária uma receita de forno válida.

Toda a pilha na mão é processada. Não utiliza combustível nem concede experiência de forno. Se o resultado de uma receita especial não couber em uma pilha, a operação é recusada sem consumir o minério ou iniciar recarga; use uma quantidade menor. Alimentos e itens fora da lista não são convertidos. Componentes do resultado seguem a receita, sem copiar nomes/dados personalizados do minério para a barra.

O próximo horário permitido é gravado no perfil do jogador, no mundo, e não é removido ao desconectar. Reabrir o servidor não elimina a recarga. O teste de persistência reabre o DataStore; não se promete atomicidade entre esse perfil e todos os arquivos de inventário do Minecraft durante uma queda abrupta.

## Visão noturna e interfaces

`/luz` adiciona visão noturna com duração infinita ou remove a visão noturna existente. Não remove outros efeitos. Assim como o efeito vanilla, pode ser removido por leite/morte; use o comando novamente para ativar.

Bancada e rebolo usam os mecanismos nativos de receitas e experiência. Não precisam de um bloco físico próximo. Fechar os menus devolve os insumos pelo comportamento do inventário vanilla; se não houver espaço, o jogo pode soltá-los no mundo. A bigorna mantém o custo de níveis, sem danificar um bloco físico de bigorna.

## Atualização de configurações

O primeiro carregamento adiciona os aliases novos aos comandos existentes, preservando aliases personalizados, flags `enabled` e `aliasEnabled`. `commands.json` ganha `utilityAliasesVersion: 1`; um backup é criado na atualização de um arquivo antigo. Depois dessa migração, aliases removidos pelo administrador não são recriados a cada inicialização. Novos campos de fundição são acrescentados a `utilities.json`, preservando os demais campos. Configurações inválidas são rejeitadas antes de salvar a migração.

Conflitos com comandos de outros mods continuam seguindo a política de conflitos existente. Reinicie o servidor para registrar os novos comandos e aliases. O `/reparar` mantém o comportamento e permissões administrativas do `/repair` existente, incluindo o argumento opcional de jogador.

## Verificação

Build completo e 55 testes JUnit aprovados, incluindo migração idempotente de aliases, preservação de configurações, validação da recarga e persistência após reabrir o DataStore.

As 40 verificações de integração passaram. Os resultados estão em [utilities-0.4.0-guard.1](utilities-0.4.0-guard.1). O ensaio usa servidor Minecraft 1.21.1, Java 21.0.9, Fabric Loader 0.17.2, Fabric API 0.116.7+1.21.1 e LuckPerms 5.4.140, com jogadores/conexões simulados, dispatcher, inventários, receitas e screen handlers reais. Não usa clientes gráficos. As 17 verificações do hotfix do Ender Chest continuam no fixture, seguidas dos testes das novas utilidades.

Para repetir: `./gradlew build remapPermissionFixtureJar`, servidor descartável em loopback com o fixture separado e `-Dessentials.permissionChecks=true`, módulo `utilities` ativo e alias `ec` para Ender Chest. Não instale o fixture em produção. As suítes históricas completas de combate/Core não foram repetidas nesta alteração.

A variante Zorykai preserva a formatação existente de mensagens e traduz somente os rótulos visuais conhecidos. O empacotador reaplica os rótulos nas classes novas, sem reaproveitar implementações antigas; somente o renderizador de mensagens, comprovadamente idêntico à base, mantém a formatação binária anterior.
