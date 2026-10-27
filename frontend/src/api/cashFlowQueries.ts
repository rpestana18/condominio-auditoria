// Queries and actions of ADR 0009: "Entradas e saídas" (RF-13.11), line review and origin of each line (RF-13.12,
// RF-13.13) and the correction of the end of the fiscal year (RF-13.17). Every number comes ready from the backend.
import { useMutation, useQuery } from "@tanstack/react-query";
import { getJson, putJson } from "./client";
import { base, queryString, useReloadBudget } from "./budgetQueries";
import type {
  BudgetDetail,
  BudgetLineOriginRequest,
  BudgetLineReview,
  BudgetLineReviewEvent,
  CashFlowEntries,
  CashFlowMonthly,
  CashFlowRowCode,
  FiscalYearEndRequest,
  LineReviewBatchRequest,
  LineReviewLine,
} from "./types";

// ---------- "Entradas e saídas" (RF-13.11) ----------

/** Empty = the operating fund (API default), "all" = every fund, or a fund id. */
export type CashFlowFund = string | null;

export function useCashFlowMonthly(condominiumId: string, fund: CashFlowFund, from: string | null = null, to: string | null = null) {
  return useQuery({
    queryKey: ["cash-flow-monthly", condominiumId, fund ?? "operating", from ?? "", to ?? ""],
    queryFn: () => getJson<CashFlowMonthly>(`${base(condominiumId)}/cash-flow/monthly${queryString({ fund, from, to })}`),
  });
}

export interface CashFlowEntryFilter {
  fund: CashFlowFund;
  month: string | null;
  line: string | null;
  block: CashFlowRowCode | null;
  account: string | null;
}

/** The entries behind a cell of the table (or the whole month), each one with its destination. */
export function useCashFlowEntries(condominiumId: string, filter: CashFlowEntryFilter | null) {
  return useQuery({
    queryKey: ["cash-flow-entries", condominiumId, filter],
    queryFn: () =>
      getJson<CashFlowEntries>(
        `${base(condominiumId)}/cash-flow/entries${queryString({
          fund: filter?.fund ?? null,
          month: filter?.month ?? null,
          line: filter?.line ?? null,
          block: filter?.block ?? null,
          account: filter?.account ?? null,
        })}`,
      ),
    enabled: filter !== null,
  });
}

// ---------- Line review and origin (RF-13.12, RF-13.13) ----------

export function useLineReview(condominiumId: string, budgetId: string | undefined) {
  return useQuery({
    queryKey: ["line-review", condominiumId, budgetId],
    queryFn: () => getJson<BudgetLineReview>(`${base(condominiumId)}/budgets/${budgetId}/line-review`),
    enabled: !!budgetId,
  });
}

export function useLineReviewEvents(condominiumId: string, budgetId: string | undefined, active: boolean) {
  return useQuery({
    queryKey: ["line-review-events", condominiumId, budgetId],
    queryFn: async () => (await getJson<BudgetLineReviewEvent[]>(`${base(condominiumId)}/budgets/${budgetId}/line-review/events`)) ?? [],
    enabled: !!budgetId && active,
  });
}

/** Admin only (403 for the others). Every screen that distributes spending by line reloads. */
export function useReviewLines(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: LineReviewBatchRequest) =>
      putJson<BudgetLineReview>(`${base(condominiumId)}/budgets/${budgetId}/line-review`, request),
    onSuccess: reload,
  });
}

export function useSaveLineOrigin(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: ({ lineId, request }: { lineId: string; request: BudgetLineOriginRequest }) =>
      putJson<LineReviewLine>(`${base(condominiumId)}/budgets/${budgetId}/lines/${lineId}/origin`, request),
    onSuccess: reload,
  });
}

// ---------- End of the fiscal year (RF-13.17) ----------

export function useCorrectFiscalYearEnd(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: FiscalYearEndRequest) =>
      putJson<BudgetDetail>(`${base(condominiumId)}/budgets/${budgetId}/fiscal-year-end`, request),
    onSuccess: reload,
  });
}
