import { useState } from "react";
import { useAccountMappingEvents } from "../../api/budgetQueries";
import { useSession } from "../../context";
import { formatDateTime } from "../../format";
import { accountMappingStatusLabel, sourceLabel } from "../budgetVsActual/labels";

/** Account mapping trail (insert only): who, when, previous and new target and status. */
export function AccountMappingTrail({ budgetId }: { budgetId: string }) {
  const { condominiumId } = useSession();
  const [open, setOpen] = useState(false);
  const { data: events = [], isLoading } = useAccountMappingEvents(condominiumId, budgetId, open);

  return (
    <details className="block" onToggle={(e) => setOpen(e.currentTarget.open)}>
      <summary>Histórico de alterações</summary>
      {isLoading && <p className="notice">Carregando…</p>}
      {open && !isLoading && events.length === 0 && <p className="notice">Nenhuma alteração registrada.</p>}
      {events.length > 0 && (
        <table className="table compact">
          <thead>
            <tr>
              <th>Quando</th>
              <th>Quem</th>
              <th>Conta</th>
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
                <td>
                  {e.account} {e.name}
                </td>
                <td className="muted">
                  {e.previousTarget ?? "—"}
                  {e.previousStatus && ` (${accountMappingStatusLabel[e.previousStatus].toLowerCase()})`}
                </td>
                <td>
                  {e.newTarget} ({accountMappingStatusLabel[e.newStatus].toLowerCase()})
                </td>
                <td className="muted">
                  {sourceLabel[e.source]}
                  {e.reason && ` · ${e.reason}`}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
    </details>
  );
}
