import type { SpendingProfile, TrackingCell } from "../../api/types";
import { formatDifference, formatMoney, formatMoneyOrDash } from "../../format";

interface Props {
  cell: TrackingCell | undefined;
  profile: SpendingProfile;
  /** Partial month (RF-13.10): numbers up to the date, outside the cumulative. */
  partial: boolean;
  /** Text shown (as a tooltip) when the month has no numbers, e.g. "sem extrato carregado". */
  emptyText: string;
  onOpen?: () => void;
}

/**
 * Highlight class of a month cell (RF-13.4). Neutral colors, never red (RF-11.13): one for above planned, another for below.
 * "anual ou esporádica" lines get the highlight on the fiscal year balance, not on the month (Q46);
 * "rateio à parte" lines show the movement, without difference (Q45).
 */
function highlightClass(difference: number | null | undefined, profile: SpendingProfile): string {
  if (difference == null || profile === "ANNUAL" || profile === "SEPARATE_APPORTIONMENT") return "";
  if (difference > 0) return "cell-above";
  if (difference < 0) return "cell-below";
  return "";
}

/** One month of one line: actual and difference to the month's planned. A click opens the entries (RF-03.1.12). */
export function TrackingCellView({ cell, profile, partial, emptyText, onOpen }: Props) {
  if (cell?.actual == null) {
    return (
      <td className="number tracking-cell empty" title={emptyText}>
        —
      </td>
    );
  }
  const classes = ["number", "tracking-cell", highlightClass(cell.difference, profile), partial ? "cell-partial" : ""].join(" ");
  const withDifference = profile !== "SEPARATE_APPORTIONMENT" && cell.difference != null;
  return (
    <td className={classes}>
      <button
        className="cell-button"
        onClick={onOpen}
        title={`Previsto ${formatMoneyOrDash(cell.planned)} · ${cell.entries ?? 0} lançamento(s)${partial ? " · mês parcial" : ""}`}
      >
        <span>{formatMoney(cell.actual)}</span>
        {withDifference && <small>{formatDifference(cell.difference!)}</small>}
      </button>
    </td>
  );
}
