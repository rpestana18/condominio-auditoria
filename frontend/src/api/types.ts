// Types generated from contracts/openapi.yaml (pnpm generate-api). Do not edit schema.ts by hand.
import type { components } from "./schema";

type Schemas = components["schemas"];

export type FileCategory = Schemas["FileCategory"];
export type FileStatus = Schemas["FileStatus"];
export type FileCategoryOption = Schemas["FileCategoryResponse"];
export type CategoryChange = Schemas["ChangeCategoryRequest"];
export type CurrentUser = Schemas["CurrentUserResponse"];
export type SourceFileSummary = Schemas["SourceFileResponse"];
export type SourceFileDetail = Schemas["SourceFileDetailResponse"];
export type Dashboard = Schemas["DashboardResponse"];
export type Problem = Schemas["Problem"];
export type Role = CurrentUser["roles"][number];

// Budget vs actual (ADR 0004)
export type BudgetStatus = Schemas["BudgetStatus"];
export type BudgetSummary = Schemas["BudgetSummaryResponse"];
export type BudgetDetail = Schemas["BudgetDetailResponse"];
export type BudgetLine = Schemas["BudgetLineResponse"];
export type BudgetCheck = Schemas["BudgetCheckResponse"];
export type BudgetWarning = Schemas["BudgetWarningResponse"];
export type BudgetConfirmationRequest = Schemas["BudgetConfirmationRequest"];
export type LineMark = NonNullable<BudgetLine["mark"]>;

// Account mapping
export type MappingTargetType = Schemas["MappingTargetType"];
export type AccountMappingStatus = Schemas["AccountMappingStatus"];
export type AccountMappingSource = Schemas["AccountMappingSource"];
export type AccountMappingFilter = Schemas["AccountMappingFilter"];
export type MappingTarget = Schemas["MappingTargetResponse"];
export type MappedAccount = Schemas["AccountMappingResponse"];
export type AccountMappingList = Schemas["AccountMappingsResponse"];
export type MappingTargetRequest = Schemas["MappingTargetRequest"];
export type AccountMappingBatchRequest = Schemas["AccountMappingBatchRequest"];
export type AccountMappingBatchResult = Schemas["AccountMappingBatchResponse"];
export type SkippedAccount = Schemas["SkippedAccountResponse"];
export type AccountMappingSuggestionsResult = Schemas["AccountMappingSuggestionsResponse"];
export type AccountMappingSheetResult = Schemas["AccountMappingSheetResponse"];
export type AccountMappingEvent = Schemas["AccountMappingEventResponse"];

// Calculation result (comes ready from the backend; the screen only shows it)
export type BudgetVsActual = Schemas["BudgetVsActualResponse"];
export type BudgetVsActualMonth = BudgetVsActual["months"][number];
export type BudgetVsActualTotals = NonNullable<BudgetVsActual["totals"]>;
export type Rule20 = NonNullable<BudgetVsActual["rule20"]>;
export type CashFlowCheck = NonNullable<BudgetVsActual["cashFlowCheck"]>;
export type BudgetVsActualFund = BudgetVsActual["funds"][number];
export type BudgetVsActualGroup = Schemas["BudgetVsActualGroupResponse"];
export type BudgetVsActualLine = BudgetVsActualGroup["lines"][number];
export type EntryBlock = Schemas["EntryBlockResponse"];
export type EntryEvidence = Schemas["EvidenceResponse"];
export type UsedCashFlow = Schemas["UsedCashFlowResponse"];

// Funds, reallocation, budget trail and findings (steps 8 and 9)
export type Fund = Schemas["FundResponse"];
export type BudgetEvent = Schemas["BudgetEventResponse"];
export type Reallocation = Schemas["ReallocationResponse"];
export type ReallocationRequest = Schemas["ReallocationRequest"];
export type FindingStatus = Schemas["FindingStatus"];
export type Finding = Schemas["FindingResponse"];
// Indexing for search (RF-04.7) and contractable features (RF-10)
export type FileIndexing = Schemas["IndexingResponse"];
export type IndexingStatus = Schemas["IndexingStatus"];
export type CondominiumContext = Schemas["CondominiumContextResponse"];
export type CondominiumFeature = Schemas["FeatureResponse"];
export type FeatureChange = Schemas["ChangeFeatureRequest"];
export type FeatureEvent = Schemas["FeatureEventResponse"];
export type ActivePeriod = Schemas["ActivePeriodResponse"];
export type UsageFunction = Schemas["UsageFunction"];
export type UsageTotal = Schemas["UsageTotalResponse"];
export type PeriodUsage = Schemas["UsageResponse"];

// Assistant (RF-04.8 to 04.18) and the condominium's AI configuration (RF-09.6)
export type AiMode = Schemas["AiMode"];
export type AssistantContext = Schemas["AssistantContextResponse"];
export type AiProvider = Schemas["AiProviderResponse"];
export type AiModel = Schemas["AiModelResponse"];
export type AiConfiguration = Schemas["AiConfigurationResponse"];
export type AiConfigurationRequest = Schemas["AiConfigurationRequest"];
export type DocumentFilters = Schemas["DocumentFiltersRequest"];
export type QuestionRequest = Schemas["QuestionRequest"];
export type ConversationTurn = NonNullable<QuestionRequest["history"]>[number];
export type AssistantAnswer = Schemas["AssistantAnswerResponse"];
export type DocumentParagraph = AssistantAnswer["fromDocuments"][number];
export type StoredData = Schemas["StoredDataResponse"];
export type DocumentChunk = Schemas["DocumentChunkResponse"];
export type DocumentCitation = Schemas["DocumentCitationResponse"];
export type DocumentSearchRequest = Schemas["DocumentSearchRequest"];

// Budget analysis (ADR 0005): fiscal years, comparison between fiscal years, printed column and budget items
export type BudgetExtension = Schemas["BudgetExtensionResponse"];
export type AccountMappingSummary = Schemas["AccountMappingSummaryResponse"];
export type FiscalYear = Schemas["FiscalYearResponse"];
export type FiscalYearMonth = FiscalYear["months"][number];
export type FiscalYearComparison = Schemas["FiscalYearComparisonResponse"];
export type ComparedFiscalYear = FiscalYearComparison["fiscalYears"][number];
export type ComparedSummary = FiscalYearComparison["summary"][number];
export type ComparedGroup = FiscalYearComparison["groups"][number];
export type ComparedBudgetItem = FiscalYearComparison["lines"][number];
export type UnmatchedLine = FiscalYearComparison["unmatched"][number];
export type ComparedValue = Schemas["ComparedValueResponse"];
export type FiscalYearVariation = Schemas["VariationResponse"];
export type PrintedColumnCheck = Schemas["PrintedColumnCheckResponse"];
export type PrintedColumnGroup = PrintedColumnCheck["groups"][number];
export type BudgetItemStatus = Schemas["BudgetItemStatus"];
export type BudgetItemSource = Schemas["BudgetItemSource"];
export type BudgetItemFilter = Schemas["BudgetItemFilter"];
export type BudgetItem = Schemas["BudgetItemResponse"];
export type BudgetLineItem = Schemas["BudgetLineItemResponse"];
export type BudgetItemSummary = Schemas["BudgetItemSummaryResponse"];
export type BudgetItemList = Schemas["BudgetItemsResponse"];
export type LineBudgetItemRequest = Schemas["LineBudgetItemRequest"];
export type BudgetItemBatchRequest = Schemas["BudgetItemBatchRequest"];
export type BudgetItemBatchResult = Schemas["BudgetItemBatchResponse"];
export type BudgetItemSuggestionsResult = Schemas["BudgetItemSuggestionsResponse"];
export type BudgetItemEvent = Schemas["BudgetItemEventResponse"];

// Indicators (RF-11.10 to RF-11.13): ready-made series of the 7 charts. Null series = does not apply to the chosen fund.
export type Indicators = Schemas["IndicatorsResponse"];
export type ExecutionPoint = NonNullable<Indicators["monthlyExecution"]>[number];
export type Rule20Point = NonNullable<Indicators["rule20"]>[number];
export type CumulativePoint = NonNullable<Indicators["cumulative"]>[number];
export type GroupIndicatorSeries = NonNullable<Indicators["actualByGroup"]>[number];
export type LargestDifferences = NonNullable<Indicators["largestDifferences"]>;
export type LineDifference = Schemas["LineDifferenceResponse"];
export type FundIndicatorSeries = NonNullable<Indicators["funds"]>[number];
export type IndicatorComparison = NonNullable<Indicators["comparison"]>;
/** Month status in every series: only WITH_CASH_FLOW has numbers. */
export type IndicatorMonthStatus = ExecutionPoint["status"];

// Month-by-month evolution on the home screen (RF-12)
export type MonthlyEvolution = Schemas["MonthlyEvolutionResponse"];
export type MonthlySeries = Schemas["MonthlySeries"];
export type MonthlyPoint = Schemas["MonthlyPoint"];
export type MonthlyPointStatus = MonthlyPoint["status"];
export type MonthlyEntry = MonthlyPoint["entries"][number];
export type MonthlyVariation = Schemas["MonthlyVariation"];
export type UtilityType = Schemas["UtilityType"];
export type UtilityAccount = Schemas["UtilityAccountResponse"];
