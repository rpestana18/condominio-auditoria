import type { BalanceBridge, BridgeMonth } from "../../api/types";
import { formatDifference, formatMoneyOrDash, formatMonthShort } from "../../format";
import { MonthStatusNote } from "../budgetTracking/MonthStatusNote";
import { isProvisionalItem, provisionalTitle } from "./bridgeSteps";

/**
 * The bridge month by month (one column per month), to show when each part of the balance was formed (RF-13.6).
 * The month in progress (Q50) only has opening balance, inflows, outflows, variation and balance up to the date:
 * its items come with a null amount and show "—" until the month closes.
 */
export function BridgeMonthsTable({ bridge }: { bridge: BalanceBridge }) {
  const { months, items } = bridge;
  if (months.length === 0) return null;
  const amount = (value: number | null | undefined) => (value == null ? "—" : formatDifference(value));
  const partialClass = (m: BridgeMonth) => (m.partialUntil ? "number cell-partial" : "number");
  const anyPartial = months.some((m) => m.partialUntil);
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
                <td key={m.month} className={partialClass(m)}>
                  {formatMoneyOrDash(m.openingBalance)}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="sticky-column">
                Entradas
              </th>
              {months.map((m) => (
                <td key={m.month} className={partialClass(m)}>
                  {formatMoneyOrDash(m.inflows)}
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="sticky-column">
                Saídas
              </th>
              {months.map((m) => (
                <td key={m.month} className={partialClass(m)}>
                  {formatMoneyOrDash(m.outflows)}
                </td>
              ))}
            </tr>
            {items.map((item) => (
              <tr key={item.code} className={item.code === "UNEXPLAINED" && bridge.closes === false ? "bridge-unexplained" : undefined}>
                <th scope="row" className="sticky-column">
                  {item.label}
                  {isProvisionalItem(bridge, item.code) && (
                    <span className="badge provisional" title={provisionalTitle(bridge)}>
                      provisório
                    </span>
                  )}
                </th>
                {months.map((m) => (
                  <td
                    key={m.month}
                    className={partialClass(m)}
                    title={m.partialUntil ? "Mês em andamento: as parcelas ficam em branco até o mês fechar" : undefined}
                  >
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
                <td key={m.month} className={partialClass(m)}>
                  <strong>{amount(m.variation)}</strong>
                </td>
              ))}
            </tr>
            <tr>
              <th scope="row" className="sticky-column">
                Saldo final
              </th>
              {months.map((m) => (
                <td key={m.month} className={partialClass(m)}>
                  {formatMoneyOrDash(m.closingBalance)}
                  {m.partialUntil && <small className="month-note">saldo até a data</small>}
                </td>
              ))}
            </tr>
          </tbody>
        </table>
      </div>
      {anyPartial && (
        <p className="muted">
          No mês em andamento aparecem só as entradas, as saídas e os saldos até a data; as parcelas ficam em branco (“—”) até o mês
          fechar, e o mês fica fora do total.
        </p>
      )}
    </section>
  );
}
