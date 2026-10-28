# Proposta de Arquitetura — Monorepo

Versão 0.4 · 04/10/2026 · Status: **tecnologias do MVP decididas pelo usuário** (ver `tecnologias.md` e `adr/`); serviços separados pela ADR 0002; assistente e módulos pela ADR 0003; **previsto × realizado pela ADR 0004 (aprovada em 04/10/2026)**; T9 nuvem fica para depois

Stack: **Java 25 + Spring Boot + Spring AI** (serviços backend, rag e mcp), **RabbitMQ** (backend ↔ rag), **gRPC** (mcp → backend), **Gradle** multi-projeto (Kotlin DSL + catálogo de versões), **PostgreSQL + pgvector**, **Keycloak** (token Bearer), **React + TypeScript + Vite** (pnpm), **leitor de documentos em Python** isolado, relatórios com **Thymeleaf + OpenHTMLtoPDF + Apache POI**, processamento pela fila. Tudo sobe com um `docker compose up`.

---

## 1. Visão geral

Cada parte é um **serviço próprio**, no seu contêiner, e só conversa com as outras por contrato (ADR 0002).

```
                    ┌──────────────────────┐
                    │  FRONTEND (React)    │
                    └──────────┬───────────┘
                               │ REST/JSON (contracts/openapi.yaml)
┌──────────────┐  gRPC   ┌─────▼────────────────┐        ┌──────────────────────┐
│     MCP      │────────▶│       BACKEND        │◀──────▶│ PostgreSQL           │
│ ferramentas  │ (proto) │ API · contábil ·     │        │ schema por serviço   │
│ p/ IA externa│         │ auditoria · relatórios│        │ (backend, rag)       │
└──────▲───────┘         └──┬────────────▲──────┘        └──────────────────────┘
       │ MCP (HTTP)         │ fila       │ fila
   Claude do usuário        │ RabbitMQ   │ RabbitMQ
                         ┌──▼────────────┴──────┐  HTTP   ┌──────────────────────┐
                         │         RAG          │────────▶│ READER (Python)      │
                         │ leitura · conferência│         │ arquivo → JSON       │
                         │ · enriquecimento ·   │         └──────────────────────┘
                         │ embeddings · busca   │
                         └──────────┬───────────┘
                                    ▼
                     ┌──────────────────────────────┐
                     │ ARMAZENAMENTO DOS ORIGINAIS  │  pasta local (MVP) ou S3 (nuvem)
                     └──────────────────────────────┘
```

Princípio central: **IA para entender documentos; código determinístico para calcular e auditar**. Todo número exibido vem do banco, calculado por regra versionada.

**Multi-condomínio (D12)**: toda tabela de negócio leva `condominio_id`. O isolamento é aplicado no backend em toda consulta (e, com PostgreSQL, também por *row-level security*). Índices do RAG e arquivos também são separados por condomínio. Regras ficam em duas camadas: **legais** (código, iguais para todos) e **do condomínio** (parâmetros extraídos da convenção, do RI e da PO e confirmados pelo Admin, com vigência).

---

## 2. Estrutura do monorepo

```
condominio-auditoria/
├── settings.gradle.kts         # projetos Gradle: um por serviço Java + libs técnicas
├── build.gradle.kts
├── gradle/libs.versions.toml   # catálogo único de versões
├── api/                        # serviço (ex-backend, ADR 0006): API REST, contábil, auditoria, orçamento, relatórios, registro dos arquivos
├── rag/                        # serviço: leitura, interpretação de layouts, conferência, enriquecimento, embeddings, busca
├── mcp/                        # serviço: ferramentas MCP; chama o backend por gRPC com o token do usuário
├── reader/                     # leitor de documentos em Python: arquivo → JSON (sem estado)
├── frontend/                   # React + TypeScript + Vite (pnpm); tipos gerados do OpenAPI
├── libs/
│   ├── storage/                # interface Storage: pasta local (MVP) ou S3 (nuvem), por parâmetro
│   ├── grpc-contract/          # código gerado de contracts/grpc
│   └── logging/                # requestId e contexto dos logs (MDC, interceptores gRPC, fila), ADR 0008
├── contracts/
│   ├── openapi.yaml            # frontend ↔ backend
│   ├── reader/v2/              # rag ↔ reader (JSON Schema; leitor/v1 fica como histórico)
│   ├── mensagens/v3/           # api ↔ rag pela fila (JSON Schema + exemplos testados dos dois lados; v1 e v2 são histórico)
│   ├── grpc/query/v2/       # mcp → backend (.proto)
│   └── correlacao.md           # requestId em HTTP, gRPC e fila (ADR 0008; nenhum corpo muda)
├── infra/
│   ├── docker-compose.yml      # PostgreSQL, RabbitMQ, Keycloak, reader, api, rag, mcp, frontend
│   ├── java.Dockerfile         # imagem dos serviços Java (o serviço vem por argumento)
│   └── keycloak/               # realm exportado: clientes, perfis, tempos de sessão
├── dados/                      # arquivos originais enviados (fora do git): dados/<condominio>/<categoria>/<ano>/
├── data/golden/                # documentos de referência + resultado esperado
├── docs/adr/                   # uma ADR por decisão
├── .claude/agents/             # agentes especialistas
├── CLAUDE.md
└── README.md
```

Regras: um serviço **nunca importa classe de outro** nem lê o schema de banco de outro. Conversa só por `contracts/`. `libs/` guarda só código técnico, sem regra de negócio. Dentro de cada serviço Java, a organização é por camada, com o assunto dentro da camada, e o código é em inglês (ADR 0006, aprovada em 08/10/2026): `br.com.condominioauditoria.<papel>.{config, controller, dto, mapper, model, repository, service, event, listener, messaging, grpc, report, security, exception, util}`. O backend passa a se chamar `api` (pasta e pacote) na fase 1 da ADR 0006.

### 2.1 Arquivos × banco (decisão do usuário)
- **Originais nunca vão para o banco.** Ficam em `dados/`, imutáveis, lidos pela interface `Storage` (`libs/storage`).
- **O banco guarda só dados processados**: extraídos, normalizados e enriquecidos. Todo registro aponta para caminho do arquivo, página e hash.
- Os trechos e vetores do RAG também são dados processados; se o banco se perder, tudo é reprocessável a partir de `dados/`.

### 2.2 Processamento em segundo plano (fila RabbitMQ)
- Upload: o backend grava o original, cria o registro com status **Pendente** e responde na hora. Depois do commit, publica o pedido de leitura na fila `rag.files-received`.
- O rag lê o original, chama o leitor, interpreta, confere e publica em `api.processing-results`: **INICIADO**, depois **CONCLUIDO** (com os dados) ou **FALHOU** (com o motivo). Só confirma o pedido depois de publicar o resultado.
- O backend grava o resultado em **uma transação** (apaga a extração anterior do arquivo e insere a nova). Falha = rollback e retentativa; depois de 3 vezes, a mensagem vai para a fila `.erro`.
- Cada leitura tem um `processamentoId`; reprocessar gera outro, e resultado com id antigo é descartado.
- Filas duráveis: serviço reiniciado encontra o trabalho esperando. Uma varredura reenvia o que ficou parado há mais de 15 minutos (até 3 tentativas).
- Muitos arquivos: sobe-se mais réplicas do rag; o paralelismo de cada réplica é parâmetro.

### 2.3 Logs e correlação (ADR 0008, aprovada em 28/10/2026)
- Texto no local; JSON na nuvem com o formato estruturado nativo do Spring Boot (`LOGGING_STRUCTURED_FORMAT_CONSOLE=ecs`) e, no reader, `LOG_FORMATO=json`. Nenhuma biblioteca nova.
- Um `requestId` próprio liga tudo o que um pedido causa: cabeçalho `X-Request-Id` (HTTP), metadado `x-request-id` (gRPC) e cabeçalho AMQP `requestId` (fila, sem mudar o corpo). Fica no MDC com `user` (o `sub` do Keycloak), `condominiumId`, `fileId`, `processingId` e `indexingId` (`libs/logging`; contrato em `contracts/correlacao.md`).
- Erros logados uma vez, na borda: 4xx em WARN sem stack, 5xx em ERROR com stack, 503/504 em WARN; o `requestId` volta no corpo do erro e a tela o mostra nos 5xx. Mensagem da fila que desiste: ERROR com os ids, sem o corpo.
- Nunca no log: token, chaves, senha, nome de pessoa, username, nome original do arquivo, conteúdo de documento, valores de lançamentos, pergunta e resposta do assistente, texto livre do usuário. Mensagens em português, chaves em inglês.
- Docker compose: `json-file` com 10 MB × 5 por serviço.

### 2.4 Segurança (Keycloak)
- Keycloak gerencia usuários, senhas, perfis (Usuário, Gestor, Admin) e vínculo com condomínios (claim no token).
- Frontend faz login OIDC (Authorization Code + PKCE). Backend é resource server: valida o JWT e aplica perfil e condomínio em todo endpoint.
- Token de acesso curto, renovado com o uso; sessão cai por inatividade (configurável no realm, ex.: 30 min).
- O serviço mcp exige token e repassa o **token do próprio usuário** ao backend em cada chamada gRPC; o backend aplica a mesma regra de perfil e condomínio da API REST. O Claude só vê o que o usuário pode ver.

---

## 3. Módulos

### 3.1 Ingestão (serviço `rag` + `reader`)
Responsável por transformar arquivo em dado estruturado.

Pipeline por arquivo:
1. **Recebimento**: salva original imutável, calcula hash, registra categoria/competência/autor.
2. **Detecção**: tipo real do arquivo; PDF digital vs. escaneado.
3. **Extração**: texto + tabelas (PDF/XLSX/DOCX); OCR quando necessário.
4. **Classificação** (IA): confirma categoria, competência, conta bancária, fornecedor.
5. **Normalização**: converte para o modelo `Lançamento` (data, descrição, favorecido, CNPJ/CPF, rubrica, valor em centavos, origem = arquivo/página/linha). Parsers específicos por *layout* conhecido (ex.: balancete da administradora X) têm prioridade; IA com saída estruturada é o *fallback*.
6. **Validação**: somas batem com totais do próprio documento; datas dentro da competência; se falhar, status "precisa revisão" com motivo.
7. **Publicação**: o rag devolve os dados pela fila; o backend grava e dispara a reauditoria do mês. A indexação para busca acontece no próprio rag.

Idempotente: reenviar o mesmo arquivo não duplica nada.

**PO aprovada (ADR 0004):** o `rag` reconhece a PO da administradora do piloto pelo título e lê as linhas pela posição das palavras do leitor v1, sem IA e sem mudar o leitor (`rag.parser.budget`, `rag.model.budget`). Confere subtotais, total, previsto do mês e código repetido, e devolve tudo no `ResultadoProcessamento` **v2** (`previsaoOrcamentaria`). Desde a ADR 0009 (mensagem **v4**), a planilha de apoio da PO (`.xlsx`, contrato do leitor v2 sem mudança) é reconhecida pelo cabeçalho e devolvida no bloco `budgetLineOrigins`: o método de cada linha é deduzido pelos números (média × (1 + índice) até R$ 0,01, igual à média, valor digitado ou sem média). No fluxo de caixa, o enriquecimento passa a marcar `recebimentoCota` nos créditos "RECIBOS ACUMULADOS", usados na arrecadação dos fundos de reserva e de obras.

### 3.2 RAG (serviço `rag`, Spring AI)
- Indexa **texto** dos documentos (contratos, **atas de assembleia**, convenção, RI, POs). Atas também geram registros estruturados de `Deliberacao` (assunto, valor, fundo, prazo) usados para justificar achados em *chunks* com metadados (categoria, competência, página).
- Busca **híbrida** (palavra-chave + vetorial) com filtro por categoria/período/permissão.
- Geração com **citações obrigatórias**. Perguntas numéricas são roteadas para **ferramentas** que consultam o banco (*text-to-query* controlado ou endpoints prontos), nunca respondidas só pelo texto.
- Também alimenta o backend: extrai cláusulas de contratos (valor, índice de reajuste, data-base, vigência) e regras da convenção (rateio, multas, fundo de reserva) para parâmetros que o admin confirma.
- **Assistente (módulo contratável, RF-04 e RF-10). ADR 0003, aprovada pelo usuário em 03/10/2026:** índice no schema `rag` (trechos cortados por página, aba e linha, ou seção e parágrafo; vetores `vector(1024)` com pgvector; busca por palavra em português sem acento); busca híbrida com fusão de posições e filtro de condomínio antes da busca; embeddings locais (`bge-m3` no contêiner Ollama) em todos os modos; indexação pela fila `rag.indexing`, só para condomínio com o módulo ligado; chat recebido do backend por gRPC (`contracts/grpc/assistant/v2`: `Search`, `Ask` em fluxo e `ListProviders`), com os números buscados no backend pelo `Consulta` com o token do usuário.

### 3.3 Backend (serviço `backend`)
- **Auth e perfis** (Usuário/Gestor/Admin) aplicados em todos os endpoints.
- **Domínio contábil**: plano de contas, rubricas, mapeamento balancete↔PO, unidades e frações.
- **Motor de auditoria**: executa as regras de auditoria por competência; gera `Achado` com evidência; versão das regras registrada.
- **Conciliação**: algoritmo de casamento balancete × extrato × comprovante (valor exato, janela de datas, similaridade de favorecido; casamentos 1:N e N:1).
- **Orçamento e previsão**: previsto × realizado; projeção por rubrica (estatística + contratos + índices).
  - **ADR 0004:** pacote `backend.orcamento`. Grava a PO lida (só para arquivo da categoria PO) e o Admin a confirma, com exercício, ata, código efetivo das linhas repetidas e ligação das linhas 1.9 aos fundos. De-para por versão da PO (conta do fluxo → linha da PO, ajuste, a realocar ou transferência), com sugestão por comparação de texto sem IA, cópia da versão anterior e planilha, sempre "sugerido" até o Admin confirmar, e trilha só de inserção. Realocação mínima (RF-03.1.7) com chave estável do lançamento. **Os números não são gravados:** uma função pura (`CalculoPrevistoRealizado`) calcula na consulta e alimenta a tela, o PDF e o Excel. Os achados (regra dos 20%, conta sem linha da PO, reserva acima do teto) são recalculados depois do commit de cada mudança e gravados por chave única.
  - **ADR 0005 (aprovada em 07/10/2026):** análise de vários exercícios no mesmo pacote. Catálogo de **rubricas** do condomínio (cada linha da PO ligada a uma rubrica, sugerida pela conta da PO e pelo grupo, confirmada pelo Admin, com trilha só de inserção), que faz a correspondência entre exercícios. Prorrogação como campo da PO. "PO anterior pela coluna impressa" montada na consulta a partir de `orcado_anterior`. Comparação e indicadores calculados na consulta por funções puras que reusam o `CalculoPrevistoRealizado`.
  - **ADR 0009 (aprovada em 27/10/2026):** a seção "Análise da PO" começa em "Entradas e saídas" (`MonthlyCashFlow`, função pura na consulta: entradas, saídas por linha da PO e por bloco, transferências, saldo do mês e acumulado, conferidos com o extrato; meses antes da PO com a marca `BEFORE_BUDGET`). Antes de distribuir gasto por linha, o Admin confere as linhas da PO (`budget_line.review_status`, trilha só de inserção, avisos em `BudgetLineReviewCheck`); enquanto falta, as telas trazem o passo pendente na resposta, sem erro 4xx. Tipos da linha revistos (`RECURRING`, `OCCASIONAL`, `SEASONAL`, `ANNUAL`, `PROVISION`, `SEPARATE_APPORTIONMENT`), origem de cada linha (`budget_line_origin`, lida pelo `rag` da planilha de apoio e enviada na mensagem v4) e ponte do saldo v5 (`LINE_SAVINGS`, que inclui as linhas eventuais e anuais desde a resposta da Q52 em 28/10/2026, `PROVISIONS_RESERVED`, `INCOME_ABOVE_PLANNED_EXPENSE`, ...). Ação "corrigir o fim do exercício", com trilha.
- **Relatórios**: PDF/Excel sob demanda, gerados no backend com Thymeleaf + OpenHTMLtoPDF e Apache POI a partir do mesmo objeto que a tela recebe (ADR 0004: sem gráfico no PDF e no Excel; barras de execução em CSS).
- **Trilha de auditoria** de todas as mudanças.
- **Registro dos arquivos** e pedidos de leitura ao rag pela fila.

### 3.4 Frontend (`frontend/`, React + TypeScript)
Telas: Login · Início (KPIs + gráficos) · Arquivos (por categoria, mais recente primeiro, upload para Gestor/Admin) · Achados · Orçamento/Previsão · Assistente · Administração. Componente global "último arquivo" fixo em um canto.

ADR 0004: telas "Previsto × realizado" (todos os perfis), "De-para" e confirmação da PO (edição só do Admin). Gráficos com o Recharts, que já está no projeto. O cartão da tela inicial usa o acumulado do exercício.
ADR 0005 (aprovada em 07/10/2026): menu "Análise da PO" com filtro de exercício, telas "Comparar exercícios" e "Indicadores" (Recharts, sem cálculo no frontend) e tela de rubricas para o Admin.

### 3.5 MCP (serviço `mcp`, Spring AI)
- Expõe o sistema como **ferramentas MCP** (transporte HTTP sem sessão). Já existem: `list_condominiums`, `fund_summary`, `list_files`, `file_checks`, `find_entries`. Depois: `listar_achados`, `previsto_realizado` (requisito próprio, RF-08.1; fora da ADR 0004), `search_documents` (RAG), `gerar_relatorio`.
- Não tem banco nem regra: cada ferramenta é uma chamada **gRPC** ao backend (`contracts/grpc/query/v2`), com o token do usuário. Listas grandes chegam em fluxo (stream).
- Serve para: uso do sistema pelo Claude Desktop/Code; e testes de integração ponta a ponta conduzidos pelo agente MCP.
- **Papel de integração**: o agente MCP é acionado sempre que um serviço muda um contrato (OpenAPI, fila, gRPC, ferramenta), para verificar que todos os consumidores continuam funcionando.

### 3.6 AI Gateway (no serviço `rag`, onde ficam as chamadas a modelos)
Camada única para modelos de IA. **Modo por condomínio (RF-09)**: MCP_EXTERNO (piloto, o Claude do usuário via MCP), API_KEY (chave própria do condomínio, criptografada) ou DESLIGADO. Troca de provedor/modelo por configuração, cache de respostas por hash do insumo, registro de custo/tokens, *prompts* versionados, saídas estruturadas validadas por esquema. Ponto onde entra o **mascaramento LGPD** na fase de nuvem.

**ADR 0003 (aprovada pelo usuário em 03/10/2026):** modo de IA também **por módulo e por função** (RF-09.6: respostas e embeddings do Assistente; sem configuração própria, herda o modo geral), com `LOCAL` previsto. O **catálogo de provedores** fica na configuração do `rag` e o backend o lê por gRPC (`ListProviders`). O `rag` não guarda configuração: cada pedido do backend traz modo, provedor, modelo e a chave já resolvidos; a chave vem cifrada com a chave pública do `rag` e só ele a decifra. O uso (tokens, buscas, chamadas MCP, páginas indexadas) volta ao backend em cada resposta e é gravado lá (RF-09.7).

### 3.7 Módulos contratáveis por condomínio (RF-10; ADR 0003)
- O **backend é o dono**: catálogo de módulos (configuração versionada), estado por condomínio, trilha de ativação só de inserção (períodos ativos calculados dela), configuração de IA e registro de uso. Hoje o catálogo tem só o `ASSISTENTE`, desligado por padrão em condomínio novo.
- Nenhum outro serviço consulta o estado: o frontend lê `GET /api/condominiums/{condominiumId}/context`; o `mcp` recebe a recusa do backend; o `rag` só age quando recebe um pedido (indexar, buscar, perguntar). Ligar ou desligar vale no pedido seguinte, sem reinício.
- Desligar não apaga nada: o backend para de pedir indexação e recusa buscas; o índice fica guardado. Religar reindexa só o que é novo ou mudou.

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
   -- ADR 0004 substitui PO/POItem e MapeamentoRubrica por:
PrevisaoOrcamentaria(id, arquivo_id, sha256, versao, estado[lida|lida_com_divergencia|confirmada|substituida],
           exercicio_inicio, exercicio_fim, ata_arquivo_id, sem_ata, total_impresso, previsto_mes, confirmada_por, confirmada_em)
LinhaPO(id, po_id, ordem, pagina, tipo[total|grupo|linha], codigo_impresso, codigo_efetivo, conta, marca,
           descricao, orcado_anterior, orcado, percentual_texto, observacoes, arquivo_id, sha256)
POFundo(po_id, linha_po_id, fundo_id)              -- linhas 1.9 ligadas pelo Admin; fundo Condomínio = fundo ordinário
DeParaConta(id, po_id, conta_fluxo_codigo, conta_fluxo_nome, destino[linha_po|ajuste|a_realocar|transferencia],
           linha_po_id, estado[sugerido|confirmado|recusado], origem[versao_anterior|nome|planilha], motivo)
EventoDePara(id, depara_id, antes, depois, usuario, em)   -- só inserção; migra para EventoAuditoria (RF-07.4)
Realocacao(id, impressao_lancamento, linha_po_id, por, em, desfeita_em)   -- impressão = arquivo, página, ordem, data, conta, documento, valor
   -- o resultado do previsto × realizado não é tabela: é calculado na consulta
   -- ADR 0009 (V21): a conferência das linhas fica em budget_line (review_status[PENDING|REVIEWED], reviewed_by,
   -- reviewed_at, group_account, description_prevails) com a trilha budget_line_review_event (só inserção); o tipo da
   -- linha passa a RECURRING|OCCASIONAL|SEASONAL|ANNUAL|PROVISION|SEPARATE_APPORTIONMENT
OrigemLinhaPO = budget_line_origin(budget_line_id 1:1, contas_origem, periodo_media, valor_media, metodo,
           indice_nome, indice_valor, indice_previsao, valor_final, motivo, fonte[SHEET|ADMIN], arquivo_id, aba, linha, sha256)
   -- só explica: o previsto é sempre o valor da PO (O4). Entradas e saídas (MonthlyCashFlow) e a ponte v4 são calculadas na consulta
Unidade(id, identificacao, fracao_ideal)
RegraAuditoria(id, codigo, versao, parametros, vigencia)
ExecucaoAuditoria(id, competencia, versao_regras, iniciada_em, concluida_em)
Achado(id, execucao_id, regra_id, severidade, descricao, evidencias[], estado, comentario)
EventoAuditoria(id, usuario_id, acao, entidade, antes, depois, em)
```

---

## 5. Fluxos principais

**Implantação de um condomínio** (Admin): cadastra o condomínio → envia convenção, RI e PO → ingestão + RAG extraem frações, fundos, rubricas e regras → Admin confirma parâmetro a parâmetro, vendo o trecho de origem → condomínio ativo → carga histórica opcional.

**PO e de-para** (Admin, ADR 0004): envia a PO na categoria PO → `rag` lê e confere → backend grava "lida" ou "lida com divergência" → Admin confirma exercício, ata, códigos repetidos e fundos 1.9 → gera sugestões do de-para (texto, versão anterior ou planilha) e confirma → previsto × realizado disponível, calculado na consulta.

**Upload mensal** (Gestor): envia balancete + extrato + comprovantes de setembro → ingestão extrai e valida → RAG indexa → motor de auditoria roda setembro → dashboard e achados atualizados → indicador "último arquivo" muda.

**Consulta** (Usuário): abre o Início → KPIs vêm do banco (sem reprocessar) → clica num achado → vê evidência com o PDF aberto na página.

**Pergunta** (qualquer perfil): "o reajuste do elevador está certo?" → RAG recupera cláusula do contrato + ferramenta consulta pagamentos → resposta com cálculo determinístico e citações.

**Integração** (desenvolvimento): mudança no contrato OpenAPI → agente MCP regenera tipos, roda testes de contrato e ponta a ponta → só então a mudança é aceita.

---

## 6. Qualidade e testes

- **Golden files**: para cada categoria, documentos reais anonimizados com o resultado esperado (lançamentos e achados). Toda mudança em parser/regra roda contra eles.
- Testes de unidade (regras, conciliação), de contrato (OpenAPI), ponta a ponta (MCP dirigindo o sistema).
- Testes dos logs (ADR 0008): o tratamento central (4xx WARN, 5xx ERROR), a propagação do `requestId` (HTTP, gRPC e fila) e a ausência do nome do arquivo no log.
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
3. Agentes de desenvolvimento só alteram seu módulo. Mudança em `contracts/` exige o agente MCP para validar a integração.
4. Definições dos agentes em `agentes/` (deste diretório), prontas para virar `.claude/agents/*.md` no repositório.

---

## 8. Caminho para a nuvem (fase 3)

Os mesmos contêineres do `docker-compose` vão para um provedor (T9). Logs em JSON por variável (ADR 0008); rastreamento com OpenTelemetry, se o usuário quiser, entra com ADR própria. Trocas previstas, todas por parâmetro: armazenamento local → objeto (S3/GCS/Blob); banco local → PostgreSQL gerenciado; Keycloak em contêiner gerenciado ou serviço; modo de IA API_KEY por condomínio; AI Gateway com mascaramento ativo.
