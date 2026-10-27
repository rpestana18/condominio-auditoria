import { CartesianGrid, Line, LineChart, ReferenceLine, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useCashFlowMonthly, type CashFlowFund } from "../../api/cashFlowQueries";
import type { BalanceBridge } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatDifference, formatMoney, formatMoneyShort, formatMonthName, formatMonthShort } from "../../format";
import { cashFlowMonthNote } from "../cashFlow/CashFlowTable";

/**
 * (a) of RF-13.16: the total balance month by month, continuous from the first cash flow loaded (also before the
 * approved budget), with a vertical mark where the fiscal year of the budget starts. Same monthly answer as
 * "Entradas e saídas" (ADR 0009, Decision 4).
 */
export function ContinuousBalanceChart({ fund }: { fund: CashFlowFund }) {
  const { condominiumId } = useSession();
  const { data } = useCashFlowMonthly(condominiumId, fund);
  if (!data || data.months.length === 0) return null;
  const points = data.months.map((m) => ({
    month: m.month,
    label: formatMonthShort(m.month),
    note: cashFlowMonthNote(m),
    balance: m.closingBalance ?? null,
  }));
  const start = data.budget?.fiscalYearStart;
  const end = data.budget?.fiscalYearEnd;
  const startPoint = start ? points.find((p) => p.month === start) : undefined;
  const budgetLabel = start && end ? `PO ${start.slice(0, 4)}/${end.slice(0, 4)}` : "PO";
  return (
    <section className="block">
      <h2>Saldo mês a mês</h2>
      <ResponsiveContainer width="100%" height={240}>
        <LineChart data={points} margin={{ left: 16, right: 16, top: 16 }}>
          <CartesianGrid strokeDasharray="3 3" vertical={false} />
          <XAxis dataKey="label" />
          <YAxis tickFormatter={formatMoneyShort} width={80} />
          <Tooltip
            formatter={(value) => formatMoney(Number(value))}
            labelFormatter={(label, payload) => {
              const note = payload?.[0]?.payload?.note as string | null | undefined;
              return note ? `${label} · ${note}` : String(label);
            }}
          />
          {startPoint && <ReferenceLine x={startPoint.label} stroke="var(--text-muted)" strokeDasharray="4 4" label={{ value: budgetLabel, position: "top" }} />}
          <Line dataKey="balance" name="Saldo no fim do mês" stroke="var(--series-1)" strokeWidth={2} dot isAnimationActive={false} connectNulls={false} />
        </LineChart>
      </ResponsiveContainer>
      <p className="muted">Saldo no fim de cada mês, como no extrato. Meses antes da PO aprovada também aparecem.</p>
    </section>
  );
}

/**
 * (b) of RF-13.16: before and after the approved budget, with the numbers of the bridge of the fiscal year: what already
 * existed at the start and what was formed since then (the partial month apart).
 */
export function BeforeAfterBudget({ bridge }: { bridge: BalanceBridge }) {
  if (bridge.openingBalance == null || bridge.variation == null) return null;
  const partial = bridge.partialMonth;
  return (
    <section className="block">
      <h2>Antes e depois da PO aprovada</h2>
      <dl className="number-list">
        <dt>Já existia em {formatDate(bridge.openingDate)}</dt>
        <dd>{formatMoney(bridge.openingBalance)}</dd>
        <dt>Formado desde a PO</dt>
        <dd>
          {formatDifference(bridge.variation)} até {formatDate(bridge.closingDate)}
          {partial?.variation != null && (
            <span className="muted">
              {" "}
              ({formatDifference(partial.variation)} em {formatMonthName(partial.month).toLowerCase()}, parcial)
            </span>
          )}
        </dd>
      </dl>
    </section>
  );
}
