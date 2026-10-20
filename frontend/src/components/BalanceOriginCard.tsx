import { Link } from "react-router";
import { useBalanceBridge } from "../api/budgetTrackingQueries";
import type { Dashboard } from "../api/types";
import { useSession } from "../context";
import { formatDate, formatDifference, formatPercentage } from "../format";
import { bridgePeriodText, partialMonthText } from "./balanceBridge/bridgeTexts";

/**
 * Summary of the balance origin on the home screen (RF-13.7), next to "Saldo acumulado": the bridge of the fiscal year
 * of the operating fund, with the same numbers as the "Origem do saldo" screen (same API call). Without a confirmed
 * operating fund, it does not show (the RF-05.1b notice applies). "Saldo acumulado" is the balance of the last report
 * (possibly a partial month); the bridge closes on closingDate (last complete month), and the partial month comes apart.
 */
export function BalanceOriginCard({ dashboard }: { dashboard: Dashboard }) {
  const { condominiumId } = useSession();
  const confirmed = !!dashboard.operatingFund?.confirmed;
  const { data: bridge } = useBalanceBridge(condominiumId, { fund: null, from: null, to: null }, confirmed);
  if (!confirmed || !bridge || bridge.status !== "CALCULATED") return null;
  const partial = partialMonthText(bridge);

  return (
    <Link to="/origem-do-saldo" className="number-card card-link wide-card" title="Abrir a origem do saldo">
      <span className="card-title">
        Origem do saldo · {bridgePeriodText(bridge)}
      </span>
      {bridge.closingDate && (
        <span className="muted">A ponte vai até {formatDate(bridge.closingDate)}, o último mês completo.</span>
      )}
      <strong>{bridge.variation == null ? "—" : formatDifference(bridge.variation)}</strong>
      <ul className="bridge-card-list">
        {bridge.items.map((item) => (
          <li key={item.code} className={item.code === "UNEXPLAINED" && bridge.closes === false ? "bridge-unexplained" : undefined}>
            <span>{item.label}</span>
            <span className="number">
              {formatDifference(item.amount)} <small className="muted">{formatPercentage(item.percentageOfVariation)}</small>
            </span>
          </li>
        ))}
      </ul>
      {partial && <span className="muted">{partial}</span>}
      {bridge.closes === false && <span className="notice warning">a ponte não fecha</span>}
      <span className="muted">Ver a origem do saldo</span>
    </Link>
  );
}
