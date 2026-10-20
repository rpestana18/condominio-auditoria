import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { BalanceBridge } from "../../api/types";
import { formatDifference, formatMoneyShort } from "../../format";

/**
 * Items of the bridge as horizontal bars (amounts from the API). Neutral tones: one for items that add to the balance,
 * another for items that take from it. "Não explicado" different from zero uses the warning color.
 */
export function BridgeChart({ bridge }: { bridge: BalanceBridge }) {
  const data = bridge.items
    .filter((i) => i.amount !== 0 || i.code === "UNEXPLAINED")
    .map((i) => ({ label: i.label, amount: i.amount, code: i.code }));
  if (data.length === 0) return null;
  const color = (code: string, amount: number) => {
    if (code === "UNEXPLAINED" && bridge.closes === false) return "var(--warning)";
    return amount >= 0 ? "var(--series-1)" : "var(--series-2)";
  };
  return (
    <section className="block">
      <h2>Parcelas da variação do saldo</h2>
      <ResponsiveContainer width="100%" height={Math.max(220, data.length * 42)}>
        <BarChart data={data} layout="vertical" margin={{ left: 24, right: 24 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" tickFormatter={formatMoneyShort} />
          <YAxis type="category" dataKey="label" width={260} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value) => formatDifference(Number(value))} />
          <Bar dataKey="amount" name="Valor" isAnimationActive={false}>
            {data.map((d) => (
              <Cell key={d.code} fill={color(d.code, d.amount)} />
            ))}
          </Bar>
        </BarChart>
      </ResponsiveContainer>
      <p className="tracking-legend">
        <span className="legend-swatch" style={{ background: "var(--series-1)" }} /> soma ao saldo
        <span className="legend-swatch" style={{ background: "var(--series-2)" }} /> reduz o saldo
      </p>
    </section>
  );
}
