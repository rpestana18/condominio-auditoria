import { Link } from "react-router";
import { Bar, BarChart, CartesianGrid, Legend, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { useDashboard } from "../api/queries";
import type { Dashboard } from "../api/types";
import { NumberCard } from "../components/NumberCard";
import { FiscalYearBudgetVsActualCard } from "../components/FiscalYearBudgetVsActualCard";
import { CumulativeBalanceCard } from "../components/CumulativeBalanceCard";
import { MonthlyEvolutionSection } from "../components/evolution/MonthlyEvolutionSection";
import { useSession } from "../context";
import { formatDate, formatMoney, formatMoneyShort, formatPeriod } from "../format";

export function Home() {
  const { condominiumId, can } = useSession();
  const { data: dashboard, isLoading, error } = useDashboard(condominiumId);

  if (isLoading) return <p className="notice">Carregando…</p>;
  if (error) return <p className="notice error">{error.message}</p>;
  if (!dashboard) {
    return (
      <section className="empty">
        <h1>Ainda não há números para mostrar</h1>
        <p>Os números aparecem aqui assim que o primeiro fluxo de caixa mensal for processado.</p>
        {can("MANAGER", "ADMIN") && (
          <Link className="button" to="/arquivos">
            Enviar arquivo
          </Link>
        )}
      </section>
    );
  }
  return <MonthDashboard dashboard={dashboard} />;
}

function MonthDashboard({ dashboard }: { dashboard: Dashboard }) {
  const result = dashboard.inflows - dashboard.outflows;
  // Only the 8 funds with the most movement, from largest to smallest; the others show in the table below
  const withMovement = dashboard.funds
    .filter((f) => f.inflows !== 0 || f.outflows !== 0)
    .sort((a, b) => Math.max(b.inflows, b.outflows) - Math.max(a.inflows, a.outflows))
    .slice(0, 8);
  const negatives = dashboard.funds.filter((f) => f.closingBalance < 0);

  return (
    <>
      <header className="page-title">
        <h1>Resumo de {formatPeriod(dashboard.periodStart, dashboard.periodEnd)}</h1>
        <span className={dashboard.failedChecks === 0 ? "badge ok" : "badge warning"}>
          {dashboard.failedChecks === 0
            ? "Relatório conferido: todas as somas batem"
            : `${dashboard.failedChecks} conferência(s) não bateram`}
        </span>
      </header>

      <div className="cards">
        <NumberCard title="Saldo no início do mês" value={dashboard.openingBalance} />
        <NumberCard title="Entradas" value={dashboard.inflows} />
        <NumberCard title="Saídas" value={dashboard.outflows} />
        <NumberCard
          title="Resultado do mês"
          value={result}
          highlight={result >= 0 ? "positive" : "negative"}
          hint="Soma de todos os fundos, inclusive os de finalidade própria (energia, água, reserva)"
        />
        <NumberCard title="Saldo no fim do mês" value={dashboard.closingBalance} />
        <CumulativeBalanceCard dashboard={dashboard} />
        <FiscalYearBudgetVsActualCard />
      </div>

      <MonthlyEvolutionSection />

      <section className="block">
        <h2>Entradas e saídas dos fundos com mais movimento</h2>
        <ResponsiveContainer width="100%" height={Math.max(260, withMovement.length * 44)}>
          <BarChart data={withMovement} layout="vertical" margin={{ left: 24, right: 24 }}>
            <CartesianGrid strokeDasharray="3 3" horizontal={false} />
            <XAxis type="number" tickFormatter={formatMoneyShort} />
            <YAxis type="category" dataKey="fund" width={190} tick={{ fontSize: 12 }} />
            <Tooltip formatter={(value) => formatMoney(Number(value))} />
            <Legend />
            <Bar dataKey="inflows" name="Entradas" fill="var(--color-inflow)" isAnimationActive={false} />
            <Bar dataKey="outflows" name="Saídas" fill="var(--color-outflow)" isAnimationActive={false} />
          </BarChart>
        </ResponsiveContainer>
      </section>

      <div className="two-columns">
        <section className="block">
          <h2>Saldo por fundo</h2>
          {negatives.length > 0 && (
            <p className="notice warning">
              {negatives.length} fundo(s) com saldo negativo: {negatives.map((f) => f.fund).join(", ")}
            </p>
          )}
          <table className="table">
            <thead>
              <tr>
                <th>Fundo</th>
                <th className="number">Resultado do mês</th>
                <th className="number">Saldo atual</th>
              </tr>
            </thead>
            <tbody>
              {dashboard.funds.map((f) => (
                <tr key={f.fund}>
                  <td>{f.fund}</td>
                  <td className={`number ${f.result < 0 ? "negative" : ""}`}>{formatMoney(f.result)}</td>
                  <td className={`number ${f.closingBalance < 0 ? "negative" : ""}`}>{formatMoney(f.closingBalance)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>

        <section className="block">
          <h2>Maiores despesas do mês</h2>
          <ol className="expense-list">
            {dashboard.largestExpenses.map((e, i) => (
              <li key={i}>
                <div>
                  <strong>{formatMoney(e.amount)}</strong>
                  <span className="muted">
                    {formatDate(e.date)} · {e.fund} · pág. {e.page}
                  </span>
                </div>
                <span className="memo">{e.account}</span>
                <span className="memo muted">{e.memo}</span>
              </li>
            ))}
          </ol>
        </section>
      </div>
    </>
  );
}
