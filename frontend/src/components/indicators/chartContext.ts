import { useNavigate } from "react-router";
import { budgetVsActualAddress, type BudgetVsActualDestination } from "../comparison/navigation";

/** Action that opens a number in budget vs actual, with the evidence (RF-11.12). */
export type OpenInBudgetVsActual = (destination: BudgetVsActualDestination) => () => void;

/** Same address used by "Comparar exercícios" (?po=&periodo=&fundo=&alvo=). */
export function useOpenInBudgetVsActual(): OpenInBudgetVsActual {
  const navigate = useNavigate();
  return (destination) => () => navigate(budgetVsActualAddress(destination));
}

/** What every chart needs besides its own series: where the numbers came from and how to open the evidence. */
export interface ChartContext {
  /** Budget of the fiscal year shown (comes in the response, even when the filter is empty). */
  budgetId: string;
  /** The screen's fund filter; goes along to budget vs actual. */
  fundId: string | null;
  /** E.g. "09/2026 a 08/2027 (2026/2027)". */
  period: string;
  /** Most recent upload of the cash flows used (ISO); null without a loaded cash flow. */
  dataAsOf: string | null | undefined;
  open: OpenInBudgetVsActual;
}

/** Point of a monthly series with the evidence target that comes from the API. */
interface PointWithTarget {
  month: string;
  status: string;
  target?: string | null;
}

/**
 * Action of a monthly point: opens the month in budget vs actual with the point's target. A month without numbers
 * (no cash flow or two cash flows) or without a target has nothing to open: returns `undefined`.
 */
export function openPoint(context: ChartContext, point: PointWithTarget, fundId = context.fundId): (() => void) | undefined {
  if (point.status !== "WITH_CASH_FLOW" || !point.target) return undefined;
  return context.open({ budgetId: context.budgetId, period: point.month, target: point.target, fundId });
}
