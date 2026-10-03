# Regras para agentes neste repositório

- Responda e documente em **português do Brasil**. Nomes no código também em português (domínio contábil brasileiro).
- O usuário tem a **decisão final sobre tecnologias**. Nenhuma biblioteca ou serviço novo entra sem ADR aprovada (`docs/adr/`).
- Dinheiro é sempre `BigDecimal` com 2 casas (Java) e nunca `double`. Cálculos são determinísticos e testados.
- Arquivos originais nunca vão para o banco e nunca são alterados. O banco guarda só dados processados, sempre com arquivo, página e hash de origem.
- O leitor Python (`services/ingestion-py`) não tem regra de negócio nem banco. Mudou a saída? Mude o contrato em `contracts/ingestion/` (nova versão) e o Java.
- O contrato da API é `contracts/openapi.yaml`. Mudou a API? Atualize o contrato e rode `pnpm gerar-api` no frontend.
- Toda mudança em leitor ou regra roda contra `data/golden/` e não pode piorar nenhum caso.
- O sistema aponta indícios com evidência; nunca escreve conclusões acusatórias.
- Agentes especialistas em `.claude/agents/`. Requisitos em `docs/requisitos.md`, arquitetura em `docs/arquitetura.md`.

## Comandos

```bash
./gradlew test                                      # testes do backend (Java 25)
cd services/ingestion-py && pytest                  # testes do leitor
cd frontend && pnpm build                           # checagem de tipos + build
cd infra && docker compose up --build               # sistema inteiro
```
