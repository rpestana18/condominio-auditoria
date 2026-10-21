import type { TrackingMonthStatus } from "../../api/types";
import { formatDayMonth } from "../../format";

interface Props {
  status: TrackingMonthStatus;
  /** Partial month (RF-13.10): entries up to this date; null = complete. */
  partialUntil?: string | null;
}

/** Mark of the month in progress (Q50, RF-13.10): "mês em andamento · parcial até 20/10". */
export function inProgressText(partialUntil: string): string {
  return `mês em andamento · parcial até ${formatDayMonth(partialUntil)}`;
}

/** Text of a month without full numbers. A month without a cash flow is never shown as zero (RF-03.1.10). */
export function monthStatusText({ status, partialUntil }: Props): string | null {
  if (status === "NO_CASH_FLOW") return "sem extrato carregado";
  if (status === "TWO_CASH_FLOWS") return "dois fluxos no mês";
  if (partialUntil) return inProgressText(partialUntil);
  return null;
}

/**
 * Small mark under the month header: "mês em andamento · parcial até 20/10" (in two lines, to keep the column narrow),
 * "sem extrato carregado" or "dois fluxos no mês".
 */
export function MonthStatusNote(props: Props) {
  const text = monthStatusText(props);
  if (!text) return null;
  if (props.status === "WITH_CASH_FLOW" && props.partialUntil) {
    return (
      <span className="month-note partial" title={text}>
        mês em andamento
        <br />
        parcial até {formatDayMonth(props.partialUntil)}
      </span>
    );
  }
  return <span className="month-note">{text}</span>;
}
