# Proposta de Arquitetura — Monorepo

Versão 0.2 · 03/10/2026 · Status: **tecnologias do MVP decididas pelo usuário** (ver `03-tecnologias.md`; T9 nuvem fica para depois)

Stack: **Java 25 + Spring Boot + Spring AI** (backend, RAG, MCP), **Gradle** multi-módulo (Kotlin DSL + catálogo de versões), **PostgreSQL + pgvector**, **Keycloak** (token Bearer), **React + TypeScript + Vite** (pnpm), **leitor de documentos em Python** isolado, relatórios com **Thymeleaf + OpenHTMLtoPDF + Apache POI**, tarefas com **@Async**. Tudo sobe com um `docker compose up`.

---

## 1. Visão geral

```
                         ┌───────────────────────────┐
                         │        FRONTEND (web)     │
                         │ dashboard · arquivos ·    │
                         │ achados · previsão · chat │
                         └─────────────┬─────────────┘
                                       │ HTTP/JSON (API REST)
┌──────────────┐          ┌────────────▼─────────────┐          ┌──────────────┐
│  MCP SERVER  │──API────▶│          BACKEND         │◀────────▶│  BANCO (T3)  │
│ ferramentas  │          │ auth/perfis · domínio    │          │ relacional + │
│ p/ agentes IA│          │ contábil · auditoria ·   │          │ vetores      │
└──────────────┘          │ orçamento · relatórios   │          └──────────────┘
                          └───┬──────────────┬───────┘
                   fila de    │              │ consultas/
                   jobs (T7)  │              │ respostas
                     ┌────────▼───────┐ ┌────▼───────────┐
                     │   INGESTÃO     │ │      RAG       │
                     │ parse PDF/XLSX │ │ chunk · embed  │
                     │ DOCX · OCR ·   │ │ busca híbrida ·│
                     │ normalização   │ │ resposta c/    │
                     └───────┬────────┘ │ citações       │
                             │          └───────┬────────┘
                             ▼                  ▼
                     ┌─────────────────────────────────┐
                     │ ARMAZENAMENTO DE ARQUIVOS (T8)  │   ┌──────────────┐
                     │ originais imutáveis + derivados │   │ MODELOS DE IA│
                     └─────────────────────────────────┘   │ (T5) via     │
                                                           │ camada única │
                                                           └──────────────┘
```

Princípio central: **IA para entender documentos; código determinístico para calcular e auditar**. Todo número exibido vem do banco, calculado por regra versionada.

**Multi-condomínio (D12)**: toda tabela de negócio leva `condominio_id`. O isolamento é aplicado no backend em toda consulta (e, com PostgreSQL, também por *row-level security*). Índices do RAG e arquivos também são separados por condomínio. Regras ficam em duas camadas: **legais** (código, iguais para todos) e **do condomínio** (parâmetros extraídos da convenção, do RI e da PO e confirmados pelo Admin, com vigência).

---

## 2. Estrutura do monorepo

```
condominio-auditoria/
├── settings.gradle.kts         # módulos Gradle do backend
├── build.gradle.kts
├── gradle/libs.versions.toml   # catálogo único de versões
├── backend/
│   ├── app/                    # Spring Boot: API REST, segurança (resource server), @Async, servidor MCP, montagem
│   ├── domain/                 # entidades e regras puras (Java, sem Spring): Lancamento, Fundo, Achado, Rubrica…
│   ├── ingestion/              # chama o leitor Python, valida o contrato, normaliza, enriquece e persiste
│   ├── rag/                    # Spring AI: chunking, embeddings locais, pgvector, busca com página de origem
│   ├── audit/                  # motor de auditoria, conciliação, previsto × realizado, projeções
│   ├── ai-gateway/             # modo de IA por condomínio (MCP_EXTERNO, API_KEY, DESLIGADO), custo, mascaramento
│   ├── storage/                # interface Armazenamento: disco local (MVP) ou S3 (nuvem), por parâmetro
│   └── reports/                # Thymeleaf + OpenHTMLtoPDF (PDF) e Apache POI (Excel)
├── services/
│   └── ingestion-py/           # leitor de documentos: contêiner sem estado, arquivo → JSON (Dockerfile próprio)
├── frontend/                   # React + TypeScript + Vite (pnpm); tipos gerados do OpenAPI
├── contracts/
│   ├── openapi.yaml            # contrato da API (backend ↔ frontend ↔ MCP)
│   └── ingestion/v1/           # JSON Schema da saída do leitor Python (versionado)
├── infra/
│   ├── docker-compose.yml      # PostgreSQL, Keycloak, leitor Python, backend, frontend
│   ├── keycloak/               # realm exportado: clientes, perfis, tempos de sessão
│   └── cloud/                  # (fase 3) IaC
├── dados/                      # arquivos originais enviados (fora do git): dados/<condominio>/<categoria>/<ano>/
├── data/golden/                # documentos de referência anonimizados + resultado esperado
├── docs/adr/                   # uma ADR por decisão T*
├── .claude/agents/             # agentes especialistas
├── CLAUDE.md
└── README.md
```

Regra de dependência entre módulos Gradle: `domain` não depende de ninguém; `ingestion`, `rag`, `audit`, `reports`, `ai-gateway` e `storage` dependem só de `domain`; `app` monta tudo. O leitor Python não conhece o banco nem as regras: recebe arquivo e devolve JSON no contrato `contracts/ingestion/v1`.

### 2.1 Arquivos × banco (decisão do usuário)
- **Originais nunca vão para o banco.** Ficam em `dados/`, imutáveis, lidos pela interface `Armazenamento`.
- **O banco guarda só dados processados**: extraídos, normalizados e enriquecidos. Todo registro aponta para caminho do arquivo, página e hash.
- Os trechos e vetores do RAG também são dados processados; se o banco se perder, tudo é reprocessável a partir de `dados/`.

### 2.2 Processamento em segundo plano (@Async)
- Upload grava o arquivo, cria o registro com status **Pendente** e responde na hora.
- A tarefa: leitura (Python) e embeddings **fora** da transação; depois **uma transação** grava tudo (apaga a extração anterior do mesmo arquivo, insere a nova). Falha = rollback e status **Falhou** com motivo.
- Status gravado em transação própria (`REQUIRES_NEW`) para a tela acompanhar.
- Na subida, registros em **Processando** voltam para a fila. Pool de threads com limite configurável.

### 2.3 Segurança (Keycloak)
- Keycloak gerencia usuários, senhas, perfis (Usuário, Gestor, Admin) e vínculo com condomínios (claim no token).
- Frontend faz login OIDC (Authorization Code + PKCE). Backend é resource server: valida o JWT e aplica perfil e condomínio em todo endpoint.
- Token de acesso curto, renovado com o uso; sessão cai por inatividade (configurável no realm, ex.: 30 min).
- O servidor MCP exige token: o Claude só vê o que o usuário pode ver.

---

## 3. Módulos

### 3.1 Ingestão (`backend/ingestion` + `services/ingestion-py`)
Responsável por transformar arquivo em dado estruturado.

Pipeline por arquivo:
1. **Recebimento**: salva original imutável, calcula hash, registra categoria/competência/autor.
2. **Detecção**: tipo real do arquivo; PDF digital vs. escaneado.
3. **Extração**: texto + tabelas (PDF/XLSX/DOCX); OCR quando necessário.
4. **Classificação** (IA): confirma categoria, competência, conta bancária, fornecedor.
5. **Normalização**: converte para o modelo `Lançamento` (data, descrição, favorecido, CNPJ/CPF, rubrica, valor em centavos, origem = arquivo/página/linha). Parsers específicos por *layout* conhecido (ex.: balancete da administradora X) têm prioridade; IA com saída estruturada é o *fallback*.
6. **Validação**: somas batem com totais do próprio documento; datas dentro da competência; se falhar, status "precisa revisão" com motivo.
7. **Publicação**: grava no banco e dispara indexação no RAG e reauditoria do mês.

Idempotente: reenviar o mesmo arquivo não duplica nada.

### 3.2 RAG (`backend/rag`, Spring AI)
- Indexa **texto** dos documentos (contratos, **atas de assembleia**, convenção, RI, POs). Atas também geram registros estruturados de `Deliberacao` (assunto, valor, fundo, prazo) usados para justificar achados em *chunks* com metadados (categoria, competência, página).
- Busca **híbrida** (palavra-chave + vetorial) com filtro por categoria/período/permissão.
- Geração com **citações obrigatórias**. Perguntas numéricas são roteadas para **ferramentas** que consultam o banco (*text-to-query* controlado ou endpoints prontos), nunca respondidas só pelo texto.
- Também alimenta o backend: extrai cláusulas de contratos (valor, índice de reajuste, data-base, vigência) e regras da convenção (rateio, multas, fundo de reserva) para parâmetros que o admin confirma.

### 3.3 Backend (`backend/app`, `backend/audit`, `backend/reports`)
- **Auth e perfis** (Usuário/Gestor/Admin) aplicados em todos os endpoints.
- **Domínio contábil**: plano de contas, rubricas, mapeamento balancete↔PO, unidades e frações.
- **Motor de auditoria**: executa as regras de `backend/audit` por competência; gera `Achado` com evidência; versão das regras registrada.
- **Conciliação**: algoritmo de casamento balancete × extrato × comprovante (valor exato, janela de datas, similaridade de favorecido; casamentos 1:N e N:1).
- **Orçamento e previsão**: previsto × realizado; projeção por rubrica (estatística + contratos + índices).
- **Relatórios**: PDF/Excel sob demanda.
- **Trilha de auditoria** de todas as mudanças.
- **Orquestração de jobs** de ingestão/indexação/auditoria.

### 3.4 Frontend (`frontend/`, React + TypeScript)
Telas: Login · Início (KPIs + gráficos) · Arquivos (por categoria, mais recente primeiro, upload para Gestor/Admin) · Achados · Orçamento/Previsão · Assistente · Administração. Componente global "último arquivo" fixo em um canto.

### 3.5 MCP (servidor MCP do Spring AI dentro de `backend/app`)
- Expõe o sistema como **ferramentas MCP**: `listar_arquivos`, `consultar_lancamentos`, `listar_achados`, `previsto_realizado`, `buscar_documentos` (RAG), `rodar_auditoria`, `gerar_relatorio`, `enviar_arquivo` (só Gestor/Admin).
- Roda dentro do backend e chama os **mesmos serviços da API** (nunca SQL próprio), com o token e as permissões do usuário.
- Serve para: uso do sistema pelo Claude Desktop/Code; e testes de integração ponta a ponta conduzidos pelo agente MCP.
- **Papel de integração**: o agente MCP é acionado sempre que um módulo muda um contrato (OpenAPI, modelo de domínio, ferramenta), para verificar que todos os consumidores continuam funcionando.

### 3.6 AI Gateway (`backend/ai-gateway`)
Camada única para modelos de IA. **Modo por condomínio (RF-09)**: MCP_EXTERNO (piloto, o Claude do usuário via MCP), API_KEY (chave própria do condomínio, criptografada) ou DESLIGADO. Troca de provedor/modelo por configuração, cache de respostas por hash do insumo, registro de custo/tokens, *prompts* versionados, saídas estruturadas validadas por esquema. Ponto onde entra o **mascaramento LGPD** na fase de nuvem.

---

## 4. Modelo de dados (núcleo)

```
Condominio(id, nome, cnpj, administradora, layout_parser, status[implantacao|ativo])
Vinculo(usuario_id, condominio_id, perfil[usuario|gestor|admin])
Fundo(id, condominio_id, nome, finalidade, regra_uso, permite_saldo_negativo, politica[ex.: inadimplencia_flutuante])
Deliberacao(id, condominio_id, ata_arquivo_id, data, assunto, valor_aprovado, fundo_id, parcelas, vigencia_fim)
MeioPagamento(id, condominio_id, conta_ou_fornecedor)   -- ex.: Mercado Pago, cartão
Realocacao(id, lancamento_id, rubrica_destino_id, sugerida_por[ia|regra|usuario], confirmada_por, em)
ParametroCondominio(id, condominio_id, chave, valor, origem_arquivo_id, trecho, vigencia, confirmado_por)
Usuario(id, nome, email, super_admin, ativo)
Arquivo(id, categoria, competencia, nome, hash, versao, enviado_por, enviado_em, status, caminho)
Pagina/Chunk(id, arquivo_id, pagina, texto, embedding, metadados)
ContaBancaria(id, banco, agencia, conta, tipo)
Rubrica(id, codigo, nome, grupo)             MapeamentoRubrica(nome_origem, rubrica_id)
Lancamento(id, origem[balancete|extrato|comprovante|folha], arquivo_id, pagina, linha,
           data, competencia, descricao, favorecido, documento_fiscal, rubrica_id,
           conta_id, fundo_id, valor_centavos, natureza[credito|debito], transferencia_entre_fundos)
-- todas as tabelas abaixo também carregam condominio_id
Conciliacao(id, lancamento_a, lancamento_b, tipo, score, status)
Contrato(id, arquivo_id, fornecedor, cnpj, objeto, valor_centavos, indice_reajuste,
         data_base, inicio, fim)
PO(id, ano, arquivo_id, ata_id, aprovada_em)   POItem(po_id, rubrica_id, mes, valor_centavos)
Unidade(id, identificacao, fracao_ideal)
RegraAuditoria(id, codigo, versao, parametros, vigencia)
ExecucaoAuditoria(id, competencia, versao_regras, iniciada_em, concluida_em)
Achado(id, execucao_id, regra_id, severidade, descricao, evidencias[], estado, comentario)
EventoAuditoria(id, usuario_id, acao, entidade, antes, depois, em)
```

---

## 5. Fluxos principais

**Implantação de um condomínio** (Admin): cadastra o condomínio → envia convenção, RI e PO → ingestão + RAG extraem frações, fundos, rubricas e regras → Admin confirma parâmetro a parâmetro, vendo o trecho de origem → condomínio ativo → carga histórica opcional.

**Upload mensal** (Gestor): envia balancete + extrato + comprovantes de setembro → ingestão extrai e valida → RAG indexa → motor de auditoria roda setembro → dashboard e achados atualizados → indicador "último arquivo" muda.

**Consulta** (Usuário): abre o Início → KPIs vêm do banco (sem reprocessar) → clica num achado → vê evidência com o PDF aberto na página.

**Pergunta** (qualquer perfil): "o reajuste do elevador está certo?" → RAG recupera cláusula do contrato + ferramenta consulta pagamentos → resposta com cálculo determinístico e citações.

**Integração** (desenvolvimento): mudança no contrato OpenAPI → agente MCP regenera tipos, roda testes de contrato e ponta a ponta → só então a mudança é aceita.

---

## 6. Qualidade e testes

- **Golden files**: para cada categoria, documentos reais anonimizados com o resultado esperado (lançamentos e achados). Toda mudança em parser/regra roda contra eles.
- Testes de unidade (regras, conciliação), de contrato (OpenAPI), ponta a ponta (MCP dirigindo o sistema).
- Avaliação do RAG: conjunto de perguntas com respostas e fontes esperadas.

---

## 7. Governança dos agentes

```
         Usuário (decisão final)
               │
   ┌───────────┴───────────┐
   │ Requisitos ⇄ Arquiteto │   fase de definição: nada é desenvolvido sem
   └───────────┬───────────┘   requisito aprovado + ADR aprovada pelo usuário
               │ especificações / ADRs
   ┌─────┬─────┼──────┬──────────┐
 Ingestão RAG Backend Frontend    MCP (integração: acionado a cada mudança de contrato)
```

Regras:
1. Requisitos e Arquiteto trabalham juntos e entregam **requisito + ADR** antes de qualquer desenvolvimento.
2. Toda escolha de tecnologia vira uma ADR com opções, prós/contras e recomendação; **status "proposta" até o usuário aprovar**.
3. Agentes de desenvolvimento só alteram seu módulo. Mudança em `contracts/` ou `backend/domain` exige o agente MCP para validar a integração.
4. Definições dos agentes em `agentes/` (deste diretório), prontas para virar `.claude/agents/*.md` no repositório.

---

## 8. Caminho para a nuvem (fase 3)

Os mesmos contêineres do `docker-compose` vão para um provedor (T9). Trocas previstas, todas por parâmetro: armazenamento local → objeto (S3/GCS/Blob); banco local → PostgreSQL gerenciado; Keycloak em contêiner gerenciado ou serviço; modo de IA API_KEY por condomínio; AI Gateway com mascaramento ativo.
