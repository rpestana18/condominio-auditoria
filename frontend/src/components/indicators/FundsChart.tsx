import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { FundIndicatorSeries } from "../../api/types";
import { formatMonth, formatMoneyShort } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import { openPoint, type ChartContext } from "./chartContext";
import { MONTH_AXIS_HEIGHT, MonthAxisTick } from "./MonthAxisTick";
import { monthMoney, monthAxisLabel } from "./formatting";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  series: FundIndicatorSeries[];
  context: ChartContext;
}

const fundName = (s: FundIndicatorSeries) => (s.lineCode ? `${s.lineCode} ${s.fund}` : s.fund);

/**
 * Chart 6 (RF-11.11 and RF-03.1.9): for each fund linked to a 1.9 line, the collection and the monthly planned,
 * in grouped bars. Clicking opens the fund on that month in budget vs actual.
 */
export function FundsChart({ series, context }: Props) {
  const models = series.map((s) => fundModel(s, context));
  return (
    <IndicatorCard
      number={6}
      title="Fundos: arrecadação × previsto"
      period={context.period}
      unit="R$"
      dataAsOf={context.dataAsOf}
      hint="Um gráfico por fundo ligado às linhas 1.9 da PO. Clique numa barra para abrir o fundo no mês."
      table={models.map(({ table }) => (
        <AlternativeTable key={table.caption} model={table} />
      ))}
    >
      {series.length === 0 ? (
        <p className="muted">Nenhum fundo ligado às linhas 1.9 da PO deste exercício.</p>
      ) : (
        series.map((s, i) => <SingleFundChart key={s.fundId} series={s} {...models[i]} />)
      )}
    </IndicatorCard>
  );
}

function fundModel(series: FundIndicatorSeries, context: ChartContext) {
  // The point's fund goes as a filter: budget vs actual opens only its panel
  const actions = series.points.map((p) => openPoint(context, p, series.fundId));
  const table: TableModel = {
    caption: `${fundName(series)}: arrecadação × previsto`,
    labelColumn: "Mês",
    columns: ["Previsto (R$)", "Arrecadado (R$)"],
    rows: series.points.map((p, i) => ({
      key: p.month,
      label: formatMonth(p.month),
      cells: [
        { text: monthMoney(p.planned, p.status), onOpen: actions[i] },
        { text: monthMoney(p.collected, p.status), onOpen: actions[i] },
      ],
    })),
  };
  return { actions, table };
}

interface FundProps {
  series: FundIndicatorSeries;
  actions: ((() => void) | undefined)[];
  table: TableModel;
}

function SingleFundChart({ series, actions, table }: FundProps) {
  const data = series.points.map((p, index) => ({
    index,
    axis: monthAxisLabel(p.month, p.status),
    planned: p.planned ?? null,
    collected: p.collected ?? null,
  }));
  const onClick = (item: { payload?: unknown }) => actions[(item.payload as { index: number }).index]?.();
  return (
    <>
      <h3>{fundName(series)}</h3>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={data} margin={{ top: 8, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip content={tableTooltip(table)} />
          <Legend />
          <Bar dataKey="planned" name="Previsto" fill="var(--color-planned)" isAnimationActive={false} cursor="pointer" onClick={onClick} />
          <Bar dataKey="collected" name="Arrecadado" fill="var(--color-neutral)" isAnimationActive={false} cursor="pointer" onClick={onClick} />
        </BarChart>
      </ResponsiveContainer>
    </>
  );
}
