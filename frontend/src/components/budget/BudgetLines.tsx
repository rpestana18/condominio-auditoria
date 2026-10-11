import { openFile } from "../../api/client";
import type { BudgetLine } from "../../api/types";
import { useSession } from "../../context";
import { formatMoney } from "../../format";
import { markLabel } from "../budgetVsActual/labels";

interface Props {
  lines: BudgetLine[];
  previousBudgetedColumn?: string | null;
  budgetedColumn?: string | null;
}

/** The lines as they were read (RF-03.1.1). Values read are never edited here. */
export function BudgetLines({ lines, previousBudgetedColumn, budgetedColumn }: Props) {
  const { condominiumId } = useSession();
  return (
    <section className="block">
      <h2>Linhas lidas</h2>
      <div className="scroll">
        <table className="table compact budget-vs-actual">
          <thead>
            <tr>
              <th>Código</th>
              <th>Conta da PO</th>
              <th>Descrição</th>
              <th className="number">Orçado {previousBudgetedColumn ?? "anterior"}</th>
              <th className="number">Orçado {budgetedColumn ?? ""}</th>
              <th className="number">%</th>
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
    </section>
  );
}
