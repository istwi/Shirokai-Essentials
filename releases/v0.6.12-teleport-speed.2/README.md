# Zorykai Essentials 0.6.12-teleport-speed.2

Base: feature/cosmetics-personalization, commit9899a00, incluindo a correção anterior de teleportes e cosméticos.

Correção: alterações de caminhada agora atualizam PlayerAbilities e o atributo GENERIC_MOVEMENT_SPEED, enviando ambos ao cliente. Alterar somente PlayerAbilities não atualizava a base de movimento em execução; um valor antigo podia continuar lento mesmo depois do reset até recarregar os dados. O reset agora corrige imediatamente as duas bases, mantendo modificadores de poções/equipamentos.

Comandos:
- /speed 1: caminhada normal no chão; voo normal se estiver voando. Mantém a seleção automática anterior.
- /speed walk 1: caminhada normal, mesmo estando no ar. Aceita1–10.
- /speed fly 1: voo normal, mesmo estando no chão. Aceita1–10 e não concede permissão para voar.
- /speed reset ou /speed 0: restaura caminhada0,10 e voo0,05.
- Todos aceitam jogador no fim, com a permissão speed.others existente.
- /shirokaiessentials:speed reset: alternativa independente se outro mod estiver fornecendo o /speed comum. Os modos walk/fly também existem nessa raiz. Nenhum comando de terceiros é removido ou substituído.

Permissões preservadas: shirokai.essentials.speed e shirokai.essentials.speed.others. O módulo utilities e o comando speed precisam estar habilitados. A raiz alternativa respeita as mesmas permissões/configurações e falhas de módulo.

Instalação: desligue o servidor, substitua SOMENTE o JAR Essentials anterior pelo arquivo zorykai-visual deste diretório e inicie. Não deixe as duas variantes em mods. Preserve config e dados. Depois execute /shirokaiessentials:speed reset no jogador afetado. Pelo console: shirokaiessentials:speed reset NomeDoPlayer.

Validação: 63 testes unitários e17 verificações em servidor Minecraft1.21.1 real, Java21, Fabric Loader0.18.3 e Fabric API0.116.18. Jogadores sintéticos com captura de pacotes; não houve cliente gráfico nem medição de deslocamento por cliente. Testes verificam atributo de caminhada, habilidades, pacotes reais, alvo, permissões, modificadores preservados e colisão simulada de /speed. Não houve nova rodada completa de cosméticos, teleportes, Cobblemon ou LuckPerms neste hotfix. Evidências e SHA-256 acompanham os JARs.
