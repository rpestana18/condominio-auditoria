# Correlação entre os serviços: o `requestId`

Decisão: ADR 0008 (padrão de logs), aprovada pelo usuário em 28/10/2026, decisão D2 ("`requestId` próprio, sem biblioteca nova").

O `requestId` identifica um pedido do começo ao fim e aparece em toda linha de log de todos os serviços que trabalham nele. Ele **não muda nenhum corpo** de mensagem, de requisição ou de resposta: viaja só em cabeçalhos e metadados. Por isso nenhum contrato ganhou versão nova (a regra do `CLAUDE.md` de "nova versão e os dois lados no mesmo PR" vale para mudança de corpo).

## Formato

- Texto de 1 a 64 caracteres: letras, dígitos, `.`, `-` e `_` (ex.: um UUID).
- Quem recebe um valor fora desse formato (ou nenhum) descarta e cria um novo (UUID). Assim nada de fora entra no log como texto livre.
- É opcional em todos os canais: um cliente antigo, sem o cabeçalho, continua funcionando.

## Onde viaja

| Canal | Nome | Quem manda | Quem lê |
|---|---|---|---|
| HTTP | cabeçalho `X-Request-Id` | frontend → api (REST); cliente MCP → mcp; rag → reader | api, mcp e reader. A api e o reader devolvem o mesmo cabeçalho na resposta; todo erro da api traz o valor em `requestId` (`Problem`, `contracts/openapi.yaml`) |
| gRPC | metadado `x-request-id` | mcp → api (`query/v2`); api → rag (`assistant/v2`); rag → api (`query/v2`, ferramentas do chat) | o servidor gRPC de cada lado, que o põe no MDC em cada etapa da chamada |
| Fila (AMQP) | cabeçalho `requestId` da mensagem | api → rag (`rag.files-received`, `rag.indexing`); rag → api (`api.processing-results`, `api.indexing-results`) | o listener de cada fila; a resposta do rag sai com o mesmo id que chegou |

O corpo das mensagens da fila continua o de `mensagens/v3` e `mensagens/v5` (o resultado do processamento está na v5 desde a ADR 0009, Decisão 8). Os ids de negócio do corpo (`fileId`, `processingId`, `indexingId`, `condominiumId`) continuam sendo a chave principal e também vão para o log; o `requestId` só liga a fila à requisição que a causou. Uma mensagem publicada fora de uma requisição (a varredura que reenvia arquivos parados) leva um id novo.

## O que nunca viaja junto

Token, nome do usuário, nome original do arquivo, conteúdo de documento. O usuário aparece no log pelo `sub` do Keycloak, lido do token por quem o valida, e não por cabeçalho próprio.
