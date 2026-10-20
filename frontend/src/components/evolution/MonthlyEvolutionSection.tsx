import { useState } from "react";
import { Link } from "react-router";
import { Bar, BarChart, CartesianGrid, Legend, Line, LineChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useMonthlyEvolution } from "../../api/queries";
import type { MonthlyEvolution, MonthlySeries } from "../../api/types";
import { useSession } from "../../context";
import { formatMoneyShort, formatMonth, formatMonthShort } from "../../format";
import { AlternativeTable, tableTooltip, type TableModel } from "../indicators/AlternativeTable";
import { MONTH_AXIS_HEIGHT, MonthAxisTick } from "../indicators/MonthAxisTick";
import { seriesColor } from "../indicators/colors";
import { EvolutionEntries } from "./EvolutionEntries";
import { axisLabel, pointText, variationText } from "./formatting";

const OPEN_TITLE = "Ver os lançamentos do mês";

/** Month-by-month evolution on the home screen (RF-12): condo fee received and utilities, last 12 months. */
export function MonthlyEvolutionSection() {
  const { condominiumId } = useSession();
  const { data: evolution, isLoading, error } = useMonthlyEvolution(condominiumId);
  if (isLoading) return null;
  if (error) return <p className="notice error">{error.message}</p>;
  if (!evolution) return null;
  const period = `${formatMonthShort(evolution.months[0])} a ${formatMonthShort(evolution.until)}`;
  return (
    <>
      <CondoFeeChart evolution={evolution} period={period} />
      <UtilitiesChart evolution={evolution} period={period} />
    </>
  );
}

/** One table per series: amount and the two comparisons (RF-12.4); every amount opens the month's entries. */
function seriesTable(series: MonthlySeries, open: (month: string) => void): TableModel {
  return {
    caption: series.label,
    labelColumn: "Mês",
    columns: ["Valor (R$)", "Mês anterior", "Mesmo mês do ano anterior"],
    rows: series.points.map((p) => {
      const onOpen = p.entries.length > 0 ? () => open(p.month) : undefined;
      return {
        key: p.month,
        label: formatMonth(p.month),
        cells: [
          { text: pointText(p, series), onOpen, openTitle: OPEN_TITLE },
          { text: variationText(p.previousMonth) },
          { text: variationText(p.previousYear) },
        ],
      };
    }),
  };
}

function CondoFeeChart({ evolution, period }: { evolution: MonthlyEvolution; period: string }) {
  const [selected, setSelected] = useState<string | null>(null);
  const series = evolution.condoFee;
  if (!series) {
    return (
      <section className="block">
        <h2>Cota ordinária recebida</h2>
        <p className="muted">O gráfico aparece quando o fundo ordinário for confirmado (cartão "Saldo acumulado").</p>
      </section>
    );
  }
  const table = seriesTable(series, setSelected);
  const data = series.points.map((p, index) => ({
    index,
    axis: axisLabel(p.month, [p.status]),
    amount: p.status === "LOADED" ? p.amount : null,
  }));
  return (
    <section className="block chart" aria-labelledby="evolution-condo-fee">
      <h2 id="evolution-condo-fee">Cota ordinária recebida</h2>
      <p className="muted indicator-meta">
        <span>Período: {period}</span>
        <span>Unidade: R$</span>
        <span>Fundo: {evolution.operatingFund}</span>
      </p>
      <ResponsiveContainer width="100%" height={280}>
        <BarChart data={data} margin={{ top: 16, left: 4, right: 24, bottom: 4 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip content={tableTooltip(table)} />
          <Bar
            dataKey="amount"
            name="Cota recebida"
            fill="var(--color-neutral)"
            isAnimationActive={false}
            cursor="pointer"
            onClick={(item) => {
              const point = series.points[(item.payload as { index: number }).index];
              if (point?.entries.length) setSelected(point.month);
            }}
          />
        </BarChart>
      </ResponsiveContainer>
      <p className="muted">
        Soma dos recibos de cota que entraram no fundo ordinário em cada mês. Clique numa barra para ver os lançamentos.
      </p>
      <details className="alternative-table">
        <summary>Ver tabela</summary>
        <AlternativeTable model={table} />
      </details>
      {selected && (
        <EvolutionEntries title="Cota ordinária recebida" month={selected} series={[series]} onClose={() => setSelected(null)} />
      )}
    </section>
  );
}

function UtilitiesChart({ evolution, period }: { evolution: MonthlyEvolution; period: string }) {
  const { can } = useSession();
  const [selected, setSelected] = useState<string | null>(null);
  const utilities = evolution.utilities;
  const pending = evolution.pendingUtilityAccounts > 0 && can("MANAGER", "ADMIN") && (
    <p className="notice warning">
      Confirme as contas das concessionárias: {evolution.pendingUtilityAccounts} conta(s) sugerida(s).{" "}
      <Link to="/concessionarias">Abrir contas das concessionárias</Link>
    </p>
  );
  if (utilities.length === 0) {
    return (
      <section className="block">
        <h2>Concessionárias</h2>
        {pending || <p className="muted">Nenhuma conta de concessionária confirmada.</p>}
      </section>
    );
  }
  // One line per utility; a month without an amount stays null (no point, never zero)
  const data = evolution.months.map((month, index) => {
    const row: Record<string, string | number | null> = {
      index,
      axis: axisLabel(
        month,
        utilities.map((s) => s.points[index].status),
      ),
    };
    utilities.forEach((s) => {
      const p = s.points[index];
      row[s.key] = p.status === "LOADED" ? p.amount : null;
    });
    return row;
  });
  const tables = utilities.map((s) => seriesTable(s, setSelected));
  // The tooltip shows the amount of each utility in the month, with the change to the previous month
  const tooltip: TableModel = {
    caption: "Concessionárias",
    labelColumn: "Mês",
    columns: utilities.map((s) => s.label),
    rows: evolution.months.map((month, i) => ({
      key: month,
      label: formatMonth(month),
      cells: utilities.map((s) => {
        const p = s.points[i];
        return { text: p.status === "LOADED" ? `${pointText(p, s)} · ${variationText(p.previousMonth)}` : pointText(p, s) };
      }),
    })),
  };
  return (
    <section className="block chart" aria-labelledby="evolution-utilities">
      <h2 id="evolution-utilities">Concessionárias</h2>
      <p className="muted indicator-meta">
        <span>Período: {period}</span>
        <span>Unidade: R$</span>
        <span>Mês do pagamento no fluxo</span>
      </p>
      {pending}
      <ResponsiveContainer width="100%" height={300}>
        <LineChart
          data={data}
          margin={{ top: 16, left: 4, right: 24, bottom: 4 }}
          onClick={(state) => {
            const index = Number(state?.activeTooltipIndex);
            if (!Number.isInteger(index)) return;
            if (utilities.some((s) => s.points[index]?.entries.length)) setSelected(evolution.months[index]);
          }}
        >
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="axis" tick={<MonthAxisTick />} interval={0} height={MONTH_AXIS_HEIGHT} />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip content={tableTooltip(tooltip)} />
          <Legend />
          {utilities.map((s, i) => (
            <Line
              key={s.key}
              dataKey={s.key}
              name={s.label}
              stroke={seriesColor(i)}
              strokeWidth={2}
              dot={{ r: 4 }}
              connectNulls={false}
              isAnimationActive={false}
              cursor="pointer"
            />
          ))}
        </LineChart>
      </ResponsiveContainer>
      <p className="muted">Soma das contas confirmadas de cada concessionária, em qualquer fundo. Clique num mês para ver os lançamentos.</p>
      <details className="alternative-table">
        <summary>Ver tabela</summary>
        {tables.map((t) => (
          <AlternativeTable key={t.caption} model={t} />
        ))}
      </details>
      {selected && (
        <EvolutionEntries title="Concessionárias" month={selected} series={utilities} onClose={() => setSelected(null)} />
      )}
    </section>
  );
}
