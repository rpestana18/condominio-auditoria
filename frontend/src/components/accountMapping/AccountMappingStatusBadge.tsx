import type { AccountMappingStatus } from "../../api/types";
import { accountMappingStatusLabel } from "../budgetVsActual/labels";

const classes: Record<AccountMappingStatus, string> = { CONFIRMED: "ok", SUGGESTED: "warning", REJECTED: "neutral" };

export function AccountMappingStatusBadge({ status }: { status: AccountMappingStatus | null | undefined }) {
  if (!status) return <span className="badge neutral">Sem de-para</span>;
  return <span className={`badge ${classes[status]}`}>{accountMappingStatusLabel[status]}</span>;
}
