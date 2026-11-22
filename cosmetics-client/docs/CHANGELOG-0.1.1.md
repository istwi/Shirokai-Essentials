# 0.1.1-test.1 — Um par de asas e animação suave

Atualização apenas do cliente; continua compatível com Essentials 0.6.2-guard.1. Substitua o JAR antigo do cliente, mantendo apenas uma versão instalada.

- Removidas as camadas `cover_*` e `shoulder_*`; ficam somente seis penas principais de cada lado.
- Eliminadas sobreposições coplanares internas entre os segmentos de uma pena. Os segmentos agora se encontram nas bordas, e as penas vizinhas têm profundidades diferentes.
- Bases largas e paralelas fecham as frestas na parte superior; os vãos entre as pontas externas continuam fazendo parte do recorte das penas.
- Batida lenta ao andar: ciclo de 70 ticks (3,5 segundos a 20 TPS), abertura visível e transição gradual de cerca de oito ticks.
- Fase independente da velocidade: acelerar/parar não multiplica mais a idade total do jogador, evitando saltos na rotação.
- Parado, as asas voltam à pose de repouso. Cada jogador tem seu próprio estado de animação.

Testes: cinco testes de movimento (repouso, amplitude, mudança de velocidade após sessão longa, parada suave e renderização repetida no mesmo instante), mais fluxo em cliente gráfico real usando menu, caminhada, agachamento, cores, desativação e vista superior. As capturas e o vídeo em `v0.1.1/` são do Minecraft, sem shaders. Não foram reproduzidos todos os mods gráficos do servidor do usuário.
