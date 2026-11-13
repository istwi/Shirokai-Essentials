# Relatório de validação — 0.1.0

## Resultado

**Build concluído com sucesso; 23 testes JUnit e 25 verificações de integração aprovados.** O conjunto de integração foi executado com o **JAR remapeado instalado em `mods/`**, em modo normal do Fabric, sem `fabric.development=true` e sem clientes com mods. Também houve execução anterior com o launcher de desenvolvimento do Loom.

Ambiente efetivamente utilizado: Minecraft 1.21.1, Fabric Loader 0.16.14, Fabric API 0.116.7+1.21.1, JDK Temurin 21.0.13+9, Gradle 8.12.1, Linux, Node 24.22.1 e minecraft-protocol 1.68.0. LuckPerms, Multiworld, Cobblemon, Impactor e Essential Commands **não estavam instalados**.

O artefato também foi inspecionado: `fabric.mod.json` com ID, versão e ambiente corretos, entrypoint presente e referências Minecraft remapeadas para intermediary.

## Testes JUnit

| Área | Testes | Resultado |
| --- | ---: | --- |
| Configuração, flags, aliases, schema e reload | 7 | aprovados |
| Persistência, namespaces, backups, falhas de gravação e dados inválidos | 7 | aprovados |
| Limites, expiração, seleção e desconexão de pedidos TPA | 4 | aprovados |
| Brigadier: colisões, alias alternativo, comandos desligados, fail module/startup e registro posterior | 5 | aprovados |

O teste de falha de gravação bloqueia o destino do backup e confirma que tanto o arquivo original quanto o estado em memória permanecem inalterados. Os testes de colisão usam árvores Brigadier reais; não simulam todos os mods mencionados na especificação.

## Integração dedicada

O script usa três jogadores simulados, incluindo um OP, conectados pelo protocolo vanilla 1.21.1. O servidor só escuta em `127.0.0.1`, usa modo offline exclusivamente para os jogadores de teste e é reiniciado duas vezes durante o cenário.

Foram validados:

1. Preservação do `/tp` vanilla e diagnóstico da colisão.
2. Árvore enviada a jogador comum com comandos permitidos, sem aliases desligados ou comandos administrativos/desativados.
3. Limite de homes para jogador comum.
4. Renomeação e exclusão de homes.
5. Execução de alias habilitado e destino correto.
6. Cooldown de teleporte.
7. Retorno à posição anterior por `/back`.
8. Cancelamento de warmup por movimento.
9. Cancelamento de warmup por dano.
10. Recusa de destino com lava.
11. Ocultação de warp privado para jogador sem acesso.
12. Spawn global configurado.
13. TPA com aceite e movimento do remetente.
14. TPAHere com consentimento e movimento do destinatário.
15. Bloqueio persistente de pedidos de teleporte.
16. Impossibilidade de aceitar pedido expirado.
17. Teleporte de home entre Overworld e Nether.
18. Persistência do identificador completo `minecraft:the_nether`.
19. Rejeição de reload inválido, preservando a configuração em execução.
20. Remoção de comandos e aliases da árvore do cliente ao desligar o módulo e reiniciar.
21. Preservação das homes com o módulo desligado.
22. Restauração do módulo após reinício.
23. Acesso às homes persistidas após reinício.
24. Negação de acesso quando LuckPerms é obrigatório e está ausente.
25. Atualização da árvore de comandos após reload de permissões.

Resultado legível por máquina: [RESULTADOS-INTEGRACAO.json](RESULTADOS-INTEGRACAO.json).

## Reproduzir

Use um JDK 21 completo, Node 22+ e Python 3. Nenhuma etapa publica release ou envia arquivos a um servidor externo.

```sh
./gradlew build generateRemapClasspath
npm ci --prefix tests/integration --ignore-scripts
python3 tests/integration/prepare-production.py
SHIROKAI_PRODUCTION=1 npm test --prefix tests/integration
```

Defina `JAVA_HOME` para o JDK 21 antes da integração em modo de produção. Se usar cache Gradle alternativo, mantenha `GRADLE_USER_HOME` consistente nos comandos. O launcher de teste aproveita os JARs resolvidos pelo Loom; Minecraft, Fabric API e demais dependências não são redistribuídos no pacote do mod.

Para executar apenas em modo de desenvolvimento:

```sh
npm test --prefix tests/integration
```

O script cria e recria exclusivamente `build/integration/run`, grava `eula=true` no servidor de teste, copia as configurações de exemplo e salva logs/resultados em `build/integration/`. Não reutilize essa pasta como mundo de produção. A porta local 25565 precisa estar disponível. O cliente ignora a decodificação de receitas, que não faz parte dos testes de comandos/teleporte.

Relatório HTML JUnit: `build/reports/tests/test/index.html`. Logs do servidor: `build/integration/server-*.log`. Resultado da última execução: `build/integration/results.json`.

## Limites da validação

- A autenticação online Microsoft/Mojang não foi testada. O ambiente emitiu aviso de falha ao consultar chaves públicas por acesso de rede do processo Java; os clientes offline locais entraram normalmente.
- Não foi validada a integração de permissões com LuckPerms instalado, apenas compilação contra API 5.4, comportamento sem o mod e exigência explícita de sua presença.
- Não foram executados testes com Cobblemon 1.8.3, Multiworld, Impactor, SkiesKits ou Essential Commands reais. Colisão com o comando vanilla e cenários Brigadier foram testados; isso não demonstra compatibilidade total com esses mods.
- Dimensões customizadas usam o mesmo identificador completo e lookup de mundos, mas a integração executada utilizou Overworld e Nether. Remoção de dimensão, mundo customizado e bloqueio de batalhas Cobblemon ainda precisam de testes específicos.
- Não houve teste de carga, crash físico durante rename ou disco realmente cheio. A falha de gravação foi induzida de forma controlada.
- Recursos administrativos de teleporte e opções automáticas de spawn não tiveram cobertura completa de jogo. As Fases 3–6 não estão implementadas nem foram apresentadas como aprovadas.
