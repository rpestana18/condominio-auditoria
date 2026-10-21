import { useState } from "react";
import { useSearchParams } from "react-router";
import { useFunds } from "../api/budgetQueries";
import { useBalanceBridge, type BridgeFilter } from "../api/budgetTrackingQueries";
import type { BalanceBridge, BalanceBridgeItemCode, Fund } from "../api/types";
import { BridgeChart } from "../components/balanceBridge/BridgeChart";
import { BridgeFilters } from "../components/balanceBridge/BridgeFilters";
import { BridgeItemDetail } from "../components/balanceBridge/BridgeItemDetail";
import { BridgeMonthsTable } from "../components/balanceBridge/BridgeMonthsTable";
import { BridgeSummary } from "../components/balanceBridge/BridgeSummary";
import type { OpenBridgeEvidence } from "../components/balanceBridge/bridgeEvidence";
import { splitMappingWarning } from "../components/MappingWarningNote";
import { EvidencePanel } from "../components/budgetVsActual/EvidencePanel";
import type { EvidenceTarget } from "../components/budgetVsActual/evidence";
import { useSession } from "../context";
import { formatMonth } from "../format";

/**
 * "Origem do saldo" (RF-13.6), for every role: the bridge between the opening and the closing balance of the chosen
 * fund. Filters in the address (?fundo=<id|all>&de=AAAA-MM&ate=AAAA-MM), so the home screen summary opens the same view.
 */
export function BalanceOrigin() {
  const { condominiumId } = useSession();
  const [parameters, setParameters] = useSearchParams();
  const filter: BridgeFilter = { fund: parameters.get("fundo"), from: parameters.get("de"), to: parameters.get("ate") };
  // The period only goes to the API with both months filled
  const complete = !!filter.from && !!filter.to;
  const sent: BridgeFilter = complete ? filter : { ...filter, from: null, to: null };
  const { data: funds = [] } = useFunds(condominiumId);
  const query = useBalanceBridge(condominiumId, sent);

  const change = (next: BridgeFilter) => {
    const search = new URLSearchParams();
    if (next.fund) search.set("fundo", next.fund);
    if (next.from) search.set("de", next.from);
    if (next.to) search.set("ate", next.to);
    setParameters(search);
  };

  return (
    <>
      <header className="page-title">
        <h1>Origem do saldo</h1>
      </header>
      <BridgeFilters funds={funds} filter={filter} onChange={change} />
      {(filter.from || filter.to) && !complete && <p className="notice">Escolha os dois meses para mudar o período.</p>}
      {query.isLoading && <p className="notice">Carregando…</p>}
      {query.error && <p className="notice error">{query.error.message}</p>}
      {query.data && (
        <Content
          // Another view starts with nothing selected
          key={`${sent.fund}-${sent.from}-${sent.to}`}
          bridge={query.data}
          funds={funds}
          defaultPeriod={!complete}
        />
      )}
    </>
  );
}

interface ContentProps {
  bridge: BalanceBridge;
  funds: Fund[];
  /** The API picked the period (fiscal year up to the last month loaded). */
  defaultPeriod: boolean;
}

/**
 * Period of the evidence API for this bridge, or null when the entries would not match the numbers shown:
 * only the operating fund (budget vs actual is calculated on it) and, for the period, one month or the default fiscal year
 * without a partial month outside the total.
 */
function evidencePeriod(bridge: BalanceBridge, funds: Fund[], defaultPeriod: boolean): string | null {
  const operating = funds.find((f) => f.operating);
  if (bridge.allFunds || !operating || bridge.fundId !== operating.id || !bridge.budget) return null;
  if (bridge.from === bridge.to) return bridge.from;
  const outsideTotal = bridge.months.some((m) => m.status === "WITH_CASH_FLOW" && !m.inTotal);
  return defaultPeriod && !outsideTotal ? "cumulative" : null;
}

function Content({ bridge, funds, defaultPeriod }: ContentProps) {
  const [selected, setSelected] = useState<BalanceBridgeItemCode | null>(null);
  const [evidence, setEvidence] = useState<EvidenceTarget | null>(null);
  const period = evidencePeriod(bridge, funds, defaultPeriod);
  const openEvidence: OpenBridgeEvidence | null = period ? setEvidence : null;
  const item = bridge.items.find((i) => i.code === selected);

  if (bridge.status !== "CALCULATED") {
    return (
      <>
        {bridge.warnings.map((w, i) => (
          <p key={`${w.code}-${i}`} className="notice warning">
            {w.text}
          </p>
        ))}
        <p className="notice warning">{bridge.message ?? "Ainda não há números para a origem do saldo."}</p>
      </>
    );
  }

  // The mapping warning goes next to "Economia nas linhas", the others stay on top
  const { mapping, others } = splitMappingWarning(bridge.warnings);

  return (
    <div className={evidence ? "with-detail" : undefined}>
      <div>
        {others.map((w, i) => (
          <p key={`${w.code}-${i}`} className="notice warning">
            {w.text}
          </p>
        ))}
        <div className="two-columns">
          <BridgeSummary bridge={bridge} selected={selected} onSelect={setSelected} mappingWarning={mapping} />
          <BridgeChart bridge={bridge} />
        </div>
        {item && <BridgeItemDetail item={item} onOpenEvidence={openEvidence} />}
        {item && !period && (
          <p className="muted">
            Os lançamentos abrem para o fundo ordinário, com um mês só ou com o exercício sem mês parcial. Escolha um mês para vê-los.
          </p>
        )}
        <BridgeMonthsTable bridge={bridge} />
        <p className="muted">
          As parcelas são números para conferência e somam exatamente a variação do saldo. Cálculo: versão {bridge.calculationVersion}.
        </p>
      </div>
      {evidence && period && bridge.budget && (
        <EvidencePanel
          key={evidence.target}
          period={period}
          budget={bridge.budget}
          target={{ ...evidence, title: `${evidence.title} · ${formatMonth(period)}` }}
          onClose={() => setEvidence(null)}
        />
      )}
    </div>
  );
}
