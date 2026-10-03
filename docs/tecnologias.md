# Opções de Tecnologia — para decisão do usuário

Versão 0.1 · 03/10/2026 · Status: **todas as tecnologias do MVP decididas** (03/10/2026); T9 nuvem depois

## Decisões tomadas

| Tema | Decisão do usuário | Data |
|---|---|---|
| T1 Backend | **Java + Spring Boot** (RAG, regras, auditoria, API, MCP) | 03/10/2026 |
| T4 Leitura de documentos | **Serviço Python isolado** (`services/ingestion-py`): contêiner sem estado, sem banco e sem regra de negócio, um endpoint (arquivo → JSON). Contrato JSON Schema versionado em `contracts/`, validado pelo Java. Pode ser trocado por um leitor em Java que respeite o mesmo contrato | 03/10/2026 |
| T5 Modelo de IA | **Claude via API da Anthropic** (chave no Claude Console, cobrada à parte da assinatura), atrás de uma camada única no código | 03/10/2026 |
| Modo de IA (decidido 03/10, ver RF-09) | **Sem chave de API no MVP**: o sistema roda sem IA, que é opcional por configuração. A IA entra pelo **servidor MCP**, usado pelo Claude Desktop ou Claude Code da assinatura do usuário. Embeddings do RAG locais, em Java. Chat na tela e classificação em segundo plano ficam para quando houver chave de API. **Parametrizável por condomínio** (MCP_EXTERNO, API_KEY própria criptografada, DESLIGADO), sem mudar código | 03/10/2026 |
| T3 Banco de dados | **PostgreSQL** (+ pgvector para os vetores do RAG). Guarda só dados processados: extraídos, normalizados e enriquecidos, cada registro com caminho do arquivo, página e hash | 03/10/2026 |
| T8 Arquivos originais | **Pasta no sistema de arquivos** (`dados/<condominio>/<categoria>/<ano>/`), nunca no banco e nunca alterados. Acesso por uma interface única de armazenamento; na nuvem, parâmetro troca para storage de objetos (S3 ou similar) sem mudar código. MinIO sai do MVP | 03/10/2026 |
| T2 Frontend | **React + TypeScript (Vite)**, SPA que só consome a API do Spring. Foco em gráficos, dashboards e apresentação de dados. Usuário vem de JavaScript, AngularJS, Angular 2 e TypeScript (Dart): manter código didático e bem tipado | 03/10/2026 |
| T6 RAG e T12 MCP | **Spring AI**: RAG (chunking, embeddings locais, pgvector, busca com página de origem), cliente Claude e servidor MCP no mesmo backend Spring Boot | 03/10/2026 |
| T7 Tarefas em segundo plano | **@Async do Spring** (pool de threads com limite configurável). Perder tarefa é aceitável, pois o original fica na pasta. Regras: uma transação por documento, gravada só no fim (rollback em falha, leitura fora da transação); status do arquivo (Pendente, Processando, Concluído, Falhou + erro) gravado à parte; na subida, o que ficou "Processando" volta para a fila; reprocessar apaga a extração anterior do arquivo na mesma transação (idempotente) | 03/10/2026 |
| T10 Autenticação e perfis | **Keycloak** (contêiner, módulo à parte): usuários, senhas, perfis Usuário/Gestor/Admin e vínculo com condomínios. Front e back só aceitam **token Bearer** válido (backend Spring = resource server OAuth2/JWT). Token de acesso curto renovado com o uso; sessão expira por inatividade (configurável, ex.: 30 min). Servidor MCP também exige token. Usuário conhece Keycloak a fundo | 03/10/2026 |
| T11 Relatórios | **Thymeleaf + OpenHTMLtoPDF** (PDF a partir de template HTML/CSS) e **Apache POI** (Excel), gerados no backend para sair igual na tela, na exportação e via MCP | 03/10/2026 |
| T13 Build e monorepo | **Gradle** multi-módulo (Kotlin DSL + catálogo de versões) no backend, Java 25 LTS; **pnpm + Vite** no frontend; Dockerfile no leitor Python; **Docker Compose** sobe tudo com um comando | 03/10/2026 |
| T9 Nuvem | Fica para quando o MVP estiver pronto | — |

A tabela abaixo é a análise original. A opção A de T1 (Python) foi **substituída** pela decisão acima.

Para cada tema: opções com prós e contras e a **recomendação do arquiteto**. A decisão é sua. Responda com o código (ex.: "T1-A, T2-B…") ou "aceito as recomendações". Cada decisão vira uma ADR em `docs/adr/` do repositório.

Critérios usados: (1) roda local com um comando; (2) vai para nuvem sem reescrita; (3) qualidade para extrair tabelas financeiras de PDF/Excel/Word; (4) agentes de IA trabalham bem com a stack; (5) simplicidade para um projeto de poucas pessoas.

---

## T1 — Linguagem e framework do backend (inclui ingestão e RAG)

| Opção | Prós | Contras |
|---|---|---|
| **A. Python + FastAPI** | Melhor ecossistema para PDF/Excel/OCR (pdfplumber, Docling, openpyxl, pandas) e IA/RAG; OpenAPI automático; `Decimal` nativo | Duas linguagens no repo (Python + TS no frontend) |
| B. TypeScript + NestJS (ou Fastify) | Uma linguagem só no repo inteiro; tipagem compartilhada com o frontend | Bibliotecas de extração de PDF/tabelas e OCR bem mais fracas; acabaria precisando de um serviço Python mesmo assim |
| C. Python + Django (+ DRF) | Admin, auth e ORM prontos; muito maduro | Mais pesado; assíncrono menos natural; admin do Django não substitui o frontend pedido |

**Recomendação: A.** O coração do sistema é extrair e calcular dados de documentos, e é onde Python é claramente superior.

## T2 — Frontend

| Opção | Prós | Contras |
|---|---|---|
| **A. React + Vite + TypeScript (SPA) + shadcn/ui + Recharts/ECharts** | Simples de rodar local; enorme ecossistema de gráficos e tabelas; consome a API diretamente | Sem renderização no servidor (irrelevante para app interno) |
| B. Next.js (React) | Rotas e SSR prontos; fácil de hospedar na Vercel | Camada de servidor extra que duplica papel do backend; mais complexidade |
| C. Streamlit / Dash (Python) | Muito rápido para protótipo de dashboard; tudo em Python | Limitado para perfis, upload por categoria, UX refinada; difícil evoluir para produto |

**Recomendação: A.** Se quiser ver telas em poucos dias antes de investir, C pode servir só como protótipo descartável.

## T3 — Banco de dados

| Opção | Prós | Contras |
|---|---|---|
| **A. PostgreSQL + pgvector** | Um só banco para dados contábeis e vetores do RAG; transações e `numeric` exato; existe gerenciado em todas as nuvens | Precisa de um contêiner (Docker) |
| B. SQLite (+ sqlite-vec) | Zero instalação; arquivo único | Concorrência fraca; migração para nuvem exige trocar de banco |
| C. PostgreSQL + banco vetorial separado (Qdrant/Chroma) | Busca vetorial mais especializada | Dois bancos para manter e sincronizar; desnecessário no volume de um condomínio |

**Recomendação: A.**

## T4 — Extração de documentos (PDF, Excel, Word) e OCR

| Opção | Prós | Contras |
|---|---|---|
| **A. Híbrido: bibliotecas locais (pdfplumber/Docling para PDF, openpyxl/pandas para Excel, python-docx para Word) + modelo de IA com visão como *fallback* para layouts difíceis e escaneados** | Grátis e determinístico no caso comum; IA só quando necessário; funciona local | Mais código (parsers por layout) |
| B. Tudo via modelo de IA multimodal (envia a página, recebe JSON) | Pouquíssimo código; lida com qualquer layout | Custo por página; resultado pode variar entre execuções; todo documento sai da máquina |
| C. Serviço gerenciado de OCR/tabelas (AWS Textract, Google Document AI, Azure Document Intelligence) | Muito bom em tabelas e escaneados | Exige nuvem desde o MVP; custo; dependência de fornecedor |

**Recomendação: A.** OCR local (Tesseract via Docling/ocrmypdf) só entra se você confirmar que há escaneados (Q1).

## T5 — Modelo de IA (raciocínio, classificação, RAG)

| Opção | Prós | Contras |
|---|---|---|
| **A. Claude via API da Anthropic** (ex.: Sonnet 5.5 para o dia a dia, Opus 5.5 para análises complexas, Haiku 4.5 para classificação barata) | Ótimo em português, documentos longos, saídas estruturadas e uso de ferramentas; mesmo ecossistema do Claude Code e do MCP | Dados saem da máquina; custo por uso |
| B. Outro provedor de nuvem (OpenAI, Google Gemini) | Qualidade comparável; preços competitivos | Integração MCP/agentes menos direta com o Claude Code que você já usa |
| C. Modelo local (Llama/Qwen via Ollama) | Nada sai da máquina; custo zero por chamada | Exige máquina com GPU/memória; qualidade bem inferior em extração e raciocínio contábil |

**Recomendação: A**, sempre atrás do AI Gateway (`packages/ai-gateway`) para que trocar de provedor seja configuração. Importante: no MVP local, com A ou B, o conteúdo dos documentos vai para a API do provedor.

## T6 — Embeddings e orquestração do RAG

| Opção | Prós | Contras |
|---|---|---|
| **A. Implementação própria enxuta** (chunking + embeddings + pgvector + busca híbrida com `tsvector` em português) com um modelo de embedding multilíngue (Voyage, OpenAI ou local `bge-m3`) | Controle total; pouco código; fácil de testar e citar fontes | Você mantém o código |
| B. LlamaIndex | Muitos conectores e estratégias prontas | Abstrações pesadas; versões mudam rápido |
| C. LangChain / LangGraph | Ecossistema grande; agentes prontos | Mesmo problema de abstração e instabilidade de API |

**Recomendação: A**, com embedding local `bge-m3` se quiser nada saindo da máquina na indexação, ou Voyage/OpenAI para melhor qualidade. Sub-decisão T6.1: qual embedding.

## T7 — Execução de tarefas em segundo plano (ingestão, auditoria)

| Opção | Prós | Contras |
|---|---|---|
| A. Tarefas em processo (FastAPI BackgroundTasks) | Nada extra para instalar | Perde a tarefa se o servidor reiniciar; não escala |
| **B. Fila em Postgres (Procrastinate ou similar)** | Sem novo serviço; persistente; tarefas sobrevivem a reinício | Menos recursos que filas dedicadas |
| C. Celery/RQ + Redis | Padrão de mercado; escala bem | Mais um serviço (Redis) para manter |

**Recomendação: B.**

## T8 — Armazenamento dos arquivos originais

| Opção | Prós | Contras |
|---|---|---|
| A. Pasta local | Simples | Migrar para nuvem exige mudança de código |
| **B. MinIO local (compatível com S3)** | Mesmo código local e na nuvem (S3/GCS/R2) | Um contêiner a mais |
| C. Dentro do banco (bytea) | Backup único | Banco cresce muito; ruim para arquivos grandes |

**Recomendação: B**, ou A atrás de uma interface de armazenamento se quiser menos contêineres no MVP.

## T9 — Nuvem (fase 3, decidir depois)

| Opção | Prós | Contras |
|---|---|---|
| A. AWS | Mais completo; região São Paulo | Complexidade e custo de gestão |
| B. Google Cloud (Cloud Run + Cloud SQL) | Muito simples para contêineres; região São Paulo | Menos serviços que AWS |
| C. VPS simples (Hetzner, DigitalOcean, Hostinger) com Docker Compose | Barato e previsível; o mesmo `docker-compose` do MVP | Você cuida de backup, segurança e atualizações |

**Recomendação: decidir só na fase 3.** Para um único condomínio, B ou C tendem a ser suficientes.

## T10 — Autenticação e perfis

| Opção | Prós | Contras |
|---|---|---|
| **A. Própria no backend (usuário/senha com hash Argon2, JWT/sessão, perfis no banco)** | Simples para o MVP local | Precisa evoluir (MFA, recuperação de senha) na nuvem |
| B. Keycloak (contêiner) | Completo: MFA, SSO, perfis | Pesado para 3 perfis e poucos usuários |
| C. Provedor gerenciado (Auth0, Clerk, Supabase Auth) | Pronto e seguro | Depende de internet e de serviço externo já no MVP |

**Recomendação: A no MVP**, migrar para C (ou B) na nuvem.

## T11 — Geração de relatórios (PDF/Excel)

| Opção | Prós | Contras |
|---|---|---|
| **A. HTML → PDF com WeasyPrint + openpyxl para Excel** | Relatório com o mesmo visual do sistema; templates fáceis | WeasyPrint tem dependências de sistema (resolvido no Docker) |
| B. ReportLab | Controle fino do PDF | Layout trabalhoso de manter |
| C. Exportação feita no navegador (frontend) | Sem carga no servidor | Relatórios inconsistentes; difícil de testar e de usar via MCP |

**Recomendação: A.**

## T12 — Servidor MCP

| Opção | Prós | Contras |
|---|---|---|
| **A. SDK oficial MCP em Python (FastMCP)** | Mesma linguagem do backend; reutiliza modelos de `packages/domain` | — |
| B. SDK oficial MCP em TypeScript | SDK mais antigo e completo; reutiliza tipos gerados do OpenAPI | Linguagem diferente do backend (se T1-A) |

**Recomendação: segue T1.** Com T1-A, opção A.

## T13 — Ferramentas do monorepo

| Opção | Prós | Contras |
|---|---|---|
| **A. `uv` (workspaces Python) + `pnpm` (frontend) + `Makefile`/`just` na raiz + Docker Compose** | Simples; rápido; cada linguagem com sua ferramenta nativa | Dois gerenciadores |
| B. Nx ou Turborepo | Cache de build e grafo de dependências | Pensados para JS; suporte a Python fraco |
| C. Pants / Bazel | Poliglota e muito poderoso | Curva de aprendizado alta demais para o tamanho do projeto |

**Recomendação: A.** Inclui GitHub Actions para CI (lint, testes, golden files).

---

## Resumo das recomendações

| Tema | Recomendação |
|---|---|
| T1 Backend | Python + FastAPI |
| T2 Frontend | React + Vite + TS |
| T3 Banco | PostgreSQL + pgvector |
| T4 Extração | Bibliotecas locais + IA como fallback |
| T5 Modelo de IA | Claude (API Anthropic) atrás de um gateway |
| T6 RAG | Implementação própria; embedding a decidir |
| T7 Jobs | Fila em Postgres |
| T8 Arquivos | MinIO (S3 local) |
| T9 Nuvem | Decidir na fase 3 |
| T10 Auth | Própria no MVP |
| T11 Relatórios | WeasyPrint + openpyxl |
| T12 MCP | SDK Python |
| T13 Monorepo | uv + pnpm + just + Docker Compose |
