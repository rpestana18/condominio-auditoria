import type { TrackingBlock, TrackingBlocks, TrackingMonth } from "../../api/types";
import { formatMoney, formatMonthShort } from "../../format";
import { MonthStatusNote, monthStatusText } from "./MonthStatusNote";
import type { OpenTrackingEvidence } from "./TrackingGrid";

interface Props {
  blocks: TrackingBlocks;
  months: TrackingMonth[];
  onOpenEvidence: OpenTrackingEvidence;
}

interface Row {
  key: keyof TrackingBlocks;
  label: string;
  /** Evidence target of budget vs actual; "rateio à parte" has no target of its own. */
  target?: string;
}

const rows: Row[] = [
  { key: "withoutBudgetLine", label: "Sem linha da PO", target: "NO_BUDGET_LINE" },
  { key: "toReallocate", label: "A realocar", target: "TO_REALLOCATE" },
  { key: "adjustments", label: "Ajustes (não são despesa)", target: "ADJUSTMENTS" },
  { key: "separateApportionment", label: "Rateio à parte (movimentação, sem diferença)" },
];

/** Separate blocks of RF-13.4, as in RF-03.1.13: outside the budget lines, month by month. */
export function TrackingBlocksTable({ blocks, months, onOpenEvidence }: Props) {
  return (
    <section className="block">
      <h2>Blocos à parte</h2>
      <div className="scroll">
        <table className="table compact tracking-grid">
          <thead>
            <tr>
              <th className="sticky-column">Bloco</th>
              {months.map((m) => (
                <th key={m.month} className="number month-column">
                  {formatMonthShort(m.month)}
                  <MonthStatusNote status={m.status} partialUntil={m.partialUntil} />
                </th>
              ))}
              <th className="number">Acumulado</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <BlockRow key={row.key} row={row} block={blocks[row.key]} months={months} onOpenEvidence={onOpenEvidence} />
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">Rateio à parte: linhas de luz, água, gás e seguro predial, mostradas só com a movimentação.</p>
    </section>
  );
}

function BlockRow({ row, block, months, onOpenEvidence }: { row: Row; block: TrackingBlock } & Omit<Props, "blocks">) {
  return (
    <tr>
      <th scope="row" className="sticky-column">
        {row.label}
      </th>
      {months.map((m) => {
        const cell = block.cells.find((c) => c.month === m.month);
        if (cell?.amount == null) {
          return (
            <td key={m.month} className="number" title={monthStatusText(m) ?? ""}>
              —
            </td>
          );
        }
        return (
          <td key={m.month} className={`number ${m.partialUntil ? "cell-partial" : ""}`}>
            {row.target ? (
              <button
                className="cell-button"
                title={`${cell.entries ?? 0} lançamento(s)`}
                onClick={() => onOpenEvidence(m.month, { target: row.target!, title: row.label })}
              >
                <span>{formatMoney(cell.amount)}</span>
              </button>
            ) : (
              formatMoney(cell.amount)
            )}
          </td>
        );
      })}
      <td className="number">{block.cumulative == null ? "—" : formatMoney(block.cumulative)}</td>
    </tr>
  );
}
