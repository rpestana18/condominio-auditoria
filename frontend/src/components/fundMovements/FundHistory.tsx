import type { FundMonthSummary, FundMovement } from "../../api/types";
import { formatMonthShort } from "../../format";
import { MonthStatusNote } from "../budgetTracking/MonthStatusNote";
import { FundName, MovementCells } from "./FundMonthTable";

interface Props {
  fund: FundMovement;
  months: FundMonthSummary[];
  onClose: () => void;
}

/** One fund month by month (RF-13.5): same columns as the month table, one row per month. */
export function FundHistory({ fund, months, onClose }: Props) {
  return (
    <section className="block">
      <div className="block-title">
        <h2>
          <FundName fund={fund} /> · mês a mês
        </h2>
        <button className="link-button" onClick={onClose}>
          Fechar
        </button>
      </div>
      <div className="scroll">
        <table className="table compact fund-table">
          <thead>
            <tr>
              <th className="sticky-column">Mês</th>
              <th className="number">Saldo inicial</th>
              <th className="number">Cota recebida</th>
              <th className="number">Outras receitas</th>
              <th className="number">Transf. recebidas</th>
              <th className="number">Outros créditos</th>
              <th className="number">Créditos</th>
              <th className="number">Despesas</th>
              <th className="number">Transf. enviadas</th>
              <th className="number">Outros débitos</th>
              <th className="number">Débitos</th>
              <th className="number">Saldo final</th>
              <th>Conferência com o extrato</th>
              <th className="number">Previsto</th>
              <th className="number">Arrecadado</th>
            </tr>
          </thead>
          <tbody>
            {months.map((summary) => {
              const movement = fund.months.find((m) => m.month === summary.month);
              return (
                <tr key={summary.month} className={summary.partialUntil ? "cell-partial" : undefined}>
                  <th scope="row" className="sticky-column">
                    {formatMonthShort(summary.month)}
                    <MonthStatusNote status={summary.status} partialUntil={summary.partialUntil} />
                  </th>
                  {movement?.present ? (
                    <MovementCells movement={movement} />
                  ) : (
                    <td colSpan={14} className="muted">
                      {summary.status === "NO_CASH_FLOW" ? "sem extrato carregado" : "não aparece no extrato deste mês"}
                    </td>
                  )}
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
    </section>
  );
}
