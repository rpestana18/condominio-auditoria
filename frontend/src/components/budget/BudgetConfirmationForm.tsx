import { useState, type FormEvent } from "react";
import { ApiError } from "../../api/client";
import { useFiles } from "../../api/queries";
import { useConfirmBudget, useFunds } from "../../api/budgetQueries";
import type { BudgetConfirmationRequest, BudgetDetail } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatMoney } from "../../format";

/**
 * Budget confirmation by the Admin (RF-03.1.3): fiscal year, minutes (or "sem ata"), an optional display label for the
 * lines with a repeated item (RF-13.2: never required, the line is identified by its position) and the link of each 1.9.x line to a cash flow fund (RF-03.1.9). The values read are not
 * edited. The backend validates everything; the screen only shows the reasons for a refusal.
 */
export function BudgetConfirmationForm({ detail }: { detail: BudgetDetail }) {
  const { condominiumId } = useSession();
  const confirm = useConfirmBudget(condominiumId, detail.budget.id);
  const { data: minutesFiles = [] } = useFiles(condominiumId, "MINUTES");
  const { data: condominiumFunds = [] } = useFunds(condominiumId);

  const [start, setStart] = useState(detail.budget.fiscalYearStart ?? "");
  // RF-13.17 (D2): the default end is the suggestion of the API (first February after the start), not the printed one
  const [end, setEnd] = useState(detail.suggestedFiscalYearEnd ?? detail.budget.fiscalYearEnd ?? "");
  const [endTouched, setEndTouched] = useState(false);
  const [withoutMinutes, setWithoutMinutes] = useState(false);
  const [minutesId, setMinutesId] = useState("");
  const [approvalDate, setApprovalDate] = useState("");
  const [codes, setCodes] = useState<Record<string, string>>(() =>
    Object.fromEntries(detail.repeatedCodes.flatMap((r) => r.lines.map((l) => [l.lineId, l.effectiveCode]))),
  );
  const [funds, setFunds] = useState<Record<string, string>>(() =>
    Object.fromEntries(detail.funds.map((f) => [f.lineId, f.fundId])),
  );
  const [reapproval, setReapproval] = useState(false);
  const [acknowledged, setAcknowledged] = useState(false);
  const [justification, setJustification] = useState("");

  const withDiscrepancy = detail.budget.status === "READ_WITH_DISCREPANCY";
  const fundLines = detail.lines.filter((l) => l.fundLine && l.type === "LINE");
  // Cash flow funds by their printed name. The operating fund (Condomínio fund) cannot be linked to 1.9.x.
  const cashFlowFunds = condominiumFunds.filter((f) => !f.operating);

  function submit(event: FormEvent) {
    event.preventDefault();
    const request: BudgetConfirmationRequest = {
      fiscalYearStart: start,
      fiscalYearEnd: end,
      withoutMinutes,
      minutesFileId: withoutMinutes ? null : minutesId || null,
      approvalDate: withoutMinutes ? null : approvalDate || null,
      effectiveCodes: Object.entries(codes).map(([lineId, code]) => ({ lineId, code })),
      funds: Object.entries(funds)
        .filter(([, fundId]) => fundId)
        .map(([lineId, fundId]) => ({ lineId, fundId })),
      reapproval,
      discrepancyAcknowledged: acknowledged,
      justification: acknowledged ? justification : null,
    };
    confirm.mutate(request);
  }

  const reasons = confirm.error instanceof ApiError ? (confirm.error.problem?.reasons ?? []) : [];

  return (
    <form className="block form" onSubmit={submit}>
      <h2>Confirmar a PO</h2>

      <fieldset>
        <legend>Exercício</legend>
        <label className="field">
          Mês inicial
          <input
            type="month"
            required
            value={start}
            onChange={(e) => {
              setStart(e.target.value);
              // Same rule as the API suggestion, only while the Admin has not chosen the end
              if (!endTouched) setEnd(firstFebruaryAfter(e.target.value) ?? end);
            }}
          />
        </label>
        <label className="field">
          Mês final
          <input
            type="month"
            required
            value={end}
            onChange={(e) => {
              setEnd(e.target.value);
              setEndTouched(true);
            }}
          />
        </label>
        <p className="muted">Sugestão: fevereiro, exercício de março a março (D2). Confira com a ata.</p>
      </fieldset>

      <fieldset>
        <legend>Ata que aprovou a PO</legend>
        <label className="inline-field">
          <input type="checkbox" checked={withoutMinutes} onChange={(e) => setWithoutMinutes(e.target.checked)} />
          Sem ata (fica como pendência de implantação)
        </label>
        {!withoutMinutes && (
          <>
            <label className="field">
              Ata (arquivos da categoria Atas)
              <select required value={minutesId} onChange={(e) => setMinutesId(e.target.value)}>
                <option value="">Escolha a ata</option>
                {minutesFiles.map((m) => (
                  <option key={m.id} value={m.id}>
                    {m.name} · enviada em {formatDate(m.uploadedAt)}
                  </option>
                ))}
              </select>
            </label>
            <label className="field">
              Data da assembleia
              <input type="date" required value={approvalDate} onChange={(e) => setApprovalDate(e.target.value)} />
            </label>
          </>
        )}
      </fieldset>

      {detail.repeatedCodes.length > 0 && (
        <fieldset>
          <legend>Itens repetidos na PO (só aviso)</legend>
          <p className="muted">
            O item repetido não impede a confirmação: cada linha fica separada, com os próprios valores. Se quiser, informe um rótulo de
            exibição; deixar como impresso também vale.
          </p>
          {detail.repeatedCodes.map((r) =>
            r.lines.map((l) => (
              <label key={l.lineId} className="field">
                {r.printedCode} · {l.description} (rótulo opcional)
                <input
                  value={codes[l.lineId] ?? ""}
                  onChange={(e) => setCodes((current) => ({ ...current, [l.lineId]: e.target.value }))}
                />
              </label>
            )),
          )}
        </fieldset>
      )}

      {fundLines.length > 0 && (
        <fieldset>
          <legend>Fundos ligados às linhas de fundos</legend>
          <p className="muted">
            Cada linha recebe no máximo um fundo, e cada fundo atende no máximo uma linha. Nomes como impressos no fluxo.
          </p>
          {fundLines.map((l) => (
            <label key={l.id} className="field">
              {l.effectiveCode} {l.description} · {formatMoney(l.budgeted)}/mês
              <select
                value={funds[l.id] ?? ""}
                onChange={(e) => setFunds((current) => ({ ...current, [l.id]: e.target.value }))}
              >
                <option value="">Sem fundo ligado</option>
                {cashFlowFunds.map((f) => (
                  <option key={f.id} value={f.id}>
                    {f.name}
                  </option>
                ))}
              </select>
            </label>
          ))}
        </fieldset>
      )}

      <fieldset>
        <legend>Outras indicações</legend>
        <label className="inline-field">
          <input type="checkbox" checked={reapproval} onChange={(e) => setReapproval(e.target.checked)} />
          É reaprovação (substitui a PO confirmada nos mesmos meses, a partir do início desta)
        </label>
        {withDiscrepancy && (
          <>
            <label className="inline-field">
              <input type="checkbox" checked={acknowledged} onChange={(e) => setAcknowledged(e.target.checked)} />
              Confirmar ciente da divergência de soma (o erro está no próprio documento)
            </label>
            {acknowledged && (
              <label className="field">
                Justificativa (obrigatória, vai para a trilha)
                <textarea required rows={3} value={justification} onChange={(e) => setJustification(e.target.value)} />
              </label>
            )}
          </>
        )}
      </fieldset>

      {confirm.isError && (
        <div className="notice error">
          {confirm.error.message}
          {reasons.length > 0 && (
            <ul>
              {reasons.map((r) => (
                <li key={r}>{r}</li>
              ))}
            </ul>
          )}
        </div>
      )}
      {confirm.isSuccess && <p className="notice ok">PO confirmada.</p>}
      <div className="actions">
        <button className="button" type="submit" disabled={confirm.isPending}>
          {confirm.isPending ? "Confirmando…" : "Confirmar a PO"}
        </button>
      </div>
    </form>
  );
}

/** Default end of the fiscal year shown in the form: the first February after the start ("AAAA-MM"), as the API suggests. */
function firstFebruaryAfter(start: string): string | null {
  const match = /^(\d{4})-(\d{2})$/.exec(start);
  if (!match) return null;
  const year = Number(match[1]);
  const month = Number(match[2]);
  return `${month < 2 ? year : year + 1}-02`;
}
