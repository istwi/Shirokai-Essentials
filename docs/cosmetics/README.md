# Cosméticos simples — Essentials 0.6.5-trails.1

Versão exclusivamente server-side para Minecraft 1.21.1, Fabric e Java 21. Não exige mod nem pacote de recursos no cliente.

`/cosmeticos` apresenta somente seis categorias, cada uma em seu submenu:

- **Cor do nick:** ciano, dourado ou arco-íris suave.
- **Rastros no chão:** trilha larga emitida em pontos espaçados enquanto o jogador caminha no chão.
- **Aura:** espiral de partículas da cabeça aos pés.
- **Entrada triunfal:** efeito breve cerca de dois segundos depois de entrar.
- **Efeito de vitória:** celebração ao vencer PvP do Minecraft ou quando uma integração administrativa chama o evento.
- **Celebração de raid:** acionada para os participantes na transição de vitória do Shirokai/Zorykai Raids.

Aura e demais efeitos contínuos mantêm o intervalo original de `5` ticks. O rastro usa `trailIntervalTicks: 6`, criando um pequeno espaçamento temporal para as partículas permanecerem visualmente pelo caminho em vez de parecerem presas ao jogador.

A densidade básica do rastro é `4`. Partículas pequenas recebem multiplicadores proporcionais: água usa `3x`; elétrico, fantasma, neve, magia e encantado usam `2x`; partículas visualmente maiores usam `1x`. Todos continuam sujeitos aos orçamentos por jogador e por tick.

Asas, coroas, círculos, pegadas separadas, teleporte temático, sazonais e coleções foram retirados. O complemento cliente 3D não faz parte desta versão.

## Personalização segura

Jogadores com `shirokai.essentials.cosmetics.customize` podem abrir **Personalizar partículas** e ajustar velocidade, tamanho, densidade e direção. A tela oferece prévia temporária, restauração do padrão, aplicar e cancelar. As escolhas são salvas por UUID.

Os limites administrativos `maxSpeed`, `maxSize` e `maxDensity` aceitam valores de 1 a 3. O jogador nunca ultrapassa esses limites; os orçamentos global e individual de partículas continuam prevalecendo.

## Permissões

```text
shirokai.essentials.cosmetics.use
shirokai.essentials.cosmetics.customize
shirokai.essentials.cosmetics.nick_cyan
shirokai.essentials.cosmetics.nick_gold
shirokai.essentials.cosmetics.nick_rainbow
shirokai.essentials.cosmetics.trail_stars
shirokai.essentials.cosmetics.aura_water
shirokai.essentials.cosmetics.join_nature
shirokai.essentials.cosmetics.victory_fire
shirokai.essentials.cosmetics.raid_soul
shirokai.essentials.cosmetics.admin
```

Os estilos disponíveis nas cinco categorias de partículas são `stars`, `fire`, `water`, `nature`, `electric`, `soul`, `snow`, `heart`, `magic`, `enchant`, `cloud` e `cherry`. O menu mostra a permissão exata de cada opção.

Exemplo no LuckPerms:

```text
/lp group default permission set shirokai.essentials.cosmetics.use true
/lp group vip permission set shirokai.essentials.cosmetics.trail_stars true
```

O curinga `shirokai.essentials.cosmetics.*` também inclui a administração. Para grupos de jogadores, conceda os IDs desejados individualmente.

## Configuração

Arquivo `config/shirokai-essentials/cosmetics.json`:

```json
{
  "enabled": true,
  "nickEnabled": true,
  "suppressInBattle": true,
  "intervalTicks": 5,
  "trailIntervalTicks": 6,
  "trailBaseDensity": 4,
  "maxParticlesPerPlayer": 48,
  "maxParticlesPerTick": 500,
  "viewDistance": 32,
  "celebrationCooldownSeconds": 10,
  "maxSpeed": 3,
  "maxSize": 3,
  "maxDensity": 3,
  "disabled": []
}
```

`disabled` aceita IDs ativos, como `aura_fire`. IDs das categorias removidas são ignorados durante o carregamento para permitir o uso de configurações antigas. `/cosmeticosadmin reload` aplica alterações válidas.

As escolhas ficam em `<mundo>/shirokai-essentials/players/<uuid>.json`. Entradas antigas como `wings_stars` continuam no arquivo para evitar uma migração destrutiva, mas não aparecem no menu, não recebem permissões e não produzem efeitos. Equipar os efeitos simples continua funcionando normalmente.

`/cosmeticos desativar` mantém as escolhas e oculta os efeitos. O botão principal permite reativá-los. A cor do nick usa uma equipe de scoreboard própria e não substitui uma equipe de rank já aplicada por outro mod.

## Integração de eventos

```text
/cosmeticosadmin celebrar jogador vitoria
/cosmeticosadmin celebrar jogador raid
```

Os comandos respeitam a opção equipada, a permissão atual e a recarga configurada. A integração de raid observa a transição de vitória das versões testadas do Raids. Vitória em batalha Pokémon comum não está integrada.

Os resultados automatizados estão em `results.json`. Os testes de servidor usam jogadores simulados e não equivalem a uma avaliação visual em cliente gráfico.
