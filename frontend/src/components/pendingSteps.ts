import type { BlockAccount, CalculationWarning, LineReviewState } from "../api/types";

/** Warning codes that become steps of the "Antes de ler estes números" box instead of a text on top. */
export const NO_CONFIRMED_MAPPING = "NO_CONFIRMED_MAPPING";
export const PROFILE_NOT_CONFIRMED = "PROFILE_NOT_CONFIRMED";
export const LINES_NOT_REVIEWED = "LINES_NOT_REVIEWED";

/**
 * The exceptions to know before reading the numbers (Q54, 29/10/2026): only the cash flow accounts without a line found
 * in the budget. The budget lines read and the suggested line types are valid by themselves and the automatic mapping
 * already counts, so none of them is a step. Counts and amounts come from the API; the screen only shows them.
 */
export interface PendingSteps {
  /** Budget the steps refer to (the links open its mapping and its line types). */
  budgetId: string | null;
  /** RF-13.12: the review of the budget lines, when the API still says it is pending (never since Q54). */
  linesToReview?: LineReviewState | null;
  /** Cash flow accounts without a line found (the Admin's "sem linha da PO" not included). */
  unmappedAccounts: number;
  /** Their amount in "sem linha da PO", when the API sends it. */
  unmappedAmount: number | null;
  /** Accounts themselves, largest amount first (as sent by the API). */
  accounts: BlockAccount[];
  /** Kept for the callers; since Q54 an unconfirmed type is not a step (the suggestion is valid). */
  profilesNotConfirmed: boolean;
  /** The API flags the bridge items that depend on the mapping as provisional (absent outside the bridge). */
  provisional?: boolean;
}

export const hasPendingSteps = (steps: PendingSteps) => !!steps.linesToReview || steps.unmappedAccounts > 0;

/** The review state when it is still pending, as a step of the box (RF-13.12). */
export const linesToReview = (review: LineReviewState | null | undefined) => (review && !review.reviewed ? review : null);

/** "PO aprovada", at the review of the lines. */
export const reviewLink = (budgetId: string | null) => (budgetId ? `/previsoes/${budgetId}#conferencia` : "/previsoes");

/** "De-para" screen of the budget, filtered on the accounts without a line found. */
export function mappingLink(budgetId: string | null): string {
  const search = new URLSearchParams();
  if (budgetId) search.set("po", budgetId);
  search.set("filtro", "UNMAPPED");
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
  const skip = new Set([LINES_NOT_REVIEWED, NO_CONFIRMED_MAPPING, PROFILE_NOT_CONFIRMED, ...shownElsewhere]);
  return [...new Set(warnings.filter((w) => !skip.has(w.code)).map((w) => w.text))];
}
