// Queries and actions of the "Análise da PO" (ADR 0005): fiscal years, comparison between fiscal years,
// printed column check and budget items. Every number comes ready from the backend; here we only fetch and send.
import { useMutation, useQuery } from "@tanstack/react-query";
import { putJson, postJson, getJson } from "./client";
import { base, queryString, useReloadBudget } from "./budgetQueries";
import type {
  FiscalYearComparison,
  PrintedColumnCheck,
  BudgetItemEvent,
  FiscalYear,
  BudgetItemFilter,
  Indicators,
  BudgetLineItem,
  BudgetItemBatchRequest,
  LineBudgetItemRequest,
  BudgetItemBatchResult,
  BudgetItemSuggestionsResult,
  BudgetItem,
  BudgetItemList,
} from "./types";

// ---------- Fiscal years (RF-11.4 and RF-11.5) ----------

/** Fiscal years from the most recent to the oldest: one per confirmed budget and, when applicable, the printed column. */
export function useFiscalYears(condominiumId: string) {
  return useQuery({
    queryKey: ["fiscal-years", condominiumId],
    queryFn: async () => (await getJson<FiscalYear[]>(`${base(condominiumId)}/fiscal-years`)) ?? [],
  });
}

/**
 * Whether the condominium already has a confirmed budget (a "PO" fiscal year in the list). Undefined while the list
 * loads. The screens that work on a fiscal year only ask the API with a confirmed budget: without one, the API refuses
 * the default period (400/404) and the screen shows the empty state instead. If the list fails, returns true and the
 * screen's own query shows the error.
 */
export function useHasConfirmedBudget(condominiumId: string): boolean | undefined {
  const { data, isSuccess, isError } = useFiscalYears(condominiumId);
  if (isError) return true;
  return isSuccess ? data.some((f) => f.type === "PO") : undefined;
}

// ---------- Compare fiscal years (RF-11.6) ----------

export interface ComparisonFilter {
  /** Ids from the fiscal year list ("budget:<uuid>" or "column:<uuid>"). Null = the backend picks the two most recent. */
  fiscalYears: string[] | null;
  fundId: string | null;
  sameMonths: boolean;
}

export function useFiscalYearComparison(condominiumId: string, filter: ComparisonFilter, enabled = true) {
  const { fiscalYears, fundId, sameMonths } = filter;
  return useQuery({
    queryKey: ["fiscal-year-comparison", condominiumId, fiscalYears?.join(",") ?? "default", fundId ?? "all", sameMonths],
    queryFn: () =>
      getJson<FiscalYearComparison>(
        `${base(condominiumId)}/fiscal-year-comparison${queryString({
          // The contract asks for a comma-separated list (style: form, explode: false)
          fiscalYears: fiscalYears?.join(","),
          fund: fundId,
          sameMonths: sameMonths ? "true" : null,
        })}`,
      ),
    enabled,
  });
}

/** Check of the "Orçado anterior" column printed on budget `budgetId` (RF-11.5). Only fetches when `budgetId` exists. */
export function usePrintedColumn(condominiumId: string, budgetId: string | null) {
  return useQuery({
    queryKey: ["printed-column", condominiumId, budgetId],
    queryFn: () => getJson<PrintedColumnCheck>(`${base(condominiumId)}/budgets/${budgetId}/printed-column`),
    enabled: !!budgetId,
  });
}

// ---------- Indicators (RF-11.10 to RF-11.13) ----------

/**
 * Series of the 7 charts for the fiscal year of budget `budgetId` (empty = the most recent). With the Condomínio fund,
 * the funds series comes null; with another fund, series 1 to 5 come null. Nothing is calculated here.
 */
export function useIndicators(condominiumId: string, budgetId: string | null, fundId: string | null, enabled = true) {
  return useQuery({
    queryKey: ["indicators", condominiumId, budgetId ?? "current", fundId ?? "all"],
    queryFn: () => getJson<Indicators>(`${base(condominiumId)}/indicators${queryString({ budget: budgetId, fund: fundId })}`),
    enabled,
  });
}

// ---------- Budget items (RF-11.7) ----------

/** The condominium's budget item catalog, ordered by name. */
export function useBudgetItems(condominiumId: string, active = true) {
  return useQuery({
    queryKey: ["budget-items", condominiumId],
    queryFn: async () => (await getJson<BudgetItem[]>(`${base(condominiumId)}/budget-items`)) ?? [],
    enabled: active,
  });
}

export function useBudgetLineItems(condominiumId: string, budgetId: string | undefined, filter: BudgetItemFilter) {
  return useQuery({
    queryKey: ["budget-line-items", condominiumId, budgetId, filter],
    queryFn: () =>
      getJson<BudgetItemList>(
        `${base(condominiumId)}/budgets/${budgetId}/budget-items${queryString({ filter: filter === "ALL" ? null : filter })}`,
      ),
    enabled: !!budgetId,
  });
}

export function useBudgetItemEvents(condominiumId: string, budgetId: string, active: boolean) {
  return useQuery({
    queryKey: ["budget-item-events", condominiumId, budgetId],
    queryFn: async () => (await getJson<BudgetItemEvent[]>(`${base(condominiumId)}/budgets/${budgetId}/budget-items/events`)) ?? [],
    enabled: active,
  });
}

/** Admin only (the backend answers 403 for Manager and User). A catalog budget item or a new one, never both. */
export function useSetBudgetItem(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: ({ lineId, request }: { lineId: string; request: LineBudgetItemRequest }) =>
      putJson<BudgetLineItem>(`${base(condominiumId)}/budgets/${budgetId}/budget-items/${lineId}`, request),
    onSuccess: reload,
  });
}

/** Admin only: confirms or rejects several lines; the backend records one event per line. */
export function useBudgetItemBatch(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: BudgetItemBatchRequest) =>
      postJson<BudgetItemBatchResult>(`${base(condominiumId)}/budgets/${budgetId}/budget-items/batch`, request),
    onSuccess: reload,
  });
}

/** Admin only: asks for the suggestions (same budget account and same group), without AI. Everything comes in as suggested. */
export function useSuggestBudgetItems(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: () => postJson<BudgetItemSuggestionsResult>(`${base(condominiumId)}/budgets/${budgetId}/budget-items/suggestions`),
    onSuccess: reload,
  });
}
