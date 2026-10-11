import { CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { CumulativePoint } from "../../api/types";
import { formatMonth, formatMoneyShort } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import { openPoint, type ChartContext } from "./chartContext";
import { MONTH_AXIS_HEIGHT, MonthAxisTick } from "./MonthAxisTick";
import { monthMoney, monthAxisLabel } from "./formatting";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  points: CumulativePoint[];
  context: ChartContext;
}

/**
 * Chart 3 (RF-11.11): planned and actual summed up to each month, already summed by the API (only months with a cash flow
 * enter the sum). A month without a cash flow has no point: the line breaks there, it never drops to zero.
 */
export function CumulativeChart({ points, context }: Props) {
  const actions = points.map((p) => openPoint(context, p));
  const table: TableModel = {
    caption: "Previsto × realizado acumulado do fundo Condomínio",
    labelColumn: "Mês",
    columns: ["Previsto acumulado (R$)", "Realizado acumulado (R$)"],
    rows: points.map((p, i) => ({
      key: p.month,
      label: formatMonth(p.month),
      cells: [
        { text: monthMoney(p.cumulativePlanned, p.status), onOpen: actions[i] },
        { text: monthMoney(p.cumulativeActual, p.status), onOpen: actions[i] },
      ],
    })),
  };
  const data = points.map((p, index) => ({
    index,
    axis: monthAxisLabel(p.month, p.status),
    planned: p.cumulativePlanned ?? null,
    actual: p.cumulativeActual ?? null,
  }));

  return (
    <IndicatorCard
      number={3}
      title="Previsto × realizado acumulado do fundo Condomínio"
      period={context.period}
      unit="R$"
      dataAsOf={context.dataAsOf}
      hint="Soma dos meses com fluxo até cada mês. Clique num ponto para abrir aquele mês no previsto × realizado."
      table={<AlternativeTable model={table} />}
    >
      <ResponsiveContainer width="100%" height={300}>
        <LineChart
          data={data}
          margin={{ top: 16, left: 4, right: 24, bottom: 4 }}
          onClick={(state) => {
            const index = Number(state?.activeTooltipIndex);
            if (Number.isInteger(index)) actions[index]?.();
          }}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip content={tableTooltip(table)} />
          <Legend />
          <Line
            dataKey="planned"
            name="Previsto acumulado"
            stroke="var(--color-planned)"
            strokeWidth={2}
            strokeDasharray="6 4"
            dot={{ r: 4 }}
            isAnimationActive={false}
            cursor="pointer"
          />
          <Line
            dataKey="actual"
            name="Realizado acumulado"
            stroke="var(--color-neutral)"
            strokeWidth={2}
            dot={{ r: 4 }}
            isAnimationActive={false}
            cursor="pointer"
          />
        </LineChart>
      </ResponsiveContainer>
    </IndicatorCard>
  );
}
