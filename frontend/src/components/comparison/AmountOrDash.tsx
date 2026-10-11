import { formatMoneyOrDash } from "../../format";
import { SourcedValue } from "../budgetVsActual/SourcedValue";

interface Props {
  value: number | null | undefined;
  /** Present when the value has a target: clicking opens budget vs actual with the evidence. */
  onOpen?: () => void;
}

/** Comparison value: null becomes "—" (never R$ 0,00); with a target, it becomes a link to the entries. */
export function AmountOrDash({ value, onOpen }: Props) {
  if (value === null || value === undefined || !onOpen) return <>{formatMoneyOrDash(value)}</>;
  return <SourcedValue value={value} onOpen={onOpen} />;
}
