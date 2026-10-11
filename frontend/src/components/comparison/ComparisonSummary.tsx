import type { ReactNode } from "react";
import type { ComparedFiscalYear, ComparedSummary } from "../../api/types";
import { formatMonth, formatMoney, formatPercentage } from "../../format";
import type { EvidenceOpener } from "./navigation";
import { Variation } from "./Variation";
import { AmountOrDash } from "./AmountOrDash";

interface Props {
  fiscalYears: ComparedFiscalYear[];
  summary: ComparedSummary[];
  opener: EvidenceOpener;
}

/** A table row: the name of the number and how to render each fiscal year's cell. */
interface Metric {
  label: string;
  hint?: string;
  cell: (s: ComparedSummary) => ReactNode;
}

/** Integer that may be missing (e.g. the printed column has no findings): null becomes "—". */
const integerOrDash = (value: number | null | undefined) => (value === null || value === undefined ? "—" : String(value));

/**
 * View 1 of RF-11.6, summary per fiscal year. The numbers are the backend's, in the API order; the columns are
 * the fiscal years (from the most recent to the oldest) and the variation is against the next column.
 */
export function ComparisonSummary({ fiscalYears, summary, opener }: Props) {
  const metrics: Metric[] = [
    { label: "Previsto do mês", hint: "Soma das linhas sem os fundos", cell: (s) => formatMoney(s.monthlyPlanned) },
    { label: "Variação do previsto do mês", cell: (s) => <Variation variation={s.monthlyPlannedVariation} /> },
    { label: "Previsto do exercício", cell: (s) => formatMoney(s.fiscalYearPlanned) },
    { label: "Meses com fluxo", cell: (s) => String(s.monthsWithCashFlow) },
    { label: "Previsto dos meses comparados", cell: (s) => <AmountOrDash value={s.planned} /> },
    {
      label: "Realizado dos meses comparados",
      hint: "Clique para ver os lançamentos no previsto × realizado",
      cell: (s) => <AmountOrDash value={s.actual} onOpen={opener(s.fiscalYearId, s.target)} />,
    },
    { label: "Variação do realizado", cell: (s) => <Variation variation={s.actualVariation} /> },
    { label: "Execução", hint: "Realizado ÷ previsto", cell: (s) => formatPercentage(s.execution) },
    {
      label: "Maior excesso mensal (regra dos 20%)",
      cell: (s) =>
        s.largestOverrun ? (
          <>
            {formatMoney(s.largestOverrun.amount)}
            <small className="note">
              {formatPercentage(s.largestOverrun.percentage)} em {formatMonth(s.largestOverrun.month)}
            </small>
          </>
        ) : (
          "—"
        ),
    },
    { label: "Meses acima do limite", cell: (s) => integerOrDash(s.monthsAboveLimit) },
    { label: "Achados abertos", cell: (s) => integerOrDash(s.openFindings) },
    {
      label: "Situação",
      hint: "Há valor a realocar ou conta sem linha da PO",
      cell: (s) => (s.provisional ? <span className="badge warning">Provisório</span> : "—"),
    },
  ];

  return (
    <section className="block">
      <h2>Resumo por exercício</h2>
      <div className="scroll">
        <table className="table">
          <thead>
            <tr>
              <th scope="col">Número</th>
              {fiscalYears.map((f) => (
                <th key={f.id} scope="col" className="number">
                  {f.label}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {metrics.map((m) => (
              <tr key={m.label}>
                <th scope="row" title={m.hint}>
                  {m.label}
                </th>
                {fiscalYears.map((f) => {
                  const s = summary.find((x) => x.fiscalYearId === f.id);
                  return (
                    <td key={f.id} className="number">
                      {s ? m.cell(s) : "—"}
                    </td>
                  );
                })}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
