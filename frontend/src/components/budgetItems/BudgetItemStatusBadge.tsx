import type { BudgetItemStatus } from "../../api/types";
import { budgetItemStatusLabel } from "../budgetVsActual/labels";

const classes: Record<BudgetItemStatus, string> = { CONFIRMED: "ok", SUGGESTED: "warning", REJECTED: "neutral" };

export function BudgetItemStatusBadge({ status }: { status: BudgetItemStatus | null | undefined }) {
  if (!status) return <span className="badge neutral">Sem rubrica</span>;
  return <span className={`badge ${classes[status]}`}>{budgetItemStatusLabel[status]}</span>;
}
