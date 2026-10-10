import { useState } from "react";
import { usePrintedColumn } from "../../api/budgetAnalysisQueries";
import type { PrintedColumnGroup } from "../../api/types";
import { useSession } from "../../context";
import { formatDifference, formatMoney, formatMoneyOrDash } from "../../format";

interface Props {
  /** Budget that printed the "Orçado anterior" column. */
  budgetId: string;
  onClose: () => void;
}

/**
 * Check of the "Orçado anterior" column (RF-11.5): printed subtotal × sum of the lines of each group,
 * printed total, warnings and, when the previous budget was already uploaded, the per-group differences between the two.
 * Everything comes ready from GET /budgets/{budgetId}/printed-column.
 */
export function PrintedColumnCheckPanel({ budgetId, onClose }: Props) {
  const { condominiumId } = useSession();
  const { data: check, isLoading, error } = usePrintedColumn(condominiumId, budgetId);

  return (
    <aside className="detail" aria-label="Conferência da coluna impressa">
      <header>
        <h2>{check?.label ?? "Coluna impressa"}</h2>
        <button className="link-button" onClick={onClose} aria-label="Fechar">
          ✕
        </button>
      </header>
      {isLoading && <p className="notice">Carregando…</p>}
      {error && <p className="notice error">{error.message}</p>}
      {check && (
        <>
          {check.superseded && (
            <p className="notice">
              A PO {check.previousBudgetLabel ?? "anterior"} foi enviada e é usada na comparação. A coluna impressa fica só
              como conferência.
            </p>
          )}
          <dl>
            <dt>Total impresso</dt>
            <dd>
              {formatMoneyOrDash(check.printedTotal)}{" "}
              <span className="muted">
                ({check.totalIncludesFunds ? "confere com os subtotais incluindo os fundos" : "comparado aos subtotais sem os fundos"})
              </span>
            </dd>
            <dt>Fundos</dt>
            <dd>{formatMoney(check.funds)}</dd>
            <dt>Previsto do mês</dt>
            <dd>
              {formatMoney(check.monthlyPlanned)} <span className="muted">(soma das linhas, sem os fundos)</span>
            </dd>
          </dl>
          {check.warnings.map((w, i) => (
            <p key={i} className="notice warning">
              {w}
            </p>
          ))}

          <h3>Grupos: impresso × soma das linhas</h3>
          <table className="table compact budget-vs-actual">
            <thead>
              <tr>
                <th>Grupo</th>
                <th className="number">Impresso</th>
                <th className="number">Soma das linhas</th>
                <th className="number">Diferença</th>
              </tr>
            </thead>
            {check.groups.map((g) => (
              <ColumnGroup key={g.code} group={g} />
            ))}
          </table>

          {check.differences.length > 0 && (
            <>
              <h3>PO anterior enviada × coluna impressa</h3>
              <table className="table compact">
                <thead>
                  <tr>
                    <th>Grupo</th>
                    <th className="number">PO enviada</th>
                    <th className="number">Coluna impressa</th>
                  </tr>
                </thead>
                <tbody>
                  {check.differences.map((d) => (
                    <tr key={d.code}>
                      <td>
                        {d.code} {d.description}
                      </td>
                      <td className="number">{formatMoney(d.uploadedBudget)}</td>
                      <td className="number">{formatMoney(d.printedColumn)}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </>
          )}
        </>
      )}
    </aside>
  );
}

/** A group of the column; when clicked, shows the lines with the amount and the printed "%" (text as read, not calculated). */
function ColumnGroup({ group }: { group: PrintedColumnGroup }) {
  const [open, setOpen] = useState(false);
  return (
    <tbody>
      <tr className="group-row" onClick={() => setOpen(!open)}>
        <td>
          <span aria-hidden>{open ? "▾" : "▸"}</span> {group.code} {group.description}
          {group.funds && <span className="badge neutral">fundos</span>}
        </td>
        <td className="number">{formatMoney(group.printed)}</td>
        <td className="number">{formatMoney(group.amount)}</td>
        <td className="number">
          {formatDifference(group.difference)}
          <small className="note">{group.matches ? "confere" : "não confere"}</small>
        </td>
      </tr>
      {open &&
        group.lines.map((l) => (
          <tr key={l.lineId ?? l.code}>
            <td>
              {l.code} {l.description}
              {l.account && <small className="note">{l.account}</small>}
            </td>
            <td className="number muted">{l.percentageText ? `% impresso: ${l.percentageText}` : ""}</td>
            <td className="number">{formatMoney(l.amount)}</td>
            <td />
          </tr>
        ))}
    </tbody>
  );
}
