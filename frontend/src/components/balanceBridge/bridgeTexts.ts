import type { BalanceBridge, BalanceBridgeItemCode } from "../../api/types";
import { formatDate, formatDifference, formatMoneyOrDash, formatMonth, formatMonthName } from "../../format";
import { inProgressText } from "../budgetTracking/MonthStatusNote";

/**
 * Item codes of the bridge that have no percentage of the variation. APPORTIONMENT_FUNDS ("Fundos de rateio: entradas −
 * saídas", Q49) only exists in the bridge of every fund (fund=all), and because of it the percentages do not add up to 100%.
 */
export const ITEMS_WITHOUT_PERCENTAGE: ReadonlySet<BalanceBridgeItemCode> = new Set<BalanceBridgeItemCode>(["APPORTIONMENT_FUNDS"]);

/** Whether the item shows a percentage of the variation (the value itself comes from the API). */
export const hasPercentage = (code: BalanceBridgeItemCode) => !ITEMS_WITHOUT_PERCENTAGE.has(code);

/**
 * Discreet note for the bridge of every fund (Q49): the percentages do not add up to 100% because the apportionment funds
 * item has none. Uses the label sent by the API. Null when the bridge has no such item.
 */
export function percentagesNote(bridge: BalanceBridge): string | null {
  const item = bridge.items.find((i) => !hasPercentage(i.code));
  if (!item) return null;
  return `Os percentuais não somam 100%: “${item.label}” não tem percentual da variação.`;
}

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
 * The month in progress, shown apart from the total, only with what came in and went out up to the date (Q50):
 * "Outubro, mês em andamento · parcial até 20/10: entradas R$ 459.045,72, saídas R$ 374.579,86, variação +R$ 84.465,86,
 * fora do total". Null when the period has no partial month.
 */
export function partialMonthText(bridge: BalanceBridge): string | null {
  const partial = bridge.partialMonth;
  if (!partial) return null;
  const variation = partial.variation == null ? "variação não apurada" : `variação ${formatDifference(partial.variation)}`;
  return (
    `${formatMonthName(partial.month)}, ${inProgressText(partial.partialUntil)}: ` +
    `entradas ${formatMoneyOrDash(partial.inflows)}, saídas ${formatMoneyOrDash(partial.outflows)}, ${variation}, fora do total`
  );
}
