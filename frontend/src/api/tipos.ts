// Tipos gerados a partir de contracts/openapi.yaml (pnpm gerar-api). Não edite esquema.ts à mão.
import type { components } from "./esquema";

type Esquemas = components["schemas"];

export type Categoria = Esquemas["FileCategory"];
export type StatusArquivo = Esquemas["FileStatus"];
export type CategoriaDto = Esquemas["FileCategoryResponse"];
export type NovaCategoria = Esquemas["ChangeCategoryRequest"];
export type UsuarioLogado = Esquemas["CurrentUserResponse"];
export type ArquivoResumo = Esquemas["SourceFileResponse"];
export type ArquivoDetalhe = Esquemas["SourceFileDetailResponse"];
export type Painel = Esquemas["DashboardResponse"];
export type Problema = Esquemas["Problem"];
export type Perfil = UsuarioLogado["roles"][number];

// Previsto × realizado (ADR 0004)
export type EstadoPrevisao = Esquemas["BudgetStatus"];
export type PrevisaoResumo = Esquemas["BudgetSummaryResponse"];
export type PrevisaoDetalhe = Esquemas["BudgetDetailResponse"];
export type LinhaPo = Esquemas["BudgetLineResponse"];
export type ConferenciaPo = Esquemas["BudgetCheckResponse"];
export type AvisoPo = Esquemas["BudgetWarningResponse"];
export type PedidoConfirmacao = Esquemas["BudgetConfirmationRequest"];
export type MarcaLinha = NonNullable<LinhaPo["mark"]>;

// De-para
export type TipoDestino = Esquemas["MappingTargetType"];
export type EstadoDepara = Esquemas["AccountMappingStatus"];
export type OrigemDepara = Esquemas["AccountMappingSource"];
export type FiltroDepara = Esquemas["AccountMappingFilter"];
export type DestinoDepara = Esquemas["MappingTargetResponse"];
export type ContaDepara = Esquemas["AccountMappingResponse"];
export type DeparaLista = Esquemas["AccountMappingsResponse"];
export type PedidoDestino = Esquemas["MappingTargetRequest"];
export type PedidoLote = Esquemas["AccountMappingBatchRequest"];
export type ResultadoLote = Esquemas["AccountMappingBatchResponse"];
export type ContaIgnorada = Esquemas["SkippedAccountResponse"];
export type ResultadoSugestoes = Esquemas["AccountMappingSuggestionsResponse"];
export type ResultadoPlanilha = Esquemas["AccountMappingSheetResponse"];
export type EventoDepara = Esquemas["AccountMappingEventResponse"];

// Resultado do cálculo (vem pronto do backend; a tela só mostra)
export type PrevistoRealizado = Esquemas["BudgetVsActualResponse"];
export type MesPrevistoRealizado = PrevistoRealizado["months"][number];
export type TotaisPrevistoRealizado = NonNullable<PrevistoRealizado["totals"]>;
export type Regra20 = NonNullable<PrevistoRealizado["rule20"]>;
export type ConferenciaFluxo = NonNullable<PrevistoRealizado["cashFlowCheck"]>;
export type FundoPrevistoRealizado = PrevistoRealizado["funds"][number];
export type GrupoPrevistoRealizado = Esquemas["BudgetVsActualGroupResponse"];
export type LinhaPrevistoRealizado = GrupoPrevistoRealizado["lines"][number];
export type BlocoPrevistoRealizado = Esquemas["EntryBlockResponse"];
export type EvidenciaLancamento = Esquemas["EvidenceResponse"];
export type FluxoUsado = Esquemas["UsedCashFlowResponse"];

// Fundos, realocação, trilha da PO e achados (passos 8 e 9)
export type FundoFluxo = Esquemas["FundResponse"];
export type EventoPrevisao = Esquemas["BudgetEventResponse"];
export type Realocacao = Esquemas["ReallocationResponse"];
export type PedidoRealocacao = Esquemas["ReallocationRequest"];
export type EstadoAchado = Esquemas["FindingStatus"];
export type Achado = Esquemas["FindingResponse"];
// Indexação para a busca (RF-04.7) e módulos contratáveis (RF-10)
export type IndexacaoArquivo = Esquemas["IndexingResponse"];
export type SituacaoIndexacao = Esquemas["IndexingStatus"];
export type ContextoCondominio = Esquemas["CondominiumContextResponse"];
export type ModuloDoCondominio = Esquemas["FeatureResponse"];
export type AlteracaoModulo = Esquemas["ChangeFeatureRequest"];
export type EventoModulo = Esquemas["FeatureEventResponse"];
export type PeriodoAtivo = Esquemas["ActivePeriodResponse"];
export type FuncaoUso = Esquemas["UsageFunction"];
export type TotalUso = Esquemas["UsageTotalResponse"];
export type UsoDoPeriodo = Esquemas["UsageResponse"];

// Assistente (RF-04.8 a 04.18) e configuração de IA do condomínio (RF-09.6)
export type ModoIa = Esquemas["AiMode"];
export type ContextoAssistente = Esquemas["AssistantContextResponse"];
export type ProvedorIa = Esquemas["AiProviderResponse"];
export type ModeloIa = Esquemas["AiModelResponse"];
export type ConfiguracaoIa = Esquemas["AiConfigurationResponse"];
export type PedidoConfiguracaoIa = Esquemas["AiConfigurationRequest"];
export type FiltrosDocumentos = Esquemas["DocumentFiltersRequest"];
export type PedidoPergunta = Esquemas["QuestionRequest"];
export type TrocaHistorico = NonNullable<PedidoPergunta["history"]>[number];
export type RespostaAssistente = Esquemas["AssistantAnswerResponse"];
export type ParagrafoDocumentos = RespostaAssistente["fromDocuments"][number];
export type DadoGravado = Esquemas["StoredDataResponse"];
export type TrechoDocumento = Esquemas["DocumentChunkResponse"];
export type CitacaoDocumento = Esquemas["DocumentCitationResponse"];
export type PedidoBuscaDocumentos = Esquemas["DocumentSearchRequest"];

// Análise da PO (ADR 0005): exercícios, comparação entre exercícios, coluna impressa e rubricas
export type ProrrogacaoPo = Esquemas["BudgetExtensionResponse"];
export type ResumoDepara = Esquemas["AccountMappingSummaryResponse"];
export type Exercicio = Esquemas["FiscalYearResponse"];
export type MesExercicio = Exercicio["months"][number];
export type ComparacaoExercicios = Esquemas["FiscalYearComparisonResponse"];
export type ExercicioComparado = ComparacaoExercicios["fiscalYears"][number];
export type ResumoComparado = ComparacaoExercicios["summary"][number];
export type GrupoComparado = ComparacaoExercicios["groups"][number];
export type RubricaComparada = ComparacaoExercicios["lines"][number];
export type LinhaSemCorrespondencia = ComparacaoExercicios["unmatched"][number];
export type ValorComparado = Esquemas["ComparedValueResponse"];
export type VariacaoExercicio = Esquemas["VariationResponse"];
export type ConferenciaColuna = Esquemas["PrintedColumnCheckResponse"];
export type GrupoConferenciaColuna = ConferenciaColuna["groups"][number];
export type EstadoRubrica = Esquemas["BudgetItemStatus"];
export type OrigemRubrica = Esquemas["BudgetItemSource"];
export type FiltroRubrica = Esquemas["BudgetItemFilter"];
export type Rubrica = Esquemas["BudgetItemResponse"];
export type LinhaComRubrica = Esquemas["BudgetLineItemResponse"];
export type ResumoRubricas = Esquemas["BudgetItemSummaryResponse"];
export type RubricasDaPo = Esquemas["BudgetItemsResponse"];
export type PedidoRubricaLinha = Esquemas["LineBudgetItemRequest"];
export type PedidoLoteRubrica = Esquemas["BudgetItemBatchRequest"];
export type ResultadoLoteRubrica = Esquemas["BudgetItemBatchResponse"];
export type ResultadoSugestoesRubrica = Esquemas["BudgetItemSuggestionsResponse"];
export type EventoRubrica = Esquemas["BudgetItemEventResponse"];

// Indicadores (RF-11.10 a RF-11.13): séries prontas dos 7 gráficos. Série nula = não se aplica ao fundo escolhido.
export type Indicadores = Esquemas["IndicatorsResponse"];
export type PontoExecucao = NonNullable<Indicadores["monthlyExecution"]>[number];
export type PontoRegra20 = NonNullable<Indicadores["rule20"]>[number];
export type PontoAcumulado = NonNullable<Indicadores["cumulative"]>[number];
export type SerieGrupoIndicador = NonNullable<Indicadores["actualByGroup"]>[number];
export type MaioresDiferencas = NonNullable<Indicadores["largestDifferences"]>;
export type DiferencaIndicador = Esquemas["LineDifferenceResponse"];
export type SerieFundoIndicador = NonNullable<Indicadores["funds"]>[number];
export type ComparacaoIndicador = NonNullable<Indicadores["comparison"]>;
/** Situação do mês em todas as séries: só WITH_CASH_FLOW tem números. */
export type SituacaoMesIndicador = PontoExecucao["status"];
