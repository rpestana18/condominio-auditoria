// Queries and actions for budget vs actual, the budget (PO) and the account mapping (ADR 0004).
// Every number comes calculated from the backend; here we only fetch and send.
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { putJson, postForm, postJson, deleteJson, getJson } from "./client";
import type {
  Finding,
  MappedAccount,
  BudgetEvent,
  Fund,
  AccountMappingList,
  AccountMappingEvent,
  EntryEvidence,
  AccountMappingFilter,
  BudgetConfirmationRequest,
  MappingTargetRequest,
  AccountMappingBatchRequest,
  BudgetDetail,
  BudgetSummary,
  ReallocationRequest,
  BudgetVsActual,
  Reallocation,
  AccountMappingBatchResult,
  AccountMappingSheetResult,
  ConfirmSuggestedRequest,
} from "./types";

/** "2026-09" (one month) or "cumulative" (the budget's fiscal year), as the API expects. */
export type Period = string;

export type ExportFormat = "pdf" | "xlsx";

/** Builds "?a=1&b=2" ignoring empty values. */
export function queryString(parameters: Record<string, string | null | undefined>): string {
  const search = new URLSearchParams();
  for (const [name, value] of Object.entries(parameters)) if (value) search.set(name, value);
  const text = search.toString();
  return text ? `?${text}` : "";
}

export const base = (condominiumId: string) => `/condominiums/${condominiumId}`;

// ---------- Budget (PO) ----------

export function useBudgets(condominiumId: string) {
  return useQuery({
    queryKey: ["budgets", condominiumId],
    queryFn: async () => (await getJson<BudgetSummary[]>(`${base(condominiumId)}/budgets`)) ?? [],
  });
}

export function useBudget(condominiumId: string, budgetId: string | undefined) {
  return useQuery({
    queryKey: ["budget", condominiumId, budgetId],
    queryFn: () => getJson<BudgetDetail>(`${base(condominiumId)}/budgets/${budgetId}`),
    enabled: !!budgetId,
  });
}

// ---------- Budget vs actual ----------

/** `fundId` filters in the backend: the operating fund brings only the Condomínio fund; another fund, only its panel; without it, everything. */
export function useBudgetVsActual(condominiumId: string, period: Period | null, budgetId?: string | null, fundId?: string | null) {
  return useQuery({
    queryKey: ["budget-vs-actual", condominiumId, period, budgetId ?? "current", fundId ?? "all"],
    queryFn: () =>
      getJson<BudgetVsActual>(`${base(condominiumId)}/budget-vs-actual${queryString({ period, budget: budgetId, fund: fundId })}`),
    enabled: period !== null,
  });
}

/**
 * Entries behind a number on the screen. Target: "line:<id>", "group:<id>", "total", "fund:<id>", ADJUSTMENTS,
 * TO_REALLOCATE, NO_BUDGET_LINE or TRANSFERS (API contract).
 */
export function useEvidence(condominiumId: string, period: Period, budgetId: string | null | undefined, target: string | null) {
  return useQuery({
    queryKey: ["evidence", condominiumId, period, budgetId ?? "current", target],
    queryFn: async () =>
      (await getJson<EntryEvidence[]>(
        `${base(condominiumId)}/budget-vs-actual/evidence${queryString({ period, budget: budgetId, target })}`,
      )) ?? [],
    enabled: target !== null,
  });
}

/** Export of the same view as the screen (same filters), generated in the backend (RF-03.1.14). */
export function exportPath(
  condominiumId: string,
  format: ExportFormat,
  period: Period,
  budgetId?: string | null,
  fundId?: string | null,
) {
  return `${base(condominiumId)}/budget-vs-actual/export${queryString({ format, period, budget: budgetId, fund: fundId })}`;
}

/** Cash flow funds by their printed name (fund filter and linking of the 1.9 lines). */
export function useFunds(condominiumId: string) {
  return useQuery({
    queryKey: ["funds", condominiumId],
    queryFn: async () => (await getJson<Fund[]>(`${base(condominiumId)}/funds`)) ?? [],
  });
}

/** Findings of a month (or of all months, without a reference month), read only. */
export function useFindings(condominiumId: string, referenceMonth: string | null) {
  return useQuery({
    queryKey: ["findings", condominiumId, referenceMonth ?? "all"],
    queryFn: async () => (await getJson<Finding[]>(`${base(condominiumId)}/findings${queryString({ referenceMonth })}`)) ?? [],
  });
}

export function useBudgetEvents(condominiumId: string, budgetId: string | undefined) {
  return useQuery({
    queryKey: ["budget-events", condominiumId, budgetId],
    queryFn: async () => (await getJson<BudgetEvent[]>(`${base(condominiumId)}/budgets/${budgetId}/events`)) ?? [],
    enabled: !!budgetId,
  });
}

// ---------- Reallocation (RF-03.1.7) ----------

export function useReallocations(condominiumId: string, budgetId: string | undefined) {
  return useQuery({
    queryKey: ["reallocations", condominiumId, budgetId],
    queryFn: async () => (await getJson<Reallocation[]>(`${base(condominiumId)}/reallocations${queryString({ budget: budgetId })}`)) ?? [],
    enabled: !!budgetId,
  });
}

// ---------- Account mapping ----------

export function useAccountMappings(condominiumId: string, budgetId: string | undefined, filter: AccountMappingFilter) {
  return useQuery({
    queryKey: ["account-mappings", condominiumId, budgetId, filter],
    queryFn: () =>
      getJson<AccountMappingList>(
        `${base(condominiumId)}/budgets/${budgetId}/account-mappings${queryString({ filter: filter === "ALL" ? null : filter })}`,
      ),
    enabled: !!budgetId,
  });
}

export function useAccountMappingEvents(condominiumId: string, budgetId: string | undefined, active: boolean) {
  return useQuery({
    queryKey: ["account-mapping-events", condominiumId, budgetId],
    queryFn: async () => (await getJson<AccountMappingEvent[]>(`${base(condominiumId)}/budgets/${budgetId}/account-mappings/events`)) ?? [],
    enabled: !!budgetId && active,
  });
}

/**
 * The budget, the account mapping or a budget item changed: budget vs actual, the budget analysis, the home screen
 * and the lists are reloaded. Exported for the budget analysis queries (budgetAnalysisQueries.ts).
 */
export function useReloadBudget() {
  const client = useQueryClient();
  return () => {
    const keys = [
      "budgets", "budget", "budget-events", "account-mappings", "account-mapping-events", "budget-vs-actual", "evidence", "dashboard",
      "reallocations", "findings", "fiscal-years", "fiscal-year-comparison", "printed-column", "budget-items", "budget-line-items", "budget-item-events", "indicators",
      // RF-13: tracking grid, balance bridge, fund movements, spending profiles and partial months
      "budget-tracking", "balance-bridge", "fund-movements", "spending-profiles", "spending-profile-events", "partial-months",
      "partial-month-events", "monthly-evolution",
      // ADR 0009: "Entradas e saídas", line review and origin
      "cash-flow-monthly", "cash-flow-entries", "line-review", "line-review-events",
    ];
    for (const key of keys) {
      void client.invalidateQueries({ queryKey: [key] });
    }
  };
}

/** Admin only (the backend answers 403 for the others). */
export function useConfirmBudget(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: BudgetConfirmationRequest) =>
      postJson<BudgetDetail>(`${base(condominiumId)}/budgets/${budgetId}/confirmation`, request),
    onSuccess: reload,
  });
}

export function useSetAccountMapping(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: ({ account, request }: { account: string; request: MappingTargetRequest }) =>
      putJson<MappedAccount>(`${base(condominiumId)}/budgets/${budgetId}/account-mappings/${encodeURIComponent(account)}`, request),
    onSuccess: reload,
  });
}

export function useAccountMappingBatch(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: AccountMappingBatchRequest) =>
      postJson<AccountMappingBatchResult>(`${base(condominiumId)}/budgets/${budgetId}/account-mappings/batch`, request),
    onSuccess: reload,
  });
}

/**
 * "Marcar todas como conferidas" (optional since Q54): confirms every automatic link at once. `expected` is the count the screen
 * showed; if it changed in the meantime, the backend answers 409 and confirms nothing.
 */
export function useConfirmAllSuggestions(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: ConfirmSuggestedRequest) =>
      postJson<AccountMappingBatchResult>(`${base(condominiumId)}/budgets/${budgetId}/account-mappings/suggestions/confirmation`, request),
    onSuccess: reload,
    // A 409 means the list changed: reload it so the Admin sees the new count
    onError: reload,
  });
}

export function useAccountMappingSheet(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (file: File) => {
      const body = new FormData();
      body.append("file", file);
      return postForm<AccountMappingSheetResult>(`${base(condominiumId)}/budgets/${budgetId}/account-mappings/sheet`, body);
    },
    onSuccess: reload,
  });
}

/** Manager and Admin only (the backend answers 403 for the User). */
export function useReallocate(condominiumId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: ReallocationRequest) => postJson<Reallocation>(`${base(condominiumId)}/reallocations`, request),
    onSuccess: reload,
  });
}

/** Undoing does not delete: the reallocation stays recorded as undone and the amount goes back to "a realocar". */
export function useUndoReallocation(condominiumId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (reallocationId: string) => deleteJson<Reallocation>(`${base(condominiumId)}/reallocations/${reallocationId}`),
    onSuccess: reload,
  });
}

/** Admin only: changes the link between the 1.9 lines and the funds after confirmation (full list). */
export function useChangeBudgetFunds(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (funds: { lineId: string; fundId: string | null }[]) =>
      putJson<BudgetDetail>(`${base(condominiumId)}/budgets/${budgetId}/funds`, { funds: funds.map((f) => ({ lineId: f.lineId, fundId: f.fundId })) }),
    onSuccess: reload,
  });
}
