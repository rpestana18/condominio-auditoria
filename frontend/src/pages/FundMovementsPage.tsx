import { useState } from "react";
import { useFundMovements } from "../api/budgetTrackingQueries";
import type { FundMovements } from "../api/types";
import { monthStatusText } from "../components/budgetTracking/MonthStatusNote";
import { FundHistory } from "../components/fundMovements/FundHistory";
import { FundMonthTable } from "../components/fundMovements/FundMonthTable";
import { FundTotals } from "../components/fundMovements/FundTotals";
import { PartialMonthsSection } from "../components/fundMovements/PartialMonthsSection";
import { useSession } from "../context";
import { formatMonth } from "../format";

/**
 * "Fundos" (RF-13.5), for every role: every fund that appears in some cash flow, month by month. The period is the
 * API default (the fiscal year); the screen only picks which month to show on top. Also lists the partial months (RF-13.10).
 */
export function FundMovementsPage() {
  const { condominiumId } = useSession();
  const query = useFundMovements(condominiumId, null, null);
  return (
    <>
      <header className="page-title">
        <h1>Fundos</h1>
        {query.data && (
          <span className="badge neutral">
            {formatMonth(query.data.from)} a {formatMonth(query.data.to)}
          </span>
        )}
      </header>
      {query.isLoading && <p className="notice">Carregando…</p>}
      {query.error && <p className="notice error">{query.error.message}</p>}
      {query.data && <Content movements={query.data} />}
      <PartialMonthsSection />
    </>
  );
}

function Content({ movements }: { movements: FundMovements }) {
  const withNumbers = movements.months.filter((m) => m.status === "WITH_CASH_FLOW");
  const [month, setMonth] = useState<string | null>(withNumbers.at(-1)?.month ?? null);
  const [fundId, setFundId] = useState<string | null>(null);
  const summary = movements.months.find((m) => m.month === month);
  // The operating fund first; the others in the API order
  const funds = [...movements.funds.filter((f) => f.operatingFund), ...movements.funds.filter((f) => !f.operatingFund)];
  const fund = funds.find((f) => f.fundId === fundId);

  if (movements.funds.length === 0) return <p className="notice">Nenhum extrato lido no período.</p>;
  return (
    <>
      {movements.warnings.map((w, i) => (
        <p key={`${w.code}-${i}`} className="notice warning">
          {w.text}
        </p>
      ))}
      <div className="filters">
        <label>
          Mês
          <select value={month ?? ""} onChange={(e) => setMonth(e.target.value)}>
            {movements.months.map((m) => {
              const note = monthStatusText(m);
              return (
                <option key={m.month} value={m.month} disabled={m.status === "NO_CASH_FLOW"}>
                  {formatMonth(m.month)}
                  {note ? ` · ${note}` : ""}
                </option>
              );
            })}
          </select>
        </label>
      </div>
      {summary && month ? (
        <>
          <FundTotals summary={summary} funds={funds} />
          <section className="block">
            <div className="block-title">
              <h2>Movimento de cada fundo em {formatMonth(month)}</h2>
              <span className="muted">Clique em um fundo para ver o mês a mês</span>
            </div>
            <FundMonthTable funds={funds} month={month} selectedFundId={fundId} onSelectFund={setFundId} />
            <p className="muted">
              Créditos e débitos divididos pelas marcações do sistema (recibos de cota, transferências entre fundos, de-para). O que não
              tem marcação aparece em “outros créditos” ou “outros débitos”.
            </p>
          </section>
        </>
      ) : (
        <p className="notice">Nenhum mês com extrato carregado no período.</p>
      )}
      {fund && <FundHistory fund={fund} months={movements.months} onClose={() => setFundId(null)} />}
      <p className="muted">Cálculo: versão {movements.calculationVersion}.</p>
    </>
  );
}
