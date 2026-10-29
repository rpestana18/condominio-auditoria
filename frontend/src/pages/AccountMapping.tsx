import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useAccountMappings, useBudget, useBudgets } from "../api/budgetQueries";
import type { MappedAccount, AccountMappingFilter, BudgetSummary } from "../api/types";
import { AccountMappingActions } from "../components/accountMapping/AccountMappingActions";
import { TargetEditor } from "../components/accountMapping/TargetEditor";
import { AccountMappingTable } from "../components/accountMapping/AccountMappingTable";
import { AccountMappingTrail } from "../components/accountMapping/AccountMappingTrail";
import { budgetStatusLabel } from "../components/budgetVsActual/labels";
import { useSession } from "../context";

/** Q54: the screen opens on the exceptions; the rest is linked by the system and already counts. */
const filters: { code: AccountMappingFilter; label: string }[] = [
  { code: "EXCEPTIONS", label: "Só exceções" },
  { code: "ALL", label: "Todas" },
  { code: "UNMAPPED", label: "Sem linha encontrada" },
  { code: "LOW_CONFIDENCE", label: "Baixa confiança" },
  { code: "AUTOMATIC", label: "Automáticas" },
  { code: "CONFIRMED", label: "Conferidas pelo Admin" },
  { code: "REJECTED", label: "Recusadas" },
  { code: "SAME_AS_PREVIOUS_VERSION", label: "Iguais à versão anterior" },
];

/** The default budget is the most recent confirmed one (the list already comes from the most recent to the oldest). */
const defaultBudget = (budgets: BudgetSummary[]) => budgets.find((b) => b.status === "CONFIRMED") ?? budgets[0];

/** Accounts without a line found come first, then the low-confidence links, then the rest in the backend's order. */
const rank = (a: MappedAccount) => (a.status == null ? 0 : a.lowConfidence || a.status === "REJECTED" ? 1 : 2);
const withoutMappingFirst = (accounts: MappedAccount[]) => [...accounts].sort((a, b) => rank(a) - rank(b));

/**
 * "De-para" screen (RF-03.1.13, Q54): everyone can view; only the Admin changes, confirms and refuses.
 * The filter lives in the address (?filtro=UNMAPPED), so the budget vs actual warning brings the Admin straight here.
 * Opening the screen already applies the automatic links (the backend links by itself) and shows only the exceptions.
 */
export function AccountMapping() {
  const { condominiumId, can } = useSession();
  const admin = can("ADMIN");
  const [parameters, setParameters] = useSearchParams();
  const { data: budgets = [], isLoading: loadingBudgets } = useBudgets(condominiumId);
  const budgetId = parameters.get("po") ?? defaultBudget(budgets)?.id;
  const filter = (parameters.get("filtro") as AccountMappingFilter | null) ?? "EXCEPTIONS";
  const { data: list, isLoading, error } = useAccountMappings(condominiumId, budgetId, filter);
  const { data: budget } = useBudget(condominiumId, budgetId);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [editing, setEditing] = useState<MappedAccount | null>(null);

  const change = (name: string, value: string | null) => {
    setSelected(new Set());
    setParameters((current) => {
      const next = new URLSearchParams(current);
      if (value) next.set(name, value);
      else next.delete(name);
      return next;
    });
  };

  if (loadingBudgets) return <p className="notice">Carregando…</p>;
  if (!budgetId) {
    return (
      <section className="empty">
        <h1>Nenhuma PO lida ainda</h1>
        <p>O de-para aparece depois que a PO aprovada for enviada na categoria PO e lida.</p>
      </section>
    );
  }

  const budgetConfirmed = budget?.budget.status === "CONFIRMED";
  // Lines that can be a target: only the expense ones (no total, group or funds)
  const targetLines = budget?.lines.filter((l) => l.type === "LINE" && !l.fundLine) ?? [];
  const accounts = withoutMappingFirst(list?.accounts ?? []);
  const withoutMapping = list?.summary.withoutMapping ?? 0;
  const exceptions = list ? list.summary.withoutMapping + list.summary.lowConfidence + list.summary.rejected : 0;

  return (
    <>
      <header className="page-title">
        <h1>De-para das contas do fluxo</h1>
        {list && (
          <span className={exceptions === 0 ? "badge ok" : "badge warning"}>
            {list.summary.automatic + list.summary.confirmed} de {list.summary.accounts} contas ligadas
          </span>
        )}
        <Link className="link-button" to={`/previsto-realizado?po=${budgetId}`}>
          Ver o previsto × realizado
        </Link>
      </header>

      <div className="filters">
        <label>
          PO
          <select value={budgetId} onChange={(e) => change("po", e.target.value)}>
            {budgets.map((b) => (
              <option key={b.id} value={b.id}>
                {b.version ? `Versão ${b.version}` : "Sem versão"} · {b.printedFiscalYear ?? b.fileName} · {budgetStatusLabel[b.status]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {list && (
        <p className="muted">
          {list.summary.automatic} ligadas pelo sistema ({list.summary.lowConfidence} de baixa confiança) · {list.summary.confirmed}{" "}
          conferidas pelo Admin · {list.summary.withoutMapping} sem linha encontrada · {list.summary.rejected} recusadas. O sistema liga
          sozinho (versão anterior, planilha guardada, planilha de apoio da PO e nome, nunca pelo número da conta) e a ligação já vale no
          previsto × realizado. Confira só as exceções; qualquer ligação pode ser alterada, com trilha.
        </p>
      )}
      {budgetConfirmed && withoutMapping > 0 && (
        <p className="notice warning">
          <strong>
            {withoutMapping} {withoutMapping === 1 ? "conta sem linha encontrada" : "contas sem linha encontrada"}
          </strong>
          {admin
            ? ': ficam em "sem linha da PO", com aviso, até você escolher o destino em "Escolher destino" (busca por código e por nome). '
            : ': ficam em "sem linha da PO", com aviso, até o Admin escolher o destino. '}
          {filter !== "UNMAPPED" && (
            <button className="link-button" onClick={() => change("filtro", "UNMAPPED")}>
              Ver só as sem linha encontrada
            </button>
          )}
        </p>
      )}
      {!budgetConfirmed && budget && (
        <p className="notice warning">
          A PO ainda não foi confirmada. <Link to={`/previsoes/${budgetId}`}>Conferir a PO</Link> antes de editar o de-para.
        </p>
      )}

      {admin && budgetConfirmed && (
        <AccountMappingActions budgetId={budgetId} list={list} selected={[...selected]} onBatchDone={() => setSelected(new Set())} />
      )}

      <div className="tabs" role="tablist">
        {filters.map((f) => (
          <button key={f.code} role="tab" aria-selected={filter === f.code} onClick={() => change("filtro", f.code === "EXCEPTIONS" ? null : f.code)}>
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : accounts.length === 0 ? (
        <p className="notice">{filter === "EXCEPTIONS" ? "Nenhuma exceção: todas as contas estão ligadas a uma linha da PO." : "Nenhuma conta neste filtro."}</p>
      ) : (
        <AccountMappingTable
          accounts={accounts}
          editing={
            admin && budgetConfirmed
              ? {
                  selected,
                  onSelect: (account, checked) =>
                    setSelected((current) => {
                      const next = new Set(current);
                      if (checked) next.add(account);
                      else next.delete(account);
                      return next;
                    }),
                  onSelectAll: (checked) => setSelected(checked ? new Set(accounts.map((a) => a.account)) : new Set()),
                  onEdit: setEditing,
                }
              : undefined
          }
        />
      )}

      <AccountMappingTrail budgetId={budgetId} />
      {editing && <TargetEditor budgetId={budgetId} account={editing} lines={targetLines} onClose={() => setEditing(null)} />}
    </>
  );
}
