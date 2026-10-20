import { useState } from "react";
import { useConfirmSpendingProfiles } from "../../api/budgetTrackingQueries";
import type { SpendingProfile, SpendingProfileBatchRequest, SpendingProfileLine, SpendingProfiles } from "../../api/types";
import { useSession } from "../../context";
import { formatMoney } from "../../format";
import { markLabel, spendingProfileLabel, spendingProfileStatusLabel } from "../budgetVsActual/labels";

const profiles = Object.keys(spendingProfileLabel) as SpendingProfile[];

interface Props {
  budgetId: string;
  data: SpendingProfiles;
}

/**
 * Spending profile of each expense line (RF-13.3): the system's suggestion and what is in force. Everyone sees it;
 * only the Admin chooses and confirms (the backend answers 403 for the others). The profile never changes the planned.
 */
export function SpendingProfilesTable({ budgetId, data }: Props) {
  const { condominiumId, can } = useSession();
  const isAdmin = can("ADMIN");
  const confirm = useConfirmSpendingProfiles(condominiumId, budgetId);
  const [chosen, setChosen] = useState<Record<string, SpendingProfile>>({});
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [reason, setReason] = useState("");

  const choice = (line: SpendingProfileLine) => chosen[line.lineId] ?? line.profile;
  const toggle = (lineId: string, on: boolean) =>
    setSelected((current) => {
      const next = new Set(current);
      if (on) next.add(lineId);
      else next.delete(lineId);
      return next;
    });

  function send(request: SpendingProfileBatchRequest) {
    confirm.mutate(request, {
      onSuccess: () => {
        setSelected(new Set());
        setChosen({});
      },
    });
  }

  /** Same profile as recorded and still suggested = confirms the suggestion (profile null, API contract). */
  const confirmSelected = () =>
    send({
      items: data.lines
        .filter((l) => selected.has(l.lineId))
        .map((l) => ({ lineId: l.lineId, profile: choice(l) === l.profile && l.status === "SUGGESTED" ? null : choice(l) })),
      reason: reason || null,
    });

  const confirmAllSuggestions = () =>
    send({
      items: data.lines.filter((l) => l.status === "SUGGESTED").map((l) => ({ lineId: l.lineId, profile: null })),
      reason: reason || null,
    });

  return (
    <section className="block">
      <div className="block-title">
        <h2>Tipo de cada linha</h2>
        <span className="muted">
          {data.confirmed} confirmado(s) · {data.suggested} sugerido(s)
        </span>
      </div>
      <p className="muted">
        Sem confirmação, a linha vale como “mensal” e a tela mostra “tipo não confirmado”. O tipo não muda o previsto: muda só como o
        saldo é classificado na origem do saldo e o destaque do acompanhamento.
      </p>
      {isAdmin && (
        <div className="filters">
          <label className="field">
            Motivo (opcional, vai para a trilha)
            <input value={reason} onChange={(e) => setReason(e.target.value)} />
          </label>
          <button className="button" disabled={selected.size === 0 || confirm.isPending} onClick={confirmSelected}>
            Confirmar as {selected.size} selecionada(s)
          </button>
          <button className="button secondary" disabled={data.suggested === 0 || confirm.isPending} onClick={confirmAllSuggestions}>
            Confirmar todas as sugestões
          </button>
        </div>
      )}
      {confirm.error && <p className="notice error">{confirm.error.message}</p>}
      {confirm.isSuccess && <p className="notice ok">Tipos gravados.</p>}
      <div className="scroll">
        <table className="table compact">
          <thead>
            <tr>
              {isAdmin && <th aria-label="Selecionar" />}
              <th>Item</th>
              <th>Identificação</th>
              <th>Descrição</th>
              <th className="number">Orçado do exercício</th>
              <th>Observações</th>
              <th>Sugestão do sistema</th>
              <th>Tipo</th>
              <th>Situação</th>
            </tr>
          </thead>
          <tbody>
            {data.lines.map((l) => (
              <tr key={l.lineId}>
                {isAdmin && (
                  <td>
                    <input
                      type="checkbox"
                      aria-label={`Selecionar ${l.code}`}
                      checked={selected.has(l.lineId)}
                      onChange={(e) => toggle(l.lineId, e.target.checked)}
                    />
                  </td>
                )}
                <td>{l.code}</td>
                <td>{l.mark ? <span className="badge neutral">{markLabel[l.mark]}</span> : (l.account ?? "")}</td>
                <td>{l.description}</td>
                <td className="number">{formatMoney(l.budgeted)}</td>
                <td className="muted notes-cell" title={l.notes ?? undefined}>
                  {l.notes}
                </td>
                <td>
                  {spendingProfileLabel[l.suggestedProfile]}
                  {l.suggestionReason && <small className="note">{l.suggestionReason}</small>}
                </td>
                <td>
                  {isAdmin ? (
                    <select
                      aria-label={`Tipo de ${l.code}`}
                      value={choice(l)}
                      onChange={(e) => {
                        setChosen((current) => ({ ...current, [l.lineId]: e.target.value as SpendingProfile }));
                        toggle(l.lineId, true);
                      }}
                    >
                      {profiles.map((p) => (
                        <option key={p} value={p}>
                          {spendingProfileLabel[p]}
                        </option>
                      ))}
                    </select>
                  ) : (
                    spendingProfileLabel[l.profile]
                  )}
                </td>
                <td>
                  <span className={l.status === "CONFIRMED" ? "badge ok" : "badge warning"}>
                    {l.status === "CONFIRMED" ? spendingProfileStatusLabel.CONFIRMED : "tipo não confirmado"}
                  </span>
                  {l.changedBy && <small className="note">por {l.changedBy}</small>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
