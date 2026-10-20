import { openFile } from "../../api/client";
import type { BudgetLine } from "../../api/types";
import { useSession } from "../../context";
import { formatMoney } from "../../format";
import { markLabel } from "../budgetVsActual/labels";

interface Props {
  lines: BudgetLine[];
  previousBudgetedColumn?: string | null;
  budgetedColumn?: string | null;
  /** Lines whose printed item repeats in the budget: only an informative mark (RF-13.2). */
  repeatedLineIds?: string[];
}

/**
 * The budget as it was printed (RF-13.2): the seven columns of the document (Item, Identificação despesa, Descrição,
 * Orçado anterior, Orçado do exercício, % Orçado, Observações), group by group, with the file and the page of each line.
 * Values read are never edited here. Descrição is shown as read (in group 1.3 it carries the supplier, with no effect on calculation).
 */
export function BudgetLines({ lines, previousBudgetedColumn, budgetedColumn, repeatedLineIds = [] }: Props) {
  const { condominiumId } = useSession();
  const repeated = new Set(repeatedLineIds);
  return (
    <section className="block">
      <h2>PO como foi impressa</h2>
      <div className="scroll">
        <table className="table compact budget-vs-actual">
          <thead>
            <tr>
              <th>Item</th>
              <th>Identificação despesa</th>
              <th>Descrição</th>
              <th className="number">Orçado {previousBudgetedColumn ?? "anterior"}</th>
              <th className="number">Orçado {budgetedColumn ?? "do exercício"}</th>
              <th className="number">% Orçado</th>
              <th>Observações</th>
              <th>Origem</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => (
              <tr key={l.id} className={l.type === "LINE" ? undefined : "group-row"}>
                <td>
                  {l.effectiveCode}
                  {l.effectiveCode !== l.printedCode && <small className="muted"> (impresso {l.printedCode})</small>}
                  {repeated.has(l.id) && (
                    <span className="badge neutral" title="Só aviso: não bloqueia a confirmação; a linha é identificada pela posição na PO">
                      item repetido
                    </span>
                  )}
                </td>
                <td>{l.mark ? <span className="badge neutral">{markLabel[l.mark]}</span> : (l.account ?? l.accountText ?? "")}</td>
                <td>{l.description}</td>
                <td className="number">{formatMoney(l.previousBudgeted)}</td>
                <td className="number">{formatMoney(l.budgeted)}</td>
                <td className="number">{l.percentageText}</td>
                <td className="muted">{l.notes}</td>
                <td>
                  <button
                    className="link-button"
                    title={`SHA-256 ${l.sha256}`}
                    onClick={() => void openFile(`/condominiums/${condominiumId}/files/${l.fileId}/content`, l.page)}
                  >
                    pág. {l.page}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">
        Subtotais como impressos. A soma das linhas de cada grupo, conferida com o impresso, está em “Conferência da leitura”.
      </p>
    </section>
  );
}
