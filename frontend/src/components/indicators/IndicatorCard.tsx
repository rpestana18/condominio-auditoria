import type { ReactNode } from "react";
import { formatDateTime } from "../../format";

interface Props {
  /** Chart number in RF-11.11 (1 to 7). */
  number: number;
  title: string;
  /** E.g. "09/2026 a 08/2027". */
  period: string;
  /** "R$", "%" or the combination, when the card has more than one scale (chart 7). */
  unit: string;
  /** Most recent upload of the cash flows used (ISO), from the API's `dataAsOf` field. */
  dataAsOf: string | null | undefined;
  /** A usage sentence (e.g. "Clique numa barra para abrir o mês"). Never explains a cause or trend (RF-11.13). */
  hint?: string;
  /** The chart. */
  children: ReactNode;
  /** The alternative table (or more than one), with the same values as the chart. */
  table: ReactNode;
}

/** Common frame of the 7 charts (RF-11.10): title, period, unit, data date and "Ver tabela". */
export function IndicatorCard({ number, title, period, unit, dataAsOf, hint, children, table }: Props) {
  const titleId = `indicator-${number}`;
  return (
    <section className="block chart indicator" aria-labelledby={titleId}>
      <h2 id={titleId}>
        {number}. {title}
      </h2>
      <p className="muted indicator-meta">
        <span>Período: {period}</span>
        <span>Unidade: {unit}</span>
        <span>Dados de: {dataAsOf ? formatDateTime(dataAsOf) : "sem fluxo carregado"}</span>
      </p>
      {children}
      {hint && <p className="muted">{hint}</p>}
      <details className="alternative-table">
        <summary>Ver tabela</summary>
        {table}
      </details>
    </section>
  );
}

/** Neutral note in place of a chart whose series came null for the chosen filter. */
export function NotApplicable({ title, reason }: { title: string; reason: string }) {
  return (
    <section className="block indicator-not-applicable">
      <h2>{title}</h2>
      <p className="muted">{reason}</p>
    </section>
  );
}
