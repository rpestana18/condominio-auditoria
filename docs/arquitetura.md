# Proposta de Arquitetura — Monorepo

Versão 0.3 · 03/10/2026 · Status: **tecnologias do MVP decididas pelo usuário** (ver `tecnologias.md` e `adr/`); serviços separados pela ADR 0002; T9 nuvem fica para depois

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
                         │         RAG          │────────▶│ LEITOR (Python)      │
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
├── backend/                    # serviço: API REST, contábil, auditoria, orçamento, relatórios, registro dos arquivos
├── rag/                        # serviço: leitura, interpretação de layouts, conferência, enriquecimento, embeddings, busca
├── mcp/                        # serviço: ferramentas MCP; chama o backend por gRPC com o token do usuário
├── leitor/                     # serviço Python: arquivo → JSON (sem estado)
├── frontend/                   # React + TypeScript + Vite (pnpm); tipos gerados do OpenAPI
├── libs/
│   ├── armazenamento/          # interface Armazenamento: pasta local (MVP) ou S3 (nuvem), por parâmetro
│   └── contrato-grpc/          # código gerado de contracts/grpc
├── contracts/
│   ├── openapi.yaml            # frontend ↔ backend
│   ├── leitor/v1/              # rag ↔ leitor (JSON Schema)
│   ├── mensagens/v1/           # backend ↔ rag pela fila (JSON Schema + exemplos testados dos dois lados)
│   └── grpc/consulta/v1/       # mcp → backend (.proto)
├── infra/
│   ├── docker-compose.yml      # PostgreSQL, RabbitMQ, Keycloak, leitor, backend, rag, mcp, frontend
│   ├── java.Dockerfile         # imagem dos serviços Java (o serviço vem por argumento)
│   └── keycloak/               # realm exportado: clientes, perfis, tempos de sessão
├── dados/                      # arquivos originais enviados (fora do git): dados/<condominio>/<categoria>/<ano>/
├── data/golden/                # documentos de referência + resultado esperado
├── docs/adr/                   # uma ADR por decisão
├── .claude/agents/             # agentes especialistas
├── CLAUDE.md
└── README.md
```

Regras: um serviço **nunca importa classe de outro** nem lê o schema de banco de outro. Conversa só por `contracts/`. `libs/` guarda só código técnico, sem regra de negócio. Dentro de cada serviço, a organização é por pacote (ex.: `rag.dominio`, `rag.leitura`, `rag.processamento`).

### 2.1 Arquivos × banco (decisão do usuário)
- **Originais nunca vão para o banco.** Ficam em `dados/`, imutáveis, lidos pela interface `Armazenamento`.
- **O banco guarda só dados processados**: extraídos, normalizados e enriquecidos. Todo registro aponta para caminho do arquivo, página e hash.
- Os trechos e vetores do RAG também são dados processados; se o banco se perder, tudo é reprocessável a partir de `dados/`.

### 2.2 Processamento em segundo plano (fila RabbitMQ)
- Upload: o backend grava o original, cria o registro com status **Pendente** e responde na hora. Depois do commit, publica o pedido de leitura na fila `rag.arquivos-recebidos`.
- O rag lê o original, chama o leitor, interpreta, confere e publica em `backend.resultados`: **INICIADO**, depois **CONCLUIDO** (com os dados) ou **FALHOU** (com o motivo). Só confirma o pedido depois de publicar o resultado.
- O backend grava o resultado em **uma transação** (apaga a extração anterior do arquivo e insere a nova). Falha = rollback e retentativa; depois de 3 vezes, a mensagem vai para a fila `.erro`.
- Cada leitura tem um `processamentoId`; reprocessar gera outro, e resultado com id antigo é descartado.
- Filas duráveis: serviço reiniciado encontra o trabalho esperando. Uma varredura reenvia o que ficou parado há mais de 15 minutos (até 3 tentativas).
- Muitos arquivos: sobe-se mais réplicas do rag; o paralelismo de cada réplica é parâmetro.

### 2.3 Segurança (Keycloak)
- Keycloak gerencia usuários, senhas, perfis (Usuário, Gestor, Admin) e vínculo com condomínios (claim no token).
- Frontend faz login OIDC (Authorization Code + PKCE). Backend é resource server: valida o JWT e aplica perfil e condomínio em todo endpoint.
- Token de acesso curto, renovado com o uso; sessão cai por inatividade (configurável no realm, ex.: 30 min).
- O serviço mcp exige token e repassa o **token do próprio usuário** ao backend em cada chamada gRPC; o backend aplica a mesma regra de perfil e condomínio da API REST. O Claude só vê o que o usuário pode ver.

---

## 3. Módulos

### 3.1 Ingestão (serviço `rag` + `leitor`)
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

### 3.2 RAG (serviço `rag`, Spring AI)
- Indexa **texto** dos documentos (contratos, **atas de assembleia**, convenção, RI, POs). Atas também geram registros estruturados de `Deliberacao` (assunto, valor, fundo, prazo) usados para justificar achados em *chunks* com metadados (categoria, competência, página).
- Busca **híbrida** (palavra-chave + vetorial) com filtro por categoria/período/permissão.
- Geração com **citações obrigatórias**. Perguntas numéricas são roteadas para **ferramentas** que consultam o banco (*text-to-query* controlado ou endpoints prontos), nunca respondidas só pelo texto.
- Também alimenta o backend: extrai cláusulas de contratos (valor, índice de reajuste, data-base, vigência) e regras da convenção (rateio, multas, fundo de reserva) para parâmetros que o admin confirma.
- **Assistente (módulo contratável, RF-04 e RF-10). ADR 0003, aprovada pelo usuário em 03/10/2026:** índice no schema `rag` (trechos cortados por página, aba e linha, ou seção e parágrafo; vetores `vector(1024)` com pgvector; busca por palavra em português sem acento); busca híbrida com fusão de posições e filtro de condomínio antes da busca; embeddings locais (`bge-m3` no contêiner Ollama) em todos os modos; indexação pela fila `rag.indexacao`, só para condomínio com o módulo ligado; chat recebido do backend por gRPC (`contracts/grpc/assistente/v1`), com os números buscados no backend pelo `Consulta` com o token do usuário.

### 3.3 Backend (serviço `backend`)
- **Auth e perfis** (Usuário/Gestor/Admin) aplicados em todos os endpoints.
- **Domínio contábil**: plano de contas, rubricas, mapeamento balancete↔PO, unidades e frações.
- **Motor de auditoria**: executa as regras de auditoria por competência; gera `Achado` com evidência; versão das regras registrada.
- **Conciliação**: algoritmo de casamento balancete × extrato × comprovante (valor exato, janela de datas, similaridade de favorecido; casamentos 1:N e N:1).
- **Orçamento e previsão**: previsto × realizado; projeção por rubrica (estatística + contratos + índices).
- **Relatórios**: PDF/Excel sob demanda.
- **Trilha de auditoria** de todas as mudanças.
- **Registro dos arquivos** e pedidos de leitura ao rag pela fila.

### 3.4 Frontend (`frontend/`, React + TypeScript)
Telas: Login · Início (KPIs + gráficos) · Arquivos (por categoria, mais recente primeiro, upload para Gestor/Admin) · Achados · Orçamento/Previsão · Assistente · Administração. Componente global "último arquivo" fixo em um canto.

### 3.5 MCP (serviço `mcp`, Spring AI)
- Expõe o sistema como **ferramentas MCP** (transporte HTTP sem sessão). Já existem: `listar_condominios`, `resumo_fundos`, `listar_arquivos`, `conferencias_do_arquivo`, `buscar_lancamentos`. Depois: `listar_achados`, `previsto_realizado`, `buscar_documentos` (RAG), `gerar_relatorio`.
- Não tem banco nem regra: cada ferramenta é uma chamada **gRPC** ao backend (`contracts/grpc/consulta/v1`), com o token do usuário. Listas grandes chegam em fluxo (stream).
- Serve para: uso do sistema pelo Claude Desktop/Code; e testes de integração ponta a ponta conduzidos pelo agente MCP.
- **Papel de integração**: o agente MCP é acionado sempre que um serviço muda um contrato (OpenAPI, fila, gRPC, ferramenta), para verificar que todos os consumidores continuam funcionando.

### 3.6 AI Gateway (no serviço `rag`, onde ficam as chamadas a modelos)
Camada única para modelos de IA. **Modo por condomínio (RF-09)**: MCP_EXTERNO (piloto, o Claude do usuário via MCP), API_KEY (chave própria do condomínio, criptografada) ou DESLIGADO. Troca de provedor/modelo por configuração, cache de respostas por hash do insumo, registro de custo/tokens, *prompts* versionados, saídas estruturadas validadas por esquema. Ponto onde entra o **mascaramento LGPD** na fase de nuvem.

**ADR 0003 (aprovada pelo usuário em 03/10/2026):** modo de IA também **por módulo e por função** (RF-09.6: respostas e embeddings do Assistente; sem configuração própria, herda o modo geral), com `LOCAL` previsto. O **catálogo de provedores** fica na configuração do `rag` e o backend o lê por gRPC (`ListarProvedores`). O `rag` não guarda configuração: cada pedido do backend traz modo, provedor, modelo e a chave já resolvidos; a chave vem cifrada com a chave pública do `rag` e só ele a decifra. O uso (tokens, buscas, chamadas MCP, páginas indexadas) volta ao backend em cada resposta e é gravado lá (RF-09.7).

### 3.7 Módulos contratáveis por condomínio (RF-10; ADR 0003)
- O **backend é o dono**: catálogo de módulos (configuração versionada), estado por condomínio, trilha de ativação só de inserção (períodos ativos calculados dela), configuração de IA e registro de uso. Hoje o catálogo tem só o `ASSISTENTE`, desligado por padrão em condomínio novo.
- Nenhum outro serviço consulta o estado: o frontend lê `GET /condominios/{id}/contexto`; o `mcp` recebe a recusa do backend; o `rag` só age quando recebe um pedido (indexar, buscar, perguntar). Ligar ou desligar vale no pedido seguinte, sem reinício.
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
3. Agentes de desenvolvimento só alteram seu módulo. Mudança em `contracts/` exige o agente MCP para validar a integração.
4. Definições dos agentes em `agentes/` (deste diretório), prontas para virar `.claude/agents/*.md` no repositório.

---

## 8. Caminho para a nuvem (fase 3)

Os mesmos contêineres do `docker-compose` vão para um provedor (T9). Trocas previstas, todas por parâmetro: armazenamento local → objeto (S3/GCS/Blob); banco local → PostgreSQL gerenciado; Keycloak em contêiner gerenciado ou serviço; modo de IA API_KEY por condomínio; AI Gateway com mascaramento ativo.
