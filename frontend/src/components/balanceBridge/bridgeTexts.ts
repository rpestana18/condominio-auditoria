import type { BalanceBridge } from "../../api/types";
import { formatDate, formatDayMonth, formatDifference, formatMonth, formatMonthName } from "../../format";

/**
 * Period of the bridge total, by the balance dates sent by the API: "de 30/04/2026 a 30/09/2026".
 * Without the dates (no complete month), falls back to the requested months.
 */
export function bridgePeriodText(bridge: BalanceBridge): string {
  if (bridge.openingDate && bridge.closingDate) {
    return `de ${formatDate(bridge.openingDate)} a ${formatDate(bridge.closingDate)}`;
  }
  return `${formatMonth(bridge.from)} a ${formatMonth(bridge.to)}`;
}

/**
 * The partial month, shown apart from the total: "Outubro, parcial até 20/10: +R$ 84.465,86, fora do total".
 * Null when the period has no partial month.
 */
export function partialMonthText(bridge: BalanceBridge): string | null {
  const partial = bridge.partialMonth;
  if (!partial) return null;
  const variation = partial.variation == null ? "variação não apurada" : formatDifference(partial.variation);
  return `${formatMonthName(partial.month)}, parcial até ${formatDayMonth(partial.partialUntil)}: ${variation}, fora do total`;
}
