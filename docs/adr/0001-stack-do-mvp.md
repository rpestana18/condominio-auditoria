# ADR 0001: Tecnologias do MVP

- **Status:** aprovada pelo usuário em 03/10/2026
- **Detalhe das opções avaliadas:** `docs/tecnologias.md`

| Tema | Decisão |
|---|---|
| Backend | Java 25 + Spring Boot |
| Leitura de documentos | Serviço Python isolado e sem estado (`leitor/`), contrato JSON Schema versionado em `contracts/ingestion/` |
| Modelo de IA | Claude. Modo por condomínio: MCP externo (piloto), chave de API própria ou desligado |
| Banco | PostgreSQL + pgvector, só com dados processados |
| Arquivos originais | Pasta no disco, fora do banco, via interface `Armazenamento` (S3 na nuvem, por parâmetro) |
| Frontend | React + TypeScript + Vite |
| RAG e MCP | Spring AI (cada um no seu serviço: ver ADR 0002) |
| Tarefas em segundo plano | ~~`@Async`~~ substituído pela fila RabbitMQ entre backend e rag (ADR 0002). Continuam: transação única por documento, status e reprocesso idempotente |
| Autenticação | Keycloak, token Bearer, sessão expira por inatividade |
| Relatórios | Thymeleaf + OpenHTMLtoPDF (PDF) e Apache POI (Excel) |
| Build | Gradle multi-módulo (Kotlin DSL), pnpm, Docker Compose |
| Nuvem | Decidir quando o MVP estiver pronto |

Mudar qualquer item exige nova ADR aprovada pelo usuário.
