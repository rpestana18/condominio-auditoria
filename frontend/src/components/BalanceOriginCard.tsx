import { Link } from "react-router";
import { useBalanceBridge } from "../api/budgetTrackingQueries";
import type { Dashboard } from "../api/types";
import { useSession } from "../context";
import { formatDifference, formatMonth, formatPercentage } from "../format";

/**
 * Summary of the balance origin on the home screen (RF-13.7), next to "Saldo acumulado": the bridge of the fiscal year
 * of the operating fund, with the same numbers as the "Origem do saldo" screen (same API call). Without a confirmed
 * operating fund, it does not show (the RF-05.1b notice applies).
 */
export function BalanceOriginCard({ dashboard }: { dashboard: Dashboard }) {
  const { condominiumId } = useSession();
  const confirmed = !!dashboard.operatingFund?.confirmed;
  const { data: bridge } = useBalanceBridge(condominiumId, { fund: null, from: null, to: null }, confirmed);
  if (!confirmed || !bridge || bridge.status !== "CALCULATED") return null;

  return (
    <Link to="/origem-do-saldo" className="number-card card-link wide-card" title="Abrir a origem do saldo">
      <span className="card-title">
        Origem do saldo · {formatMonth(bridge.from)} a {formatMonth(bridge.to)}
      </span>
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
      {bridge.closes === false && <span className="notice warning">a ponte não fecha</span>}
      <span className="muted">Ver a origem do saldo</span>
    </Link>
  );
}
