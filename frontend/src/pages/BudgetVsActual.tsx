import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { reviewLink } from "../components/pendingSteps";
import { useFiscalYears } from "../api/budgetAnalysisQueries";
import { useFunds, useBudgets, useBudgetVsActual } from "../api/budgetQueries";
import type { BudgetVsActual as Result } from "../api/types";
import { SeparateBlocks } from "../components/budgetVsActual/SeparateBlocks";
import { ExportButtons } from "../components/budgetVsActual/ExportButtons";
import { BudgetVsActualStatus } from "../components/budgetVsActual/BudgetVsActualStatus";
import { targetFromAddress, type EvidenceTarget } from "../components/budgetVsActual/evidence";
import { MonthFindings } from "../components/budgetVsActual/MonthFindings";
import { BudgetVsActualFilters } from "../components/budgetVsActual/BudgetVsActualFilters";
import { GroupsChart } from "../components/budgetVsActual/GroupsChart";
import { MonthsChart } from "../components/budgetVsActual/MonthsChart";
import { Rule20Indicator } from "../components/budgetVsActual/Rule20Indicator";
import { EvidencePanel } from "../components/budgetVsActual/EvidencePanel";
import { FundsPanel } from "../components/budgetVsActual/FundsPanel";
import { FiscalYearSummary } from "../components/budgetVsActual/FiscalYearSummary";
import { BudgetVsActualSummary } from "../components/budgetVsActual/BudgetVsActualSummary";
import { NoNumbers } from "../components/budgetVsActual/NoNumbers";
import { BudgetVsActualTable } from "../components/budgetVsActual/BudgetVsActualTable";
import { useSession } from "../context";
import { formatDayMonth, formatMonth } from "../format";

/**
 * "Previsto × realizado" screen (RF-03.1.13 and RF-11.4), for every role. The filters live in the address
 * (?periodo=2026-09&po=...&fundo=<fund id>), so the links from the home screen and from the account mapping open the same view.
 * The fiscal year is chosen from the GET /fiscal-years list and goes to the API as that fiscal year's `po`; without `po`,
 * the API uses the current fiscal year. With `?alvo=line:<id>` (coming from "Comparar exercícios"), the evidence opens right away.
 */
export function BudgetVsActual() {
  const { condominiumId } = useSession();
  const [parameters, setParameters] = useSearchParams();
  const budgetId = parameters.get("po");
  const fundId = parameters.get("fundo");
  const urlTarget = parameters.get("alvo");

  const { data: fiscalYears = [] } = useFiscalYears(condominiumId);
  const { data: budgets = [] } = useBudgets(condominiumId);
  const { data: funds = [] } = useFunds(condominiumId);
  // The cumulative view brings the months of the fiscal year (for the filter and the month-by-month chart)
  const cumulative = useBudgetVsActual(condominiumId, "cumulative", budgetId);
  const lastMonth = cumulative.data?.summedMonths.at(-1);
  const period = addressPeriod(parameters.get("periodo")) ?? (cumulative.isFetched ? (lastMonth ?? "cumulative") : null);
  // The fund filter goes to the API: it returns only what belongs to the chosen view
  const query = useBudgetVsActual(condominiumId, period, budgetId, fundId);

  // Without `po` in the address, the fiscal year shown is the one the API picked (the current one)
  const shownBudget = budgetId ?? cumulative.data?.budget?.id ?? null;
  const fiscalYear = fiscalYears.find((f) => f.type === "PO" && f.budgetId === shownBudget);
  // Partial month (RF-13.10), as the API marks it in the months of the cumulative view
  const partialUntil = cumulative.data?.months.find((m) => m.month === period)?.partialUntil;

  /** Changes a filter in the address. The evidence opened through the link (?alvo=) closes along with it. */
  const change = (changes: Record<string, string | null>) =>
    setParameters((current) => {
      const next = new URLSearchParams(current);
      next.delete("alvo");
      for (const [name, value] of Object.entries(changes)) {
        if (value) next.set(name, value);
        else next.delete(name);
      }
      return next;
    });

  return (
    <>
      <header className="page-title">
        <h1>Previsto × realizado{period && period !== "cumulative" ? ` · ${formatMonth(period)}` : " · acumulado"}</h1>
        {partialUntil && (
          <span className="badge warning" title="Números até a data, fora do acumulado e da regra dos 20%">
            parcial até {formatDayMonth(partialUntil)}
          </span>
        )}
        {period && query.data?.status === "CALCULATED" && <ExportButtons period={period} budgetId={budgetId} fundId={fundId} />}
      </header>
      <BudgetVsActualFilters
        fiscalYears={fiscalYears}
        budgets={budgets}
        budgetId={shownBudget}
        // Another fiscal year has other months: the period goes back to the default (last month with a cash flow)
        onChangeBudget={(id) => change({ po: id, periodo: null })}
        period={period ?? "cumulative"}
        months={cumulative.data?.months ?? []}
        onChangePeriod={(p) => change({ periodo: p })}
        funds={funds}
        fundId={fundId}
        onChangeFund={(f) => change({ fundo: f })}
      />
      {fiscalYear && <FiscalYearSummary fiscalYear={fiscalYear} />}
      {query.isLoading || !period ? (
        <p className="notice">Carregando…</p>
      ) : query.error ? (
        <p className="notice error">{query.error.message}</p>
      ) : query.data?.lineReview && !query.data.lineReview.reviewed ? (
        // RF-13.12: before the review of the budget lines, no number by line; the pending step with its link
        <section className="block no-numbers">
          <p className="notice warning">{query.data.lineReview.message ?? "Linhas da PO ainda não conferidas."}</p>
          <p>
            <Link to={reviewLink(query.data.lineReview.budgetId)}>Conferir as linhas da PO</Link>
          </p>
        </section>
      ) : query.data ? (
        <Content
          // A new target in the address remounts the content, so the panel opens right on it
          key={urlTarget ?? ""}
          result={query.data}
          period={period}
          urlTarget={urlTarget}
          onCloseUrlTarget={() => change({})}
          onChooseMonth={(m) => change({ periodo: m })}
        />
      ) : null}
    </>
  );
}

/**
 * Period written in the address, as the API expects it ("cumulative" or YYYY-MM). Old links (e.g. the home card before
 * the API went to English) wrote "acumulado": it still opens the cumulative view instead of a 400 from the API.
 */
function addressPeriod(value: string | null): string | null {
  if (!value) return null;
  return value === "acumulado" ? "cumulative" : value;
}

interface ContentProps {
  result: Result;
  period: string;
  /** Evidence target that came from the address (e.g. a click on a value in "Comparar exercícios"). */
  urlTarget: string | null;
  onCloseUrlTarget: () => void;
  onChooseMonth: (month: string) => void;
}

function Content({ result, period, urlTarget, onCloseUrlTarget, onChooseMonth }: ContentProps) {
  const [evidence, setEvidence] = useState<EvidenceTarget | null>(() => (urlTarget ? targetFromAddress(result, urlTarget) : null));
  const closeEvidence = () => {
    setEvidence(null);
    if (urlTarget) onCloseUrlTarget();
  };
  const { totals } = result;

  return (
    <div className={evidence ? "with-detail" : undefined}>
      <div>
        <BudgetVsActualStatus result={result} />
        {result.warnings.map((w, i) => (
          <p key={`${w.code}-${i}`} className="notice warning">
            {w.text}
          </p>
        ))}

        {result.status !== "CALCULATED" ? (
          <NoNumbers result={result} />
        ) : (
          <>
            {/* With another fund in the filter, the API returns empty totals and groups: only the fund's panel shows up */}
            {totals && <BudgetVsActualSummary result={result} totals={totals} onOpenEvidence={setEvidence} />}
            {result.rule20 && <Rule20Indicator rule={result.rule20} />}
            {result.groups.length > 0 &&
              (period === "cumulative" ? (
                <MonthsChart months={result.months} onChooseMonth={onChooseMonth} />
              ) : (
                <GroupsChart groups={result.groups} />
              ))}
            {result.groups.length > 0 && <BudgetVsActualTable groups={result.groups} onOpenEvidence={setEvidence} />}
            {totals && <SeparateBlocks result={result} onOpenEvidence={setEvidence} />}
            {result.funds.length > 0 && <FundsPanel funds={result.funds} onOpenEvidence={setEvidence} />}
          </>
        )}
        {period !== "cumulative" && <MonthFindings referenceMonth={period} />}
        <p className="muted">
          As diferenças são indícios para conferência, com os lançamentos de origem. Cálculo: versão {result.calculationVersion}.
        </p>
      </div>
      {evidence && result.budget && (
        <EvidencePanel period={period} budget={result.budget} target={evidence} onClose={closeEvidence} />
      )}
    </div>
  );
}
