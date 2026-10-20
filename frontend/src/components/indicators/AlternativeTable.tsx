import type { TooltipContentProps } from "recharts";

/**
 * Model of a chart's alternative table (RF-11.10). The same model feeds the table and the chart's tooltip,
 * so both show exactly the same texts. One row per axis category (month, budget line or group),
 * in the same order as the chart data.
 */
export interface TableModel {
  /** Table caption, for screen readers. */
  caption: string;
  /** Title of the first column (e.g. "Mês"). */
  labelColumn: string;
  columns: string[];
  rows: TableRow[];
}

export interface TableRow {
  key: string;
  /** First column (e.g. "09/2026" or "1.3.10 Vigia e Portaria"). */
  label: string;
  cells: Cell[];
}

export interface Cell {
  text: string;
  /** With a destination: becomes a link to budget vs actual with the evidence (RF-11.12). */
  onOpen?: () => void;
  /** Only the overrun above the 20% rule limit (RF-11.13), when the API flags `aboveLimit`. */
  alert?: boolean;
  /** Tooltip of the link; by default, the budget vs actual one. */
  openTitle?: string;
}

/** The table, inside a "Ver tabela" the person opens when they want. */
export function AlternativeTable({ model }: { model: TableModel }) {
  return (
    <div className="scroll">
      <table className="table compact">
        <caption className="muted">{model.caption}</caption>
        <thead>
          <tr>
            <th scope="col">{model.labelColumn}</th>
            {model.columns.map((c) => (
              <th key={c} scope="col" className="number">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {model.rows.map((row) => (
            <tr key={row.key}>
              <th scope="row">{row.label}</th>
              {row.cells.map((cell, i) => (
                <td key={i} className="number">
                  <CellText cell={cell} />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function CellText({ cell }: { cell: Cell }) {
  const className = cell.alert ? "critical-text" : undefined;
  if (!cell.onOpen) return <span className={className}>{cell.text}</span>;
  return (
    <button type="button" className={`sourced-value ${className ?? ""}`} onClick={cell.onOpen} title={cell.openTitle ?? "Abrir no previsto × realizado, com os lançamentos"}>
      {cell.text}
    </button>
  );
}

/**
 * Chart tooltip: the table row of the category under the mouse. Use as `<Tooltip content={tableTooltip(model)} />`.
 * The chart data stays in the same order as the model rows and carries the `index` field (row position).
 */
export function tableTooltip(model: TableModel) {
  return function ChartTooltip({ active, activeIndex, payload }: TooltipContentProps) {
    // Position of the category under the mouse; a month without a cash flow may come with no payload items, hence activeIndex first
    const fromPayload = (payload?.[0]?.payload as { index?: number } | undefined)?.index;
    const index = activeIndex !== null && activeIndex !== undefined && activeIndex !== "" ? Number(activeIndex) : fromPayload;
    const row = index === undefined || Number.isNaN(index) ? undefined : model.rows[index];
    if (!active || !row) return null;
    return (
      <div className="chart-tooltip">
        <strong>{row.label}</strong>
        <dl>
          {model.columns.map((column, i) => (
            <div key={column}>
              <dt>{column}</dt>
              <dd className={row.cells[i]?.alert ? "critical-text" : undefined}>{row.cells[i]?.text}</dd>
            </div>
          ))}
        </dl>
      </div>
    );
  };
}
