import type { BalanceBridge } from "../../api/types";
import { formatDifference, formatMoneyOrDash, formatMonthShort } from "../../format";
import { MonthStatusNote } from "../budgetTracking/MonthStatusNote";

/** The bridge month by month (one column per month), to show when each part of the balance was formed (RF-13.6). */
export function BridgeMonthsTable({ bridge }: { bridge: BalanceBridge }) {
  const { months, items } = bridge;
  if (months.length === 0) return null;
  const amount = (value: number | null | undefined) => (value == null ? "—" : formatDifference(value));
  return (
    <section className="block">
      <h2>Mês a mês</h2>
      <div className="scroll">
        <table className="table compact tracking-grid">
          <thead>
            <tr>
              <th className="sticky-column">Parcela</th>
              {months.map((m) => (
                <th key={m.month} className={`number month-column ${m.partialUntil ? "cell-partial" : ""}`}>
                  {formatMonthShort(m.month)}
                  <MonthStatusNote status={m.status} partialUntil={m.partialUntil} />
                  {!m.inTotal && m.status === "WITH_CASH_FLOW" && <span className="month-note">fora do total</span>}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            <tr>
              <th scope="row" className="sticky-column">
                Saldo inicial
              </th>
              {months.map((m) => (
                <td key={m.month} className="number">
                  {formatMoneyOrDash(m.openingBalance)}
                </td>
              ))}
            </tr>
            {items.map((item) => (
              <tr key={item.code} className={item.code === "UNEXPLAINED" && bridge.closes === false ? "bridge-unexplained" : undefined}>
                <th scope="row" className="sticky-column">
                  {item.label}
                </th>
                {months.map((m) => (
                  <td key={m.month} className="number">
                    {amount(m.items.find((i) => i.code === item.code)?.amount)}
                  </td>
                ))}
              </tr>
            ))}
            <tr>
              <th scope="row" className="sticky-column">
                Variação do mês
              </th>
              {months.map((m) => (
                <td key={m.month} className="number">
                  <strong>{amount(m.variation)}</strong>
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="sticky-column">
                Saldo final
              </th>
              {months.map((m) => (
                <td key={m.month} className="number">
                  {formatMoneyOrDash(m.closingBalance)}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  );
}
