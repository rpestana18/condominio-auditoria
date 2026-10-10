import { Bar, BarChart, CartesianGrid, Cell, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import type { LineDifference, LargestDifferences } from "../../api/types";
import { formatDifference, formatMoney, formatMoneyShort } from "../../format";
import { IndicatorCard } from "./IndicatorCard";
import type { ChartContext } from "./chartContext";
import { tableTooltip, AlternativeTable, type TableModel } from "./AlternativeTable";

interface Props {
  differences: LargestDifferences;
  context: ChartContext;
}

type Side = "above" | "below";

const sideLabel: Record<Side, string> = { above: "acima do previsto", below: "abaixo do previsto" };

/**
 * Chart 5 (RF-11.11): the budget lines furthest above and below planned in the cumulative figures (up to 10 on each side,
 * already chosen and ordered by the API), in R$. Clicking a bar opens the line in the cumulative view, with the entries.
 */
export function LargestDifferencesChart({ differences, context }: Props) {
  // Both lists in API order: first the furthest above, then the furthest below
  const items: { side: Side; d: LineDifference }[] = [
    ...differences.above.map((d) => ({ side: "above" as const, d })),
    ...differences.below.map((d) => ({ side: "below" as const, d })),
  ];
  const actions = items.map(({ d }) =>
    context.open({ budgetId: context.budgetId, period: "cumulative", target: d.target, fundId: context.fundId }),
  );
  const table: TableModel = {
    caption: "Maiores diferenças do acumulado por linha da PO (realizado menos previsto)",
    labelColumn: "Linha da PO",
    columns: ["Posição", "Previsto (R$)", "Realizado (R$)", "Diferença (R$)"],
    rows: items.map(({ side, d }, i) => ({
      key: d.lineId,
      label: `${d.code} ${d.description}`,
      cells: [
        { text: sideLabel[side] },
        { text: formatMoney(d.planned), onOpen: actions[i] },
        { text: formatMoney(d.actual), onOpen: actions[i] },
        { text: formatDifference(d.difference), onOpen: actions[i] },
      ],
    })),
  };
  const data = items.map(({ side, d }, index) => ({ index, side, name: `${d.code} ${d.description}`, difference: d.difference }));

  return (
    <IndicatorCard
      number={5}
      title="Maiores diferenças do acumulado por linha da PO"
      period={`${context.period}, acumulado dos meses com fluxo`}
      unit="R$"
      dataAsOf={context.dataAsOf}
      hint="Diferença = realizado menos previsto. Até 10 linhas acima e 10 abaixo do previsto. Clique numa barra para abrir a linha com os lançamentos."
      table={<AlternativeTable model={table} />}
    >
      {items.length === 0 ? (
        <p className="muted">Nenhuma linha com diferença no acumulado.</p>
      ) : (
        <ResponsiveContainer width="100%" height={Math.max(220, data.length * 30 + 50)}>
          <BarChart data={data} layout="vertical" margin={{ top: 8, left: 8, right: 32, bottom: 8 }}>
            <CartesianGrid strokeDasharray="3 3" horizontal={false} />
            <XAxis type="number" tickFormatter={formatMoneyShort} />
            <YAxis type="category" dataKey="name" width={280} interval={0} tick={{ fontSize: 12 }} />
            <Tooltip content={tableTooltip(table)} />
            <ReferenceLine x={0} stroke="var(--reference)" />
            <Bar
              dataKey="difference"
              name="Diferença"
              isAnimationActive={false}
              cursor="pointer"
              onClick={(item) => actions[(item.payload as { index: number }).index]?.()}
            >
              {/* Two neutral tones only to separate the API's two lists; neither is an alert color */}
              {data.map((d) => (
                <Cell key={d.index} fill={d.side === "above" ? "var(--color-neutral)" : "var(--color-planned)"} />
              ))}
            </Bar>
          </BarChart>
        </ResponsiveContainer>
      )}
    </IndicatorCard>
  );
}
