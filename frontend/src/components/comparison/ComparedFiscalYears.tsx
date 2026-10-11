import type { FiscalYear, ComparedFiscalYear } from "../../api/types";
import { formatMonth } from "../../format";

interface Props {
  /** Fiscal years the API used, from the most recent to the oldest. */
  compared: ComparedFiscalYear[];
  /** List from GET /fiscal-years: tells whether a budget replaced the printed column (which stays only as a check). */
  fiscalYears: FiscalYear[];
  /** Opens the check of the "Orçado anterior" column printed on budget `budgetId`. */
  onCheckColumn: (budgetId: string) => void;
}

const columnPrefix = "column:";

/**
 * Budget that printed the "Orçado anterior" column of a fiscal year, when there is a check to show (RF-11.5):
 * on the printed column, the item's own budget; on a budget that replaced the column, the one in the id "column:<budget uuid>".
 */
function columnBudget(compared: ComparedFiscalYear, fiscalYears: FiscalYear[]): string | null {
  if (compared.type === "PRINTED_COLUMN") return compared.budgetId;
  const replaced = fiscalYears.find((f) => f.type === compared.type && f.budgetId === compared.budgetId)?.printedColumn;
  return replaced?.startsWith(columnPrefix) ? replaced.slice(columnPrefix.length) : null;
}

/** Cards with the compared fiscal years: period, summed months and access to the printed column check. */
export function ComparedFiscalYears({ compared, fiscalYears, onCheckColumn }: Props) {
  return (
    <div className="cards">
      {compared.map((c) => {
        const budgetOfColumn = columnBudget(c, fiscalYears);
        return (
          <div key={c.id} className="number-card">
            <span className="card-title">
              {c.type === "PRINTED_COLUMN" ? "PO anterior pela coluna impressa" : c.version ? `PO versão ${c.version}` : "PO"}
            </span>
            <strong className="fund-name">{c.label}</strong>
            <span className="muted">
              {formatMonth(c.start)} a {formatMonth(c.end)}
            </span>
            <span className="muted">
              {c.type === "PRINTED_COLUMN"
                ? "Só previsto, sem realizado"
                : `Meses somados: ${c.months.map(formatMonth).join(", ") || "nenhum"}`}
            </span>
            {budgetOfColumn && (
              <button type="button" className="link-button" onClick={() => onCheckColumn(budgetOfColumn)}>
                {c.type === "PRINTED_COLUMN" ? "Conferir a coluna impressa" : "Ver a coluna impressa (conferência)"}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
