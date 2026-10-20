import { useState, type FormEvent } from "react";
import { useMarkPartialMonth, usePartialMonthEvents, usePartialMonths } from "../../api/budgetTrackingQueries";
import type { PartialMonth, PartialMonthRequest } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatDateTime, formatDayMonth, formatPeriod } from "../../format";
import { partialMonthActionLabel } from "../budgetVsActual/labels";

/**
 * Cash flows read and whether each one is a partial month (RF-13.10). The automatic rule (sent until the last day of
 * the period) is applied by the API; the Manager and the Admin can mark or unmark by hand, with a trail.
 * Only hides the actions for the User: the backend answers 403.
 */
export function PartialMonthsSection() {
  const { condominiumId, can } = useSession();
  const { data: files = [], isLoading } = usePartialMonths(condominiumId);
  const canEdit = can("MANAGER", "ADMIN");
  if (isLoading) return null;
  return (
    <section className="block">
      <h2>Extratos e mês parcial</h2>
      <p className="muted">
        Extrato enviado antes do fim do período fica “parcial até DD/MM”: aparece com os números até a data, fora do acumulado, do saldo
        das linhas e da regra dos 20%. Quando o extrato completo do mesmo mês chega, ele substitui o parcial.
      </p>
      {files.length === 0 ? (
        <p className="notice">Nenhum extrato lido ainda.</p>
      ) : (
        <table className="table compact">
          <thead>
            <tr>
              <th>Extrato</th>
              <th>Período</th>
              <th>Enviado em</th>
              <th>Situação</th>
              {canEdit && <th>Ações</th>}
            </tr>
          </thead>
          <tbody>
            {files.map((f) => (
              <PartialMonthRow key={f.fileId} file={f} canEdit={canEdit} />
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}

function PartialMonthRow({ file, canEdit }: { file: PartialMonth; canEdit: boolean }) {
  return (
    <tr className={file.supersededById ? "undone" : undefined}>
      <td>
        {file.name}
        <PartialMonthTrail fileId={file.fileId} />
      </td>
      <td>{formatPeriod(file.periodStart, file.periodEnd)}</td>
      <td>
        {file.uploadedAt ? formatDateTime(file.uploadedAt) : "—"}
        {file.uploadedBy && <span className="muted"> por {file.uploadedBy}</span>}
      </td>
      <td>
        <PartialMonthStatus file={file} />
      </td>
      {canEdit && <td>{!file.supersededById && <PartialMonthActions file={file} />}</td>}
    </tr>
  );
}

function PartialMonthStatus({ file }: { file: PartialMonth }) {
  if (file.supersededById) return <span className="badge neutral">substituído pelo extrato completo</span>;
  return (
    <>
      {file.partial ? (
        <span className="badge warning">parcial até {formatDayMonth(file.partialUntil)}</span>
      ) : (
        <span className="badge ok">completo</span>
      )}
      <small className="note">
        {file.automatic
          ? "pela regra automática"
          : `marcado por ${file.changedBy ?? "—"}${file.changedAt ? ` em ${formatDateTime(file.changedAt)}` : ""} · a regra diria ${
              file.automaticallyPartial ? "parcial" : "completo"
            }`}
        {file.reason && ` · motivo: ${file.reason}`}
      </small>
    </>
  );
}

/** Manager and Admin: mark as partial (with the date), mark as complete or go back to the automatic rule. */
function PartialMonthActions({ file }: { file: PartialMonth }) {
  const { condominiumId } = useSession();
  const mark = useMarkPartialMonth(condominiumId);
  const [marking, setMarking] = useState(false);
  const [until, setUntil] = useState(file.partialUntil ?? file.periodEnd ?? "");
  const [reason, setReason] = useState("");

  const send = (request: PartialMonthRequest) =>
    mark.mutate({ fileId: file.fileId, request }, { onSuccess: () => setMarking(false) });

  function submit(event: FormEvent) {
    event.preventDefault();
    send({ partial: true, until, reason: reason || null });
  }

  if (marking) {
    return (
      <form className="partial-form" onSubmit={submit}>
        <label className="field">
          Parcial até
          <input type="date" required value={until} min={file.periodStart ?? undefined} max={file.periodEnd ?? undefined} onChange={(e) => setUntil(e.target.value)} />
        </label>
        <label className="field">
          Motivo (vai para a trilha)
          <input value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
        <div className="card-actions">
          <button className="button" type="submit" disabled={mark.isPending}>
            Salvar
          </button>
          <button className="link-button" type="button" onClick={() => setMarking(false)}>
            Cancelar
          </button>
        </div>
        {mark.error && <span className="notice error">{mark.error.message}</span>}
      </form>
    );
  }
  return (
    <div className="card-actions wrap">
      <button className="link-button" disabled={mark.isPending} onClick={() => setMarking(true)}>
        {file.partial ? "Mudar a data" : "Marcar como parcial"}
      </button>
      {file.partial && (
        <button className="link-button" disabled={mark.isPending} onClick={() => send({ partial: false, until: null, reason: null })}>
          Marcar como completo
        </button>
      )}
      {!file.automatic && (
        <button className="link-button" disabled={mark.isPending} onClick={() => send({ partial: null, until: null, reason: null })}>
          Voltar à regra automática
        </button>
      )}
      {mark.error && <span className="notice error">{mark.error.message}</span>}
    </div>
  );
}

/** Trail of the partial month of one cash flow (insert only), loaded only when opened. */
function PartialMonthTrail({ fileId }: { fileId: string }) {
  const { condominiumId } = useSession();
  const [open, setOpen] = useState(false);
  const { data: events = [], isLoading } = usePartialMonthEvents(condominiumId, fileId, open);
  return (
    <details className="trail" onToggle={(e) => setOpen((e.target as HTMLDetailsElement).open)}>
      <summary className="muted">Histórico</summary>
      {isLoading && <p className="notice">Carregando…</p>}
      {open && !isLoading && events.length === 0 && <p className="muted">Sem mudanças registradas.</p>}
      <ul className="simple-list">
        {events.map((e) => (
          <li key={e.id}>
            <strong>{partialMonthActionLabel[e.action]}</strong> · {formatDateTime(e.occurredAt)} por {e.username}
            {e.partialUntil && ` · parcial até ${formatDate(e.partialUntil)}`}
            {e.reason && <span className="muted"> · motivo: {e.reason}</span>}
          </li>
        ))}
      </ul>
    </details>
  );
}
