import type { BudgetLineItem } from "../../api/types";
import { formatMoney } from "../../format";
import { budgetItemSourceLabel } from "../budgetVsActual/labels";
import { BudgetItemStatusBadge } from "./BudgetItemStatusBadge";

interface Props {
  lines: BudgetLineItem[];
  /** Only the Admin gets selection and editing; for everyone else the table is read only. */
  editing?: {
    selected: Set<string>;
    onSelect: (lineId: string, checked: boolean) => void;
    onSelectAll: (checked: boolean) => void;
    onEdit: (line: BudgetLineItem) => void;
  };
}

/** Budget lines with the budget item, the status, the source and the suggestion reason (RF-11.7). */
export function BudgetItemTable({ lines, editing }: Props) {
  const all = editing && lines.length > 0 && lines.every((l) => editing.selected.has(l.lineId));
  return (
    <div className="scroll">
      <table className="table">
        <thead>
          <tr>
            {editing && (
              <th>
                <input type="checkbox" aria-label="Selecionar todas" checked={!!all} onChange={(e) => editing.onSelectAll(e.target.checked)} />
              </th>
            )}
            <th>Linha da PO</th>
            <th>Conta da PO</th>
            <th className="number">Orçado</th>
            <th>Rubrica</th>
            <th>Estado</th>
            <th>Origem e motivo</th>
            {editing && <th aria-label="Ações" />}
          </tr>
        </thead>
        <tbody>
          {lines.map((l) => (
            <tr key={l.lineId} className={editing?.selected.has(l.lineId) ? "selected" : undefined}>
              {editing && (
                <td>
                  <input
                    type="checkbox"
                    aria-label={`Selecionar a linha ${l.code}`}
                    checked={editing.selected.has(l.lineId)}
                    onChange={(e) => editing.onSelect(l.lineId, e.target.checked)}
                  />
                </td>
              )}
              <td>
                <strong>{l.code}</strong> {l.description}
                {l.group && <small className="note">grupo {l.group}</small>}
              </td>
              <td className="muted">{l.account}</td>
              <td className="number">{formatMoney(l.budgeted)}</td>
              <td>{l.budgetItem?.name ?? <span className="muted">—</span>}</td>
              <td>
                <BudgetItemStatusBadge status={l.status} />
              </td>
              <td className="muted">
                {l.source && <span>{budgetItemSourceLabel[l.source]}</span>}
                {l.reason && <small className="note">{l.reason}</small>}
              </td>
              {editing && (
                <td>
                  <button className="link-button" onClick={() => editing.onEdit(l)}>
                    Alterar
                  </button>
                </td>
              )}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
