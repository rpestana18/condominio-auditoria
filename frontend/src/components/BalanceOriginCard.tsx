import { Link } from "react-router";
import { useHasConfirmedBudget } from "../api/budgetAnalysisQueries";
import { useBalanceBridge } from "../api/budgetTrackingQueries";
import type { Dashboard } from "../api/types";
import { useSession } from "../context";
import { formatDate, formatDifference, formatMoneyOrDash, formatPercentage } from "../format";
import { BeforeReadingBox } from "./BeforeReadingBox";
import { bridgeSteps } from "./balanceBridge/bridgeSteps";
import { bridgePeriodText, hasPercentage, partialMonthText } from "./balanceBridge/bridgeTexts";

/**
 * Summary of the balance origin on the home screen (RF-13.7), next to "Saldo acumulado": the bridge of the fiscal year
 * of the operating fund, with the same numbers as the "Origem do saldo" screen (same API call). Without a confirmed
 * operating fund, it does not show (the RF-05.1b notice applies). "Saldo acumulado" is the balance of the last report
 * (possibly a partial month); the bridge closes on closingDate (last complete month), and the partial month comes apart.
 * With pending steps (mapping, line types) the card shows them with their links; while the API flags the mapping as
 * provisional, it shows only the balances, not the items. The card is a block with links, not one big link, because the
 * steps have links of their own.
 */
export function BalanceOriginCard({ dashboard }: { dashboard: Dashboard }) {
  const { condominiumId } = useSession();
  const confirmed = !!dashboard.operatingFund?.confirmed;
  // Without a confirmed budget there is no fiscal year for the default period: the card does not show
  const hasBudget = useHasConfirmedBudget(condominiumId) === true;
  const { data: bridge } = useBalanceBridge(condominiumId, { fund: null, from: null, to: null }, confirmed && hasBudget);
  if (!confirmed || !bridge || bridge.status !== "CALCULATED") return null;
  const partial = partialMonthText(bridge);

  const provisional = !!bridge.mapping?.provisional;

  return (
    <section className="number-card wide-card">
      <Link to="/origem-do-saldo" className="card-title card-title-link" title="Abrir a origem do saldo">
        Origem do saldo · {bridgePeriodText(bridge)}
      </Link>
      {bridge.closingDate && (
        <span className="muted">A ponte vai até {formatDate(bridge.closingDate)}, o último mês completo.</span>
      )}
      <strong>{bridge.variation == null ? "—" : formatDifference(bridge.variation)}</strong>
      {provisional ? (
        <ul className="bridge-card-list">
          <li>
            <span>Saldo inicial{bridge.openingDate && ` em ${formatDate(bridge.openingDate)}`}</span>
            <span className="number">{formatMoneyOrDash(bridge.openingBalance)}</span>
          </li>
          <li>
            <span>Saldo final{bridge.closingDate && ` em ${formatDate(bridge.closingDate)}`}</span>
            <span className="number">{formatMoneyOrDash(bridge.closingBalance)}</span>
          </li>
        </ul>
      ) : (
        <ul className="bridge-card-list">
          {bridge.items.map((item) => (
            <li key={item.code} className={item.code === "UNEXPLAINED" && bridge.closes === false ? "bridge-unexplained" : undefined}>
              <span>{item.label}</span>
              <span className="number">
                {item.amount == null ? "—" : formatDifference(item.amount)}
                {hasPercentage(item.code) && <small className="muted"> {formatPercentage(item.percentageOfVariation)}</small>}
              </span>
            </li>
          ))}
        </ul>
      )}
      <BeforeReadingBox steps={bridgeSteps(bridge)} compact />
      {provisional && <span className="muted">As parcelas aparecem aqui depois do de-para.</span>}
      {partial && <span className="muted">{partial}</span>}
      {bridge.closes === false && <span className="notice warning">a ponte não fecha</span>}
      <Link to="/origem-do-saldo">Ver a origem do saldo</Link>
    </section>
  );
}
