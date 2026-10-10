import { useState } from "react";
import type { BudgetVsActualGroup, BudgetVsActualLine } from "../../api/types";
import { differenceClass, formatDifference, formatMoney, formatPercentage } from "../../format";
import type { OpenEvidence } from "./evidence";
import { markLabel } from "./labels";
import { SourcedValue } from "./SourcedValue";

type Order = "BUDGET" | "DIFFERENCE";

interface Props {
  groups: BudgetVsActualGroup[];
  onOpenEvidence: OpenEvidence;
}

/** Table by group, with subtotals (RF-03.1.13). Every actual, of a line or of a group, leads to the entries. */
export function BudgetVsActualTable({ groups, onOpenEvidence }: Props) {
  const [order, setOrder] = useState<Order>("BUDGET");
  const [collapsed, setCollapsed] = useState<Set<string>>(new Set());

  const toggle = (id: string) =>
    setCollapsed((current) => {
      const next = new Set(current);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });

  // Only changes the display order; the values stay the API's
  const sortLines = (lines: BudgetVsActualLine[]) =>
    order === "BUDGET" ? lines : [...lines].sort((a, b) => b.difference - a.difference);

  return (
    <section className="block">
      <header className="block-title">
        <h2>Linhas da PO</h2>
        <label className="muted">
          Ordenar por{" "}
          <select value={order} onChange={(e) => setOrder(e.target.value as Order)}>
            <option value="BUDGET">ordem da PO</option>
            <option value="DIFFERENCE">maior diferença</option>
          </select>
        </label>
      </header>
      <div className="scroll">
        <table className="table budget-vs-actual">
          <thead>
            <tr>
              <th>Código</th>
              <th>Descrição</th>
              <th>Contas do fluxo</th>
              <th className="number">Previsto</th>
              <th className="number">Realizado</th>
              <th className="number">Diferença</th>
              <th className="number">Execução</th>
            </tr>
          </thead>
          {groups.map((g) => (
            <tbody key={g.lineId}>
              <tr className="group-row" onClick={() => toggle(g.lineId)}>
                <td>
                  <span aria-hidden>{collapsed.has(g.lineId) ? "▸" : "▾"}</span> {g.code}
                </td>
                <td colSpan={2}>{g.description}</td>
                <td className="number">{formatMoney(g.planned)}</td>
                <td className="number">
                  <SourcedValue
                    value={g.actual}
                    onOpen={() => onOpenEvidence({ target: `group:${g.lineId}`, title: `${g.code} ${g.description}` })}
                  />
                </td>
                <td className={`number ${differenceClass(g.difference)}`}>{formatDifference(g.difference)}</td>
                <td className="number">{formatPercentage(g.execution)}</td>
              </tr>
              {!collapsed.has(g.lineId) &&
                sortLines(g.lines).map((l) => <LineRow key={l.lineId} line={l} onOpenEvidence={onOpenEvidence} />)}
            </tbody>
          ))}
        </table>
      </div>
    </section>
  );
}

function LineRow({ line, onOpenEvidence }: { line: BudgetVsActualLine; onOpenEvidence: OpenEvidence }) {
  const open = () =>
    onOpenEvidence({
      target: `line:${line.lineId}`,
      title: `${line.code} ${line.description}`,
      budgetLine: { page: line.page, notes: line.notes },
    });
  return (
    <tr>
      <td>{line.code}</td>
      <td>
        {line.description}
        {line.mark && <span className="badge neutral">{markLabel[line.mark]}</span>}
        {line.notes && <small className="note">Obs. da PO: {line.notes}</small>}
      </td>
      <td className="muted">{line.cashFlowAccounts.join(", ") || "—"}</td>
      <td className="number">{formatMoney(line.planned)}</td>
      <td className="number">
        <SourcedValue value={line.actual} onOpen={open} />
        {line.entries > 0 && <small className="muted"> ({line.entries})</small>}
      </td>
      <td className={`number ${differenceClass(line.difference)}`}>{formatDifference(line.difference)}</td>
      <td className="number">{formatPercentage(line.execution)}</td>
    </tr>
  );
}
