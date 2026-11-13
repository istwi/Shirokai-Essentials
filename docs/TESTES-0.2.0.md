# Validação — Shirokai Essentials 0.2.0

**Build aprovado: 46 testes JUnit e 81 verificações de integração passaram.**

Os resultados desta entrega são gerados em [RESULTADOS-INTEGRACAO.json](RESULTADOS-INTEGRACAO.json). O build produz um JAR remapeado real, carregado em `mods/` nos testes de integração. Relatórios JUnit ficam em `build/reports/tests/test/index.html`.

## Ambientes utilizados

| Conjunto | Servidor e dependências | Cobertura |
| --- | --- | --- |
| JUnit | Java 21, Gradle 8.12.1, Loom 1.9.2 | Configurações, flags, persistência, migração de schema, falhas de gravação, NBT, duração de punições e planejamento de inventário |
| Regressão | Minecraft 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.7+1.21.1 | Homes, warps, spawn, TPA, cooldown/warmup, cancelamento, dimensões e reinícios |
| Recursos ampliados | Mesmo servidor, LuckPerms **5.4.140**, Impactor **5.3.5+1.21.1**, Multiworld **1.14.2** | Chat, permissões, moderação, utilidades, kits, consulta econômica, chunks, mundos customizados e migração |
| Módulo desativado | Mesmo conjunto de mods reais | FAIL_MODULE, preservação do chat vanilla, revogação de permissão de warp no warmup e freeze desativado sem apagar punições |
| Cobblemon | Fabric Loader **0.17.2**, Cobblemon **1.8.3+1.21.1** e os três mods acima | Inicialização conjunta e invocação da API pública de batalhas por UUID |

Os clientes simulados usam protocolo vanilla 1.21.1, Node 24.22.1 e minecraft-protocol 1.68.0. Os cenários com jogadores não exigiram Shirokai no cliente. O cenário com Cobblemon é uma verificação de servidor/ABI sem jogadores; não afirma que clientes vanilla possam entrar num servidor Cobblemon.

## Verificações relevantes

A regressão verifica preservação do `/tp` vanilla, comandos/aliases ausentes da árvore do cliente quando desativados, limites e persistência de homes, `/back`, TPA/TPAHere, expiração, cooldown e cancelamento por movimento/dano. Destinos com lava são recusados.

Os cenários ampliados verificam MOTD automático com `/motd` desligado; AFK manual por alias e automático com comando manual desligado; mensagens, respostas, ignore, bloqueio, espionagem optativa, correio offline, apelidos e conteúdo MiniMessage de jogadores mantido literal.

LuckPerms foi exercitado para negação explícita sobre OP, mudança de permissões e hierarquia. Mute bloqueou mensagens próprias e `/msg` vanilla; também foram exercitados avisos, ban/unban vanilla, freeze, prisão temporária e expiração. Fly/god foram restaurados após reinício e deixaram de agir quando suas funcionalidades foram desativadas, conservando preferências salvas. Os testes de dano aguardam o período vanilla de proteção após login.

Kits foram entregues com `custom_name` de Minecraft 1.21.1, cooldown antes/depois de reinício e recibo persistente. Uma prévia resistiu a pacote forjado de quick-move sem alterar o inventário. Um recibo incerto bloqueou a entrega até recuperação auditada, sem reenviar itens. Invsee foi aberto como fotografia somente leitura.

O Multiworld criou um mundo real `multiworld:shirokai_test`; uma home nesse mundo funcionou e sobreviveu ao reinício. A carga assíncrona rejeitou pedido duplicado e foi cancelada por movimento; o RTP carregou chunks novos e encontrou superfície segura.

A migração utilizou fixtures NBT do formato identificado 0.35.2: dry-run sem criar arquivos de jogadores ausentes, conflito de nome, warp com permissão antiga recusado, preservação de origem, backup prévio e aplicação idempotente. Isso não substitui um dry-run sobre os dados reais do servidor.

Saldo e ranking consultaram `impactor:dollars` no serviço real do Impactor. Aliases de mutação financeira do Shirokai não foram registrados. O bytecode do JAR exato 5.3.5 foi inspecionado: a transferência chama `save()` nas contas separadamente e o helper descarta o future retornado pelo provedor, sem aguardar um commit conjunto.

## Reproduzir

Requer JDK 21 completo, Node 22+, Python 3 e porta local livre. Os testes usam mundo descartável, modo offline local e `eula=true` em `build/`; não reutilize essas pastas como produção.

```sh
./gradlew build generateRemapClasspath
npm ci --prefix tests/integration --ignore-scripts
python3 tests/integration/prepare-production.py
SHIROKAI_PRODUCTION=1 npm test --prefix tests/integration
```

Para os cenários ampliados, disponibilize os JARs oficiais das três versões em uma pasta externa ao projeto e defina `JAVA_HOME`:

```sh
SHIROKAI_TEST_MODS=/caminho/mods-teste node tests/integration/expanded.cjs
SHIROKAI_TEST_MODS=/caminho/mods-teste node tests/integration/module-failure.cjs
```

Para Cobblemon:

```sh
python3 tests/integration/prepare-production.py --loader 0.17.2 --output build/integration/cobblemon-launch.json
SHIROKAI_TEST_MODS=/caminho/mods-teste \
SHIROKAI_COBBLEMON_MOD=/caminho/Cobblemon-fabric-1.8.3+1.21.1.jar \
node tests/integration/cobblemon.cjs
```

`GRADLE_USER_HOME`, quando personalizado, deve ser consistente nos comandos. `SHIROKAI_JAVA_PROXY=1` habilita o adaptador do proxy do ambiente cloud para os cenários ampliados, usando os valores herdados e o truststore do ambiente Linux. O servidor Minecraft ainda pode emitir aviso de consulta de chaves públicas em modo offline; autenticação online não foi testada.

Logs/resultados ficam em `build/regression-integration/`, `build/expanded-integration/`, `build/module-failure-integration/` e `build/cobblemon-integration/`. A regressão padrão usa `build/integration/`; `SHIROKAI_PORT` e `SHIROKAI_OUTPUT` permitem isolá-la em outra porta/pasta. Os testes ampliados usam porta 25565, os testes de módulo 25568 e o smoke Cobblemon 25566.

## Limites da evidência

Não houve uma batalha completa com clientes Cobblemon, teste de carga prolongado, queda física durante rename ou disco realmente cheio. A falha de gravação foi induzida de forma controlada. Bancada, bigorna, reparação e edição do ender chest usam APIs vanilla e compilaram, mas não tiveram uma simulação completa de uso de GUI em todas as combinações. O parser de receitas do cliente de teste é mantido opaco; receitas não são o objeto destes cenários.

A consulta Cobblemon usa a assinatura real do JAR 1.8.3 e faz uma chamada sem efeitos colaterais. Isso demonstra compatibilidade de inicialização e ABI, não prova todos os estados de batalha. Importação de formatos diferentes de Essential Commands 0.35.2 continua bloqueada. Pagamentos, mutações econômicas e custos não foram expostos ou apresentados como aprovados.

O histórico da primeira entrega está em [TESTES-0.1.0.md](TESTES-0.1.0.md).
