import { Bar, CartesianGrid, ComposedChart, Legend, Line, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { CashFlowMonth } from "../../api/types";
import { formatMoney, formatMoneyShort, formatMonthShort } from "../../format";
import { cashFlowMonthNote } from "./CashFlowTable";

/**
 * Inflows and outflows month by month (bars side by side) with the line of the accumulated balance (RF-13.11, N2):
 * shows when the outflows were smaller than the inflows. A month without a cash flow has no bar, never zero.
 */
export function CashFlowChart({ months }: { months: CashFlowMonth[] }) {
  const data = months.map((m) => ({
    month: formatMonthShort(m.month),
    note: cashFlowMonthNote(m),
    inflows: m.inflows ?? null,
    outflows: m.outflows ?? null,
    balance: m.closingBalance ?? null,
  }));
  return (
    <section className="block">
      <h2>Entradas e saídas mês a mês</h2>
      <ResponsiveContainer width="100%" height={280}>
        <ComposedChart data={data} margin={{ left: 16, right: 16 }}>
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
          <Bar dataKey="inflows" name="Entradas" fill="var(--color-planned)" isAnimationActive={false} />
          <Bar dataKey="outflows" name="Saídas" fill="var(--color-actual)" isAnimationActive={false} />
          <Line dataKey="balance" name="Saldo acumulado" stroke="var(--series-3)" strokeWidth={2} dot isAnimationActive={false} />
        </ComposedChart>
      </ResponsiveContainer>
    </section>
  );
}
