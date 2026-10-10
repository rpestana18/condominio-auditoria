import { formatMoney } from "../format";

interface Props {
  title: string;
  value: number;
  highlight?: "positive" | "negative";
  hint?: string;
}

export function NumberCard({ title, value, highlight, hint }: Props) {
  return (
    <div className="number-card" title={hint}>
      <span className="card-title">{title}</span>
      <strong className={highlight}>{formatMoney(value)}</strong>
    </div>
  );
}
