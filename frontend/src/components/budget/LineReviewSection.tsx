import { useEffect, useState } from "react";
import { useLocation } from "react-router";
import { useLineReview, useLineReviewEvents, useReviewLines, useSaveLineOrigin } from "../../api/cashFlowQueries";
import type { BudgetLineOrigin, BudgetLineOriginRequest, LineReviewLine, OriginMethod } from "../../api/types";
import { useSession } from "../../context";
import { formatDateTime, formatMoney, formatMonth } from "../../format";
import { markLabel } from "../budgetVsActual/labels";

const methodLabel: Record<OriginMethod, string> = {
  AVERAGE_TIMES_INDEX: "média × índice",
  EQUAL_TO_AVERAGE: "igual à média",
  TYPED: "valor digitado",
  NO_AVERAGE: "sem média de conta",
};

const actionLabel: Record<string, string> = {
  REVIEWED: "conferida",
  REOPENED: "reaberta",
  MARKS_CHANGED: "marcas alteradas",
  ORIGIN_CHANGED: "origem alterada",
};

/**
 * Review of the budget lines (RF-13.12) and the origin of each value (RF-13.13), in "PO aprovada". Everyone sees it;
 * only the Admin reviews, marks and fills the origin (the backend answers 403 for the others). The warnings come from
 * the API and never block. While a line is pending, no screen distributes spending by line.
 */
export function LineReviewSection({ budgetId }: { budgetId: string }) {
  const { condominiumId, can } = useSession();
  const isAdmin = can("ADMIN");
  const { data, isLoading, error } = useLineReview(condominiumId, budgetId);
  const review = useReviewLines(condominiumId, budgetId);
  const [onlyWarnings, setOnlyWarnings] = useState(false);
  const [openLine, setOpenLine] = useState<string | null>(null);
  const [showTrail, setShowTrail] = useState(false);
  const { hash } = useLocation();
  // The links "Conferir as linhas da PO" open this section (#conferencia)
  useEffect(() => {
    if (data && hash === "#conferencia") document.getElementById("conferencia")?.scrollIntoView();
  }, [data, hash]);

  if (isLoading) return <p className="notice">Carregando a conferência…</p>;
  if (error) return <p className="notice error">{error.message}</p>;
  if (!data) return null;
  const { state } = data;
  const lines = onlyWarnings ? data.lines.filter((l) => l.warnings.length > 0) : data.lines;

  const setStatus = (line: LineReviewLine, reviewed: boolean) =>
    review.mutate({ items: [{ lineId: line.lineId, status: reviewed ? "REVIEWED" : "PENDING" }], reviewAll: false });
  const setMark = (line: LineReviewLine, marks: { groupAccount?: boolean; descriptionPrevails?: boolean }) =>
    review.mutate({ items: [{ lineId: line.lineId, status: null, ...marks }], reviewAll: false });

  return (
    <section className="block" id="conferencia">
      <div className="block-title">
        <h2>Conferência das linhas</h2>
        <span className={state.reviewed ? "badge ok" : "badge warning"}>
          {state.reviewedLines} de {state.totalLines} conferidas
        </span>
      </div>
      <p className="muted">
        Confira o que o sistema leu do documento: conta, descrição, valor, observação e, no grupo 1.9, os fundos. Os avisos só apontam o
        que merece atenção. Enquanto houver linha pendente, as telas não distribuem os gastos por linha da PO. {data.linesWithOrigin} linha(s)
        com origem do valor.
      </p>
      {!state.reviewed && state.message && <p className="notice warning">{state.message}</p>}
      <div className="filters">
        <label className="inline-field">
          <input type="checkbox" checked={onlyWarnings} onChange={(e) => setOnlyWarnings(e.target.checked)} />
          Só as {data.linesWithWarnings} linha(s) com aviso
        </label>
        {isAdmin && (
          <button
            className="button"
            disabled={state.reviewed || review.isPending}
            onClick={() => review.mutate({ items: [], reviewAll: true })}
          >
            Conferir todas
          </button>
        )}
        <button className="link-button" onClick={() => setShowTrail((v) => !v)}>
          {showTrail ? "Esconder a trilha" : "Ver a trilha"}
        </button>
      </div>
      {review.error && <p className="notice error">{review.error.message}</p>}
      {showTrail && <ReviewTrail budgetId={budgetId} />}
      <div className="scroll">
        <table className="table compact line-review-table">
          <thead>
            <tr>
              <th>Item</th>
              <th>Conta</th>
              <th>Descrição</th>
              <th className="number">Valor mensal</th>
              <th>Observação</th>
              <th>Avisos</th>
              <th>Origem</th>
              <th>Marcas</th>
              <th>Situação</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l) => (
              <LineRows
                key={l.lineId}
                line={l}
                isAdmin={isAdmin}
                busy={review.isPending}
                open={openLine === l.lineId}
                onToggle={() => setOpenLine(openLine === l.lineId ? null : l.lineId)}
                onStatus={(reviewed) => setStatus(l, reviewed)}
                onMark={(marks) => setMark(l, marks)}
                budgetId={budgetId}
              />
            ))}
          </tbody>
        </table>
      </div>
      <p className="muted">Avisos: versão {data.checkVersion}.</p>
    </section>
  );
}

interface LineRowsProps {
  line: LineReviewLine;
  isAdmin: boolean;
  busy: boolean;
  open: boolean;
  onToggle: () => void;
  onStatus: (reviewed: boolean) => void;
  onMark: (marks: { groupAccount?: boolean; descriptionPrevails?: boolean }) => void;
  budgetId: string;
}

function LineRows({ line, isAdmin, busy, open, onToggle, onStatus, onMark, budgetId }: LineRowsProps) {
  const reviewed = line.status === "REVIEWED";
  return (
    <>
      <tr>
        <td>
          {line.code}
          {line.code !== line.printedCode && <small className="muted"> (impresso {line.printedCode})</small>}
          {line.funds && <small className="muted"> fundo</small>}
        </td>
        <td>{line.mark ? <span className="badge neutral">{markLabel[line.mark]}</span> : (line.account ?? "")}</td>
        <td>{line.description}</td>
        <td className="number">{formatMoney(line.budgeted)}</td>
        <td className="muted notes-cell" title={line.notes ?? undefined}>
          {line.notes}
        </td>
        <td className="warnings">
          {line.warnings.map((w) => (
            <small key={w.code + w.message} className="badge warning" title={w.message}>
              {w.message}
            </small>
          ))}
        </td>
        <td>
          <button className="link-button" onClick={onToggle}>
            {line.origin ? methodLabel[line.origin.method] : "origem não informada"}
          </button>
        </td>
        <td>
          {isAdmin ? (
            <>
              <label className="inline-field" title="O2: a conta impressa é de um grupo de contas">
                <input type="checkbox" checked={line.groupAccount} disabled={busy} onChange={(e) => onMark({ groupAccount: e.target.checked })} />
                conta de grupo
              </label>
              <label className="inline-field" title="O1: a linha vale pela descrição, não pela conta impressa">
                <input
                  type="checkbox"
                  checked={line.descriptionPrevails}
                  disabled={busy}
                  onChange={(e) => onMark({ descriptionPrevails: e.target.checked })}
                />
                vale a descrição
              </label>
            </>
          ) : (
            [line.groupAccount && "conta de grupo", line.descriptionPrevails && "vale a descrição"].filter(Boolean).join(" · ")
          )}
        </td>
        <td>
          <span className={reviewed ? "badge ok" : "badge warning"}>{reviewed ? "conferida" : "pendente"}</span>
          {line.reviewedBy && line.reviewedAt && (
            <small className="note">
              por {line.reviewedBy} em {formatDateTime(line.reviewedAt)}
            </small>
          )}
          {isAdmin && (
            <button className="link-button" disabled={busy} onClick={() => onStatus(!reviewed)}>
              {reviewed ? "Reabrir" : "Conferir"}
            </button>
          )}
        </td>
      </tr>
      {open && (
        <tr>
          <td colSpan={9}>
            <OriginDetail line={line} isAdmin={isAdmin} budgetId={budgetId} />
          </td>
        </tr>
      )}
    </>
  );
}

function OriginDetail({ line, isAdmin, budgetId }: { line: LineReviewLine; isAdmin: boolean; budgetId: string }) {
  const [editing, setEditing] = useState(false);
  const o = line.origin;
  return (
    <div className="origin-detail">
      {o ? <OriginView origin={o} budgeted={line.budgeted} /> : <p className="muted">Origem não informada.</p>}
      {isAdmin && !editing && (
        <button className="link-button" onClick={() => setEditing(true)}>
          {o ? "Corrigir a origem" : "Informar a origem"}
        </button>
      )}
      {isAdmin && editing && <OriginForm line={line} budgetId={budgetId} onDone={() => setEditing(false)} />}
    </div>
  );
}

function OriginView({ origin: o, budgeted }: { origin: BudgetLineOrigin; budgeted: number }) {
  return (
    <dl>
      <dt>Contas da média</dt>
      <dd>{o.sourceAccounts && o.sourceAccounts.length > 0 ? o.sourceAccounts.join(", ") : "—"}</dd>
      <dt>Período da média</dt>
      <dd>{o.averageFrom && o.averageTo ? `${formatMonth(o.averageFrom)} a ${formatMonth(o.averageTo)}` : "—"}</dd>
      <dt>Média</dt>
      <dd>{o.averageAmount == null ? "—" : formatMoney(o.averageAmount)}</dd>
      <dt>Método</dt>
      <dd>{o.methodLabel ?? methodLabel[o.method]}</dd>
      {o.indexName && (
        <>
          <dt>Índice</dt>
          <dd>
            {o.indexName} {o.indexRate != null && `${(o.indexRate * 100).toLocaleString("pt-BR", { maximumFractionDigits: 2 })}%`}
            {o.indexForecast && <span className="badge neutral">previsão</span>}
            {o.indexNote && <small className="note">{o.indexNote}</small>}
          </dd>
        </>
      )}
      <dt>Valor final</dt>
      <dd>
        {o.finalAmount == null ? "—" : formatMoney(o.finalAmount)}{" "}
        <span className="muted">(vale o da PO: {formatMoney(budgeted)})</span>
      </dd>
      {o.reason && (
        <>
          <dt>Motivo</dt>
          <dd>{o.reason}</dd>
        </>
      )}
      <dt>Fonte</dt>
      <dd>
        {o.source === "SHEET" ? `planilha de apoio ${o.fileName ?? ""}${o.sheet ? `, aba ${o.sheet}` : ""}${o.sheetRow ? `, linha ${o.sheetRow}` : ""}` : "informada pelo Admin"}
        {o.changedBy && o.changedAt && ` · ${o.changedBy} em ${formatDateTime(o.changedAt)}`}
      </dd>
    </dl>
  );
}

const toNumber = (text: string) => (text.trim() === "" ? null : Number(text.replace(/\./g, "").replace(",", ".")));

function OriginForm({ line, budgetId, onDone }: { line: LineReviewLine; budgetId: string; onDone: () => void }) {
  const { condominiumId } = useSession();
  const save = useSaveLineOrigin(condominiumId, budgetId);
  const o = line.origin;
  const [method, setMethod] = useState<OriginMethod>(o?.method ?? "TYPED");
  const [accounts, setAccounts] = useState((o?.sourceAccounts ?? []).join(", "));
  const [from, setFrom] = useState(o?.averageFrom ?? "");
  const [to, setTo] = useState(o?.averageTo ?? "");
  const [average, setAverage] = useState(o?.averageAmount?.toString().replace(".", ",") ?? "");
  const [indexName, setIndexName] = useState(o?.indexName ?? "");
  const [indexRate, setIndexRate] = useState(o?.indexRate != null ? String(o.indexRate * 100).replace(".", ",") : "");
  const [final, setFinal] = useState(o?.finalAmount?.toString().replace(".", ",") ?? "");
  const [reason, setReason] = useState(o?.reason ?? "");

  function submit() {
    const rate = toNumber(indexRate);
    const request: BudgetLineOriginRequest = {
      method,
      sourceAccounts: accounts.split(/[,;\s]+/).filter(Boolean),
      averageFrom: from || null,
      averageTo: to || null,
      averageAmount: toNumber(average),
      baseAmount: toNumber(average),
      indexName: indexName || null,
      indexRate: rate == null ? null : rate / 100,
      indexForecast: o?.indexForecast ?? false,
      indexNote: o?.indexNote ?? null,
      finalAmount: toNumber(final),
      reason: reason || null,
    };
    save.mutate({ lineId: line.lineId, request }, { onSuccess: onDone });
  }

  return (
    <div className="form">
      <div className="filters">
        <label className="field">
          Método
          <select value={method} onChange={(e) => setMethod(e.target.value as OriginMethod)}>
            {Object.entries(methodLabel).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          Contas da média
          <input value={accounts} onChange={(e) => setAccounts(e.target.value)} placeholder="1692, 1693" />
        </label>
        <label className="field">
          Média de
          <input type="month" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="field">
          até
          <input type="month" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <label className="field">
          Média (R$)
          <input inputMode="decimal" value={average} onChange={(e) => setAverage(e.target.value)} />
        </label>
        <label className="field">
          Índice
          <input value={indexName} onChange={(e) => setIndexName(e.target.value)} />
        </label>
        <label className="field">
          Índice (%)
          <input inputMode="decimal" value={indexRate} onChange={(e) => setIndexRate(e.target.value)} />
        </label>
        <label className="field">
          Valor final (R$)
          <input inputMode="decimal" value={final} onChange={(e) => setFinal(e.target.value)} />
        </label>
        <label className="field">
          Motivo
          <input value={reason} onChange={(e) => setReason(e.target.value)} />
        </label>
      </div>
      <button className="button" disabled={save.isPending} onClick={submit}>
        Gravar a origem
      </button>{" "}
      <button className="link-button" onClick={onDone}>
        Cancelar
      </button>
      {save.error && <p className="notice error">{save.error.message}</p>}
    </div>
  );
}

function ReviewTrail({ budgetId }: { budgetId: string }) {
  const { condominiumId } = useSession();
  const { data: events = [], isLoading } = useLineReviewEvents(condominiumId, budgetId, true);
  if (isLoading) return <p className="notice">Carregando a trilha…</p>;
  if (events.length === 0) return <p className="muted">Nenhuma conferência registrada.</p>;
  return (
    <ul className="simple-list">
      {events.map((e) => (
        <li key={e.id}>
          {formatDateTime(e.occurredAt)} · {e.username} · {e.lineCode} {e.lineDescription}: {actionLabel[e.action] ?? e.action}
          {e.reason && <small className="muted"> ({e.reason})</small>}
        </li>
      ))}
    </ul>
  );
}
