import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { BudgetVsActualMonth } from "../../api/types";
import { formatDayMonth, formatMonth, formatMonthShort, formatMoney, formatMoneyShort } from "../../format";

const statusNote: Record<BudgetVsActualMonth["status"], string> = {
  WITH_CASH_FLOW: "",
  NO_CASH_FLOW: "sem fluxo",
  TWO_CASH_FLOWS: "dois fluxos",
};

interface Props {
  months: BudgetVsActualMonth[];
  onChooseMonth: (month: string) => void;
}

/**
 * The 12 months of the fiscal year (RF-03.1.10). A month without a cash flow has no bar and gets the warning on the axis:
 * it never shows as zero.
 */
export function MonthsChart({ months, onChooseMonth }: Props) {
  const data = months.map((m) => {
    // Second axis line: the month status, the partial mark (RF-13.10) and, after the 12 months, the extended mark (RF-11.3)
    const partial = m.partialUntil ? `parcial até ${formatDayMonth(m.partialUntil)}` : "";
    const mark = [statusNote[m.status], partial, m.extended ? "prorrogado" : ""].filter(Boolean).join(" · ");
    return {
      month: m.month,
      label: mark ? `${formatMonthShort(m.month)}\n${mark}` : formatMonthShort(m.month),
      planned: m.status === "WITH_CASH_FLOW" ? m.planned : null,
      actual: m.status === "WITH_CASH_FLOW" ? m.actualExpense : null,
    };
  });
  return (
    <section className="block chart">
      <h2>Mês a mês no exercício</h2>
      <p className="muted">Clique num mês para abrir o detalhe.</p>
      <ResponsiveContainer width="100%" height={300}>
        <BarChart
          data={data}
          margin={{ left: 12, right: 12, bottom: 12 }}
          onClick={(state) => {
            const index = Number(state?.activeTooltipIndex);
            if (Number.isInteger(index) && data[index]) onChooseMonth(data[index].month);
          }}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" tick={<MonthTick />} interval={0} height={44} />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip
            labelFormatter={(_, items) => {
              const month = items?.[0]?.payload?.month as string | undefined;
              return month ? formatMonth(month) : "";
            }}
            formatter={(value) => (value === null ? "sem fluxo carregado" : formatMoney(Number(value)))}
          />
          <Legend />
          <Bar dataKey="planned" name="Previsto" fill="var(--color-planned)" isAnimationActive={false} cursor="pointer" />
          <Bar dataKey="actual" name="Despesa realizada" fill="var(--color-actual)" isAnimationActive={false} cursor="pointer" />
        </BarChart>
      </ResponsiveContainer>
    </section>
  );
}

/** Axis label on two lines: "set/26" and, when applicable, "sem fluxo". */
function MonthTick(props: { x?: number; y?: number; payload?: { value: string } }) {
  const [month, warning] = (props.payload?.value ?? "").split("\n");
  return (
    <text x={props.x} y={props.y} textAnchor="middle" className="axis-label">
      <tspan x={props.x} dy={14}>
        {month}
      </tspan>
      {warning && (
        <tspan x={props.x} dy={14} className="axis-label-warning">
          {warning}
        </tspan>
      )}
    </text>
  );
}
