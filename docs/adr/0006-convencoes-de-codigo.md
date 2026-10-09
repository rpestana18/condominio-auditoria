# ADR 0006: Convenções de código dos serviços Java (idioma, camadas e pacotes)

- **Status:** aprovada pelo usuário em 08/10/2026 (perguntas 1, 2, 3, 5 e 6 como recomendado, com os ajustes da pergunta 3; na pergunta 4 o usuário escolheu raiz única com nome por papel). O usuário escolheu `api` para o backend no mesmo dia
- **Vale para:** `backend`, `rag` e `mcp` (e os testes de cada um). A fase 3 estende o idioma ao `leitor` (Python) e ao código do `frontend`.
- **Não muda:** a ADR 0002 (serviços separados, um schema por serviço, conversa só por `contracts/`), o stack da ADR 0001 nem nenhuma regra de negócio. Nenhuma biblioteca nova entra.
- **Muda:** a regra do `CLAUDE.md` "Nomes no código também em português", a frase de `docs/arquitetura.md:76` sobre a organização por pacote e o nome da pasta e do pacote do backend.

## Contexto

O usuário revisou o backend em 08/10/2026 e apontou três problemas: o código está em português, não há separação em camadas (model, service, repository, dto, controller, util) e o nome de pacote `backend` aparece repetido (pasta `backend/` e pacote `br.com.condominioauditoria.backend`).

Estado real do código (08/10/2026, `main` em `93afac4`):

- **Organização por funcionalidade, não por camada.** Cada pasta reúne entidade, repositório, serviço, controller e DTO do mesmo assunto. O backend tem 193 classes em 15 pacotes; `backend.orcamento` sozinho tem 84 classes, misturando entidades (`Rubrica`, `LinhaPo`), repositórios (`RubricaRepository`), serviços (`ServicoRubricas`), controllers (`RubricaController`), DTOs (`RubricaDtos`), relatórios (`RelatorioPdf`) e utilitário (`DinheiroBr`). A regra veio de `docs/arquitetura.md:76` ("Dentro de cada serviço, a organização é por pacote") e nunca foi discutida com o usuário numa ADR.
- **Nomes em português** por regra do `CLAUDE.md:3` ("Nomes no código também em português").
- **Controllers acessam repositórios direto**, sem passar por serviço. Exemplos: `ArquivoController.java:45-51` (quatro repositórios), `PrevisaoController.java:28-29` e `:55`, `FundoController.java:29-30`, `FundoOrdinarioController.java:32-33` e `:49`, `UsuarioController.java:16`, `ModuloController.java:51`, `IaController.java:39`, `AssistenteController.java:32`.
- **Entidades não saem na resposta**: os controllers já devolvem records (`ArquivoDtos.java:16`, `ArquivoResumo.de(Arquivo)`), mas os DTOs ficam em classes "sacola" por assunto (`ArquivoDtos`, `PrevisaoDtos`, `RubricaDtos`) com o mapeamento dentro do próprio record, e os controllers carregam entidades para montar a resposta (`ArquivoController.java:91`, `PrevisaoController.java:55`).
- **Por que existe `backend/…/br/com/condominioauditoria/backend`:** `backend/` é o projeto Gradle (`settings.gradle.kts`), com seu jar e seu contêiner. O pacote repete o nome do serviço para que `backend`, `rag` e `mcp` tenham raízes diferentes (`…backend`, `…rag`, `…mcp`), porque os três têm classes com o mesmo nome (`mensagens.Filas`, `mensagens.ContratoMensagens`, `grpc.ServidorGrpc`). É a mesma convenção do Maven multi-módulo (groupId + artifactId), mas não estava escrita em lugar nenhum.
- **27 entidades sem `@Table` nem `@Column`** (só 2 anotações no backend): o nome da tabela e das colunas sai do nome da classe e do campo. Trocar o nome da classe muda a tabela, então a renomeação precisa fixar os nomes atuais nas anotações.
- **Os nomes também estão nos contratos:** campos JSON da API (`contracts/openapi.yaml`, 2.389 linhas), mensagens da fila (`contracts/mensagens/v1` e `v2`), gRPC (`contracts/grpc/`), tabelas e colunas das migrações Flyway (V1 a V15 no backend, V1 e V2 no rag), papéis do Keycloak (`USUARIO`, `GESTOR`, `ADMIN`) e o código do frontend gerado pela API.

São seis decisões. O usuário aprova ou troca cada uma em separado (seção "Perguntas para o usuário").

---

## Decisão 1: idioma

| Opção | Prós | Contras |
|---|---|---|
| A. Manter português no código | Nada muda. Termos do domínio sem tradução | Contra o pedido do usuário. Mistura com Spring/Java (`findByCondominioId`, `getStatus`). Padrão fora do mercado |
| **B. Código em inglês: pacotes, classes, métodos, campos, enums, constantes, testes e comentários. Em português ficam os documentos (`docs/`), os textos da tela, as mensagens mostradas ao usuário, os relatórios, os commits e as descrições de PR** | Atende o pedido. Padrão do mercado. A conversa com o usuário e os documentos continuam em português | Termos brasileiros precisam de um glossário fixo (abaixo) para não ter três traduções do mesmo termo |
| C. Inglês só nos nomes, comentários em português | Comentário de regra contábil fica mais fácil para o usuário ler | Mistura dois idiomas no mesmo arquivo |

**Decidido: B**, com o glossário abaixo como regra. Termo que não tiver tradução fiel entra no glossário antes de entrar no código; ninguém inventa tradução nova.

### Glossário (português → inglês no código)

| Termo | No código | Observação |
|---|---|---|
| condomínio | `Condominium` | |
| unidade, fração ideal | `Unit`, `idealFraction` | |
| arquivo (enviado) | `SourceFile` | `File` colide com `java.io.File` |
| categoria do arquivo | `FileCategory` | |
| lançamento | `LedgerEntry` | |
| fundo, saldo do fundo | `Fund`, `FundBalance` | |
| fundo de reserva | `ReserveFund` | |
| conferência (de totais) | `TotalsCheck` | |
| fluxo de caixa | `CashFlow` | |
| balancete | `TrialBalance` | |
| extrato | `BankStatement` | |
| previsão orçamentária (PO) | `Budget` | "PO" no texto da tela continua |
| linha da PO | `BudgetLine` | |
| rubrica | `BudgetItem` | catálogo do condomínio (ADR 0005) |
| exercício | `FiscalYear` | |
| prorrogação | `BudgetExtension` | |
| de-para | `AccountMapping` | |
| previsto × realizado | `BudgetVsActual` | |
| realocação | `Reallocation` | |
| achado | `Finding` | |
| evidência | `Evidence` | |
| severidade | `Severity` | |
| regra (de auditoria), parâmetro | `AuditRule`, `RuleParameter` | |
| ata de assembleia | `MeetingMinutes` | |
| convenção, regimento interno | `Bylaws`, `InternalRules` | |
| síndico | `BuildingManager` | |
| cota condominial | `CondoFee` | |
| painel | `Dashboard` | |
| módulo contratável | `Feature` | `Module` colide com `java.lang.Module` |
| assistente | `Assistant` | |
| IA | `Ai` | |
| perfis Usuário, Gestor, Admin | `USER`, `MANAGER`, `ADMIN` | o texto da tela continua "Usuário", "Gestor" e "Admin" |
| dinheiro | `Money` | |
| fundo ordinário | `OperatingFund` | |
| posição financeira (de um fundo) | `FundPosition` | |
| saldo anterior, saldo atual | `openingBalance`, `closingBalance` | |
| entradas, saídas | `inflows`, `outflows` | |
| despesa | `Expense` | |
| histórico (do lançamento) | `memo` | |
| impressão do lançamento | `LedgerEntryFingerprint` | chave que identifica o lançamento entre reprocessamentos (ADR 0004) |
| troca de categoria | `CategoryChange` | |
| indexação | `Indexing` | |
| recebimento de cota | `condoFeeReceipt` | |
| transferência entre fundos | `interFundTransfer` | |
| competência | `referenceMonth` | mês de referência do achado ou do lançamento |
| alvo (do achado) | `target` | |
| achado apurado (ainda não gravado) | `AssessedFinding` | |
| gatilho do recálculo | `RecalculationTrigger` | |
| vigência (de um parâmetro) | `validFrom`, `validTo` | |
| teto, excesso | `cap`, `overrun` | |
| termos de conduta | `ConductTerms` | |
| catálogo de módulos | `FeatureCatalog` | |
| trilha de ativação | `FeatureEvent` | |
| período ativo | `ActivePeriod` | |
| uso, registro de uso | `FeatureUsage`, `UsageService` | |
| função de uso | `UsageFunction` | |
| modo de IA | `AiMode` | |
| custo estimado | `estimatedCost` | |
| configuração de IA, trilha da configuração | `AiConfiguration`, `AiConfigurationEvent` | |
| catálogo de IA (provedores e modelos) | `AiCatalog`, `AiProvider`, `AiModel` | |
| função de IA (respostas, embeddings) | `AiFunction`, `answers`, `embeddings` | as constantes `RESPOSTAS` e `EMBEDDINGS` ficam até a fase 2 |
| chave cifrada, final da chave | `encryptedKey`, `keySuffix` | |
| cifrador da chave de API | `ApiKeyCipher` | |
| pergunta, histórico (da conversa) | `Question`, `ConversationTurn` | |
| situação da resposta | `AnswerStatus` | |
| trecho, citação | `Chunk`, `Citation` | |
| dados gravados | `storedData` | dados do banco usados na resposta do chat |
| segunda barreira | `FileAccessBarrier` | |
| consulta (gRPC) | `QueryService`, `QueryGrpcService` | o serviço gRPC `Consulta` do contrato não muda até a fase 2 |
| prazo (de uma chamada) | `timeout`, `deadline` | |
| código impresso, código efetivo | `printedCode`, `effectiveCode` | |
| orçado, orçado anterior | `budgeted`, `previousBudgeted` | |
| previsto do mês | `monthlyPlanned` | |
| início e fim do exercício | `fiscalYearStart`, `fiscalYearEnd` | |
| ata (que aprovou a PO), sem ata | `minutes`, `withoutMinutes` | |
| reaprovação, substituída | `reapproval`, `superseded` | |
| ciente da divergência | `discrepancyAcknowledged` | |
| tolerância de arredondamento | `roundingTolerance` | |
| ligação dos fundos da PO | `BudgetFundLink` | |
| estrutura da PO (total, grupos) | `BudgetStructure` | |
| avaliação da leitura da PO | `BudgetReadingAssessment` | |
| vigência da PO, PO do mês | `BudgetValidity`, `BudgetOfMonth` | |
| marca da linha da PO | `BudgetLineMark` | |
| mudança no orçamento (evento) | `BudgetChanged` | |
| rubrica da linha, trilha das rubricas | `BudgetLineItem`, `BudgetItemEvent` | |
| sugestão da rubrica | `BudgetItemSuggestion` | |
| conta do fluxo, conta da PO | `account` (`accountCode`, `accountName`) | o nome da conta continua o texto lido |
| destino (do de-para), tipo de destino | `MappingTarget`, `MappingTargetType` | |
| trilha do de-para | `AccountMappingEvent` | |
| de-para efetivo (só o confirmado) | `EffectiveAccountMapping` | |
| sugestão pelo nome, nota, palavras vazias, abreviações | `NameSuggestion`, `score`, `stopWords`, `abbreviations` | |
| planilha do de-para | `AccountMappingSheet` | |
| cópia da versão anterior, igual à versão anterior | `PreviousVersionCopy`, `sameAsPreviousVersion` | |
| sugerido, confirmado, recusado (estado) | `status` (`SUGERIDO`, `CONFIRMADO`, `RECUSADO`) | as constantes ficam até a fase 2 |
| origem (da sugestão), motivo | `source`, `reason` | |
| lote (confirmar ou recusar vários) | `batch` | |
| ignorada (no lote), recusada (na planilha) | `skipped`, `rejected` | |

---

## Decisão 2: camadas e regras entre elas

Camadas, de fora para dentro: **controller → service → repository**, com **model** e **dto** como dados.

1. **Controller** só recebe e devolve DTOs. Não injeta repositório, não carrega entidade, não tem regra de negócio. Valida a entrada com Bean Validation (`spring-boot-starter-validation`, já no `backend/build.gradle.kts`).
2. **Service** tem a regra de negócio e o `@Transactional`. Recebe e devolve DTOs (ou tipos simples) para o controller; entidades não saem da camada de serviço.
3. **Repository** são as interfaces Spring Data JPA. Só o serviço usa.
4. **Model** são as entidades JPA, os enums e os objetos de valor do domínio. Toda entidade declara `@Table` e toda coluna cujo nome não bate com o campo declara `@Column`, para o nome em inglês da classe não mudar a tabela sem migração.
5. **DTO** é um `record` por necessidade de tela ou de endpoint (`BudgetSummaryResponse`, `ConfirmBudgetRequest`), um por arquivo. Sufixos: `Request` para entrada, `Response` para saída. Acabam as classes "sacola" (`ArquivoDtos`, `PrevisaoDtos`).
6. **Mapper** é uma classe escrita à mão (`BudgetMapper`) que converte entidade ↔ DTO. Sem MapStruct (decidido pelo usuário).
7. **Cálculos puros** (como `CalculoPrevistoRealizado`, ADR 0004) ficam em `service` como classes sem estado, sem Spring e sem banco, testadas sozinhas.
8. Classes passam a ser `public` onde a camada exigir. Hoje muita coisa é package-private porque o assunto inteiro estava no mesmo pacote.

---

## Decisão 3: estrutura de pacotes

| Opção | Exemplo | Prós | Contras |
|---|---|---|---|
| A. Camadas sem subpacote | `service/BudgetService`, `service/FindingService`… | Literal ao pedido. Simples | `service` passaria de 60 classes e `model` de 40; o pacote vira lista sem ordem |
| **B. Camada primeiro, assunto dentro da camada** | `service/budget/BudgetService`, `model/budget/Budget`, `controller/budget/BudgetController` | Atende o pedido (as camadas ficam no primeiro nível) e cresce bem. Os mesmos subpacotes de assunto se repetem em todas as camadas, então é fácil achar | Mais pastas. Um assunto fica espalhado por várias camadas |
| C. Assunto primeiro, camada dentro | `budget/service/BudgetService`, `budget/model/Budget` | Cada assunto fica junto | Não é o que o usuário pediu. É a organização de hoje com subpastas |

**Decidido: B, com `model` (não `domain`).** Assuntos do backend: `condominium`, `file`, `accounting`, `audit`, `budget`, `dashboard`, `feature`, `ai`, `assistant`. Camada pequena (menos de ~10 classes) pode dispensar o subpacote.

### Backend

```
api/                                       # projeto Gradle (jar e contêiner); hoje backend/
└── src/main/java/br/com/condominioauditoria/api/
    ├── ApiApplication.java
    ├── config/          # @Configuration (beans, Jackson, Rabbit, gRPC)
    │   └── properties/  # @ConfigurationProperties (records imutáveis)
    ├── controller/      # REST: só DTO entra e sai
    │   └── budget/ audit/ file/ …
    ├── dto/             # records por tela
    │   ├── request/     # entrada (…Request), com Bean Validation
    │   └── response/    # saída (…Response)
    │       (dentro de cada um, os mesmos subpacotes de assunto)
    ├── mapper/          # entidade ↔ DTO, à mão, um por agregado (BudgetMapper)
    ├── model/           # entidades JPA e objetos de valor (records @Embeddable)
    │   ├── budget/ audit/ file/ …
    │   └── enums/       # enums do domínio (BudgetStatus, Severity, FileCategory…)
    ├── repository/      # interfaces Spring Data JPA (+ projeções de consulta)
    │   └── budget/ audit/ file/ …
    ├── service/         # regra de negócio, @Transactional
    │   ├── budget/ audit/ file/ …
    │   ├── audit/rule/  # regras de auditoria (Strategy: uma classe por regra)
    │   └── calculator/  # cálculos puros, sem Spring e sem banco (BudgetVsActualCalculator)
    ├── event/           # eventos de aplicação (records: FeatureChanged, BudgetConfirmed…)
    ├── listener/        # @EventListener / @TransactionalEventListener desses eventos
    ├── report/          # PDF (Thymeleaf + OpenHTMLtoPDF) e Excel (POI)
    ├── messaging/       # RabbitMQ: publicadores (outbox), @RabbitListener e records das mensagens
    ├── grpc/
    │   ├── server/      # servidor de consulta (chamado pelo mcp)
    │   └── client/      # cliente do assistente (chama o rag)
    ├── security/        # Spring Security, Keycloak, acesso por condomínio
    ├── exception/       # exceções de negócio e o @RestControllerAdvice
    └── util/            # utilitários puros e final (MoneyFormatter)
```

Por que cada pacote a mais (resposta à pergunta 3, seguindo as convenções Java e Spring):

- `model/enums`: há 17 enums no backend hoje (`Severidade`, `StatusArquivo`, `EstadoPrevisao`…). Ficam juntos e usados tanto pelas entidades quanto pelos DTOs.
- `dto/request` e `dto/response`: separa o que entra (validado) do que sai; nome do record termina em `Request` ou `Response`.
- `config/properties`: hoje há 5 classes `@ConfigurationProperties` espalhadas (`PropriedadesOrcamento`, `PropriedadesDepara`…).
- `event` e `listener`: 12 classes usam eventos de aplicação do Spring (ex.: `ModuloAlterado`, `DisparoRecalculoAchados`, `ReindexacaoAoLigarModulo`). Padrão Observer, com o evento separado de quem reage.
- `service/calculator`: os cálculos puros da ADR 0004 e 0005 (`CalculoPrevistoRealizado`, `ComparacaoExercicios`, `Indicadores`) ficam separados dos serviços com banco, e são testados sem Spring.
- `service/audit/rule`: cada regra de auditoria é uma classe que implementa a mesma interface (padrão Strategy), como já é hoje (`RegraExcessoMes`, `RegraTetoFundoReserva`…).
- `grpc/server` e `grpc/client`: o backend é as duas coisas.
- **Não** entram: `constants` (a constante fica na classe dona dela, como manda a convenção Java), `impl` (só há interface quando há mais de uma implementação ou fronteira, como `Storage`), `helper` e `common` (viram `util` ou ficam na classe que usa).

Exemplo de para onde vai cada classe de hoje:

| Hoje | Depois |
|---|---|
| `orcamento/PrevisaoOrcamentaria` | `model/budget/Budget` (`@Table(name = "previsao_orcamentaria")` até a fase 2) |
| `orcamento/EstadoPrevisao` | `model/enums/BudgetStatus` |
| `orcamento/PrevisaoOrcamentariaRepository` | `repository/budget/BudgetRepository` |
| `orcamento/ConfirmacaoPrevisao` | `service/budget/BudgetConfirmationService` |
| `orcamento/CalculoPrevistoRealizado` | `service/calculator/BudgetVsActualCalculator` |
| `orcamento/PrevisaoController` | `controller/budget/BudgetController` |
| `orcamento/PrevisaoDtos` | um record por arquivo em `dto/request/budget/` e `dto/response/budget/` |
| `orcamento/RelatorioPdf`, `RelatorioExcel` | `report/BudgetVsActualPdfReport`, `report/BudgetVsActualExcelReport` |
| `orcamento/DinheiroBr` | `util/MoneyFormatter` |
| `auditoria/RegraExcessoMes` | `service/audit/rule/MonthlyOverrunRule` |
| `erro/TratadorDeErros` | `exception/GlobalExceptionHandler` |
| `mensagens/PublicadorArquivos` | `messaging/FilePublisher` |
| `modulo/ModuloAlterado` | `event/FeatureChanged` |
| `orcamento/PropriedadesOrcamento` | `config/properties/BudgetProperties` |

### rag

O rag não tem controller REST: entra por fila e por gRPC. A mesma ideia, com as portas de entrada no lugar do controller.

```
rag/src/main/java/br/com/condominioauditoria/rag/
├── RagApplication.java
├── config/
│   └── properties/
├── messaging/       # receptores e publicadores da fila (porta de entrada)
├── grpc/            # servidor do assistente (porta de entrada)
├── model/           # records do domínio lido: cashflow/, budget/ (hoje rag.dominio); enums/
├── parser/          # interpretação da saída do leitor (hoje rag.leitura), dono: agente ingestao
├── dto/             # pedidos e respostas do assistente (hoje PedidoPergunta, ResultadoPergunta)
├── service/         # processamento, indexação, perguntas (hoje rag.processamento e rag.assistente.pergunta)
├── repository/      # índice no pgvector (hoje rag.indice.RepositorioIndice)
├── search/          # corte em trechos, embeddings, busca híbrida (hoje rag.indice), dono: agente rag
├── client/          # leitor HTTP, consulta ao backend por gRPC, gateway da IA
├── exception/
└── util/
```

### mcp

```
mcp/src/main/java/br/com/condominioauditoria/mcp/
├── McpApplication.java
├── config/
│   └── properties/
├── tool/            # ferramentas MCP (porta de entrada; hoje mcp.ferramentas)
└── client/          # cliente gRPC do backend (hoje ClienteBackend)
```

### libs

`libs/armazenamento` passa a `libs/storage` (pacote `br.com.condominioauditoria.storage`). `libs/contrato-grpc` passa a `libs/grpc-contract`.

---

## Decisão 4: o nome do serviço dentro do pacote (`backend/…/backend`)

Opções avaliadas: A (manter `br.com.condominioauditoria.<serviço>`), B (raiz única para todos, sem segmento de serviço) e C (nome por papel).

**Decidido pelo usuário: raiz única `br.com.condominioauditoria` e, abaixo dela, um nome por papel; o segmento `backend` sai.** O segmento por papel continua necessário para dois serviços não terem pacotes com o mesmo nome completo (o rag e o backend têm `messaging`, `grpc` e `config`).

| Serviço | Pasta (projeto Gradle) | Pacote raiz |
|---|---|---|
| backend | `api/` (hoje `backend/`) | `br.com.condominioauditoria.api` |
| rag | `rag/` | `br.com.condominioauditoria.rag` |
| mcp | `mcp/` | `br.com.condominioauditoria.mcp` |
| lib de armazenamento | `libs/storage/` (hoje `libs/armazenamento/`) | `br.com.condominioauditoria.storage` |
| lib do contrato gRPC | `libs/grpc-contract/` (hoje `libs/contrato-grpc/`) | o pacote gerado pelo `.proto` (fase 2) |

`rag` e `mcp` já são nomes de papel em inglês. O nome do backend, `api`, foi escolhido pelo usuário em 08/10/2026 (as outras opções eram `core` e a raiz direta). A pasta muda junto, para pasta e pacote terem sempre o mesmo nome; isso inclui o serviço no `infra/docker-compose.yml`, o `java.Dockerfile`, o `settings.gradle.kts`, os comandos do `CLAUDE.md` e os agentes.

Regra: **nenhum pacote repete** o nome do serviço nem do pacote pai (nada de `budget/BudgetModel` dentro de `model/budget`, nem `api/apiservice`).

---

## Decisão 5: até onde vai o inglês (escopo)

| Opção | O que muda | Prós | Contras |
|---|---|---|---|
| A. Só o código Java | Classes e pacotes; tabelas, colunas, JSON da API e das mensagens ficam em português por `@Table`, `@Column` e `@JsonProperty` | Sem mudança de contrato nem migração | Tradução permanente entre o código e o banco/API. Fica pela metade |
| **B. Tudo o que é técnico, em três fases** | Fase 1: Java. Fase 2: banco (migração Flyway que renomeia tabelas e colunas), contratos (nova versão de API, mensagens `v3`, gRPC `v2`) e papéis do Keycloak. Fase 3: código do leitor Python e do frontend (a tela continua em português) | Fica consistente de ponta a ponta. Cada fase é revisável sozinha e não muda comportamento | Mais PRs. A fase 2 muda contratos: os dois lados no mesmo PR, como o `CLAUDE.md` exige |
| C. Tudo de uma vez | Igual a B num PR só | Termina antes | PR enorme, impossível de revisar; risco alto de quebrar os casos golden |

**Decidido: B.** Na fase 1 os records de DTO e de mensagem ganham nomes em inglês com `@JsonProperty("nomeAtual")`, e as entidades ganham `@Table`/`@Column` com o nome atual, para a API, a fila e o banco não mudarem. A fase 2 retira essas anotações junto com a migração e a nova versão dos contratos.

---

## Decisão 6: como a refatoração é feita

1. Só depois desta ADR aprovada. Primeiro PR: esta ADR + `CLAUDE.md` + `.claude/agents/` + `docs/arquitetura.md` com a convenção, para os agentes já seguirem a regra em código novo.
2. **Fase 1, um PR por serviço**, na ordem `libs` → `backend` (vira `api`) → `rag` → `mcp`. O backend pode ser dividido por assunto (`budget` sozinho é grande). Cada PR só move e renomeia, mais tirar os repositórios dos controllers para os serviços; nenhuma regra muda.
3. Em todo PR: `./gradlew test` verde, os casos de `data/golden/` sem piora, `contracts/openapi.yaml` sem diferença na fase 1, e o sistema subindo com `docker compose up --build`.
4. **Fase 2 e fase 3** só depois da fase 1 inteira no `main`, cada uma com seu PR, os dois lados do contrato no mesmo PR.
5. Código novo escrito durante a refatoração já segue esta ADR.

## Impacto em cada serviço e agente responsável

| Parte | Fase | Agente |
|---|---|---|
| `CLAUDE.md`, `.claude/agents/`, `docs/arquitetura.md`, `libs/` | antes da fase 1 e fase 1 | `arquiteto` |
| `backend/` | 1 | `backend` |
| `rag/` (`parser`, `model`) | 1 | `ingestao` |
| `rag/` (`search`, `service` de perguntas, `client` da IA) | 1 | `rag` |
| `mcp/` | 1 | `mcp` |
| migrações, `contracts/`, Keycloak, testes ponta a ponta | 2 | `mcp` com `backend` e `rag` |
| `leitor/` | 3 | `ingestao` |
| `frontend/` (código; textos da tela continuam em português) | 2 (cliente gerado) e 3 | `frontend` |

## Consequências

- O código passa a seguir o padrão que o usuário conhece (camadas, DTO por tela, repositório só no serviço).
- Documentos e conversa continuam em português; o glossário é a ponte entre os dois.
- Durante a fase 1 o banco, a API e a fila mantêm os nomes em português por anotação. Isso é temporário e some na fase 2.
- Os PRs da fase 1 são grandes em número de arquivos, mas mecânicos; o histórico do Git acompanha os arquivos movidos (`git log --follow`).

## Respostas do usuário (08/10/2026)

1. Idioma: **sim** (B).
2. Glossário: **ok**, como está.
3. Pacotes: **camada primeiro (B), com `model`**; pediu para ver se faltavam pacotes "seguindo a convenção Java e padrões de projeto". Entraram `model/enums`, `dto/request` e `dto/response`, `config/properties`, `event`, `listener`, `service/calculator`, `grpc/server` e `grpc/client` (ver Decisão 3).
4. Nome do serviço: **tirar o `backend`, raiz única com nome por papel** (ver Decisão 4). Nome do backend: **`api`**.
5. Mapper: **à mão**, sem MapStruct.
6. Escopo: **tudo o que é código em inglês, opção B (três fases)**.
