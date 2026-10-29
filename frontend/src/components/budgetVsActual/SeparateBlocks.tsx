import type { ReactNode } from "react";
import { useSession } from "../../context";
import type { EntryBlock, CashFlowCheck, BudgetVsActual } from "../../api/types";
import { formatMoney } from "../../format";
import type { OpenEvidence } from "./evidence";
import { ReallocationList } from "./Reallocation";
import { SourcedValue } from "./SourcedValue";

interface Props {
  result: BudgetVsActual;
  onOpenEvidence: OpenEvidence;
}

/**
 * Blocks outside the budget lines (RF-03.1.6 and RF-03.1.13): without a budget line, to reallocate, adjustments and the
 * check against the cash flow.
 */
export function SeparateBlocks({ result, onOpenEvidence }: Props) {
  const { can } = useSession();
  const openToReallocate = () => onOpenEvidence({ target: "TO_REALLOCATE", title: "A realocar" });
  return (
    <div className="two-columns">
      <EntryBlockSection
        title="Sem linha da PO"
        hint="Contas do fluxo sem linha encontrada na PO, com a ligação recusada ou que o Admin deixou sem linha. Entram na despesa realizada, mas em nenhuma linha da PO."
        block={result.withoutBudgetLine}
        onOpen={() => onOpenEvidence({ target: "NO_BUDGET_LINE", title: "Sem linha da PO" })}
      />
      <EntryBlockSection
        title="A realocar"
        hint="Compras por meio de pagamento (RF-02B) que ainda não foram realocadas para uma linha da PO."
        block={result.toReallocate}
        onOpen={openToReallocate}
      >
        {/* Manager and Admin choose the line of each purchase in the evidence; the backend blocks the others */}
        {can("MANAGER", "ADMIN") && (result.toReallocate?.total ?? 0) !== 0 && (
          <button className="button secondary" onClick={openToReallocate}>
            Realocar compras
          </button>
        )}
        {result.budget && <ReallocationList budgetId={result.budget.id} />}
      </EntryBlockSection>
      <EntryBlockSection
        title="Ajustes (não são despesa)"
        hint="Estornos e repasses: ficam fora da despesa realizada."
        block={result.adjustments}
        onOpen={() => onOpenEvidence({ target: "ADJUSTMENTS", title: "Ajustes (não são despesa)" })}
      />
      {result.cashFlowCheck && <CashFlowCheckSection check={result.cashFlowCheck} onOpenEvidence={onOpenEvidence} />}
    </div>
  );
}

interface BlockProps {
  title: string;
  hint: string;
  block: EntryBlock | null | undefined;
  onOpen: () => void;
  children?: ReactNode;
}

function EntryBlockSection({ title, hint, block, onOpen, children }: BlockProps) {
  return (
    <section className="block">
      <header className="block-title">
        <h2 title={hint}>{title}</h2>
        {block ? <SourcedValue value={block.total} onOpen={onOpen} className="highlight" /> : <strong>{formatMoney(0)}</strong>}
      </header>
      <p className="muted">{hint}</p>
      {block && block.accounts.length > 0 && (
        <table className="table compact">
          <thead>
            <tr>
              <th>Conta do fluxo</th>
              <th>Situação</th>
              <th className="number">Lançamentos</th>
              <th className="number">Valor</th>
            </tr>
          </thead>
          <tbody>
            {block.accounts.map((a, i) => (
              <tr key={`${a.account}-${i}`}>
                <td>
                  {a.account} {a.name}
                </td>
                <td className="muted">{a.detail}</td>
                <td className="number">{a.entries}</td>
                <td className="number">{formatMoney(a.amount)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {children}
    </section>
  );
}

function CashFlowCheckSection({ check, onOpenEvidence }: { check: CashFlowCheck; onOpenEvidence: OpenEvidence }) {
  return (
    <section className="block">
      <header className="block-title">
        <h2>Conferência com o fluxo</h2>
        <span className={check.matches ? "badge ok" : "badge warning"}>{check.matches ? "Confere" : "Não confere"}</span>
      </header>
      <dl className="number-list">
        <dt>Débitos do fundo no fluxo</dt>
        <dd>
          {formatMoney(check.fundDebits)} ({check.entries} lançamentos)
        </dd>
        <dt>Despesa realizada</dt>
        <dd>{formatMoney(check.actualExpense)}</dd>
        <dt>Ajustes</dt>
        <dd>
          <SourcedValue
            value={check.adjustments}
            onOpen={() => onOpenEvidence({ target: "ADJUSTMENTS", title: "Ajustes (não são despesa)" })}
          />
        </dd>
        <dt>Transferências entre fundos</dt>
        <dd>
          <SourcedValue
            value={check.transfers}
            onOpen={() => onOpenEvidence({ target: "TRANSFERS", title: "Transferências entre fundos" })}
          />
        </dd>
      </dl>
    </section>
  );
}
