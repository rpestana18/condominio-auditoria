import type { CashFlowEntryFilter } from "../../api/cashFlowQueries";
import type { CashFlowMonth, CashFlowMonthly, CashFlowRow, CashFlowRowCode } from "../../api/types";
import { formatDifference, formatMoney, formatMonthShort } from "../../format";
import { inProgressText } from "../budgetTracking/MonthStatusNote";

/** Opens the entries list with the filter of the clicked cell (the month is filled in by the table). */
export type OpenCashFlowEntries = (filter: Omit<CashFlowEntryFilter, "fund"> & { fundId: string | null }, title: string) => void;

interface Props {
  cashFlow: CashFlowMonthly;
  onOpenEntries: OpenCashFlowEntries;
}

/** Header note of a month: no cash flow, before the approved budget or in progress (RF-13.10, RF-13.16). */
export function cashFlowMonthNote(m: CashFlowMonth): string | null {
  if (m.status === "NO_CASH_FLOW") return "sem extrato carregado";
  if (m.status === "TWO_CASH_FLOWS") return "dois extratos";
  if (m.partialUntil) return inProgressText(m.partialUntil);
  if (m.beforeBudget) return "antes da PO aprovada";
  return null;
}

/**
 * Table of RF-13.11: the months side by side and, in the rows, the inflows, the outflows by budget line (by group) and
 * the blocks apart, the transfers between funds, the month balance and the accumulated balance checked with the cash
 * flow. Every number comes from the API; clicking a cell opens its entries.
 */
export function CashFlowTable({ cashFlow, onOpenEntries }: Props) {
  const { months } = cashFlow;
  const open = (row: CashFlowRow, parent: CashFlowRowCode | null) => (month: string | null) =>
    onOpenEntries(
      {
        month,
        line: row.lineId ?? null,
        // A row of another fund opens that fund's own entries; a line row filters by the line
        block: row.lineId || row.fundId ? null : (parent ?? row.code),
        account: row.account ?? null,
        fundId: row.fundId ?? null,
      },
      row.label,
    );
  return (
    <div className="scroll tracking-scroll">
      <table className="table compact tracking-grid cash-flow-table">
        <thead>
          <tr>
            <th className="sticky-column">Entradas e saídas</th>
            {months.map((m) => {
              const note = cashFlowMonthNote(m);
              return (
                <th key={m.month} className={`number month-column ${m.partialUntil ? "cell-partial" : ""}`}>
                  {formatMonthShort(m.month)}
                  {note && <span className="month-note">{note}</span>}
                </th>
              );
            })}
            <th className="number">Total</th>
          </tr>
        </thead>
        <tbody>
          <SectionRow title="Entradas" months={months} />
          {cashFlow.inflows.map((r) => (
            <RowLines key={r.code + (r.account ?? "")} row={r} months={months} parent={null} open={open} />
          ))}
          <TotalRow title="Total das entradas" months={months} value={(m) => m.inflows} />

          <SectionRow title="Saídas" months={months} />
          {cashFlow.outflowGroups.map((g) => (
            <GroupLines key={g.lineId ?? g.code} code={g.code} description={g.description} cells={g.cells} total={g.total}>
              {g.lines.map((l) => (
                <RowLines key={l.lineId ?? l.label} row={l} months={months} parent={null} open={open} indent />
              ))}
            </GroupLines>
          ))}
          {cashFlow.outflowBlocks.map((b) => (
            <RowLines key={b.code} row={b} months={months} parent={null} open={open} emphasis />
          ))}
          <TotalRow title="Total das saídas" months={months} value={(m) => m.outflows} />

          {cashFlow.transfers.length > 0 && <SectionRow title="Transferências entre fundos" months={months} />}
          {cashFlow.transfers.map((t) => (
            <RowLines key={t.code + (t.fundId ?? "")} row={t} months={months} parent={null} open={open} />
          ))}
        </tbody>
        <tfoot>
          <tr>
            <th className="sticky-column">Saldo do mês</th>
            {months.map((m) => (
              <td key={m.month} className="number">
                {m.monthBalance == null ? "—" : formatDifference(m.monthBalance)}
              </td>
            ))}
            <td />
          </tr>
          <tr>
            <th className="sticky-column">Saldo acumulado no fim do mês</th>
            {months.map((m) => (
              <td key={m.month} className="number" title={balanceTitle(m)}>
                {m.closingBalance == null ? "—" : formatMoney(m.closingBalance)}
                {m.balanceMatches === false && <small className="badge warning">não bate com o extrato</small>}
              </td>
            ))}
            <td />
          </tr>
          <tr>
            <th className="sticky-column">Saídas × débitos do extrato</th>
            {months.map((m) => (
              <td key={m.month} className="number muted">
                {m.debitsMatch == null ? "—" : m.debitsMatch ? "conferem" : <span className="badge warning">não batem</span>}
              </td>
            ))}
            <td />
          </tr>
        </tfoot>
      </table>
    </div>
  );
}

function balanceTitle(m: CashFlowMonth): string | undefined {
  if (m.openingBalance == null) return undefined;
  return `Saldo no início do mês: ${formatMoney(m.openingBalance)}`;
}

function SectionRow({ title, months }: { title: string; months: CashFlowMonth[] }) {
  return (
    <tr className="group-row">
      <td className="sticky-column" colSpan={months.length + 2}>
        <strong>{title}</strong>
      </td>
    </tr>
  );
}

function TotalRow({ title, months, value }: { title: string; months: CashFlowMonth[]; value: (m: CashFlowMonth) => number | null | undefined }) {
  const total = months.reduce((sum, m) => sum + (value(m) ?? 0), 0);
  return (
    <tr className="group-row">
      <th className="sticky-column">{title}</th>
      {months.map((m) => {
        const v = value(m);
        return (
          <td key={m.month} className="number">
            {v == null ? "—" : formatMoney(v)}
          </td>
        );
      })}
      <td className="number">{formatMoney(Math.round(total * 100) / 100)}</td>
    </tr>
  );
}

interface GroupProps {
  code: string;
  description: string;
  cells: (number | null)[];
  total: number;
  children: React.ReactNode;
}

function GroupLines({ code, description, cells, total, children }: GroupProps) {
  return (
    <>
      <tr className="group-row">
        <td className="sticky-column">
          {code} {description} · subtotal
        </td>
        {cells.map((c, i) => (
          <td key={i} className="number">
            {c == null ? "—" : formatMoney(c)}
          </td>
        ))}
        <td className="number">{formatMoney(total)}</td>
      </tr>
      {children}
    </>
  );
}

interface RowProps {
  row: CashFlowRow;
  months: CashFlowMonth[];
  parent: CashFlowRowCode | null;
  open: (row: CashFlowRow, parent: CashFlowRowCode | null) => (month: string | null) => void;
  indent?: boolean;
  emphasis?: boolean;
}

function RowLines({ row, months, parent, open, indent, emphasis }: RowProps) {
  const onOpen = open(row, parent);
  return (
    <>
      <tr className={emphasis ? "emphasis" : undefined}>
        <td className={`sticky-column ${indent || parent ? "indent" : ""}`}>{row.label}</td>
        {months.map((m, i) => {
          const value = row.cells[i];
          if (value == null) {
            return (
              <td key={m.month} className="number muted" title={cashFlowMonthNote(m) ?? undefined}>
                —
              </td>
            );
          }
          return (
            <td key={m.month} className={`number tracking-cell ${m.partialUntil ? "cell-partial" : ""}`}>
              <button className="cell-button" onClick={() => onOpen(m.month)} title="Abrir os lançamentos">
                {formatMoney(value)}
              </button>
            </td>
          );
        })}
        <td className="number">
          <button className="cell-button" onClick={() => onOpen(null)} title="Abrir os lançamentos do período">
            {formatMoney(row.total)}
          </button>
        </td>
      </tr>
      {row.children.map((c) => (
        <RowLines key={(c.account ?? "") + (c.fundId ?? "") + c.label} row={c} months={months} parent={parent ?? row.code} open={open} />
      ))}
    </>
  );
}
