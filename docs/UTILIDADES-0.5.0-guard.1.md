# Lixeira, compactação, cortador e tear — 0.5.0-guard.1

A versão parte da 0.4.0-guard.1, preservando os comandos anteriores, a recarga de cinco minutos do `/esquentar` e o hotfix do Ender Chest.

| Comando | Função | Permissão |
| --- | --- | --- |
| `/lixeira` | Abre uma lixeira com revisão e confirmação | `shirokai.essentials.lixeira` |
| `/compactar` | Transforma grupos de nove barras na mão em blocos | `shirokai.essentials.compactar` |
| `/cortador` | Abre cortador de pedras virtual | `shirokai.essentials.cortador` |
| `/tear` | Abre tear virtual | `shirokai.essentials.tear` |

Todos dependem do módulo `utilities` ativo em `modules.json` e atuam somente no próprio jogador. Não são concedidos automaticamente a jogadores comuns; os defaults e o fallback administrativo existentes continuam aplicados. Novas entradas são acrescentadas a `commands.json` com backup, mantendo comandos desativados e configurações personalizadas.

## Lixeira

As duas primeiras linhas oferecem 18 espaços para itens. A última contém os controles bloqueados:

1. Coloque os itens nos espaços da lixeira.
2. Clique em **Revisar descarte**.
3. Confira os itens e clique em **Confirmar descarte** para removê-los definitivamente.

O descarte afeta apenas os itens depositados na lixeira. Itens restantes no inventário do jogador são preservados. Qualquer interação com inventário invalida a revisão; alterações nos itens também são comparadas contra uma cópia da revisão, exigindo nova confirmação. É necessário guardar o item do cursor antes de confirmar.

**Cancelar** e fechar com Esc devolvem os itens. Como nas interfaces vanilla, itens podem ser soltos no mundo se não couberem no inventário. A permissão é verificada ao interagir, inclusive após revogação. Os botões não podem ser extraídos por clique, troca de hotbar, Shift, clone ou descarte.

A confirmação registra auditoria antes de apagar os itens. Uma falha na escrita mantém os itens para cancelar ou tentar novamente. O descarte confirmado é definitivo; não existe comando de restauração. O conteúdo temporário da interface não possui journal de recuperação em queda abrupta do servidor; os testes comprovam fechamento normal e falha de auditoria, não recuperação após perda de energia.

## Compactação

Segure barras comuns de ferro, ouro, cobre ou netherita na mão principal. A pilha é dividida em grupos de nove: **64 barras → 7 blocos na mão + 1 barra no inventário**. Sobras podem ser combinadas com pilhas compatíveis existentes.

A operação primeiro calcula o inventário resultante. Se faltar espaço para a sobra, não altera nenhum item. Uma pilha múltipla de nove pode ser convertida mesmo com o restante do inventário cheio, pois os blocos substituem a pilha na mão.

Itens personalizados, como barras com nome/dados usados como moedas, são recusados. A receita de crafting do servidor precisa transformar nove barras no bloco correspondente, sem recipientes ou sobras especiais de receita. Receitas removidas ou incompatíveis por datapacks impedem a conversão. Não há recarga nova para `/compactar`.

## Cortador e tear

As interfaces usam os screen handlers nativos do Minecraft, com suas receitas e padrões. Não exigem um bloco físico próximo. Fechar os menus devolve os insumos pelo comportamento vanilla, sem alterar blocos do mundo. Não há mod adicional de cliente exigido por estes comandos.

## Validação e pacotes

Build completo e **56 testes JUnit** aprovados. As **71 verificações de integração** passaram. O fixture também executa as regressões do Ender Chest e das utilidades anteriores. Os resultados estão em [utilities-0.5.0-guard.1](utilities-0.5.0-guard.1).

Ambiente: Minecraft 1.21.1, Java 21.0.9, Fabric Loader 0.17.2, Fabric API 0.116.7+1.21.1 e LuckPerms 5.4.140. Os jogadores/conexões são simulados dentro do servidor; dispatcher, inventários, receitas, permissões e screen handlers são reais. Não foram usados clientes gráficos ou feito ensaio em Windows. As suítes históricas de combate/Core não foram repetidas.

Cobertura nova: migração dos comandos, receitas e consumo de cortador/tear, quatro tipos de barra, conservação das sobras, recusa sem espaço, recusa de moedas personalizadas, revisão de descarte, alterações após revisão, itens do cursor, Shift-click nos dois sentidos, cancelamento, descarte confirmado, preservação de itens externos, proteção dos botões, revogação de permissão e falha real de escrita da auditoria.

Para repetir, use `./gradlew build remapPermissionFixtureJar` e um servidor descartável em loopback com o fixture, LuckPerms, módulo `utilities` ativo, alias `ec` e `-Dessentials.permissionChecks=true`. O fixture cria usuários e itens sintéticos e injeta uma falha de auditoria; não deve ser instalado em produção.

Instale somente uma variante da pasta [releases/v0.5.0-guard.1](../releases/v0.5.0-guard.1), com o servidor parado, removendo o JAR antigo e preservando configurações e mundo. A variante Zorykai mantém os mesmos IDs, permissões e lógica.
