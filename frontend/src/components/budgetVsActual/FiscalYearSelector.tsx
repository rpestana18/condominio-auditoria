import type { FiscalYear, BudgetSummary } from "../../api/types";
import { formatMonth } from "../../format";
import { budgetStatusLabel } from "./labels";

interface Props {
  /** List from GET /fiscal-years (from the most recent to the oldest). Only the PO-type ones are used here. */
  fiscalYears: FiscalYear[];
  /** Every budget read: only used to name a version that is not in the fiscal year list (e.g. link from the "PO" screen). */
  budgets?: BudgetSummary[];
  /** Budget of the fiscal year shown: the one in the address or, without it, the one the backend used (current fiscal year). */
  budgetId: string | null;
  onChange: (budgetId: string) => void;
}

/**
 * "Exercício" filter (RF-11.4), used in budget vs actual and in "Indicadores". The value is the fiscal year's
 * `po`, which goes to the API as is. The printed column has no actuals nor a fiscal year of its own:
 * it only shows up in "Comparar exercícios".
 */
export function FiscalYearSelector({ fiscalYears, budgets = [], budgetId, onChange }: Props) {
  const withBudget = fiscalYears.filter((f) => f.type === "PO");
  // A budget version that is not in the list (e.g. not confirmed yet) can still be opened through the link
  const outsideList = budgetId && !withBudget.some((f) => f.budgetId === budgetId) ? budgets.find((b) => b.id === budgetId) : undefined;

  return (
    <label>
      Exercício
      <select value={budgetId ?? ""} onChange={(e) => onChange(e.target.value)} disabled={withBudget.length === 0 && !outsideList}>
        {withBudget.length === 0 && !outsideList && <option value="">Nenhum exercício confirmado</option>}
        {withBudget.map((f) => (
          <option key={f.id} value={f.budgetId}>
            {f.label}
            {f.version ? ` · versão ${f.version}` : ""}
            {f.extension ? ` · prorrogada até ${formatMonth(f.extension.until)}` : ""}
          </option>
        ))}
        {outsideList && (
          <option value={outsideList.id}>
            {outsideList.printedFiscalYear ?? outsideList.fileName} · {budgetStatusLabel[outsideList.status]}
          </option>
        )}
      </select>
    </label>
  );
}
