import { useState } from "react";
import { useUndoReallocation, useBudget, useReallocations, useReallocate } from "../../api/budgetQueries";
import type { EntryEvidence } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatDateTime, formatMoney } from "../../format";

/** Expense lines (1.1 to 1.8) of the budget, the only ones that can receive a reallocation. */
function useTargetLines(budgetId: string) {
  const { condominiumId } = useSession();
  const { data } = useBudget(condominiumId, budgetId);
  return data?.lines.filter((l) => l.type === "LINE" && !l.fundLine) ?? [];
}

/**
 * Line choice for an entry "a realocar" (RF-03.1.7). Only Manager and Admin get here.
 * The management company's original entry does not change: the reallocation is a layer of the system.
 */
export function ReallocateEntry({ budgetId, entry }: { budgetId: string; entry: EntryEvidence }) {
  const { condominiumId } = useSession();
  const lines = useTargetLines(budgetId);
  const reallocate = useReallocate(condominiumId);
  const [lineId, setLineId] = useState("");
  return (
    <div className="reallocate">
      <select value={lineId} onChange={(e) => setLineId(e.target.value)} aria-label="Linha da PO">
        <option value="">Realocar para a linha…</option>
        {lines.map((l) => (
          <option key={l.id} value={l.id}>
            {l.effectiveCode} {l.description}
          </option>
        ))}
      </select>
      <button
        className="button secondary"
        disabled={!lineId || reallocate.isPending}
        onClick={() => reallocate.mutate({ entryId: entry.entryId, lineId })}
      >
        Realocar
      </button>
      {reallocate.isError && <span className="notice error">{reallocate.error.message}</span>}
    </div>
  );
}

/** Undo button: the amount goes back to "a realocar" and the reallocation stays in the history as undone. */
export function UndoReallocation({ reallocationId }: { reallocationId: string }) {
  const { condominiumId } = useSession();
  const undo = useUndoReallocation(condominiumId);
  return (
    <>
      <button className="link-button" disabled={undo.isPending} onClick={() => undo.mutate(reallocationId)}>
        Desfazer realocação
      </button>
      {undo.isError && <span className="notice error"> {undo.error.message}</span>}
    </>
  );
}

/** Reallocations of this budget version, active and undone (everyone can see them; Manager and Admin can undo). */
export function ReallocationList({ budgetId }: { budgetId: string }) {
  const { condominiumId, can } = useSession();
  const { data: reallocations = [] } = useReallocations(condominiumId, budgetId);
  if (reallocations.length === 0) return null;
  return (
    <details>
      <summary>Realocações feitas ({reallocations.filter((r) => r.active).length} ativas)</summary>
      <table className="table compact">
        <thead>
          <tr>
            <th>Lançamento</th>
            <th className="number">Valor</th>
            <th>Linha</th>
            <th>Quem e quando</th>
            <th aria-label="Ações" />
          </tr>
        </thead>
        <tbody>
          {reallocations.map((r) => (
            <tr key={r.id} className={r.active ? undefined : "undone"}>
              <td>
                {formatDate(r.date)} · {r.account} {r.memo} <span className="muted">pág. {r.page}</span>
              </td>
              <td className="number">{formatMoney(r.amount)}</td>
              <td>
                {r.lineCode} {r.lineDescription}
              </td>
              <td className="muted">
                {r.reallocatedBy} em {formatDateTime(r.reallocatedAt)}
                {!r.active && r.undoneAt && ` · desfeita por ${r.undoneBy} em ${formatDateTime(r.undoneAt)}`}
              </td>
              <td>{r.active && can("MANAGER", "ADMIN") && <UndoReallocation reallocationId={r.id} />}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
