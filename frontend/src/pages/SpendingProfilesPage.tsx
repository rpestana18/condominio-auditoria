import { Link, useParams } from "react-router";
import { useBudget } from "../api/budgetQueries";
import { useSpendingProfileEvents, useSpendingProfiles } from "../api/budgetTrackingQueries";
import { spendingProfileLabel, spendingProfileStatusLabel } from "../components/budgetVsActual/labels";
import { SpendingProfilesTable } from "../components/spendingProfiles/SpendingProfilesTable";
import { useSession } from "../context";
import { formatDateTime } from "../format";

/** "Tipos de linha" of one approved budget (RF-13.3): suggestion + confirmation by the Admin, with a trail. */
export function SpendingProfilesPage() {
  const { budgetId } = useParams();
  const { condominiumId } = useSession();
  const { data: detail } = useBudget(condominiumId, budgetId);
  const { data, isLoading, error } = useSpendingProfiles(condominiumId, budgetId);

  return (
    <>
      <header className="page-title">
        <h1>Tipos de linha{detail?.budget.title ? ` · ${detail.budget.title}` : ""}</h1>
        <Link className="link-button" to={`/previsoes/${budgetId}`}>
          Voltar à PO aprovada
        </Link>
      </header>
      {isLoading && <p className="notice">Carregando…</p>}
      {error && <p className="notice error">{error.message}</p>}
      {data && budgetId && <SpendingProfilesTable budgetId={budgetId} data={data} />}
      {budgetId && <Trail budgetId={budgetId} />}
    </>
  );
}

function Trail({ budgetId }: { budgetId: string }) {
  const { condominiumId } = useSession();
  const { data: events = [] } = useSpendingProfileEvents(condominiumId, budgetId);
  if (events.length === 0) return null;
  return (
    <details className="block">
      <summary>Histórico dos tipos ({events.length})</summary>
      <ul className="simple-list">
        {events.map((e) => (
          <li key={e.id}>
            <strong>
              {e.lineCode} {e.lineDescription}
            </strong>{" "}
            · {e.previousProfile ? `${spendingProfileLabel[e.previousProfile]} → ` : ""}
            {spendingProfileLabel[e.newProfile]} ({spendingProfileStatusLabel[e.newStatus]}) · {formatDateTime(e.occurredAt)} por{" "}
            {e.username}
            {e.reason && <span className="muted"> · motivo: {e.reason}</span>}
          </li>
        ))}
      </ul>
    </details>
  );
}
