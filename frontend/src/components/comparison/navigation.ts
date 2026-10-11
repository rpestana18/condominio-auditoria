import { useNavigate } from "react-router";
import type { ComparedFiscalYear } from "../../api/types";

/** Where to open a number in budget vs actual: fiscal year (budget), period, fund and, when present, the evidence target. */
export interface BudgetVsActualDestination {
  budgetId: string;
  /** "cumulative" or YYYY-MM, as the API expects. */
  period: string;
  /** "line:<id>", "group:<id>", "fund:<id>", "total"... Without a target, the screen opens on the period without the evidence panel. */
  target?: string | null;
  fundId?: string | null;
}

/**
 * Address of budget vs actual with the filters in the format the screen reads (?po=&periodo=&fundo=&alvo=).
 * Used by "Comparar exercícios" and by "Indicadores", so both open the evidence the same way.
 */
export function budgetVsActualAddress({ budgetId, period, target, fundId }: BudgetVsActualDestination): string {
  const parameters = new URLSearchParams({ po: budgetId, periodo: period });
  if (fundId) parameters.set("fundo", fundId);
  if (target) parameters.set("alvo", target);
  return `/previsto-realizado?${parameters.toString()}`;
}

/**
 * Given the fiscal year and the target of a value, returns the action that opens that number in budget vs actual,
 * with the evidence already open (RF-11.6, click). Without a target, or on the printed column (which has no period nor
 * actuals), returns `undefined` and the value shows as plain text.
 */
export type EvidenceOpener = (fiscalYearId: string, target: string | null | undefined) => (() => void) | undefined;

export function useEvidenceOpener(fiscalYears: ComparedFiscalYear[], fundId: string | null): EvidenceOpener {
  const navigate = useNavigate();
  return (fiscalYearId, target) => {
    const fiscalYear = fiscalYears.find((f) => f.id === fiscalYearId);
    const period = fiscalYear?.period;
    if (!fiscalYear || !period || !target) return undefined;
    return () => navigate(budgetVsActualAddress({ budgetId: fiscalYear.budgetId, period, target, fundId }));
  };
}
