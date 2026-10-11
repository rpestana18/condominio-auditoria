import type { FiscalYear, Fund, BudgetVsActualMonth, BudgetSummary } from "../../api/types";
import { formatMonth } from "../../format";
import { FiscalYearSelector } from "./FiscalYearSelector";
import { FundSelector } from "./FundSelector";

interface Props {
  /** List from GET /fiscal-years (from the most recent to the oldest). Only the PO-type ones are used here. */
  fiscalYears: FiscalYear[];
  /** Every budget read: only used to name a version that is not in the fiscal year list (e.g. link from the "PO" screen). */
  budgets: BudgetSummary[];
  /** Budget of the fiscal year shown: the one in the address or, without it, the one the backend used (current fiscal year). */
  budgetId: string | null;
  onChangeBudget: (id: string) => void;
  period: string;
  /** The months of the fiscal year, from the cumulative view (with each one's status and the extended mark). */
  months: BudgetVsActualMonth[];
  onChangePeriod: (period: string) => void;
  /** Cash flow funds (GET /funds). The filter goes to the API; the screen does not split anything by itself. */
  funds: Fund[];
  fundId: string | null;
  onChangeFund: (fundId: string | null) => void;
}

const monthStatus: Record<BudgetVsActualMonth["status"], string> = {
  WITH_CASH_FLOW: "",
  NO_CASH_FLOW: " (sem fluxo carregado)",
  TWO_CASH_FLOWS: " (dois fluxos)",
};

/** Filters of RF-03.1.13 and RF-11.4: fiscal year, month or cumulative, and fund. */
export function BudgetVsActualFilters(props: Props) {
  const { fiscalYears, budgets, budgetId, onChangeBudget, period, months, onChangePeriod, funds, fundId, onChangeFund } = props;
  return (
    <div className="filters">
      <FiscalYearSelector fiscalYears={fiscalYears} budgets={budgets} budgetId={budgetId} onChange={onChangeBudget} />
      <label>
        Período
        <select value={period} onChange={(e) => onChangePeriod(e.target.value)}>
          <option value="cumulative">Acumulado do exercício</option>
          {months.map((m) => (
            <option key={m.month} value={m.month}>
              {formatMonth(m.month)}
              {monthStatus[m.status]}
              {m.extended ? " · prorrogado" : ""}
            </option>
          ))}
        </select>
      </label>
      <FundSelector funds={funds} fundId={fundId} onChange={onChangeFund} />
    </div>
  );
}
