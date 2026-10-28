# ADR 0008: Padrão de logs e correlação entre os serviços

- **Status:** aprovada pelo usuário em 28/10/2026 (as 9 recomendações)
- **Requisitos atendidos:** RNF-11 (logs), escrito a partir da revisão dos logs de 23/10/2026 (`revisao-logs-2026-10-23.md`, 30 achados, agente `arquiteto`)
- **Complementa:** ADR 0002 (serviços separados; esta ADR documenta cabeçalhos em `contracts/` sem mudar nenhum corpo) e ADR 0006 (código e chaves em inglês; aqui fica registrado que as mensagens de log são em português).
- **Não decide:** rastreamento distribuído com tempos por etapa (Micrometer Tracing e OpenTelemetry), contagem das filas `.error` em tela ou no `/actuator/health` e alertas. Ficam para a fase de nuvem, cada um com ADR própria.

## Contexto

Em 23/10/2026 um 400 na tela Previsto × realizado não deixou nenhuma linha no log do api. A revisão encontrou, entre outros: nenhum erro 4xx logado; nenhum id de correlação entre api, fila, rag, reader e mcp; o rag logando arquivos só pelo nome original (texto livre do usuário, que pode trazer nome ou CPF); o reader e o mcp sem nenhum log próprio; o api gravando "falhou" sem registrar o motivo; o compose sem limite de tamanho dos logs; e a propriedade `rag.indexacao.paralelismo` lida no código enquanto o yml define `rag.indexing.parallelism`.

## Decisões (as 9 recomendações aprovadas em 28/10/2026)

| # | Decisão | Escolha |
|---|---|---|
| D1 | Formato | Texto no local; **JSON na nuvem** com o formato estruturado nativo do Spring Boot (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`), sem biblioteca. |
| D2 | Correlação | Ids de negócio (`fileId`, `processingId`, `indexingId`, `condominiumId`) **mais um `requestId` próprio**, propagado por HTTP, gRPC e fila, no MDC. Sem biblioteca. OpenTelemetry fica para a fase de nuvem. |
| D3 | Erros HTTP e gRPC | **Tratamento central na borda**: 4xx em WARN, uma linha, sem stack; 5xx em ERROR com stack; 503 e 504 de dependência em WARN. `requestId` no corpo do erro (`Problem`). |
| D4 | Usuário no log | O **`sub` do Keycloak** (pseudônimo), nunca o username. A trilha com o nome continua no banco. |
| D5 | Arquivo no log | **Só `fileId`** (e categoria), nunca o nome original nem o caminho no armazenamento. |
| D6 | reader | `logging` da biblioteca padrão do Python, formatter JSON escrito à mão, middleware do `X-Request-Id`. |
| D7 | Filas `.error` | Um recuperador nosso loga **ERROR com os ids** da mensagem ao desistir, sem o corpo. |
| D8 | Docker compose | `json-file` com rotação: **10 MB × 5** arquivos por serviço. |
| D9 | Idioma | **Mensagens em português**; chaves dos campos (MDC e JSON) em inglês, como o código (ADR 0006). |

## Como fica

### Níveis

| Nível | Quando |
|---|---|
| ERROR (com stack) | Falha inesperada que pede ação humana: 5xx; INTERNAL no gRPC; mensagem que desistiu e foi para `.error`; defeito do leitor. |
| WARN (uma linha) | Recusa ou falha esperada: todo 4xx; 401 e 403 (inclusive no gRPC); dependência fora (fila, rag, IA), com nova tentativa; arquivo que falhou na leitura por motivo do arquivo. |
| INFO | Um por evento de negócio ou de ciclo de vida: arquivo recebido, envio duplicado recusado, reprocessamento, troca de categoria, leitura e indexação concluídas, PO confirmada, de-para, rubricas, realocação, prorrogação, módulo e IA alterados. |
| DEBUG | Detalhe técnico, desligado por padrão. |

Regra de ouro: **logar uma vez, na borda** (handler HTTP, `translate` do gRPC, listener da fila, recuperador da fila). As camadas de baixo lançam a exceção.

### Campos do contexto (MDC)

`requestId`, `user` (o `sub`), `condominiumId`, `fileId`, `processingId`, `indexingId`. As chaves estão num lugar só, `libs/logging` (`LogContext`). No texto local elas aparecem entre colchetes no começo da mensagem (`logging.pattern.correlation`, o mesmo nos três serviços Java); no JSON, como campos.

### Propagação do `requestId`

Detalhe do contrato em `contracts/correlacao.md`. Resumo:
- **HTTP:** cabeçalho `X-Request-Id`. No api, um filtro (`RequestIdFilter`, primeiro da cadeia) aceita o recebido (até 64 caracteres de `[A-Za-z0-9._-]`) ou cria um, devolve no cabeçalho da resposta e no `requestId` do `Problem`. O frontend manda um por chamada e mostra o código nos erros 5xx. O rag manda o seu ao reader.
- **gRPC:** metadado `x-request-id`, com um `ClientInterceptor` e um `ServerInterceptor` em `libs/logging` (`RequestIdGrpc`). O servidor põe o id no MDC em cada etapa da chamada (o gRPC usa threads do pool) e fica por fora da autenticação, para a recusa sair com o id. O mcp tira o id do `X-Request-Id` do cliente MCP (ou cria um) no contexto da chamada da ferramenta, porque a ferramenta roda em outra thread.
- **Fila:** cabeçalho AMQP `requestId`. Um `RabbitTemplateCustomizer` em cada serviço põe o id da thread (ou um novo, na varredura) em toda mensagem publicada; o listener lê o cabeçalho e os ids do corpo (`QueueLogContext`, só UUIDs dos campos de id) e limpa o MDC no fim. **O corpo não muda**: `mensagens/v3` e `v4` continuam valendo, sem versão nova.

### Biblioteca técnica `libs/logging`

Novo módulo Gradle em `libs/` (técnico, sem regra de negócio, como pede o `CLAUDE.md`): `LogContext` (chaves, validação e escopo do MDC), `RequestIdGrpc` (interceptores) e `QueueLogContext` (contexto de uma mensagem da fila). Depende só de `slf4j-api` e `grpc-api`, que os três serviços já usam. Filtro HTTP, recuperador da fila e customizador do `RabbitTemplate` ficam em cada serviço, porque dependem do Spring de cada um.

### Nunca logar

Token, segredo, senha; chave de IA e o envelope cifrado; CPF, CNPJ de pessoa física, nome de pessoa, e-mail, username; **nome original do arquivo e caminho no armazenamento**; conteúdo de documento; valores em dinheiro de lançamentos e linhas (contagens podem); pergunta, resposta do modelo e argumentos de ferramenta; corpo de mensagem da fila e de requisição ou resposta; texto livre digitado pelo usuário (justificativas, motivos). E nenhuma conclusão acusatória: o log descreve o fato técnico.

### Ambiente

- Local: texto, sem configuração.
- Nuvem: `LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs` nos serviços Java e `LOG_FORMATO=json` no reader.
- Docker compose: âncora `x-logging` com `json-file`, `max-size: 10m`, `max-file: 5` em todos os serviços.
- reader: `uvicorn --no-access-log`; o próprio leitor loga uma linha por requisição com o `requestId`.

## Consequências

- Um erro de tela passa a ter uma linha no log, achada pelo código que a tela mostra.
- O caminho de um arquivo (api → fila → rag → reader → fila → api) se segue pelo `fileId` e pelo `requestId`.
- Os logs deixam de ter nomes de arquivo e usernames (LGPD). Quem precisar do nome consulta o banco pelo `fileId`; quem precisar de quem fez, a trilha do banco.
- O `RejectAndDontRequeueRecoverer` padrão do Spring AMQP sai, porque o WARN dele imprime a mensagem (com o corpo). A mensagem continua indo para `.error` do mesmo jeito.
- A variável `RAG_PARALELISMO_INDEXACAO` passa a valer (antes o paralelismo da indexação era sempre 1).

## Alternativas recusadas (por ora)

- **Micrometer Tracing + OpenTelemetry** (W3C `traceparent`, spans, exportação OTLP): traria bibliotecas novas. Fica para a fase de nuvem, com ADR própria e aprovação do usuário. O `requestId` próprio é compatível: dá para levar o mesmo valor como atributo do span.
- **`python-json-logger` ou `structlog`** no reader: biblioteca nova para o que cabe em 20 linhas.
- **Username no log**: dado pessoal; a auditoria já está no banco.
