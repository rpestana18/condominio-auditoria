import type { AccountMappingStatus } from "../../api/types";
import { accountMappingStatusLabel } from "../budgetVsActual/labels";

const classes: Record<AccountMappingStatus, string> = { AUTOMATIC: "ok", CONFIRMED: "ok", SUGGESTED: "ok", REJECTED: "warning" };

/** Q54: the automatic link already counts; only "sem linha encontrada", low confidence and refused stand out. */
export function AccountMappingStatusBadge({ status, lowConfidence }: { status: AccountMappingStatus | null | undefined; lowConfidence?: boolean }) {
  if (!status) return <span className="badge warning">Sem linha encontrada</span>;
  return (
    <>
      <span className={`badge ${classes[status]}`}>{accountMappingStatusLabel[status]}</span>
      {lowConfidence && <span className="badge warning">baixa confiança</span>}
    </>
  );
}
