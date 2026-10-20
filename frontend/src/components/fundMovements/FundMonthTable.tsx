import type { FundMonthMovement, FundMovement } from "../../api/types";
import { formatMoney, formatMoneyOrDash } from "../../format";

interface Props {
  funds: FundMovement[];
  month: string;
  selectedFundId: string | null;
  onSelectFund: (fundId: string) => void;
}

/** Every fund in the chosen month (RF-13.5): credits and debits split as the API classified them, nothing hidden. */
export function FundMonthTable({ funds, month, selectedFundId, onSelectFund }: Props) {
  return (
    <div className="scroll">
      <table className="table compact clickable fund-table">
        <thead>
          <tr>
            <th className="sticky-column">Fundo</th>
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
          {funds.map((fund) => {
            const movement = fund.months.find((m) => m.month === month);
            return (
              <tr
                key={fund.fundId}
                className={fund.fundId === selectedFundId ? "selected" : undefined}
                onClick={() => onSelectFund(fund.fundId)}
              >
                <td className="sticky-column">
                  <FundName fund={fund} />
                </td>
                {movement?.present ? <MovementCells movement={movement} /> : <AbsentCells />}
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

export function FundName({ fund }: { fund: FundMovement }) {
  return (
    <>
      {fund.name}
      {fund.operatingFund && <span className="badge neutral">fundo ordinário</span>}
      {fund.budgetLineCode && <span className="badge neutral">linha {fund.budgetLineCode}</span>}
    </>
  );
}

function AbsentCells() {
  return (
    <td colSpan={14} className="muted">
      não aparece no extrato deste mês
    </td>
  );
}

/** The 14 number columns of one fund in one month. Exported for the month-by-month history of one fund. */
export function MovementCells({ movement: m }: { movement: FundMonthMovement }) {
  return (
    <>
      <td className="number">{formatMoneyOrDash(m.openingBalance)}</td>
      <td className="number">{formatMoneyOrDash(m.condoFee)}</td>
      <td className="number">{formatMoneyOrDash(m.otherIncome)}</td>
      <td className="number">{formatMoneyOrDash(m.transfersIn)}</td>
      <td className="number">{formatMoneyOrDash(m.otherCredits)}</td>
      <td className="number">
        <strong>{formatMoneyOrDash(m.credits)}</strong>
      </td>
      <td className="number">{formatMoneyOrDash(m.expenses)}</td>
      <td className="number">{formatMoneyOrDash(m.transfersOut)}</td>
      <td className="number">{formatMoneyOrDash(m.otherDebits)}</td>
      <td className="number">
        <strong>{formatMoneyOrDash(m.debits)}</strong>
      </td>
      <td className={`number ${m.closingBalance != null && m.closingBalance < 0 ? "negative" : ""}`}>
        <strong>{formatMoneyOrDash(m.closingBalance)}</strong>
      </td>
      <td>
        <BalanceCheck movement={m} />
      </td>
      <td className="number">{formatMoneyOrDash(m.planned)}</td>
      <td className="number">{formatMoneyOrDash(m.collected)}</td>
    </>
  );
}

/** Closing balance checked against the printed one (tolerance R$ 0,01, calculated by the API). */
function BalanceCheck({ movement: m }: { movement: FundMonthMovement }) {
  if (m.balanceMatches == null) return <span className="muted">sem saldo impresso</span>;
  if (m.balanceMatches) return <span className="badge ok">confere</span>;
  return (
    <span className="notice warning compact-notice">
      saldo calculado difere do extrato: calculado {formatMoneyOrDash(m.calculatedClosingBalance)}, extrato{" "}
      {m.closingBalance == null ? "—" : formatMoney(m.closingBalance)}
    </span>
  );
}
