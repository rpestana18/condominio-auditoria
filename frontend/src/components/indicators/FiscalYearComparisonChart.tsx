import { Bar, BarChart, CartesianGrid, Legend, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { IndicatorComparison } from "../../api/types";
import { formatMoneyShort, formatMoneyOrDash, formatPercentage } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import type { ChartContext } from "./chartContext";
import { seriesColor } from "./colors";
import { formatPercentageAxis } from "./formatting";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  comparison: IndicatorComparison;
  context: ChartContext;
}

/**
 * Chart 7 (RF-11.11 and RF-11.6): monthly planned of each group in each fiscal year (grouped bars) and the
 * cumulative execution of each fiscal year. Values in the order of `comparison.fiscalYears`, ready from the API.
 *
 * Click: each fiscal year brings its `budgetId` and period; each group brings its `target` in each fiscal year (same order).
 * A group's bar opens budget vs actual for that fiscal year with the group's evidence already open; the execution bar
 * opens the fiscal year on its period. The printed column (null `budgetId`) and a group without a target have no link.
 */
export function FiscalYearComparisonChart({ comparison, context }: Props) {
  const { groups } = comparison;

  /** Action that opens fiscal year `i` in budget vs actual (with the target, if any); `undefined` without a budget or period. */
  const openFiscalYear = (i: number, target?: string | null): (() => void) | undefined => {
    const { budgetId, period } = comparison.fiscalYears[i];
    if (!budgetId || !period) return undefined;
    return context.open({ budgetId, period, target, fundId: context.fundId });
  };
  /** Action of the bar of group `g` in fiscal year `i`: only with a target (without one there is no evidence to open). */
  const openGroup = (g: number, i: number) => {
    const target = groups[g].targets[i];
    return target ? openFiscalYear(i, target) : undefined;
  };
  const executionActions = comparison.fiscalYears.map((_, i) => openFiscalYear(i));

  const groupsTable: TableModel = {
    caption: "Previsto do mês por grupo da PO em cada exercício",
    labelColumn: "Grupo",
    columns: comparison.fiscalYears.map((fy) => `${fy.label} (R$)`),
    rows: groups.map((g, groupIndex) => ({
      key: g.code,
      label: `${g.code} ${g.description}`,
      cells: comparison.fiscalYears.map((_, i) => ({
        text: formatMoneyOrDash(g.monthlyPlanned[i]),
        onOpen: openGroup(groupIndex, i),
      })),
    })),
  };
  const executionTable: TableModel = {
    caption: "Execução acumulada de cada exercício",
    labelColumn: "Exercício",
    columns: ["Execução acumulada (%)"],
    rows: comparison.fiscalYears.map((fy, i) => ({
      key: fy.id,
      label: fy.label,
      cells: [{ text: formatPercentage(fy.execution), onOpen: executionActions[i] }],
    })),
  };

  // Keys "e0", "e1"... in fiscal year order; the axis shows only the group code (the name goes in the tooltip)
  const groupsData = groups.map((g, index) => {
    const row: Record<string, number | string | null> = { index, code: g.code };
    comparison.fiscalYears.forEach((_, i) => (row[`e${i}`] = g.monthlyPlanned[i] ?? null));
    return row;
  });
  const executionData = comparison.fiscalYears.map((fy, index) => ({ index, label: fy.label, execution: fy.execution ?? null }));
  const period = comparison.fiscalYears.map((fy) => fy.label).join(" × ");

  return (
    <IndicatorCard
      number={7}
      title="Comparação entre exercícios"
      period={period}
      unit="R$ (previsto do mês) e % (execução acumulada)"
      dataAsOf={context.dataAsOf}
      hint="Clique numa barra para abrir o grupo (ou o exercício) no previsto × realizado, com a evidência."
      table={
        <>
          <AlternativeTable model={groupsTable} />
          <AlternativeTable model={executionTable} />
        </>
      }
    >
      <h3>Previsto do mês por grupo</h3>
      <ResponsiveContainer width="100%" height={320}>
        <BarChart data={groupsData} margin={{ top: 8, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="code" interval={0} />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip content={tableTooltip(groupsTable)} />
          <Legend />
          {comparison.fiscalYears.map((fy, i) => (
            <Bar
              key={fy.id}
              dataKey={`e${i}`}
              name={fy.label}
              fill={seriesColor(i)}
              isAnimationActive={false}
              cursor={executionActions[i] ? "pointer" : undefined}
              onClick={(item) => openGroup((item.payload as { index: number }).index, i)?.()}
            />
          ))}
        </BarChart>
      </ResponsiveContainer>

      <h3>Execução acumulada de cada exercício</h3>
      <ResponsiveContainer width="100%" height={Math.max(140, executionData.length * 40 + 50)}>
        <BarChart data={executionData} layout="vertical" margin={{ top: 8, left: 8, right: 32, bottom: 8 }}>
          <CartesianGrid strokeDasharray="3 3" horizontal={false} />
          <XAxis type="number" tickFormatter={formatPercentageAxis} />
          <YAxis type="category" dataKey="label" width={220} interval={0} tick={{ fontSize: 12 }} />
          <Tooltip content={tableTooltip(executionTable)} />
          <ReferenceLine
            x={100}
            ifOverflow="extendDomain"
            stroke="var(--reference)"
            strokeDasharray="6 4"
            label={{ value: "100%", position: "insideTopRight", fill: "var(--text-muted)", fontSize: 12 }}
          />
          <Bar
            dataKey="execution"
            name="Execução acumulada"
            fill="var(--color-neutral)"
            isAnimationActive={false}
            cursor={executionActions.some(Boolean) ? "pointer" : undefined}
            onClick={(item) => executionActions[(item.payload as { index: number }).index]?.()}
          />
        </BarChart>
      </ResponsiveContainer>
    </IndicatorCard>
  );
}
