// Queries and actions of the reformulated "Análise da PO" (RF-13, ADR 0007): budget tracking grid, balance bridge,
// fund movements, spending profile of each line and partial month. Every number comes ready from the backend;
// here we only fetch and send.
import { useMutation, useQuery } from "@tanstack/react-query";
import { getJson, putJson } from "./client";
import { base, queryString, useReloadBudget } from "./budgetQueries";
import type {
  BalanceBridge,
  BudgetTracking,
  FundMovements,
  PartialMonth,
  PartialMonthEvent,
  PartialMonthRequest,
  SpendingProfileBatchRequest,
  SpendingProfileEvent,
  SpendingProfiles,
} from "./types";

// ---------- Budget tracking grid (RF-13.4) ----------

/** Lines × months of the fiscal year of budget `budgetId` (empty = the current one). */
export function useBudgetTracking(condominiumId: string, budgetId: string | null) {
  return useQuery({
    queryKey: ["budget-tracking", condominiumId, budgetId ?? "current"],
    queryFn: () => getJson<BudgetTracking>(`${base(condominiumId)}/budget-tracking${queryString({ budget: budgetId })}`),
  });
}

// ---------- Balance bridge (RF-13.6 and RF-13.7) ----------

/** Value of the fund filter: empty = the operating fund (API default), "all" = every fund, or a fund id. */
export type BridgeFund = string | null;

export interface BridgeFilter {
  fund: BridgeFund;
  /** "AAAA-MM"; both empty = the API picks the fiscal year up to the last month loaded. */
  from: string | null;
  to: string | null;
}

export function useBalanceBridge(condominiumId: string, filter: BridgeFilter, enabled = true) {
  const { fund, from, to } = filter;
  return useQuery({
    queryKey: ["balance-bridge", condominiumId, fund ?? "operating", from ?? "", to ?? ""],
    queryFn: () => getJson<BalanceBridge>(`${base(condominiumId)}/balance-bridge${queryString({ fund, from, to })}`),
    enabled,
  });
}

// ---------- Fund movements (RF-13.5) ----------

export function useFundMovements(condominiumId: string, from: string | null, to: string | null, enabled = true) {
  return useQuery({
    queryKey: ["fund-movements", condominiumId, from ?? "", to ?? ""],
    queryFn: () => getJson<FundMovements>(`${base(condominiumId)}/funds/movements${queryString({ from, to })}`),
    enabled,
  });
}

// ---------- Spending profile of each line (RF-13.3) ----------

export function useSpendingProfiles(condominiumId: string, budgetId: string | undefined) {
  return useQuery({
    queryKey: ["spending-profiles", condominiumId, budgetId],
    queryFn: () => getJson<SpendingProfiles>(`${base(condominiumId)}/budgets/${budgetId}/spending-profiles`),
    enabled: !!budgetId,
  });
}

export function useSpendingProfileEvents(condominiumId: string, budgetId: string | undefined) {
  return useQuery({
    queryKey: ["spending-profile-events", condominiumId, budgetId],
    queryFn: async () =>
      (await getJson<SpendingProfileEvent[]>(`${base(condominiumId)}/budgets/${budgetId}/spending-profiles/events`)) ?? [],
    enabled: !!budgetId,
  });
}

/** Admin only (the backend answers 403 for Manager and User). Profile null = confirms the suggestion. */
export function useConfirmSpendingProfiles(condominiumId: string, budgetId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: (request: SpendingProfileBatchRequest) =>
      putJson<SpendingProfiles>(`${base(condominiumId)}/budgets/${budgetId}/spending-profiles`, request),
    onSuccess: reload,
  });
}

// ---------- Partial month (RF-13.10) ----------

/** Cash flows read and whether each one is a partial month. */
export function usePartialMonths(condominiumId: string) {
  return useQuery({
    queryKey: ["partial-months", condominiumId],
    queryFn: async () => (await getJson<PartialMonth[]>(`${base(condominiumId)}/partial-months`)) ?? [],
  });
}

export function usePartialMonthEvents(condominiumId: string, fileId: string, active: boolean) {
  return useQuery({
    queryKey: ["partial-month-events", condominiumId, fileId],
    queryFn: async () => (await getJson<PartialMonthEvent[]>(`${base(condominiumId)}/files/${fileId}/partial-month/events`)) ?? [],
    enabled: active,
  });
}

/** Manager and Admin only. partial true/false marks by hand; null goes back to the automatic rule. Everything reloads. */
export function useMarkPartialMonth(condominiumId: string) {
  const reload = useReloadBudget();
  return useMutation({
    mutationFn: ({ fileId, request }: { fileId: string; request: PartialMonthRequest }) =>
      putJson<PartialMonth>(`${base(condominiumId)}/files/${fileId}/partial-month`, request),
    onSuccess: reload,
  });
}
