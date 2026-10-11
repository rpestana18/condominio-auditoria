import { formatMoney } from "../../format";

interface Props {
  value: number;
  /** Opens the entries that make up the number (RF-03.1.12). */
  onOpen: () => void;
  className?: string;
}

/** Amount in reais that leads to the source entries. */
export function SourcedValue({ value, onOpen, className }: Props) {
  return (
    <button className={`sourced-value ${className ?? ""}`} onClick={(e) => {
        e.stopPropagation(); // inside a clickable row, opens only the evidence
        onOpen();
      }} title="Ver os lançamentos de origem">
      {formatMoney(value)}
    </button>
  );
}
