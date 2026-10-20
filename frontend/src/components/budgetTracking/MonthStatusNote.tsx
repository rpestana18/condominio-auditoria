import type { TrackingMonthStatus } from "../../api/types";
import { formatDayMonth } from "../../format";

interface Props {
  status: TrackingMonthStatus;
  /** Partial month (RF-13.10): entries up to this date; null = complete. */
  partialUntil?: string | null;
}

/** Text of a month without full numbers. A month without a cash flow is never shown as zero (RF-03.1.10). */
export function monthStatusText({ status, partialUntil }: Props): string | null {
  if (status === "NO_CASH_FLOW") return "sem extrato carregado";
  if (status === "TWO_CASH_FLOWS") return "dois fluxos no mês";
  if (partialUntil) return `parcial até ${formatDayMonth(partialUntil)}`;
  return null;
}

/** Small mark under the month header: "parcial até 20/10", "sem extrato carregado" or "dois fluxos no mês". */
export function MonthStatusNote(props: Props) {
  const text = monthStatusText(props);
  if (!text) return null;
  return <span className={props.partialUntil ? "month-note partial" : "month-note"}>{text}</span>;
}
