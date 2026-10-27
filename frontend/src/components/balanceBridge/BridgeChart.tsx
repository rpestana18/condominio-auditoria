import { Bar, BarChart, CartesianGrid, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { BalanceBridge } from "../../api/types";
import { formatDifference, formatMoneyShort } from "../../format";
import { isProvisionalItem } from "./bridgeSteps";

/**
 * Items of the bridge as horizontal bars (amounts from the API). Neutral tones: one for items that add to the balance,
 * another for items that take from it. "Não explicado" different from zero uses the warning color. Provisional items
 * (they depend on the account mapping) stay out of the chart, so they do not set its scale; they remain in the table.
 */
export function BridgeChart({ bridge }: { bridge: BalanceBridge }) {
  const provisional = bridge.items.filter((i) => isProvisionalItem(bridge, i.code));
  const data = bridge.items
    .filter((i) => !isProvisionalItem(bridge, i.code))
    // Items by line come without an amount while the budget lines are not reviewed (RF-13.12): out of the chart
    .filter((i) => i.amount != null && (i.amount !== 0 || i.code === "UNEXPLAINED"))
    .map((i) => ({ label: i.label, amount: i.amount ?? 0, code: i.code }));
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
      {provisional.length > 0 && (
        <p className="muted">
          Fora do gráfico até o de-para ser confirmado (provisório): {provisional.map((i) => `“${i.label}”`).join(" e ")}.
        </p>
      )}
    </section>
  );
}
