// Texts of the indicator charts. Formatting only: every number comes ready from GET /indicators.
import type { IndicatorMonthStatus } from "../../api/types";
import { formatMonthShort, formatMoney, formatPercentage } from "../../format";

/** What shows in place of the number when the month has no numbers (RF-11.11: never zero). */
export const statusText: Record<IndicatorMonthStatus, string> = {
  WITH_CASH_FLOW: "",
  NO_CASH_FLOW: "sem fluxo carregado",
  TWO_CASH_FLOWS: "dois fluxos no mês",
};

/**
 * Value of a month for the table and the chart tooltip. Outside WITH_CASH_FLOW, the month status ("sem fluxo carregado");
 * with a cash flow but no value (e.g. zero planned, no percentage), "—".
 */
export function monthText(
  value: number | null | undefined,
  status: IndicatorMonthStatus,
  format: (value: number) => string,
): string {
  if (status !== "WITH_CASH_FLOW") return statusText[status];
  return value === null || value === undefined ? "—" : format(value);
}

export const monthMoney = (value: number | null | undefined, status: IndicatorMonthStatus) =>
  monthText(value, status, formatMoney);

export const monthPercentage = (value: number | null | undefined, status: IndicatorMonthStatus) =>
  monthText(value, status, formatPercentage);

/**
 * X axis label: "set/26" and, below it, the month's notes (one per line). A month without a cash flow gets
 * "sem fluxo / carregado" on the axis, since it has neither a bar nor a point.
 */
export function monthAxisLabel(month: string, status: IndicatorMonthStatus, ...otherNotes: (string | null | undefined)[]): string {
  const notes = status === "NO_CASH_FLOW" ? ["sem fluxo", "carregado"] : status === "TWO_CASH_FLOWS" ? ["dois fluxos"] : [];
  return [formatMonthShort(month), ...notes, ...otherNotes.filter(Boolean)].join("\n");
}

/** Axis in %: 100 becomes "100%". Only formats the number Recharts picked for the axis tick. */
export const formatPercentageAxis = (value: number) => `${value.toLocaleString("pt-BR")}%`;
