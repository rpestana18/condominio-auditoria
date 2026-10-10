import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { GroupIndicatorSeries } from "../../api/types";
import { formatMonth, formatMoneyShort } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import { openPoint, type ChartContext } from "./chartContext";
import { seriesColor } from "./colors";
import { MONTH_AXIS_HEIGHT, MonthAxisTick } from "./MonthAxisTick";
import { monthMoney, monthAxisLabel } from "./formatting";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  series: GroupIndicatorSeries[];
  context: ChartContext;
}

/**
 * Chart 4 (RF-11.11): actual of each budget group (1.1 to 1.8) month by month, in stacked bars.
 * Each slice of the bar opens that month's group in budget vs actual.
 */
export function ActualByGroupChart({ series, context }: Props) {
  // Every series brings the same months of the fiscal year, in the same order
  const months = series[0]?.points ?? [];
  const actions = series.map((s) => s.points.map((p) => openPoint(context, p)));
  const table: TableModel = {
    caption: "Realizado por grupo da PO, mês a mês",
    labelColumn: "Mês",
    columns: series.map((s) => `${s.code} ${s.description} (R$)`),
    rows: months.map((m, i) => ({
      key: m.month,
      label: formatMonth(m.month),
      cells: series.map((s, g) => {
        const point = s.points[i];
        return { text: point ? monthMoney(point.amount, point.status) : "—", onOpen: actions[g][i] };
      }),
    })),
  };
  // Keys "g0", "g1"...: Recharts would read "1.1" as an object path
  const data = months.map((m, index) => {
    const row: Record<string, number | string | null> = { index, axis: monthAxisLabel(m.month, m.status) };
    series.forEach((s, g) => (row[`g${g}`] = s.points[index]?.amount ?? null));
    return row;
  });

  return (
    <IndicatorCard
      number={4}
      title="Realizado por grupo da PO"
      period={context.period}
      unit="R$"
      dataAsOf={context.dataAsOf}
      hint="Cada cor é um grupo da PO. Clique num pedaço da barra para abrir aquele grupo no mês."
      table={<AlternativeTable model={table} />}
    >
      {series.length === 0 ? (
        <p className="muted">Nenhum grupo com números neste exercício.</p>
      ) : (
        <ResponsiveContainer width="100%" height={360}>
          <BarChart data={data} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
            <CartesianGrid strokeDasharray="3 3" vertical={false} />
            <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
            <YAxis tickFormatter={formatMoneyShort} width={80} />
            <Tooltip content={tableTooltip(table)} />
            <Legend />
            {series.map((s, g) => (
              <Bar
                key={s.code}
                dataKey={`g${g}`}
                name={`${s.code} ${s.description}`}
                stackId="groups"
                fill={seriesColor(g)}
                isAnimationActive={false}
                cursor="pointer"
                onClick={(item) => actions[g][(item.payload as { index: number }).index]?.()}
              />
            ))}
          </BarChart>
        </ResponsiveContainer>
      )}
    </IndicatorCard>
  );
}
