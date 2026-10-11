import { Bar, CartesianGrid, Cell, ComposedChart, Legend, Line, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { Rule20Point } from "../../api/types";
import { formatMonth, formatPercentage } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import { openPoint, type ChartContext } from "./chartContext";
import { MONTH_AXIS_HEIGHT, MonthAxisTick } from "./MonthAxisTick";
import { formatPercentageAxis, monthMoney, monthPercentage, monthAxisLabel, statusText } from "./formatting";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  points: Rule20Point[];
  /** Rule limit (Conv. 16.2), from the response's `limitPercentage` field. Null when no month has numbers. */
  limitPercentage: number | null | undefined;
  context: ChartContext;
}

/**
 * Chart 2 (RF-11.11): the month's overrun as a % of planned, with the limit line and the maximum scenario mark
 * (with "a realocar" and "sem linha da PO", RF-03.1.11). The only alert color on the screen (RF-11.13): the bar turns
 * red only when the point comes with `aboveLimit` true. The frontend does not compare values.
 */
export function Rule20Chart({ points, limitPercentage, context }: Props) {
  const actions = points.map((p) => openPoint(context, p));
  const table: TableModel = {
    caption: "Regra dos 20%: excesso do mês e cenário máximo",
    labelColumn: "Mês",
    columns: ["Excesso (R$)", "Excesso (%)", "Limite (%)", "Cenário máximo (R$)", "Cenário máximo (%)", "Marcas"],
    rows: points.map((p, i) => {
      const above = p.aboveLimit === true;
      return {
        key: p.month,
        label: formatMonth(p.month),
        cells: [
          { text: monthMoney(p.overrun, p.status), onOpen: actions[i] },
          { text: monthPercentage(p.percentage, p.status), onOpen: actions[i], alert: above },
          { text: monthPercentage(p.limitPercentage, p.status) },
          { text: monthMoney(p.maxScenario, p.status) },
          { text: monthPercentage(p.maxScenarioPercentage, p.status) },
          { text: marks(p), alert: above },
        ],
      };
    }),
  };
  const data = points.map((p, index) => ({
    index,
    axis: monthAxisLabel(p.month, p.status, p.provisional ? "provisório" : null),
    percentage: p.percentage ?? null,
    scenario: p.maxScenarioPercentage ?? null,
    above: p.aboveLimit === true,
  }));
  const limitText = limitPercentage === null || limitPercentage === undefined ? null : formatPercentage(limitPercentage);

  return (
    <IndicatorCard
      number={2}
      title="Regra dos 20% (Conv. 16.2)"
      period={context.period}
      unit="%"
      dataAsOf={context.dataAsOf}
      hint={
        `Barra = excesso do mês em % do previsto. Círculo = cenário máximo. ` +
        (limitText ? `Linha tracejada = limite de ${limitText}; barra em vermelho = acima do limite. ` : "") +
        `"provisório" no eixo: o mês tem valor a realocar ou conta sem linha da PO. Clique numa barra para abrir o mês.`
      }
      table={<AlternativeTable model={table} />}
    >
      <ResponsiveContainer width="100%" height={300}>
        <ComposedChart data={data} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
          <YAxis tickFormatter={formatPercentageAxis} width={56} />
          <Tooltip content={tableTooltip(table)} />
          <Legend />
          {limitPercentage !== null && limitPercentage !== undefined && (
            <ReferenceLine
              y={limitPercentage}
              ifOverflow="extendDomain"
              stroke="var(--reference)"
              strokeDasharray="6 4"
              label={{ value: `limite ${limitText}`, position: "insideTopRight", fill: "var(--text-muted)", fontSize: 12 }}
            />
          )}
          <Bar
            dataKey="percentage"
            name="Excesso do mês"
            fill="var(--color-neutral)"
            isAnimationActive={false}
            cursor="pointer"
            onClick={(item) => actions[(item.payload as { index: number }).index]?.()}
          >
            {/* Red only where the API flagged aboveLimit (RF-11.13) */}
            {data.map((d) => (
              <Cell key={d.index} fill={d.above ? "var(--critical)" : "var(--color-neutral)"} />
            ))}
          </Bar>
          <Line
            dataKey="scenario"
            name="Cenário máximo"
            stroke="none"
            legendType="circle"
            dot={{ r: 5, fill: "var(--surface)", stroke: "var(--text)", strokeWidth: 2 }}
            activeDot={false}
            isAnimationActive={false}
          />
        </ComposedChart>
      </ResponsiveContainer>
    </IndicatorCard>
  );
}

/** Marks of the month, only with what came from the API: "acima do limite", "provisório" or the month status. */
function marks(p: Rule20Point): string {
  if (p.status !== "WITH_CASH_FLOW") return statusText[p.status];
  const list = [p.aboveLimit ? "acima do limite" : null, p.provisional ? "provisório" : null].filter(Boolean);
  return list.length ? list.join(" · ") : "—";
}
