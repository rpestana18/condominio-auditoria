import { useState } from "react";
import { useBudgetLineItems } from "../../api/budgetAnalysisQueries";
import type { BudgetItemFilter, BudgetLineItem, BudgetSummary } from "../../api/types";
import { useSession } from "../../context";
import { BudgetItemActions } from "./BudgetItemActions";
import { BudgetItemEditor } from "./BudgetItemEditor";
import { BudgetItemTable } from "./BudgetItemTable";
import { BudgetItemTrail } from "./BudgetItemTrail";

const filters: { code: BudgetItemFilter; label: string }[] = [
  { code: "ALL", label: "Todas" },
  { code: "PENDING", label: "Pendentes" },
  { code: "SUGGESTED", label: "Sugeridas" },
  { code: "CONFIRMED", label: "Confirmadas" },
  { code: "REJECTED", label: "Recusadas" },
  { code: "NO_ITEM", label: "Sem rubrica" },
];

/**
 * Budget items of the budget lines (RF-11.7), on the "PO" screen. Every role sees the list; only the Admin chooses,
 * creates, confirms or rejects, and only with a confirmed budget. The backend refuses (403) the others anyway.
 */
export function BudgetItemsSection({ budget }: { budget: BudgetSummary }) {
  const { condominiumId, can } = useSession();
  const [filter, setFilter] = useState<BudgetItemFilter>("ALL");
  const { data: list, isLoading, error } = useBudgetLineItems(condominiumId, budget.id, filter);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [editing, setEditing] = useState<BudgetLineItem | null>(null);
  const canEdit = can("ADMIN") && budget.status === "CONFIRMED";
  const lines = list?.lines ?? [];

  const changeFilter = (next: BudgetItemFilter) => {
    setSelected(new Set());
    setFilter(next);
  };

  return (
    <section className="block">
      <header className="block-title">
        <h2>Rubricas (correspondência entre exercícios)</h2>
        {list && (
          <span className={list.summary.confirmed === list.summary.lines ? "badge ok" : "badge warning"}>
            {list.summary.confirmed} de {list.summary.lines} linhas confirmadas
          </span>
        )}
      </header>
      {list && (
        <p className="muted">
          {list.summary.suggested} sugeridas · {list.summary.rejected} recusadas · {list.summary.withoutItem} sem rubrica. Linhas de
          exercícios diferentes com a mesma rubrica confirmada são comparadas em "Comparar exercícios"; sugestão nunca vale
          sozinha.
        </p>
      )}

      {canEdit && (
        <BudgetItemActions
          budgetId={budget.id}
          lines={lines}
          selected={[...selected]}
          onBatchDone={() => setSelected(new Set())}
        />
      )}

      <div className="tabs" role="tablist">
        {filters.map((f) => (
          <button key={f.code} role="tab" aria-selected={filter === f.code} onClick={() => changeFilter(f.code)}>
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : lines.length === 0 ? (
        <p className="notice">Nenhuma linha neste filtro.</p>
      ) : (
        <BudgetItemTable
          lines={lines}
          editing={
            canEdit
              ? {
                  selected,
                  onSelect: (lineId, checked) =>
                    setSelected((current) => {
                      const next = new Set(current);
                      if (checked) next.add(lineId);
                      else next.delete(lineId);
                      return next;
                    }),
                  onSelectAll: (checked) => setSelected(checked ? new Set(lines.map((l) => l.lineId)) : new Set()),
                  onEdit: setEditing,
                }
              : undefined
          }
        />
      )}

      <BudgetItemTrail budgetId={budget.id} />
      {editing && <BudgetItemEditor budgetId={budget.id} line={editing} onClose={() => setEditing(null)} />}
    </section>
  );
}
