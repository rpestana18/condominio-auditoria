// Texts of the month-by-month evolution (RF-12). Formatting only: every number comes ready from the API.
import type { MonthlyPoint, MonthlyPointStatus, MonthlySeries, MonthlyVariation } from "../../api/types";
import { formatDifference, formatMoney, formatMonthShort, formatSignedPercentage } from "../../format";

export const CONDO_FEE = "CONDO_FEE";

/** What shows in place of the amount when the month has none: never zero. */
export function pointText(point: MonthlyPoint, series: MonthlySeries): string {
  switch (point.status) {
    case "LOADED":
      return point.amount === null ? "—" : formatMoney(point.amount);
    case "NO_CASH_FLOW":
      return "sem fluxo carregado";
    case "TWO_CASH_FLOWS":
      return "dois fluxos no mês";
    case "NO_ENTRY":
      return series.key === CONDO_FEE ? "nenhum recebimento no mês" : "nenhum pagamento no mês";
  }
}

/**
 * Difference with the direction arrow, always in a neutral color (RF-12.4): "▲ +R$ 3.721,24 (+18,6%)". Without both
 * months, "sem base de comparação". Never a word about cause or trend.
 */
export function variationText(variation: MonthlyVariation | null | undefined): string {
  if (!variation) return "sem base de comparação";
  const arrow = variation.amount > 0 ? "▲" : variation.amount < 0 ? "▼" : "=";
  const percentage = variation.percentage === null ? "" : ` (${formatSignedPercentage(variation.percentage)})`;
  return `${arrow} ${formatDifference(variation.amount)}${percentage}`;
}

/** X axis label: "set/26" and, below it, the note of a month without an amount. */
export function axisLabel(month: string, statuses: MonthlyPointStatus[]): string {
  const notes = statuses.every((s) => s === "NO_CASH_FLOW")
    ? ["sem fluxo", "carregado"]
    : statuses.every((s) => s === "TWO_CASH_FLOWS")
      ? ["dois fluxos"]
      : [];
  return [formatMonthShort(month), ...notes].join("\n");
}
