import type { BridgeItem, BridgeLine, BridgePart } from "../../api/types";
import type { EvidenceTarget } from "../budgetVsActual/evidence";

/** Opens the entries behind a bridge number (RF-13.6: each item leads to its entries). */
export type OpenBridgeEvidence = (target: EvidenceTarget) => void;

/** Parts of "Fora da PO" and the transfers item, in the targets of the budget vs actual evidence API. */
const partTargets: Record<string, string> = {
  TO_REALLOCATE: "TO_REALLOCATE",
  WITHOUT_BUDGET_LINE: "NO_BUDGET_LINE",
  ADJUSTMENTS: "ADJUSTMENTS",
};

export function partTarget(item: BridgeItem, part: BridgePart): EvidenceTarget | null {
  if (item.code === "OUTSIDE_BUDGET" && partTargets[part.code]) return { target: partTargets[part.code], title: part.label };
  if (item.code === "INTER_FUND_TRANSFERS") return { target: "TRANSFERS", title: item.label };
  return null;
}

export const lineTarget = (line: BridgeLine): EvidenceTarget => ({
  target: `line:${line.lineId}`,
  title: `${line.code} ${line.description}`,
});
