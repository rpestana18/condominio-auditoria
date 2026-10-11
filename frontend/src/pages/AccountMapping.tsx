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

const filters: { code: AccountMappingFilter; label: string }[] = [
  { code: "ALL", label: "Todas" },
  { code: "PENDING", label: "Pendentes" },
  { code: "SAME_AS_PREVIOUS_VERSION", label: "Iguais à versão anterior" },
  { code: "SUGGESTED", label: "Sugeridas" },
  { code: "CONFIRMED", label: "Confirmadas" },
  { code: "REJECTED", label: "Recusadas" },
  { code: "UNMAPPED", label: "Sem de-para" },
];

/** The default budget is the most recent confirmed one (the list already comes from the most recent to the oldest). */
const defaultBudget = (budgets: BudgetSummary[]) => budgets.find((b) => b.status === "CONFIRMED") ?? budgets[0];

/**
 * "De-para" screen (RF-03.1.13): everyone can view; only the Admin edits, confirms and rejects.
 * The filter lives in the address (?filtro=PENDING), so the budget vs actual warning brings the Admin straight here.
 */
export function AccountMapping() {
  const { condominiumId, can } = useSession();
  const admin = can("ADMIN");
  const [parameters, setParameters] = useSearchParams();
  const { data: budgets = [], isLoading: loadingBudgets } = useBudgets(condominiumId);
  const budgetId = parameters.get("po") ?? defaultBudget(budgets)?.id;
  const filter = (parameters.get("filtro") as AccountMappingFilter | null) ?? "ALL";
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
  const accounts = list?.accounts ?? [];

  return (
    <>
      <header className="page-title">
        <h1>De-para das contas do fluxo</h1>
        {list && (
          <span className={list.summary.confirmed === list.summary.accounts ? "badge ok" : "badge warning"}>
            {list.summary.confirmed} de {list.summary.accounts} contas confirmadas
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
          {list.summary.suggested} sugeridas · {list.summary.rejected} recusadas · {list.summary.withoutMapping} sem de-para. Só as
          confirmadas entram no previsto × realizado; sugestão nunca muda número.
        </p>
      )}
      {!budgetConfirmed && budget && (
        <p className="notice warning">
          A PO ainda não foi confirmada. <Link to={`/previsoes/${budgetId}`}>Conferir a PO</Link> antes de editar o de-para.
        </p>
      )}

      {admin && budgetConfirmed && (
        <AccountMappingActions budgetId={budgetId} selected={[...selected]} onBatchDone={() => setSelected(new Set())} />
      )}

      <div className="tabs" role="tablist">
        {filters.map((f) => (
          <button key={f.code} role="tab" aria-selected={filter === f.code} onClick={() => change("filtro", f.code === "ALL" ? null : f.code)}>
            {f.label}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="notice">Carregando…</p>
      ) : error ? (
        <p className="notice error">{error.message}</p>
      ) : accounts.length === 0 ? (
        <p className="notice">Nenhuma conta neste filtro.</p>
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
