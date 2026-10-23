import { useSearchParams } from "react-router";
import { useFiscalYears, useHasConfirmedBudget, useIndicators } from "../api/budgetAnalysisQueries";
import { useFunds } from "../api/budgetQueries";
import type { Indicators as IndicatorSeries } from "../api/types";
import { NotApplicable } from "../components/indicators/IndicatorCard";
import { useOpenInBudgetVsActual, type ChartContext } from "../components/indicators/chartContext";
import { CumulativeChart } from "../components/indicators/CumulativeChart";
import { FiscalYearComparisonChart } from "../components/indicators/FiscalYearComparisonChart";
import { MonthlyExecutionChart } from "../components/indicators/MonthlyExecutionChart";
import { FundsChart } from "../components/indicators/FundsChart";
import { LargestDifferencesChart } from "../components/indicators/LargestDifferencesChart";
import { ActualByGroupChart } from "../components/indicators/ActualByGroupChart";
import { Rule20Chart } from "../components/indicators/Rule20Chart";
import { FiscalYearSelector } from "../components/budgetVsActual/FiscalYearSelector";
import { FundSelector } from "../components/budgetVsActual/FundSelector";
import { NoConfirmedBudget } from "../components/NoConfirmedBudget";
import { useSession } from "../context";
import { formatMonth } from "../format";

/**
 * "Indicadores" screen (RF-11.10 to RF-11.13), for every role. The filters live in the address
 * (?po=<uuid>&fundo=<uuid>); without `po`, the backend uses the most recent fiscal year. The 7 charts only draw
 * the series from GET /indicators: no number is calculated here.
 */
export function Indicators() {
  const { condominiumId } = useSession();
  const [parameters, setParameters] = useSearchParams();
  const budgetId = parameters.get("po");
  const fundId = parameters.get("fundo");

  const { data: fiscalYears = [] } = useFiscalYears(condominiumId);
  const { data: funds = [] } = useFunds(condominiumId);
  // Without `po`, the API uses the most recent confirmed budget: with none, the screen shows the empty state
  const hasBudget = useHasConfirmedBudget(condominiumId);
  const withoutBudget = !budgetId && hasBudget === false;
  const query = useIndicators(condominiumId, budgetId, fundId, !!budgetId || hasBudget === true);

  const change = (name: string, value: string | null) =>
    setParameters((current) => {
      const next = new URLSearchParams(current);
      if (value) next.set(name, value);
      else next.delete(name);
      return next;
    });

  return (
    <>
      <header className="page-title">
        <h1>Indicadores</h1>
      </header>
      <div className="filters">
        {/* Without `po` in the address, the fiscal year shown is the one the API picked (the most recent) */}
        <FiscalYearSelector fiscalYears={fiscalYears} budgetId={budgetId ?? query.data?.budgetId ?? null} onChange={(id) => change("po", id)} />
        <FundSelector funds={funds} fundId={fundId} onChange={(f) => change("fundo", f)} />
      </div>

      {withoutBudget ? (
        <NoConfirmedBudget what="os indicadores do exercício" />
      ) : query.isLoading || hasBudget === undefined ? (
        <p className="notice">Carregando…</p>
      ) : query.error ? (
        <p className="notice error">{query.error.message}</p>
      ) : query.data ? (
        <Charts indicators={query.data} fundId={fundId} />
      ) : (
        <p className="notice">Nenhum exercício confirmado para mostrar indicadores.</p>
      )}
    </>
  );
}

interface ChartsProps {
  indicators: IndicatorSeries;
  fundId: string | null;
}

/** The 7 charts of RF-11.11. Null series = does not apply to the chosen fund (the API decides). */
function Charts({ indicators: ind, fundId }: ChartsProps) {
  const open = useOpenInBudgetVsActual();
  const context: ChartContext = {
    budgetId: ind.budgetId,
    fundId,
    period: `${formatMonth(ind.start)} a ${formatMonth(ind.end)} (${ind.label})`,
    dataAsOf: ind.dataAsOf,
    open,
  };
  // Charts 1 to 5 belong to the Condomínio fund: with another fund in the filter, the API sends those series as null
  const withoutCondominium = !ind.monthlyExecution && !ind.rule20 && !ind.cumulative && !ind.actualByGroup && !ind.largestDifferences;

  return (
    <>
      <p className="muted">
        Exercício {ind.label}: {formatMonth(ind.start)} a {formatMonth(ind.end)}. Os números são os mesmos da tela "Previsto ×
        realizado"; clique num valor para abri-lo lá, com os lançamentos de origem.
      </p>
      {ind.warnings.length > 0 && (
        <ul className="muted-warnings" aria-label="Avisos">
          {ind.warnings.map((w, i) => (
            <li key={i}>{w}</li>
          ))}
        </ul>
      )}

      {withoutCondominium && (
        <NotApplicable
          title="1 a 5. Gráficos do fundo Condomínio"
          reason="Os gráficos 1 a 5 são do fundo Condomínio e não se aplicam ao fundo escolhido no filtro."
        />
      )}
      {ind.monthlyExecution && <MonthlyExecutionChart points={ind.monthlyExecution} context={context} />}
      {ind.rule20 && <Rule20Chart points={ind.rule20} limitPercentage={ind.limitPercentage} context={context} />}
      {ind.cumulative && <CumulativeChart points={ind.cumulative} context={context} />}
      {ind.actualByGroup && <ActualByGroupChart series={ind.actualByGroup} context={context} />}
      {ind.largestDifferences && <LargestDifferencesChart differences={ind.largestDifferences} context={context} />}

      {ind.funds ? (
        <FundsChart series={ind.funds} context={context} />
      ) : (
        <NotApplicable
          title="6. Fundos: arrecadação × previsto"
          reason="Este gráfico é dos fundos ligados às linhas 1.9 e não se aplica ao filtro do fundo Condomínio."
        />
      )}

      {ind.comparison ? (
        <FiscalYearComparisonChart comparison={ind.comparison} context={context} />
      ) : (
        <NotApplicable title="7. Comparação entre exercícios" reason="Sem comparação para este exercício (veja os avisos acima)." />
      )}

      <p className="muted">As diferenças são indícios para conferência, sempre com os lançamentos de origem.</p>
    </>
  );
}
