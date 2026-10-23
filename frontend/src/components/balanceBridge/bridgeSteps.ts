import type { BalanceBridge, BalanceBridgeItemCode } from "../../api/types";
import { formatPercentage } from "../../format";
import { PROFILE_NOT_CONFIRMED, topNotes, type PendingSteps } from "../pendingSteps";

/** Pending steps of a bridge, from its mapping fields and the PROFILE_NOT_CONFIRMED warning. */
export function bridgeSteps(bridge: BalanceBridge): PendingSteps {
  return {
    budgetId: bridge.budget?.id ?? null,
    unmappedAccounts: bridge.mapping?.unmappedAccounts ?? 0,
    unmappedAmount: bridge.mapping?.unmappedAmount ?? null,
    accounts: bridge.mapping?.accounts ?? [],
    profilesNotConfirmed: bridge.warnings.some((w) => w.code === PROFILE_NOT_CONFIRMED),
    provisional: !!bridge.mapping?.provisional,
  };
}

/** Items that depend on the account mapping: provisional while the API flags the mapping as provisional. */
const MAPPING_ITEMS: ReadonlySet<BalanceBridgeItemCode> = new Set<BalanceBridgeItemCode>(["LINE_SAVINGS", "OUTSIDE_BUDGET"]);

export const isProvisionalItem = (bridge: BalanceBridge, code: BalanceBridgeItemCode) =>
  !!bridge.mapping?.provisional && MAPPING_ITEMS.has(code);

/** Tooltip of the "provisório" badge, with the limit sent by the API. */
export function provisionalTitle(bridge: BalanceBridge): string {
  const limit = bridge.mapping ? formatPercentage(bridge.mapping.provisionalLimitPercentage) : "";
  return `Depende do de-para: mais de ${limit} dos débitos do período estão em contas sem linha da PO. A ponte continua fechando.`;
}

/**
 * Notes on top of the bridge: the partial month (in the summary), "a ponte não fecha" (badge and row) and the steps
 * are shown elsewhere, so they are not repeated.
 */
export const bridgeNotes = (bridge: BalanceBridge) => topNotes(bridge.warnings, ["PARTIAL_MONTH", "BRIDGE_DOES_NOT_CLOSE"]);
