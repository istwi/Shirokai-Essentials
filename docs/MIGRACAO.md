# Migração do Essential Commands

O adaptador foi escrito após inspecionar **Essential Commands 0.35.2-mc1.21**, commit/tag público correspondente, e seu formato NBT. Não aceita silenciosamente dados de outras versões.

Referências do formato: [MinecraftLocation](https://github.com/John-Paul-R/Essential-Commands/blob/0.35.2-mc1.21/src/main/java/com/fibermc/essentialcommands/types/MinecraftLocation.java), [PlayerData](https://github.com/John-Paul-R/Essential-Commands/blob/0.35.2-mc1.21/src/main/java/com/fibermc/essentialcommands/playerdata/PlayerData.java) e [WorldDataManager](https://github.com/John-Paul-R/Essential-Commands/blob/0.35.2-mc1.21/src/main/java/com/fibermc/essentialcommands/WorldDataManager.java).

## Formato suportado

Dentro de uma **cópia** da pasta de dados do mundo:

- `essentialcommands/world_data.dat`: NBT comprimido com wrapper `data`, campos `spawn` e `warps`.
- `modplayerdata/<UUID>.dat`: NBT comprimido com `data.playerUuid` e `data.homes` em compound nomeado.
- Destinos: `WorldRegistryKey`, `x`, `y`, `z`, `headYaw`, `pitch`.

UUID interno deve corresponder ao arquivo. Dimensões conservam namespace completo. Nomes são normalizados para minúsculas e devem caber no formato Shirokai; colisões são relatadas. Listas legadas de homes e warps com `permissionString` antigo não são convertidas sem revisão/mapeamento de permissões.

## Procedimento

1. Faça backup do mundo e identifique a versão real do Essential Commands.
2. Coloque a cópia de `essentialcommands/` e `modplayerdata/` dentro de `<mundo>/importacao-ec/`.
3. Em `integrations.json`, defina `essentialCommandsVersion` como `0.35.2` somente se os dados corresponderem a essa versão. Recarregue as opções dinâmicas.
4. Execute no console: `ecimport dry-run importacao-ec`.
5. Leia `config/shirokai-essentials/migration-<id>.json`. O modo dry-run não cria destinos nem arquivos de jogadores ausentes; apenas o relatório é escrito.
6. Para aplicar, mantenha o servidor vazio e o Essential Commands inativo; então execute no console: `ecimport apply importacao-ec`.
7. Confira relatório, homes, warps e spawns importados antes de trocar os comandos usados pelos jogadores.

A origem deve ficar dentro da pasta do mundo, fora dos dados Shirokai. Links simbólicos são recusados. Há limites de tamanho, NBT expandido, lote total e número de arquivos; divida lotes grandes. Não se lê a pasta de jogadores vanilla nem dados de Pokémon.

## Proteções

Antes de aplicar, são salvos snapshots dos destinos anteriores e cópias dos arquivos de origem em `<mundo>/shirokai-migration-backups/<id>/`, com hashes no relatório. Os originais nunca são alterados ou excluídos.

Destinos existentes não são sobrescritos. Dimensões ausentes e permissões sem mapeamento são relatadas e ignoradas. Repetir a importação não duplica nem substitui destinos já existentes.

A aplicação agrupa as homes por jogador e grava cada arquivo uma vez. Há transações atômicas por arquivo e journal de progresso; não existe transação única entre todos os arquivos. Uma falha pode deixar progresso parcial, sinalizado por `PARTIAL_REVIEW_REQUIRED` ou por journal ainda `PREPARED` após interrupção. Não interprete esses estados como conclusão. Compare origem, backups e destinos; um novo dry-run permite revisar o que falta sem sobrescrever o que já foi importado.

A migração deve ser feita como manutenção, com servidor vazio. O parser roda fora da thread do jogo; a validação de mundos e o commit dos dados ocorrem na thread do servidor.
