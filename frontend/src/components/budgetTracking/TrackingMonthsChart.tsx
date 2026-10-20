import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { TrackingMonth } from "../../api/types";
import { formatMoney, formatMoneyShort, formatMonthShort } from "../../format";
import { monthStatusText } from "./MonthStatusNote";

/**
 * Planned × actual in budget lines, month by month (numbers from the API). A month without numbers has no bar
 * and is named on the axis ("sem extrato carregado"), never drawn as zero.
 */
export function TrackingMonthsChart({ months }: { months: TrackingMonth[] }) {
  const data = months.map((m) => ({
    month: formatMonthShort(m.month),
    note: monthStatusText(m),
    planned: m.planned ?? null,
    actual: m.actualInLines ?? null,
  }));
  return (
    <section className="block">
      <h2>Previsto × realizado em linhas da PO, mês a mês</h2>
      <ResponsiveContainer width="100%" height={260}>
        <BarChart data={data} margin={{ left: 16, right: 16 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="month" />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip
            formatter={(value) => formatMoney(Number(value))}
            labelFormatter={(label, payload) => {
              const note = payload?.[0]?.payload?.note as string | null | undefined;
              return note ? `${label} · ${note}` : String(label);
            }}
          />
          <Legend />
          <Bar dataKey="planned" name="Previsto" fill="var(--color-planned)" isAnimationActive={false} />
          <Bar dataKey="actual" name="Realizado em linhas da PO" fill="var(--color-actual)" isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
      <p className="muted">
        Meses sem barra: {data.filter((d) => d.actual === null).map((d) => d.month).join(", ") || "nenhum"} (sem números, não é zero).
      </p>
    </section>
  );
}
