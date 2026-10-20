import { Link, useParams } from "react-router";
import { openFile } from "../api/client";
import { useBudget } from "../api/budgetQueries";
import type { BudgetDetail } from "../api/types";
import { BudgetChecks } from "../components/budget/BudgetChecks";
import { BudgetConfirmationForm } from "../components/budget/BudgetConfirmationForm";
import { BudgetLines } from "../components/budget/BudgetLines";
import { EditBudgetFunds } from "../components/budget/EditBudgetFunds";
import { BudgetEvents } from "../components/budget/BudgetEvents";
import { BudgetItemsSection } from "../components/budgetItems/BudgetItemsSection";
import { findingStatusLabel, budgetStatusLabel, severityLabel } from "../components/budgetVsActual/labels";
import { useSession } from "../context";
import { formatDate, formatDateTime, formatMonth, formatMoney } from "../format";

/**
 * Detail of the budget read, with checks and lines. The Admin confirms here (RF-03.1.3);
 * the other roles only view it.
 */
export function Budget() {
  const { budgetId } = useParams();
  const { condominiumId, can } = useSession();
  const { data: detail, isLoading, error } = useBudget(condominiumId, budgetId);

  if (isLoading) return <p className="notice">Carregando…</p>;
  if (error) return <p className="notice error">{error.message}</p>;
  if (!detail) return null;
  const { budget } = detail;
  const awaiting = budget.status === "READ" || budget.status === "READ_WITH_DISCREPANCY";

  return (
    <>
      <header className="page-title">
        <h1>{budget.title ?? "Previsão orçamentária"}</h1>
        <span className={budget.status === "CONFIRMED" ? "badge ok" : "badge warning"}>{budgetStatusLabel[budget.status]}</span>
        {(budget.status === "CONFIRMED" || budget.status === "SUPERSEDED") && (
          <Link className="link-button" to={`/previsoes/${budget.id}/tipos`}>
            Tipos de linha
          </Link>
        )}
        <Link className="link-button" to="/previsoes">
          Todas as POs
        </Link>
      </header>

      <Summary detail={detail} condominiumId={condominiumId} />
      {awaiting && !can("ADMIN") && <p className="notice warning">Aguardando a confirmação do Admin.</p>}
      {awaiting && can("ADMIN") && <BudgetConfirmationForm detail={detail} />}
      <BudgetChecks checks={detail.checks} warnings={detail.warnings} />
      {budget.status === "CONFIRMED" && can("ADMIN") ? (
        <EditBudgetFunds detail={detail} />
      ) : (
        detail.funds.length > 0 && <LinkedFunds detail={detail} />
      )}
      {detail.findings.length > 0 && <Findings detail={detail} />}
      <BudgetEvents budgetId={budget.id} />
      {/* Budget items only exist after confirmation (RF-11.7); the list is for everyone, editing is Admin only */}
      {(budget.status === "CONFIRMED" || budget.status === "SUPERSEDED") && <BudgetItemsSection budget={budget} />}
      <BudgetLines
        lines={detail.lines}
        previousBudgetedColumn={detail.previousBudgetedColumn}
        budgetedColumn={detail.budgetedColumn}
        repeatedLineIds={detail.repeatedCodes.flatMap((r) => r.lines.map((l) => l.lineId))}
      />
    </>
  );
}

function Summary({ detail, condominiumId }: { detail: BudgetDetail; condominiumId: string }) {
  const { budget, confirmation } = detail;
  return (
    <section className="block">
      <dl className="number-list">
        <dt>Arquivo</dt>
        <dd>
          <button className="link-button" onClick={() => void openFile(`/condominiums/${condominiumId}/files/${budget.fileId}/content`)}>
            {budget.fileName ?? "Abrir original"}
          </button>{" "}
          <span className="hash">{budget.sha256}</span>
        </dd>
        <dt>Exercício</dt>
        <dd>
          {budget.fiscalYearStart && budget.fiscalYearEnd
            ? `${formatMonth(budget.fiscalYearStart)} a ${formatMonth(budget.fiscalYearEnd)}`
            : `impresso: ${budget.printedFiscalYear ?? "—"}`}
        </dd>
        <dt>Previsto do mês</dt>
        <dd>
          {formatMoney(budget.monthlyPlanned)} <span className="muted">(soma das linhas de despesa)</span>
          {detail.printedMonthlyPlanned !== null && detail.printedMonthlyPlanned !== undefined && (
            <span className="muted"> · impresso: {formatMoney(detail.printedMonthlyPlanned)}</span>
          )}
        </dd>
        <dt>Lida em</dt>
        <dd>{formatDateTime(budget.readAt)}</dd>
        {budget.confirmedAt && (
          <>
            <dt>Confirmada</dt>
            <dd>
              {formatDateTime(budget.confirmedAt)} por {budget.confirmedBy}
              {confirmation?.withoutMinutes ? " · sem ata" : confirmation?.approvalDate ? ` · ata de ${formatDate(confirmation.approvalDate)}` : ""}
            </dd>
          </>
        )}
        {detail.supersededFrom && (
          <>
            <dt>Substituída desde</dt>
            <dd>{formatMonth(detail.supersededFrom)}</dd>
          </>
        )}
      </dl>
    </section>
  );
}

function LinkedFunds({ detail }: { detail: BudgetDetail }) {
  return (
    <section className="block">
      <h2>Fundos ligados às linhas de fundos</h2>
      <table className="table compact">
        <tbody>
          {detail.funds.map((f) => (
            <tr key={f.lineId}>
              <td>
                {f.effectiveCode} {f.description}
              </td>
              <td className="number">{formatMoney(f.budgeted)}</td>
              <td>{f.fund}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </section>
  );
}

function Findings({ detail }: { detail: BudgetDetail }) {
  return (
    <section className="block">
      <h2>Achados desta PO</h2>
      <ul className="simple-list">
        {detail.findings.map((f) => (
          <li key={f.id}>
            <span className={f.severity === "CRITICAL" ? "badge critical" : "badge warning"}>{severityLabel[f.severity]}</span>{" "}
            <span className="badge neutral">{findingStatusLabel[f.status]}</span> {formatMonth(f.referenceMonth)} · {f.description}
          </li>
        ))}
      </ul>
    </section>
  );
}
