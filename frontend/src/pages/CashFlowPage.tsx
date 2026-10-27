import { useState } from "react";
import { Link } from "react-router";
import { useFunds } from "../api/budgetQueries";
import { useCashFlowMonthly, type CashFlowEntryFilter, type CashFlowFund } from "../api/cashFlowQueries";
import type { CashFlowMonthly } from "../api/types";
import { CashFlowChart } from "../components/cashFlow/CashFlowChart";
import { CashFlowEntriesPanel } from "../components/cashFlow/CashFlowEntriesPanel";
import { CashFlowTable } from "../components/cashFlow/CashFlowTable";
import { useSession } from "../context";
import { formatMonth } from "../format";

/**
 * "Entradas e saídas" (RF-13.11; ADR 0009, Decision 4), the entry screen of "Análise da PO", for every role: the months
 * side by side, from the first cash flow loaded to the last one (also before the approved budget, RF-13.16), and for
 * each outflow the budget line it belongs to. Calculated by the API on each query.
 */
export function CashFlowPage() {
  const { condominiumId } = useSession();
  const [fund, setFund] = useState<CashFlowFund>(null);
  const { data: funds = [] } = useFunds(condominiumId);
  const query = useCashFlowMonthly(condominiumId, fund);
  const operating = funds.find((f) => f.operating);
  return (
    <>
      <header className="page-title">
        <h1>Entradas e saídas</h1>
        {query.data?.from && query.data.to && (
          <span className="badge neutral">
            {formatMonth(query.data.from)} a {formatMonth(query.data.to)}
          </span>
        )}
      </header>
      <div className="filters">
        <label>
          Fundo
          <select value={fund ?? ""} onChange={(e) => setFund(e.target.value || null)}>
            <option value="">{operating ? `Fundo ordinário (${operating.name})` : "Fundo ordinário"}</option>
            <option value="all">Todos os fundos</option>
            {funds
              .filter((f) => !f.operating)
              .map((f) => (
                <option key={f.id} value={f.id}>
                  {f.name}
                </option>
              ))}
          </select>
        </label>
      </div>
      {query.isLoading && <p className="notice">Carregando…</p>}
      {query.error && <p className="notice error">{query.error.message}</p>}
      {query.data && <Content key={fund ?? "operating"} cashFlow={query.data} fund={fund} />}
    </>
  );
}

function Content({ cashFlow, fund }: { cashFlow: CashFlowMonthly; fund: CashFlowFund }) {
  const [entries, setEntries] = useState<{ filter: CashFlowEntryFilter; title: string } | null>(null);
  const notReviewed = cashFlow.lineReview && !cashFlow.lineReview.reviewed;
  if (cashFlow.months.length === 0) {
    return (
      <>
        {cashFlow.warnings.map((w, i) => (
          <p key={`${w.code}-${i}`} className="notice warning">
            {w.text}
          </p>
        ))}
        <p className="notice">Nenhum extrato lido ainda.</p>
      </>
    );
  }
  return (
    <>
      {cashFlow.warnings.map((w, i) => (
        <p key={`${w.code}-${i}`} className="notice warning">
          {w.text}
          {w.code === "LINES_NOT_REVIEWED" && cashFlow.lineReview && (
            <>
              {" "}
              <Link to={`/previsoes/${cashFlow.lineReview.budgetId}#conferencia`}>Conferir as linhas da PO</Link>
            </>
          )}
        </p>
      ))}
      {notReviewed && !cashFlow.warnings.some((w) => w.code === "LINES_NOT_REVIEWED") && (
        <p className="notice warning">
          Linhas da PO ainda não conferidas: as saídas aparecem por conta do extrato.{" "}
          <Link to={`/previsoes/${cashFlow.lineReview?.budgetId}#conferencia`}>Conferir as linhas da PO</Link>
        </p>
      )}
      <CashFlowTable
        cashFlow={cashFlow}
        onOpenEntries={({ fundId, ...filter }, title) => setEntries({ filter: { ...filter, fund: fundId ?? fund }, title })}
      />
      <p className="muted">
        Clique num valor para ver os lançamentos.{" "}
        <button className="link-button" onClick={() => setEntries({ filter: { fund, month: null, line: null, block: null, account: null }, title: "Lançamentos" })}>
          Abrir a lista de lançamentos
        </button>
      </p>
      {entries && (
        <CashFlowEntriesPanel
          cashFlow={cashFlow}
          filter={entries.filter}
          title={entries.title}
          onChange={(filter, title) => setEntries({ filter, title })}
          onClose={() => setEntries(null)}
        />
      )}
      <CashFlowChart months={cashFlow.months} />
      <p className="muted">Cálculo: versão {cashFlow.calculationVersion}.</p>
    </>
  );
}
