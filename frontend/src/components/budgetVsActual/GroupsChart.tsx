import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { BudgetVsActualGroup } from "../../api/types";
import { formatMoney, formatMoneyShort } from "../../format";

/** Planned × actual bars per budget group (ADR 0004, Decision 6). The values come ready from the API. */
export function GroupsChart({ groups }: { groups: BudgetVsActualGroup[] }) {
  const data = groups.map((g) => ({ name: `${g.code} ${g.description}`, planned: g.planned, actual: g.actual }));
  return (
    <section className="block chart">
      <h2>Previsto × realizado por grupo</h2>
      <ResponsiveContainer width="100%" height={Math.max(260, data.length * 48)}>
        <BarChart data={data} layout="vertical" margin={{ left: 24, right: 24 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" tickFormatter={formatMoneyShort} />
          <YAxis type="category" dataKey="name" width={230} tick={{ fontSize: 12 }} />
          <Tooltip formatter={(value) => formatMoney(Number(value))} />
          <Legend />
          <Bar dataKey="planned" name="Previsto" fill="var(--color-planned)" isAnimationActive={false} />
          <Bar dataKey="actual" name="Realizado" fill="var(--color-actual)" isAnimationActive={false} />
        </BarChart>
      </ResponsiveContainer>
    </section>
  );
}
