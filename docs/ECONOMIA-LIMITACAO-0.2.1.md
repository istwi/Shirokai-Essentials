# Impactor 5.3.5: mutações ainda bloqueadas

Saldo e ranking continuam usando a currency real `impactor:dollars`. `/pay`, `/eco` e cobrança por kits/teleportes **não foram concluídos nem habilitados**. Não há saldo paralelo, conversão de tokens ou interceptação dos comandos financeiros de terceiros.

## Reprodução no JAR oficial

O cenário `tests/integration/cobblemon.cjs` usa Impactor-Fabric-5.3.5+1.21.1, backend JSON padrão e duas contas exclusivas do servidor descartável, inicialmente com 500 dólares cada. O comando auxiliar usa a API pública `Account.transfer` e aguarda também os futures públicos de `EconomyService.save`.

1. Cria as contas e confirma os dois arquivos com saldo 500.
2. Guarda o arquivo do destinatário e coloca um diretório no lugar para provocar falha de I/O determinística.
3. Transfere 10 dólares pela API.
4. Confirma retorno `SUCCESS`, futures concluídos sem exceção e saldos em memória 490/510.
5. Confirma saldo persistido do remetente 490, destinatário não gravado e arquivo anterior ainda com 500.
6. Restaura o arquivo de teste e encerra o servidor.

Isso reproduz perda de consistência sem precisar simular queda física. O cenário usa somente arquivos dentro de `build/cobblemon-integration/run/`; não execute a injeção de falha em produção. A documentação de testes contém os comandos e as versões necessárias.

O bytecode do mesmo JAR mostra que a transferência salva as contas separadamente e descarta os futures internos. O backend Configurate captura exceções de escrita sem propagá-las ao future. Portanto, aguardar um `save` extra ou chamar `transferAsync` não fornece uma confirmação durável. Esta evidência de falha refere-se ao backend JSON testado; não é uma afirmação de teste dos demais backends.

## O que precisa ser resolvido

Um provedor compatível precisa confirmar a persistência, propagar falhas e oferecer transação conjunta de débito/crédito, com controle de concorrência e recuperação após interrupção. Um lock apenas no Shirokai não cobre gravações de outros mods nem queda entre os dois saves. Reembolso automático após uma operação incerta também pode duplicar dinheiro.

Depois de existir esse contrato verificável, a implementação pode liberar `/pay` e `/eco` e vincular cobranças a recibos idempotentes de kits/teleportes, revalidando permissão, batalha e destino antes do efeito. Até lá, mantêm-se os bloqueios exigidos pela seção Economia do TXT fornecido. Não se alterou o JAR, a configuração ou o armazenamento do Impactor instalado pelo usuário.

Não é necessária importação do outro mod: essa etapa ficou fora do trabalho solicitado.
