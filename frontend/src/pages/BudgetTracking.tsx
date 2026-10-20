import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useFiscalYears } from "../api/budgetAnalysisQueries";
import { useBudgets } from "../api/budgetQueries";
import { useBudgetTracking } from "../api/budgetTrackingQueries";
import type { BudgetTracking as Tracking } from "../api/types";
import { EvidencePanel } from "../components/budgetVsActual/EvidencePanel";
import type { EvidenceTarget } from "../components/budgetVsActual/evidence";
import { splitMappingWarning } from "../components/MappingWarningNote";
import { TrackingBlocksTable } from "../components/budgetTracking/TrackingBlocksTable";
import { TrackingFilters } from "../components/budgetTracking/TrackingFilters";
import { TrackingGrid } from "../components/budgetTracking/TrackingGrid";
import { TrackingMonthsChart } from "../components/budgetTracking/TrackingMonthsChart";
import { emptyFilter, filterGroups, isFiltering, type TrackingFilter } from "../components/budgetTracking/trackingFilter";
import { useSession } from "../context";
import { formatMonth } from "../format";

/** Evidence opened from a cell: the API period ("AAAA-MM" or "cumulative") and the target. */
interface OpenedEvidence {
  period: string;
  target: EvidenceTarget;
}

/**
 * "Acompanhamento da PO" (RF-13.4), entry screen of the "Análise da PO" section, for every role.
 * The fiscal year lives in the address (?po=<budget id>); the other filters only hide rows that came from the API.
 */
export function BudgetTracking() {
  const { condominiumId } = useSession();
  const [parameters, setParameters] = useSearchParams();
  const budgetId = parameters.get("po");
  const { data: fiscalYears = [] } = useFiscalYears(condominiumId);
  const { data: budgets = [] } = useBudgets(condominiumId);
  const query = useBudgetTracking(condominiumId, budgetId);
  const [filter, setFilter] = useState<TrackingFilter>(emptyFilter);
  const [evidence, setEvidence] = useState<OpenedEvidence | null>(null);

  const tracking = query.data;
  const changeBudget = (id: string) => {
    setFilter(emptyFilter);
    setEvidence(null);
    setParameters(id ? { po: id } : {});
  };

  return (
    <div className={evidence ? "with-detail" : undefined}>
      <div>
        <header className="page-title">
          <h1>Acompanhamento da PO</h1>
          {tracking?.budget?.fiscalYearStart && tracking.budget.fiscalYearEnd && (
            <span className="badge neutral">
              {formatMonth(tracking.budget.fiscalYearStart)} a {formatMonth(tracking.budget.fiscalYearEnd)}
            </span>
          )}
        </header>
        {query.isLoading && <p className="notice">Carregando…</p>}
        {query.error && <p className="notice error">{query.error.message}</p>}
        {tracking && (
          <>
            <TrackingFilters
              fiscalYears={fiscalYears}
              budgets={budgets}
              budgetId={budgetId ?? tracking.budget?.id ?? null}
              onChangeBudget={changeBudget}
              groups={tracking.groups}
              months={tracking.months}
              filter={filter}
              onChange={setFilter}
            />
            <Content tracking={tracking} filter={filter} onOpenEvidence={(period, target) => setEvidence({ period, target })} />
          </>
        )}
      </div>
      {evidence && tracking?.budget && (
        <EvidencePanel
          // A new cell remounts the panel on it
          key={`${evidence.period}-${evidence.target.target}`}
          period={evidence.period}
          budget={tracking.budget}
          target={{ ...evidence.target, title: `${evidence.target.title} · ${formatMonth(evidence.period)}` }}
          onClose={() => setEvidence(null)}
        />
      )}
    </div>
  );
}

interface ContentProps {
  tracking: Tracking;
  filter: TrackingFilter;
  onOpenEvidence: (period: string, target: EvidenceTarget) => void;
}

function Content({ tracking, filter, onOpenEvidence }: ContentProps) {
  if (tracking.status !== "CALCULATED") {
    return (
      <section className="block no-numbers">
        <p className="notice warning">{tracking.message ?? "Ainda não há números para o acompanhamento."}</p>
        <p className="muted">
          Confira a <Link to="/previsoes">PO aprovada</Link>, o <Link to="/depara">de-para</Link> e os extratos em{" "}
          <Link to="/arquivos">Arquivos</Link>.
        </p>
      </section>
    );
  }
  const groups = filterGroups(tracking.groups, filter);
  const lineCount = groups.reduce((count, g) => count + g.lines.length, 0);
  // The mapping warning goes next to "Sem linha da PO", the others stay on top
  const { mapping, others } = splitMappingWarning(tracking.warnings);

  return (
    <>
      {others.map((w, i) => (
        <p key={`${w.code}-${i}`} className="notice warning">
          {w.text}
        </p>
      ))}
      <TrackingMonthsChart months={tracking.months} />
      <section className="block">
        <div className="block-title">
          <h2>Linhas da PO × meses do exercício</h2>
          <span className="muted">
            {isFiltering(filter) ? `${lineCount} linha(s) no filtro` : `${lineCount} linha(s)`} · clique em um valor para ver os lançamentos
          </span>
        </div>
        <Legend />
        {groups.length === 0 ? (
          <p className="notice">Nenhuma linha neste filtro.</p>
        ) : (
          <TrackingGrid tracking={tracking} groups={groups} onOpenEvidence={onOpenEvidence} />
        )}
      </section>
      {tracking.blocks && (
        <TrackingBlocksTable
          blocks={tracking.blocks}
          months={tracking.months}
          onOpenEvidence={onOpenEvidence}
          mappingWarning={mapping}
        />
      )}
      <p className="muted">
        As diferenças são números para conferência (realizado − previsto do mês), com os lançamentos de origem. Cálculo: versão{" "}
        {tracking.calculationVersion} (previsto × realizado {tracking.budgetVsActualVersion}).
      </p>
    </>
  );
}

/** Legend of the neutral highlights: no red here (RF-11.13); red is only for the excess above 20%. */
function Legend() {
  return (
    <p className="tracking-legend">
      <span className="legend-swatch cell-above" /> acima do previsto
      <span className="legend-swatch cell-below" /> abaixo do previsto
      <span className="legend-swatch cell-partial" /> mês parcial (fora do acumulado)
      <span className="muted">Linhas anuais ou esporádicas: o destaque fica no saldo do exercício.</span>
    </p>
  );
}
