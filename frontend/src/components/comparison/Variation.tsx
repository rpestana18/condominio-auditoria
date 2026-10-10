import type { FiscalYearVariation } from "../../api/types";
import { formatDifference, formatSignedPercentage } from "../../format";

/**
 * Variation against the previous fiscal year in the list, already calculated by the backend: R$ and %.
 * A zero base comes flagged as "nova no exercício", without a percentage. No variation (oldest fiscal year): "—".
 * Neutral color on purpose: the variation is a fact, not an alert.
 */
export function Variation({ variation }: { variation: FiscalYearVariation | null | undefined }) {
  if (!variation) return <span className="muted">—</span>;
  return (
    <>
      {formatDifference(variation.amount)}
      {variation.newInFiscalYear ? (
        <small className="note">nova no exercício</small>
      ) : (
        variation.percentage !== null &&
        variation.percentage !== undefined && <small className="note">{formatSignedPercentage(variation.percentage)}</small>
      )}
    </>
  );
}
