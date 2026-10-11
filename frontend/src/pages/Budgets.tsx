import { useNavigate } from "react-router";
import { useBudgets } from "../api/budgetQueries";
import { budgetStatusLabel } from "../components/budgetVsActual/labels";
import { useSession } from "../context";
import { formatDateTime, formatMonth, formatMoney } from "../format";

/** The condominium's budgets read, from the most recent to the oldest (as they come from the API). */
export function Budgets() {
  const { condominiumId } = useSession();
  const navigate = useNavigate();
  const { data: budgets = [], isLoading, error } = useBudgets(condominiumId);

  return (
    <>
      <header className="page-title">
        <h1>Previsões orçamentárias (PO)</h1>
      </header>
      {isLoading && <p className="notice">Carregando…</p>}
      {error && <p className="notice error">{error.message}</p>}
      {!isLoading && budgets.length === 0 && (
        <p className="notice">Nenhuma PO lida ainda. Envie a PO aprovada na categoria PO, em Arquivos.</p>
      )}
      {budgets.length > 0 && (
        <table className="table clickable">
          <thead>
            <tr>
              <th>Arquivo</th>
              <th>Versão</th>
              <th>Exercício</th>
              <th className="number">Previsto do mês</th>
              <th>Situação</th>
              <th>Lida em</th>
            </tr>
          </thead>
          <tbody>
            {budgets.map((b) => (
              <tr key={b.id} onClick={() => navigate(`/previsoes/${b.id}`)}>
                <td>{b.fileName ?? b.title}</td>
                <td>{b.version ?? "—"}</td>
                <td>
                  {b.fiscalYearStart && b.fiscalYearEnd
                    ? `${formatMonth(b.fiscalYearStart)} a ${formatMonth(b.fiscalYearEnd)}`
                    : (b.printedFiscalYear ?? "—")}
                </td>
                <td className="number">{formatMoney(b.monthlyPlanned)}</td>
                <td>
                  <span className={b.status === "CONFIRMED" ? "badge ok" : "badge warning"}>{budgetStatusLabel[b.status]}</span>
                </td>
                <td>{formatDateTime(b.readAt)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </>
  );
}
