# ADR 0002: Serviços separados (backend, rag, mcp) e integração entre eles

- **Status:** aprovada pelo usuário em 03/10/2026
- **Substitui em parte:** ADR 0001, nas linhas "RAG e MCP" (onde rodam) e "Tarefas em segundo plano" (`@Async`)

## Contexto

Na primeira entrega, leitura de documentos, regras e API rodavam no mesmo processo Spring Boot (`backend/app`), com módulos Gradle separados só no código. O usuário não quer domínios diferentes no mesmo pacote: cada parte deve subir sozinha e conversar com as outras só por contrato. O sistema vai atender vários condomínios, com centenas ou milhares de arquivos.

## Decisão

### Serviços (um contêiner cada)

| Serviço | Faz | Banco |
|---|---|---|
| `frontend` | Telas (React) | nenhum |
| `backend` | API REST, contábil, auditoria, orçamento, relatórios, registro dos arquivos | schema `backend` |
| `rag` | Lê o original, chama o leitor, interpreta o layout, confere as somas, enriquece; depois, embeddings e busca | nenhum por enquanto; schema `rag` quando entrar o pgvector |
| `mcp` | Porta de entrada da IA externa (Model Context Protocol), sem regra nem banco | nenhum |
| `leitor` | Python: arquivo → JSON com posições (sem mudança) | nenhum |

Mesmo PostgreSQL, **um schema por serviço**. Nenhum serviço lê o schema de outro.

### Como conversam

| De → para | Meio | Contrato | Por quê |
|---|---|---|---|
| frontend → backend | REST/JSON | `contracts/openapi.yaml` | O navegador fala HTTP; o token do Keycloak vai no cabeçalho |
| backend → rag (pedido de leitura) | **RabbitMQ**, fila `rag.arquivos-recebidos` | `contracts/mensagens/v1/arquivo-recebido.schema.json` | Assíncrono, durável, com retentativa e fila de erro |
| rag → backend (andamento e dados extraídos) | **RabbitMQ**, fila `backend.resultados` | `contracts/mensagens/v1/resultado-processamento.schema.json` | Idem; o backend grava tudo numa transação |
| mcp → backend (consultas) | **gRPC** | `contracts/grpc/consulta/v1/consulta.proto` | Escolha do usuário: rápido, contrato forte, resposta em fluxo (stream) para milhares de lançamentos |
| rag → leitor | HTTP multipart | `contracts/leitor/v1/documento-lido.schema.json` | Sem mudança |

Só contratos são compartilhados. Cada serviço tem as suas classes; o código gerado do `.proto` fica em `libs/contrato-grpc`, e a interface de armazenamento dos originais em `libs/armazenamento`.

### Regras da fila

- O backend publica o pedido **depois do commit** do registro do arquivo.
- Cada leitura tem um `processamentoId`. Reprocessar gera um novo; resultado com id antigo é descartado. Isso torna repetição e mensagem fora de ordem inofensivas.
- O rag só confirma a mensagem (ack) depois de publicar o resultado. Se cair no meio, o RabbitMQ entrega de novo.
- A gravação no backend é uma transação só (apaga a extração anterior do arquivo e insere a nova). Falhou: rollback, retentativa (3 vezes) e depois a fila `.erro`, onde a mensagem fica para análise.
- Uma varredura a cada minuto reenvia o que ficou Pendente ou Processando há mais de 15 minutos (até 3 tentativas; depois, Falhou com motivo).
- As duas pontas validam as mensagens contra o JSON Schema, na entrada e na saída. Dinheiro vai como texto com duas casas (`"1234.56"`).

### Segurança

- O mcp exige token Bearer do Keycloak (mesmo realm) e repassa o **token do próprio usuário** em cada chamada gRPC (metadado `authorization`).
- O backend valida o token no gRPC com a mesma regra da API REST (perfil e condomínio). A IA só vê o que o usuário pode ver.
- gRPC sem TLS dentro da rede do Docker; na nuvem, TLS na malha de rede ou por parâmetro.

### Versões

| Peça | Versão | Observação |
|---|---|---|
| RabbitMQ | imagem `rabbitmq:4-management` | Spring AMQP (`spring-boot-starter-amqp`) |
| gRPC Java | 1.84.0 | `grpc-netty-shaded`, servidor e canal montados em poucas linhas; o starter de gRPC do Spring Boot só sai na 4.2 (hoje em milestone). Quando a 4.2 for estável, a troca é só de configuração |
| Protobuf | 4.33.4 | plugin Gradle `com.google.protobuf` 0.10.0 |
| MCP | Spring AI 2.0.1, transporte HTTP sem sessão (`STATELESS`) | Ferramentas com `@McpTool` |

## Consequências

- Cada serviço sobe, cai e escala sozinho. Muitos arquivos: aumenta-se o número de réplicas do rag sem mexer no backend.
- O `@Async` sai: a fila substitui a fila em memória, com a vantagem de não perder trabalho em reinício.
- Mais peças para rodar (RabbitMQ). Tudo continua subindo com um `docker compose up`.
- Mudar um contrato exige nova versão em `contracts/` e ajuste dos dois lados no mesmo PR.

Mudar qualquer item exige nova ADR aprovada pelo usuário.
