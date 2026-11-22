# Cosméticos — Essentials 0.6.0-guard.1

Sistema server-side para Minecraft 1.21.1 / Fabric / Java 21. Não precisa de pacote de recursos ou outro mod no cliente. São efeitos visuais com partículas do Minecraft; asas não permitem voar. Não altera itens, Pokémon, recompensas ou atributos de combate.

## Menus separados

`/cosmeticos` abre as categorias. Cada categoria tem seu próprio submenu, com estado da opção, permissão, descrição, remover, voltar e fechar.

- Cor do nick: ciano, dourado e arco-íris alternando a cada dois segundos.
- Rastros: ao caminhar.
- Auras: partículas orbitando.
- Pegadas elementais: nos pés enquanto caminha no chão.
- Coroas: acima da cabeça.
- Asas: contorno de partículas atrás do corpo, seguindo a direção do tronco. Ocultadas ao montar, nadar ou planar.
- Círculos: anel aos pés.
- Teleporte: efeito na chegada, observado no caminho de teleporte do Minecraft, inclusive deslocamentos curtos. Atualizações sucessivas são agrupadas. Não gera efeito na saída.
- Entrada: efeito cerca de dois segundos após entrar. Se estiver invisível, restrito, sem permissão ou em combate nesse momento, é cancelado.
- Vitória: PvP entre jogadores do Minecraft ou chamada de integração administrativa. Vitória em batalha Pokémon comum ainda não está integrada.
- Raid: celebração na transição de vitória do Shirokai/Zorykai Raids, para os participantes informados pelo próprio Raids.
- Sazonais: Halloween em outubro, Natal em dezembro e Dia dos Namorados em junho. Fora do mês, a escolha fica salva mas não emite partículas.
- Coleções: Estelar, Fogo, Água, Natureza, Elétrico e Fantasma. Equipa as dez categorias de partículas de uma vez, mantendo a cor do nick. Exige acesso a todos os componentes.

São 66 opções: três cores de nick, seis estilos para cada uma das dez categorias de partículas e três sazonais.

No principal, **Desativar tudo** mantém a seleção, remove a cor de nick gerenciada pelo mod e oculta suas partículas. Também deixa de enviar a você partículas geradas por este sistema para outros jogadores. Não oculta partículas de outros mods. O botão passa a **Reativar cosméticos**. `/cosmeticos desativar` tem o mesmo efeito.

## Permissões

Usa o PermissionService existente, respeitando LuckPerms, negativas explícitas e a política de fallback do Essentials. Sem concessões/defaults, jogadores comuns não recebem acesso automaticamente.

```text
shirokai.essentials.cosmetics.use
shirokai.essentials.cosmetics.nick_cyan
shirokai.essentials.cosmetics.nick_gold
shirokai.essentials.cosmetics.nick_rainbow
shirokai.essentials.cosmetics.trail_stars
shirokai.essentials.cosmetics.wings_fire
shirokai.essentials.cosmetics.raid_soul
shirokai.essentials.cosmetics.admin
```

Cada opção exibe sua permissão. IDs de partículas: `<categoria>_<estilo>`, onde categorias são `trail`, `aura`, `footprint`, `crown`, `wings`, `circle`, `teleport`, `join`, `victory`, `raid`; estilos são `stars`, `fire`, `water`, `nature`, `electric`, `soul`. Sazonais: `trail_halloween`, `aura_christmas`, `circle_valentine`.

Exemplo LuckPerms:

```text
/lp group default permission set shirokai.essentials.cosmetics.use true
/lp group vip permission set shirokai.essentials.cosmetics.wings_stars true
```

O curinga `shirokai.essentials.cosmetics.*` libera todas as opções **e a administração**; para VIP, prefira conceder somente os IDs desejados.

## Configuração

Arquivo gerado: `config/shirokai-essentials/cosmetics.json`.

```json
{
  "enabled": true,
  "nickEnabled": true,
  "suppressInBattle": true,
  "intervalTicks": 5,
  "maxParticlesPerPlayer": 36,
  "maxParticlesPerTick": 400,
  "viewDistance": 32,
  "celebrationCooldownSeconds": 10,
  "timezone": "America/Sao_Paulo",
  "disabled": []
}
```

`disabled` aceita IDs de opções, como `wings_fire`. Configuração inválida não substitui a versão ativa no reload. Para aplicar: `/cosmeticosadmin reload`.

Limites contam pontos de emissão, não pacotes de rede: cada ponto pode ser enviado aos espectadores próximos. O orçamento é compartilhado entre efeitos e eventos; sob carga, efeitos podem ficar incompletos. A prioridade gira entre jogadores. Não é uma garantia de desempenho em qualquer tamanho de servidor. Distância de visão, partículas desativadas no cliente e configurações de gráficos afetam a aparência.

Escolhas ficam nos perfis existentes em `<mundo>/shirokai-essentials/players/<uuid>.json`, com os mesmos salvamentos atômicos/backup do mod. Perfis antigos recebem campos vazios. Não altere esses arquivos com o servidor ligado.

## Nick e outros mods

A implementação usa equipes de scoreboard reservadas, identificadas pelo UUID e por marcador de propriedade. Não altera uma equipe de rank/tag já atribuída ao jogador. Nessa situação, a seleção fica salva, mas a cor não é aplicada. Equipes temporárias são removidas ao desativar, sair ou encerrar; resíduos identificados do próprio sistema são limpos ao iniciar após uma interrupção.

A cor pode afetar o nome na lista de jogadores, além do nome sobre a cabeça. Não modifica o nick textual nem o formato do chat. Não substitui plugins de nametag e não fornece compatibilidade universal com eles.

## Eventos e Raids

A integração opcional observa o retorno de `com.shirokai.raids.RaidVictoryTransition.begin(String, Collection)`. O ponto é chamado depois do salvamento da vitória, com os presentes na arena. Não intercepta itens, pagamentos ou Pokébolas. O Raids continua independente; sem esse mod, o Essentials carrega normalmente.

Assinatura conferida nos JARs `0.1.24-quizguard.1` e `0.1.25-returnspawn.1`. Não afirmar suporte automático para versões futuras que mudem esse método. O gancho usa `@Pseudo`/injeção opcional; não depende das classes do Cobblemon para carregar o Essentials.

Também existem comandos administrativos para um mod/plugin chamar após confirmar um evento:

```text
/cosmeticosadmin celebrar jogador vitoria
/cosmeticosadmin celebrar jogador raid
```

Eles usam o efeito equipado, verificam acesso e respeitam recarga. Não são comandos de recompensa ou desbloqueio. Não há desbloqueio automático por conquistas nesta versão: concessões são feitas pelas permissões. Efeitos são suspensos para espectadores, invisíveis e jogadores restritos; por padrão também durante combate reconhecido pelo BattleGuard. Mods de vanish que não usem invisibilidade podem exigir integração específica.

## Validação

Consulte `results.json` para hashes, contagens e limites dos ensaios. Os testes nativos usam jogadores simulados no servidor, cliques no ScreenHandler e pacotes de partículas reais. Não equivalem a uma revisão visual por um cliente gráfico; ajuste estético fino das asas/auras deve ser conferido no jogo. A integração de raid foi verificada carregando a classe real transformada e invocando seu callback injetado com participantes simulados, sem executar uma batalha ou uma vitória de raid completa.
