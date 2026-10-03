# Regras para agentes neste repositório

- Responda e documente em **português do Brasil**. Nomes no código também em português (domínio contábil brasileiro).
- O usuário tem a **decisão final sobre tecnologias**. Nenhuma biblioteca ou serviço novo entra sem ADR aprovada (`docs/adr/`).
- Dinheiro é sempre `BigDecimal` com 2 casas (Java) e nunca `double`. Cálculos são determinísticos e testados.
- Arquivos originais nunca vão para o banco e nunca são alterados. O banco guarda só dados processados, sempre com arquivo, página e hash de origem.
- Serviços separados (ADR 0002): `backend`, `rag`, `mcp`, `leitor` e `frontend`, cada um no seu contêiner. Um serviço nunca importa classe de outro nem lê o schema de banco de outro: só conversa por contrato em `contracts/` (REST, fila, gRPC). Código compartilhado só em `libs/` (técnico, sem regra de negócio).
- O leitor Python (`leitor/`) não tem regra de negócio nem banco. Mudou a saída? Mude o contrato em `contracts/leitor/` (nova versão) e o rag.
- Mensagens da fila seguem `contracts/mensagens/v1`; o gRPC segue `contracts/grpc/`. Mudou? Nova versão e os dois lados no mesmo PR.
- O contrato da API é `contracts/openapi.yaml`. Mudou a API? Atualize o contrato e rode `pnpm gerar-api` no frontend.
- Toda mudança em leitor ou regra roda contra `data/golden/` e não pode piorar nenhum caso.
- O sistema aponta indícios com evidência; nunca escreve conclusões acusatórias.
- Agentes especialistas em `.claude/agents/`. Dono de cada parte:

  | Parte | Agente |
  |---|---|
  | `backend/` (API, contábil, auditoria, relatórios) | `backend` |
  | `rag/` leitura e interpretação (`rag.leitura`, `rag.dominio`) e `leitor/` | `ingestao` |
  | `rag/` embeddings, busca e respostas | `rag` |
  | `mcp/`, `infra/`, testes ponta a ponta e todo contrato entre serviços (`contracts/`) | `mcp` |
  | `frontend/` | `frontend` |
  | `docs/requisitos.md` | `requisitos` |
  | `docs/arquitetura.md`, `docs/adr/`, `libs/` | `arquiteto` |

- Fluxo de cada entrega: `requisitos` escreve os requisitos com critérios de aceite; `arquiteto` escreve a ADR quando há decisão de estrutura ou tecnologia; o usuário aprova os dois; só então os agentes de desenvolvimento entram. Requisitos em `docs/requisitos.md`, arquitetura em `docs/arquitetura.md`.

## Comandos

```bash
./gradlew test                                      # testes de backend, rag, mcp e libs (Java 25)
cd leitor && pytest                                # testes do leitor
cd frontend && pnpm build                           # checagem de tipos + build
cd infra && docker compose up --build               # sistema inteiro
```
