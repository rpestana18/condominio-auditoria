import type { FiscalYear, FiscalYearMonth } from "../../api/types";
import { formatMonth } from "../../format";

/** Months of one status, already in the screen format ("09/2026, 10/2026"); empty when there are none. */
const list = (months: FiscalYearMonth[]) => months.map((m) => formatMonth(m.month)).join(", ");

/**
 * Status of the chosen fiscal year (RF-11.4 and RF-11.3): months with and without a cash flow, extended months,
 * account mapping status and warnings. Everything comes from GET /fiscal-years; the screen only splits the list for display.
 */
export function FiscalYearSummary({ fiscalYear }: { fiscalYear: FiscalYear }) {
  const ownMonths = fiscalYear.months.filter((m) => !m.extended);
  const extended = fiscalYear.months.filter((m) => m.extended);
  const withCashFlow = list(ownMonths.filter((m) => m.status === "WITH_CASH_FLOW"));
  const withoutCashFlow = list(ownMonths.filter((m) => m.status === "NO_CASH_FLOW"));
  const twoCashFlows = list(ownMonths.filter((m) => m.status === "TWO_CASH_FLOWS"));
  const { mapping, extension } = fiscalYear;

  return (
    <section className="block" aria-label={`Exercício ${fiscalYear.label}`}>
      <header className="block-title">
        <h2>
          Exercício {fiscalYear.label} · {formatMonth(fiscalYear.start)} a {formatMonth(fiscalYear.end)}
        </h2>
        {mapping && (
          <span className={mapping.confirmed === mapping.accounts ? "badge ok" : "badge warning"}>
            De-para: {mapping.confirmed} de {mapping.accounts} contas confirmadas
          </span>
        )}
      </header>
      <dl className="number-list">
        <dt>Com fluxo</dt>
        <dd>{withCashFlow || "nenhum mês"}</dd>
        <dt>Sem fluxo carregado</dt>
        <dd>{withoutCashFlow || "nenhum mês"}</dd>
        {twoCashFlows && (
          <>
            <dt>Com dois fluxos</dt>
            <dd>{twoCashFlows}</dd>
          </>
        )}
        {extended.length > 0 && (
          <>
            <dt>Prorrogados</dt>
            <dd>
              {extended.map((m) => (
                <span key={m.month} className="badge neutral">
                  {formatMonth(m.month)} · prorrogado
                </span>
              ))}{" "}
              <span className="muted">fora do acumulado do exercício</span>
            </dd>
          </>
        )}
        {extension && (
          <>
            <dt>Prorrogação</dt>
            <dd>
              até {formatMonth(extension.until)}, por {extension.by}: “{extension.justification}”
            </dd>
          </>
        )}
      </dl>
      {fiscalYear.warnings.map((w, i) => (
        <p key={i} className="notice warning">
          {w}
        </p>
      ))}
    </section>
  );
}
