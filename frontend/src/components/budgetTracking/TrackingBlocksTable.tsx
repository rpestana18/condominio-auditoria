import type { TrackingBlock, TrackingBlocks, TrackingMonth } from "../../api/types";
import { formatInteger, formatMoney, formatMonthShort } from "../../format";
import type { PendingSteps } from "../pendingSteps";
import { UnmappedAccountsNote } from "../UnmappedAccountsNote";
import { MonthStatusNote, monthStatusText } from "./MonthStatusNote";
import type { OpenTrackingEvidence } from "./TrackingGrid";

interface Props {
  blocks: TrackingBlocks;
  months: TrackingMonth[];
  onOpenEvidence: OpenTrackingEvidence;
  /** Pending steps: the accounts without a confirmed mapping come as one short line in the "Sem linha da PO" row. */
  steps?: PendingSteps | null;
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
export function TrackingBlocksTable({ blocks, months, onOpenEvidence, steps }: Props) {
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
              <BlockRow
                key={row.key}
                row={row}
                block={blocks[row.key]}
                months={months}
                onOpenEvidence={onOpenEvidence}
                steps={row.key === "withoutBudgetLine" ? steps : null}
              />
            ))}
          </tbody>
        </table>
      </div>
      {!!steps?.unmappedAccounts && <UnmappedAccounts block={blocks.withoutBudgetLine} />}
      <p className="muted">Rateio à parte: linhas de luz, água, gás e seguro predial, mostradas só com a movimentação.</p>
    </section>
  );
}

function BlockRow({ row, block, months, onOpenEvidence, steps }: { row: Row; block: TrackingBlock } & Omit<Props, "blocks">) {
  return (
    <tr>
      <th scope="row" className="sticky-column">
        {row.label}
        {/* The accounts are listed below the table */}
        {steps && <UnmappedAccountsNote steps={steps} withAccounts={false} />}
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

/** Display order: largest amount first; entries without an account (account null) go last, as sent by the API. */
function byAmount(a: TrackingAccount, b: TrackingAccount): number {
  if ((a.account == null) !== (b.account == null)) return a.account == null ? 1 : -1;
  return b.amount - a.amount;
}

type TrackingAccount = NonNullable<TrackingBlock["accounts"]>[number];

/**
 * Accounts of "Sem linha da PO" in the months of the cumulative (blocks.withoutBudgetLine.accounts), collapsed by
 * default. The amounts come from the API; the screen only orders them for reading.
 */
function UnmappedAccounts({ block }: { block: TrackingBlock }) {
  const accounts = [...(block.accounts ?? [])].sort(byAmount);
  if (accounts.length === 0) return null;
  return (
    <details className="mapping-accounts">
      <summary>Ver as contas sem linha da PO ({accounts.length})</summary>
      <table className="table compact">
        <thead>
          <tr>
            <th>Conta</th>
            <th>Nome</th>
            <th>Situação</th>
            <th className="number">Valor no acumulado</th>
            <th className="number">Lançamentos</th>
          </tr>
        </thead>
        <tbody>
          {accounts.map((a, i) => (
            <tr key={a.account ?? `no-account-${i}`}>
              <td>{a.account ?? "lançamentos sem conta"}</td>
              <td>{a.name ?? "—"}</td>
              <td>{a.detail ?? "—"}</td>
              <td className="number">{formatMoney(a.amount)}</td>
              <td className="number">{formatInteger(a.entries)}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </details>
  );
}
