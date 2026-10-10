import type { ComparedFiscalYear, UnmatchedLine } from "../../api/types";
import type { EvidenceOpener } from "./navigation";
import { AmountOrDash } from "./AmountOrDash";

interface Props {
  fiscalYears: ComparedFiscalYear[];
  lines: UnmatchedLine[];
  opener: EvidenceOpener;
}

/**
 * "Sem correspondência" block of RF-11.6 (view 3): lines without a confirmed budget item, each with the amount
 * of its own fiscal year. They are never added to another line; the matching is done by the Admin on the budget screen.
 */
export function UnmatchedLines({ fiscalYears, lines, opener }: Props) {
  const label = (fiscalYearId: string) => fiscalYears.find((f) => f.id === fiscalYearId)?.label ?? fiscalYearId;
  return (
    <section className="block">
      <h2>Sem correspondência ({lines.length})</h2>
      <p className="muted">
        Linhas sem rubrica confirmada. Aparecem com o valor do próprio exercício e não entram na comparação por linha.
      </p>
      {lines.length === 0 ? (
        <p className="notice">Todas as linhas têm correspondência confirmada.</p>
      ) : (
        <div className="scroll">
          <table className="table compact">
            <thead>
              <tr>
                <th>Exercício</th>
                <th>Código</th>
                <th>Conta da PO</th>
                <th>Descrição</th>
                <th>Grupo</th>
                <th className="number">Previsto do mês</th>
                <th className="number">Previsto</th>
                <th className="number">Realizado</th>
              </tr>
            </thead>
            <tbody>
              {lines.map((l) => {
                const open = opener(l.fiscalYearId, l.target);
                return (
                  <tr key={`${l.fiscalYearId}-${l.lineId}`}>
                    <td>{label(l.fiscalYearId)}</td>
                    <td>{l.code}</td>
                    <td className="muted">{l.account ?? "—"}</td>
                    <td>{l.description}</td>
                    <td className="muted">{l.group ?? "—"}</td>
                    <td className="number">
                      <AmountOrDash value={l.monthlyPlanned} onOpen={open} />
                    </td>
                    <td className="number">
                      <AmountOrDash value={l.planned} />
                    </td>
                    <td className="number">
                      <AmountOrDash value={l.actual} onOpen={open} />
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </section>
  );
}
