import { Bar, BarChart, CartesianGrid, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { ExecutionPoint } from "../../api/types";
import { formatMonth } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import { openPoint, type ChartContext } from "./chartContext";
import { MONTH_AXIS_HEIGHT, MonthAxisTick } from "./MonthAxisTick";
import { formatPercentageAxis, monthMoney, monthPercentage, monthAxisLabel } from "./formatting";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  points: ExecutionPoint[];
  context: ChartContext;
}

/**
 * Chart 1 (RF-11.11): execution of the Condomínio fund month by month (actual ÷ planned, in %, already calculated by the API),
 * with the 100% reference. A month without a cash flow has no bar and gets the note on the axis.
 */
export function MonthlyExecutionChart({ points, context }: Props) {
  const actions = points.map((p) => openPoint(context, p));
  const table: TableModel = {
    caption: "Execução mensal do fundo Condomínio",
    labelColumn: "Mês",
    columns: ["Previsto (R$)", "Despesa realizada (R$)", "Execução (%)"],
    rows: points.map((p, i) => ({
      key: p.month,
      label: formatMonth(p.month),
      cells: [
        { text: monthMoney(p.planned, p.status), onOpen: actions[i] },
        { text: monthMoney(p.actual, p.status), onOpen: actions[i] },
        { text: monthPercentage(p.execution, p.status), onOpen: actions[i] },
      ],
    })),
  };
  // A null value stays null: no bar, never zero
  const data = points.map((p, index) => ({ index, axis: monthAxisLabel(p.month, p.status), execution: p.execution ?? null }));

  return (
    <IndicatorCard
      number={1}
      title="Execução mensal do fundo Condomínio"
      period={context.period}
      unit="%"
      dataAsOf={context.dataAsOf}
      hint="Barra = despesa realizada ÷ previsto do mês. Linha tracejada = 100%. Clique numa barra para abrir o mês no previsto × realizado."
      table={<AlternativeTable model={table} />}
    >
      <ResponsiveContainer width="100%" height={300}>
        <BarChart data={data} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
          <YAxis tickFormatter={formatPercentageAxis} width={56} />
          <Tooltip content={tableTooltip(table)} />
          <ReferenceLine
            y={100}
            ifOverflow="extendDomain"
            stroke="var(--reference)"
            strokeDasharray="6 4"
            label={{ value: "100%", position: "insideTopRight", fill: "var(--text-muted)", fontSize: 12 }}
          />
          <Bar
            dataKey="execution"
            name="Execução"
            fill="var(--color-neutral)"
            isAnimationActive={false}
            cursor="pointer"
            onClick={(item) => actions[(item.payload as { index: number }).index]?.()}
          />
        </BarChart>
      </ResponsiveContainer>
    </IndicatorCard>
  );
}
