import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useFiscalYearComparison, useFiscalYears } from "../api/budgetAnalysisQueries";
import { useFunds } from "../api/budgetQueries";
import type { FiscalYearComparison, FiscalYear } from "../api/types";
import { PrintedColumnCheckPanel } from "../components/comparison/PrintedColumnCheckPanel";
import { ComparedFiscalYears } from "../components/comparison/ComparedFiscalYears";
import { ComparisonFilters } from "../components/comparison/ComparisonFilters";
import { useEvidenceOpener } from "../components/comparison/navigation";
import { ComparisonSummary } from "../components/comparison/ComparisonSummary";
import { UnmatchedLines } from "../components/comparison/UnmatchedLines";
import { ComparisonTable, type ComparedRow, type ValueMode } from "../components/comparison/ComparisonTable";
import { useSession } from "../context";

/** Views of the screen. The values are the ones written in the address (?visao=), so they stay as they are. */
type View = "resumo" | "grupos" | "linhas";

const views: { code: View; label: string }[] = [
  { code: "resumo", label: "Resumo por exercício" },
  { code: "grupos", label: "Por grupo" },
  { code: "linhas", label: "Por linha" },
];

/**
 * "Comparar exercícios" screen (RF-11.6), for every role. The filters live in the address
 * (?exercicios=budget:...,column:...&fundo=...&mesmosMeses=true&visao=grupos). Without `exercicios`, the backend
 * picks the two most recent. Every number and every variation come ready from GET /fiscal-year-comparison.
 */
export function CompareFiscalYears() {
  const { condominiumId } = useSession();
  const [parameters, setParameters] = useSearchParams();
  const chosen = parameters.get("exercicios")?.split(",").filter(Boolean) ?? null;
  const fundId = parameters.get("fundo");
  const sameMonths = parameters.get("mesmosMeses") === "true";
  const view = (parameters.get("visao") as View | null) ?? "resumo";

  const fiscalYearList = useFiscalYears(condominiumId);
  const fiscalYears = fiscalYearList.data ?? [];
  const { data: funds = [] } = useFunds(condominiumId);
  // Without a choice in the address, the backend compares the two most recent: with fewer than two fiscal years it
  // refuses (422), so the screen explains what is missing instead of asking
  const fewFiscalYears = chosen === null && fiscalYearList.isSuccess && fiscalYears.length < 2;
  const query = useFiscalYearComparison(
    condominiumId,
    { fiscalYears: chosen, fundId, sameMonths },
    chosen !== null || fiscalYearList.isError || (fiscalYearList.isSuccess && fiscalYears.length >= 2),
  );
  const response = query.data ?? null;
  // Budget whose "Orçado anterior" column is open in the check panel
  const [openColumn, setOpenColumn] = useState<string | null>(null);

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
        <h1>Comparar exercícios</h1>
      </header>
      <ComparisonFilters
        fiscalYears={fiscalYears}
        checked={chosen ?? usedIds(response, fiscalYears)}
        onChangeFiscalYears={(ids) => change("exercicios", ids.join(","))}
        ownChoice={chosen !== null}
        onResetToDefault={() => change("exercicios", null)}
        funds={funds}
        fundId={fundId}
        onChangeFund={(f) => change("fundo", f)}
        sameMonths={sameMonths}
        onChangeSameMonths={(m) => change("mesmosMeses", m ? "true" : null)}
        comparing={response?.comparing}
      />

      {fewFiscalYears ? (
        <FewFiscalYears count={fiscalYears.length} />
      ) : query.isLoading || fiscalYearList.isLoading ? (
        <p className="notice">Carregando…</p>
      ) : query.error ? (
        <p className="notice error">{query.error.message}</p>
      ) : response ? (
        <div className={openColumn ? "with-detail" : undefined}>
          <div>
            <ComparedFiscalYears compared={response.fiscalYears} fiscalYears={fiscalYears} onCheckColumn={setOpenColumn} />
            {response.warnings.map((w, i) => (
              <p key={i} className="notice warning">
                {w}
              </p>
            ))}
            <div className="tabs" role="tablist">
              {views.map((v) => (
                <button
                  key={v.code}
                  role="tab"
                  aria-selected={view === v.code}
                  onClick={() => change("visao", v.code === "resumo" ? null : v.code)}
                >
                  {v.label}
                </button>
              ))}
            </div>
            <Views response={response} view={view} fundId={fundId} />
            <p className="muted">
              Variação contra o exercício seguinte da lista. Clique num valor para abrir o previsto × realizado daquele exercício
              com os lançamentos.
            </p>
          </div>
          {openColumn && <PrintedColumnCheckPanel budgetId={openColumn} onClose={() => setOpenColumn(null)} />}
        </div>
      ) : null}
    </>
  );
}

/** Fewer than two fiscal years (confirmed budgets and printed "Orçado anterior" columns): nothing to compare yet. */
function FewFiscalYears({ count }: { count: number }) {
  return (
    <section className="block no-numbers">
      <h2>{count === 0 ? "Nenhum exercício para comparar ainda" : "Só há um exercício para comparar"}</h2>
      <p>
        A comparação precisa de dois exercícios: duas POs confirmadas, ou uma PO confirmada com a coluna "Orçado anterior"
        impressa. {count === 0 ? 'Envie a PO em Arquivos e confirme-a em "PO aprovada".' : "Envie e confirme a PO de outro exercício."}
      </p>
      <Link className="link-button" to="/arquivos">
        Ir para Arquivos
      </Link>{" "}
      <Link className="link-button" to="/previsoes">
        Ver as POs enviadas
      </Link>
    </section>
  );
}

/**
 * With no choice in the address, the checked ones are the ones the backend used. Matches by type and budget, so it does not
 * depend on the id format ("budget:<uuid>" or just the uuid).
 */
function usedIds(response: FiscalYearComparison | null, fiscalYears: FiscalYear[]): string[] {
  if (!response) return [];
  return fiscalYears.filter((f) => response.fiscalYears.some((r) => r.type === f.type && r.budgetId === f.budgetId)).map((f) => f.id);
}

interface ViewsProps {
  response: FiscalYearComparison;
  view: View;
  fundId: string | null;
}

/** The three views of RF-11.6. The value mode (monthly planned or compared months) applies to groups and lines. */
function Views({ response, view, fundId }: ViewsProps) {
  const [mode, setMode] = useState<ValueMode>("MONTHLY_PLANNED");
  const opener = useEvidenceOpener(response.fiscalYears, fundId);
  const { fiscalYears } = response;

  if (view === "resumo") return <ComparisonSummary fiscalYears={fiscalYears} summary={response.summary} opener={opener} />;

  const modeSelector = (
    <label className="muted">
      Mostrar{" "}
      <select value={mode} onChange={(e) => setMode(e.target.value as ValueMode)}>
        <option value="MONTHLY_PLANNED">previsto do mês</option>
        <option value="MONTHS">previsto e realizado dos meses comparados</option>
      </select>
    </label>
  );

  if (view === "grupos") {
    const rows: ComparedRow[] = response.groups.map((g) => ({
      key: g.code,
      title: `${g.code} ${g.description}`,
      note: g.funds ? "fundos: realizado = arrecadação" : null,
      values: g.values,
    }));
    return (
      <section className="block">
        <header className="block-title">
          <h2>Por grupo</h2>
          {modeSelector}
        </header>
        <ComparisonTable fiscalYears={fiscalYears} rows={rows} mode={mode} opener={opener} />
      </section>
    );
  }

  const rows: ComparedRow[] = response.lines.map((r) => ({
    key: r.budgetItemId,
    title: r.name,
    note: r.group ? `grupo ${r.group}` : null,
    values: r.values,
  }));
  return (
    <>
      <section className="block">
        <header className="block-title">
          <h2>Por linha (rubricas confirmadas)</h2>
          {modeSelector}
        </header>
        {rows.length === 0 ? (
          <p className="notice">Nenhuma rubrica confirmada nos exercícios escolhidos.</p>
        ) : (
          <ComparisonTable fiscalYears={fiscalYears} rows={rows} mode={mode} opener={opener} showBudgetLines />
        )}
      </section>
      <UnmatchedLines fiscalYears={fiscalYears} lines={response.unmatched} opener={opener} />
    </>
  );
}
