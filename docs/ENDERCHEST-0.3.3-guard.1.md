# Correção do Ender Chest — 0.3.3-guard.1

## Reprodução

As branches `main` e `zorykai` apontavam para `1a586e0455f6feaeba1f782ae4f5769dfcd2e906`, o commit mais recente disponível ao iniciar este trabalho. A correção foi feita sobre essa base em uma branch separada.

Com LuckPerms 5.4.140, `apply-shorthand=true` e apenas `shirokai.essentials.enderchest=true` para um jogador sem OP, a consulta do nó `shirokai.essentials.enderchest.others` retornou `TRUE`. O nó de origem informado pelo próprio LuckPerms foi `shirokai.essentials.enderchest`. A versão publicada permitiu abrir o baú de outro jogador, inclusive pelo alias configurado.

O comando já verificava `.others` tanto na árvore Brigadier quanto no serviço de utilidades. O problema era aceitar a expansão implícita da permissão principal como autorização administrativa.

## Correção

A consulta usa `CachedPermissionData.queryPermission` para verificar também a origem do resultado. Somente para `shirokai.essentials.enderchest.others`, uma concessão positiva derivada de um ancestral sem wildcard não é aceita como concessão explícita. Uma resposta positiva sem origem também não libera esse acesso.

Continuam funcionando:

- `shirokai.essentials.enderchest`: abrir o próprio baú.
- `shirokai.essentials.enderchest.others`: junto da permissão principal, abrir o de terceiros. Pode ser concedida diretamente ou herdada de um grupo administrativo.
- Wildcards administrativos explícitos, como `shirokai.essentials.*`.
- Fallback OP e defaults de `permissions.json`, quando configurados. Negação explícita do LuckPerms continua prevalecendo sobre fallback OP.

Não foram renomeados comandos, permissões, IDs ou arquivos de configuração. Não é necessário trocar a permissão de acesso próprio já concedida aos jogadores. Se um grupo possuir um wildcard administrativo ou `enderchest.others` explicitamente, continuará autorizado a acessar terceiros.

## Validação

- Build completo e **51 testes JUnit**, sem falhas; quatro testes novos da decisão de permissão.
- **17 verificações de integração**, todas aprovadas no pacote final Zorykai 0.3.3-guard.1. Os mesmos testes na versão publicada 0.3.2-guard.1 reprovaram seis verificações relacionadas ao acesso indevido.
- Servidor real Minecraft 1.21.1, Java 21.0.9, Fabric Loader 0.17.2, Fabric API 0.116.7+1.21.1 e LuckPerms 5.4.140. `apply-shorthand=true`, sem OP para o jogador comum.
- Jogadores e conexões simulados dentro do servidor; consultas LuckPerms, dispatcher de comandos, serviços e inventários reais. Não foi um ensaio com clientes gráficos. Cobblemon/Core não foram carregados neste teste específico, e as antigas suítes completas de integração não foram repetidas.

Cobertura: baú próprio, nome de outro jogador, alias, seletor, chamada direta ao serviço, permissão `.others` explícita, revogação inclusive de comando já analisado pelo Brigadier, herança explícita de grupo administrativo, wildcard, fallback OP, negação explícita e preservação dos conteúdos.

Os resultados sintéticos e os hashes dos artefatos estão em [enderchest-0.3.3-guard.1](enderchest-0.3.3-guard.1). Nenhum mundo, configuração de servidor ou dado de jogador real foi incluído.

## Repetir

Compile com `./gradlew build remapPermissionFixtureJar`. Prepare um servidor Fabric local descartável com LuckPerms, um único Essentials, o JAR separado `essentials-permission-checks-*.jar`, módulo `utilities` ativo e alias `ec` para `enderchest`. Use `server-ip=127.0.0.1` e inicie Java com `-Dessentials.permissionChecks=true`. Após 60 ticks, o fixture grava `enderchest-results.json`. O fixture cria usuários/grupo e itens de teste; não o instale num servidor de produção.

O pacote normal não contém fixtures. `python3 scripts/package_enderchest_hotfix.py` preserva os cinco arquivos de classes visuais da variante Zorykai publicada, **somente depois de comprovar que essas classes permanecem idênticas na compilação nova à base original**. A classe corrigida `PermissionService` é idêntica entre as variantes Shirokai e Zorykai. O script recusa reaproveitar uma classe visual que tenha mudado, evitando reintroduzir código antigo.
