import type { BudgetVsActual, BudgetVsActualTotals } from "../../api/types";
import { differenceClass, formatDifference, formatMonth, formatMoney, formatPercentage } from "../../format";
import type { OpenEvidence } from "./evidence";
import { SourcedValue } from "./SourcedValue";

interface Props {
  result: BudgetVsActual;
  totals: BudgetVsActualTotals;
  onOpenEvidence: OpenEvidence;
}

/** Top cards: planned, actual expense, difference and execution (all calculated by the backend). */
export function BudgetVsActualSummary({ result, totals, onOpenEvidence }: Props) {
  const cumulative = result.period === "cumulative";
  return (
    <>
      <div className="cards">
        <SummaryCard title={cumulative ? "Previsto dos meses com fluxo" : "Previsto do mês"} value={formatMoney(totals.planned)} />
        <div
          className="number-card"
          title={`Em linhas da PO: ${formatMoney(totals.inLines)}; o restante está em "a realocar" e "sem linha da PO"`}
        >
          <span className="card-title">Despesa realizada</span>
          <strong>
            <SourcedValue
              value={totals.actualExpense}
              onOpen={() => onOpenEvidence({ target: "total", title: "Despesa realizada" })}
            />
          </strong>
        </div>
        <SummaryCard title="Diferença" value={formatDifference(totals.difference)} className={differenceClass(totals.difference)} />
        <SummaryCard title="Execução" value={formatPercentage(totals.execution)} hint="Realizado ÷ previsto" />
        {cumulative && (
          <SummaryCard
            title="Previsto do exercício (referência)"
            value={formatMoney(totals.fiscalYearPlanned)}
            hint={`Previsto do mês (${formatMoney(totals.monthlyPlanned)}) × meses do exercício`}
          />
        )}
      </div>
      {cumulative && <MissingMonths result={result} />}
    </>
  );
}

function SummaryCard({ title, value, className, hint }: { title: string; value: string; className?: string; hint?: string }) {
  return (
    <div className="number-card" title={hint}>
      <span className="card-title">{title}</span>
      <strong className={className}>{value}</strong>
    </div>
  );
}

/** RF-03.1.10: the cumulative view says which months did not enter (never treats them as zero). */
function MissingMonths({ result }: { result: BudgetVsActual }) {
  const { summedMonths, monthsWithoutCashFlow, monthsWithTwoCashFlows } = result;
  return (
    <p className="muted">
      Somados: {summedMonths.map(formatMonth).join(", ") || "nenhum mês"}.
      {monthsWithoutCashFlow.length > 0 && ` Sem fluxo carregado: ${monthsWithoutCashFlow.map(formatMonth).join(", ")}.`}
      {monthsWithTwoCashFlows.length > 0 && ` Com dois fluxos (não somados): ${monthsWithTwoCashFlows.map(formatMonth).join(", ")}.`}
    </p>
  );
}
