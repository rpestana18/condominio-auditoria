import { useState } from "react";
import { useBudgetItemEvents } from "../../api/budgetAnalysisQueries";
import type { BudgetItemStatus } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime } from "../../format";
import { budgetItemActionLabel, budgetItemStatusLabel, budgetItemSourceLabel } from "../budgetVsActual/labels";

/** " (confirmado)" after the budget item name; nothing without a status. */
const withStatus = (status: BudgetItemStatus | null | undefined) => (status ? ` (${budgetItemStatusLabel[status].toLowerCase()})` : "");

/** Trail of this budget's items (insert only): who, when, previous and new budget item and status. */
export function BudgetItemTrail({ budgetId }: { budgetId: string }) {
  const { condominiumId } = useSession();
  const [open, setOpen] = useState(false);
  const { data: events = [], isLoading } = useBudgetItemEvents(condominiumId, budgetId, open);

  return (
    <details className="trail" onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary>Histórico das rubricas</summary>
      {isLoading && <p className="notice">Carregando…</p>}
      {open && !isLoading && events.length === 0 && <p className="notice">Nenhuma alteração registrada.</p>}
      {events.length > 0 && (
        <div className="scroll">
          <table className="table compact">
            <thead>
              <tr>
                <th>Quando</th>
                <th>Quem</th>
                <th>Linha</th>
                <th>Ação</th>
                <th>Antes</th>
                <th>Depois</th>
                <th>Origem</th>
              </tr>
            </thead>
            <tbody>
              {events.map((e) => (
                <tr key={e.id}>
                  <td>{formatDateTime(e.at)}</td>
                  <td>{e.username}</td>
                  <td>{e.code ? `${e.code} ${e.description ?? ""}` : <span className="muted">—</span>}</td>
                  <td>{budgetItemActionLabel[e.action]}</td>
                  <td className="muted">
                    {e.previousItem ?? "—"}
                    {withStatus(e.previousStatus)}
                  </td>
                  <td>
                    {e.newItem}
                    {withStatus(e.newStatus)}
                  </td>
                  <td className="muted">
                    {e.source && budgetItemSourceLabel[e.source]}
                    {e.reason && <small className="note">{e.reason}</small>}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </details>
  );
}
