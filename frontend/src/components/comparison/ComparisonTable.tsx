import type { ReactNode } from "react";
import type { ComparedFiscalYear, ComparedValue } from "../../api/types";
import { formatMoneyOrDash } from "../../format";
import type { EvidenceOpener } from "./navigation";
import { Variation } from "./Variation";
import { AmountOrDash } from "./AmountOrDash";

/** What the table shows for each fiscal year: the monthly planned or the compared months (planned and actual). */
export type ValueMode = "MONTHLY_PLANNED" | "MONTHS";

/** A table row: a group (1.1 to 1.9) or a budget item, with the value of each fiscal year. */
export interface ComparedRow {
  key: string;
  title: ReactNode;
  note?: string | null;
  values: ComparedValue[];
}

interface Props {
  fiscalYears: ComparedFiscalYear[];
  rows: ComparedRow[];
  mode: ValueMode;
  opener: EvidenceOpener;
  /** In the per-line view: shows the budget lines summed in each fiscal year (lines split or merged). */
  showBudgetLines?: boolean;
}

/** Views 2 and 3 of RF-11.6: values of each fiscal year side by side, with the variation against the next fiscal year. */
export function ComparisonTable({ fiscalYears, rows, mode, opener, showBudgetLines }: Props) {
  const columns = mode === "MONTHLY_PLANNED" ? 2 : 3;
  return (
    <div className="scroll">
      <table className="table compared">
        <thead>
          <tr>
            <th rowSpan={2} scope="col">
              {showBudgetLines ? "Rubrica" : "Grupo"}
            </th>
            {fiscalYears.map((f) => (
              <th key={f.id} colSpan={columns} scope="colgroup" className="fiscal-year-start">
                {f.label}
              </th>
            ))}
          </tr>
          <tr>
            {fiscalYears.map((f) =>
              mode === "MONTHLY_PLANNED" ? (
                <ValueHeaders key={f.id} names={["Previsto do mês", "Variação"]} />
              ) : (
                <ValueHeaders key={f.id} names={["Previsto", "Realizado", "Variação do realizado"]} />
              ),
            )}
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.key}>
              <th scope="row">
                {r.title}
                {r.note && <small className="note">{r.note}</small>}
              </th>
              {fiscalYears.map((f) => (
                <FiscalYearCells
                  key={f.id}
                  value={r.values.find((v) => v.fiscalYearId === f.id)}
                  mode={mode}
                  fiscalYearId={f.id}
                  opener={opener}
                  showBudgetLines={showBudgetLines}
                />
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function ValueHeaders({ names }: { names: string[] }) {
  return (
    <>
      {names.map((n, i) => (
        <th key={n} scope="col" className={i === 0 ? "number fiscal-year-start" : "number"}>
          {n}
        </th>
      ))}
    </>
  );
}

interface CellsProps {
  /** Missing when the fiscal year does not have this group or budget item: everything becomes "—". */
  value: ComparedValue | undefined;
  mode: ValueMode;
  fiscalYearId: string;
  opener: EvidenceOpener;
  showBudgetLines?: boolean;
}

function FiscalYearCells({ value, mode, fiscalYearId, opener, showBudgetLines }: CellsProps) {
  const open = opener(fiscalYearId, value?.target);
  const budgetLines = showBudgetLines && value && value.lines.length > 0 && (
    <small className="note">
      {value.lines.map((l, i) => {
        const openLine = opener(fiscalYearId, l.target);
        return (
          <span key={l.lineId}>
            {i > 0 && ", "}
            {openLine ? (
              <button type="button" className="link-button" onClick={openLine} title={l.description}>
                {l.code}
              </button>
            ) : (
              <span title={l.description}>{l.code}</span>
            )}
          </span>
        );
      })}
    </small>
  );

  if (mode === "MONTHLY_PLANNED") {
    return (
      <>
        <td className="number fiscal-year-start">
          <AmountOrDash value={value?.monthlyPlanned} onOpen={open} />
          {budgetLines}
        </td>
        <td className="number">
          <Variation variation={value?.monthlyPlannedVariation} />
        </td>
      </>
    );
  }
  return (
    <>
      <td className="number fiscal-year-start">
        {formatMoneyOrDash(value?.planned)}
        {budgetLines}
      </td>
      <td className="number">
        <AmountOrDash value={value?.actual} onOpen={open} />
      </td>
      <td className="number">
        <Variation variation={value?.actualVariation} />
      </td>
    </>
  );
}
