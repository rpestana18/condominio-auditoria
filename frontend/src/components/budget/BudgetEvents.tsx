import { useBudgetEvents } from "../../api/budgetQueries";
import type { BudgetEvent } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime } from "../../format";

const eventLabel: Record<BudgetEvent["type"], string> = {
  CONFIRMED: "Confirmada",
  SUPERSEDED: "Substituída",
  FUNDS_CHANGED: "Fundos alterados",
  EXTENDED: "Prorrogada",
  EXTENSION_UNDONE: "Prorrogação desfeita",
  EXTENSION_SHORTENED: "Prorrogação encurtada",
};

/** Budget trail (insert only): confirmation, replacement and changes to the fund links. */
export function BudgetEvents({ budgetId }: { budgetId: string }) {
  const { condominiumId } = useSession();
  const { data: events = [] } = useBudgetEvents(condominiumId, budgetId);
  if (events.length === 0) return null;
  return (
    <details className="block">
      <summary>Histórico da PO ({events.length})</summary>
      <ul className="simple-list">
        {events.map((e, i) => (
          <li key={i}>
            <strong>{eventLabel[e.type]}</strong> · {formatDateTime(e.at)} por {e.username}: {e.detail}
            {e.justification && <span className="muted"> · justificativa: {e.justification}</span>}
          </li>
        ))}
      </ul>
    </details>
  );
}
