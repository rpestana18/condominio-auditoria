import { Link } from "react-router";
import type { CalculationWarning } from "../api/types";

/** Code of the warning about accounts without a confirmed account mapping (RF-03.1.10), in the grid and in the bridge. */
export const NO_CONFIRMED_MAPPING = "NO_CONFIRMED_MAPPING";

/** Splits the API warnings: the mapping one is shown discreetly next to the affected numbers, the others on top. */
export function splitMappingWarning(warnings: CalculationWarning[]): {
  mapping: CalculationWarning | null;
  others: CalculationWarning[];
} {
  return {
    mapping: warnings.find((w) => w.code === NO_CONFIRMED_MAPPING) ?? null,
    others: warnings.filter((w) => w.code !== NO_CONFIRMED_MAPPING),
  };
}

/**
 * Discreet note with the NO_CONFIRMED_MAPPING text written by the API. It only points out that the numbers depend on
 * the account mapping; the check is done on the "De-para" screen.
 */
export function MappingWarningNote({ warning }: { warning: CalculationWarning }) {
  return (
    <small className="note mapping-note">
      {warning.text} <Link to="/depara">Conferir o de-para</Link>
    </small>
  );
}
