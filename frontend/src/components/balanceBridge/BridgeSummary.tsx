import type { BalanceBridge, BalanceBridgeItemCode, CalculationWarning } from "../../api/types";
import { formatDate, formatDifference, formatMoneyOrDash, formatPercentage } from "../../format";
import { MappingWarningNote } from "../MappingWarningNote";
import { bridgePeriodText, partialMonthText } from "./bridgeTexts";

interface Props {
  bridge: BalanceBridge;
  selected: BalanceBridgeItemCode | null;
  onSelect: (code: BalanceBridgeItemCode) => void;
  /** NO_CONFIRMED_MAPPING warning, shown discreetly next to "Economia nas linhas". */
  mappingWarning?: CalculationWarning | null;
}

/**
 * The bridge of RF-13.6: opening balance, closing balance, variation and the items that add up to it, cent by cent
 * (all calculated by the API). "Não explicado" different from zero is highlighted with "a ponte não fecha", never hidden.
 * The total goes from openingDate to closingDate (last complete month); the partial month comes apart, outside the total.
 */
export function BridgeSummary({ bridge, selected, onSelect, mappingWarning }: Props) {
  const partial = partialMonthText(bridge);
  return (
    <section className="block">
      <div className="block-title">
        <h2>
          {bridge.fundName ?? "Fundo"} · {bridgePeriodText(bridge)}
        </h2>
        {bridge.closes === false && <span className="badge warning">a ponte não fecha</span>}
        {bridge.closes === true && <span className="badge ok">a ponte fecha: não explicado R$ 0,00</span>}
      </div>
      <dl className="number-list">
        <dt>Saldo inicial{bridge.openingDate && ` em ${formatDate(bridge.openingDate)}`}</dt>
        <dd>
          {formatMoneyOrDash(bridge.openingBalance)}
          {bridge.openingSource && <span className="muted"> · {bridge.openingSource}</span>}
        </dd>
        <dt>Saldo final{bridge.closingDate && ` em ${formatDate(bridge.closingDate)}`}</dt>
        <dd>{formatMoneyOrDash(bridge.closingBalance)}</dd>
        <dt>Variação do saldo</dt>
        <dd>
          <strong>{bridge.variation == null ? "—" : formatDifference(bridge.variation)}</strong>
        </dd>
      </dl>
      {partial && <p className="muted">{partial}</p>}
      <table className="table clickable bridge-table">
        <thead>
          <tr>
            <th>Parcela</th>
            <th className="number">Valor</th>
            <th className="number">% da variação</th>
          </tr>
        </thead>
        <tbody>
          {bridge.items.map((item) => {
            const unexplained = item.code === "UNEXPLAINED";
            const highlight = unexplained && bridge.closes === false;
            return (
              <tr
                key={item.code}
                className={[selected === item.code ? "selected" : "", highlight ? "bridge-unexplained" : ""].join(" ")}
                onClick={() => onSelect(item.code)}
              >
                <td>
                  {item.label}
                  {highlight && <small className="note">a ponte não fecha: o valor aparece aqui, sem ser escondido</small>}
                  {item.code === "LINE_SAVINGS" && mappingWarning && <MappingWarningNote warning={mappingWarning} />}
                </td>
                <td className="number">{formatDifference(item.amount)}</td>
                <td className="number">{formatPercentage(item.percentageOfVariation)}</td>
              </tr>
            );
          })}
        </tbody>
        <tfoot>
          <tr>
            <th>Variação do saldo (soma das parcelas)</th>
            <td className="number">
              <strong>{bridge.variation == null ? "—" : formatDifference(bridge.variation)}</strong>
            </td>
            <td className="number">{bridge.variation == null ? "—" : "100,0%"}</td>
          </tr>
        </tfoot>
      </table>
      <p className="muted">Clique em uma parcela para ver as partes, as linhas da PO e os lançamentos.</p>
    </section>
  );
}
