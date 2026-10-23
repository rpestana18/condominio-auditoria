import type { BlockAccount, CalculationWarning } from "../api/types";

/** Warning codes that become steps of the "Antes de ler estes números" box instead of a text on top. */
export const NO_CONFIRMED_MAPPING = "NO_CONFIRMED_MAPPING";
export const PROFILE_NOT_CONFIRMED = "PROFILE_NOT_CONFIRMED";

/**
 * What is still missing before the numbers can be read (RF-03.1.10 and RF-13.3): accounts without a confirmed mapping
 * and lines whose type was not confirmed. Counts and amounts come from the API; the screen only shows them.
 */
export interface PendingSteps {
  /** Budget the steps refer to (the links open its mapping and its line types). */
  budgetId: string | null;
  /** Accounts with a code and without a confirmed mapping. */
  unmappedAccounts: number;
  /** Their amount in "sem linha da PO", when the API sends it. */
  unmappedAmount: number | null;
  /** Accounts themselves, largest amount first (as sent by the API). */
  accounts: BlockAccount[];
  profilesNotConfirmed: boolean;
  /** The API flags the bridge items that depend on the mapping as provisional (absent outside the bridge). */
  provisional?: boolean;
}

export const hasPendingSteps = (steps: PendingSteps) => steps.unmappedAccounts > 0 || steps.profilesNotConfirmed;

/** "De-para" screen of the budget, filtered on the accounts without a confirmed mapping. */
export function mappingLink(budgetId: string | null): string {
  const search = new URLSearchParams();
  if (budgetId) search.set("po", budgetId);
  search.set("filtro", "PENDING");
  return `/depara?${search.toString()}`;
}

/** Screen where the Admin confirms the type of each line of the budget. */
export const profilesLink = (budgetId: string | null) => (budgetId ? `/previsoes/${budgetId}/tipos` : "/previsoes");

/** "1 conta" / "88 contas". */
export const accountsText = (count: number) => `${count} ${count === 1 ? "conta" : "contas"}`;

/**
 * Warnings that stay on top as short notes: the ones turned into steps (or shown elsewhere on the screen, as listed in
 * `shownElsewhere`) are left out, and the same text appears only once.
 */
export function topNotes(warnings: CalculationWarning[], shownElsewhere: readonly string[] = []): string[] {
  const skip = new Set([NO_CONFIRMED_MAPPING, PROFILE_NOT_CONFIRMED, ...shownElsewhere]);
  return [...new Set(warnings.filter((w) => !skip.has(w.code)).map((w) => w.text))];
}
