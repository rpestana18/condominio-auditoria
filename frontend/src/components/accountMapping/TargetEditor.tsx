import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useSetAccountMapping } from "../../api/budgetQueries";
import type { MappedAccount, BudgetLine, MappingTargetType } from "../../api/types";
import { useSession } from "../../context";
import { formatMoney } from "../../format";
import { targetTypeLabel } from "../budgetVsActual/labels";

interface Props {
  budgetId: string;
  account: MappedAccount;
  /** Budget expense lines (groups 1.1 to 1.8), the only ones that can be a target. */
  lines: BudgetLine[];
  onClose: () => void;
}

const types: MappingTargetType[] = ["BUDGET_LINE", "ADJUSTMENT", "TO_REALLOCATE", "TRANSFER"];

/**
 * Admin chooses the target of a cash flow account (RF-03.1.4 and RF-03.1.5). The search is by code and by name
 * of the line; the cash flow account number is never used for matching.
 */
export function TargetEditor({ budgetId, account, lines, onClose }: Props) {
  const { condominiumId } = useSession();
  const setMapping = useSetAccountMapping(condominiumId, budgetId);
  const dialog = useRef<HTMLDialogElement>(null);
  const [type, setType] = useState<MappingTargetType>(account.target?.type ?? "BUDGET_LINE");
  const [lineId, setLineId] = useState<string>(account.target?.lineId ?? "");
  const [detail, setDetail] = useState(account.target?.detail ?? "");
  const [search, setSearch] = useState("");
  const [confirm, setConfirm] = useState(true);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  // List filter only to help the person find the line (by code or by text)
  const found = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return lines;
    return lines.filter((l) =>
      [l.effectiveCode, l.description, l.account ?? "", l.accountText ?? ""].some((t) => t.toLowerCase().includes(term)),
    );
  }, [search, lines]);

  function save(event: FormEvent) {
    event.preventDefault();
    setMapping.mutate(
      {
        account: account.account,
        request: { type, lineId: type === "BUDGET_LINE" ? lineId : null, detail: type === "BUDGET_LINE" ? null : detail || null, confirm },
      },
      { onSuccess: onClose },
    );
  }

  return (
    <dialog ref={dialog} className="modal wide" onClose={onClose}>
      <form onSubmit={save}>
        <h2>
          Destino da conta {account.account} {account.name}
        </h2>
        <p className="muted">
          {account.ledgerEntries} lançamentos · {formatMoney(account.debits)} no exercício
          {account.target && ` · atual: ${account.target.text}`}
        </p>
        {account.reason && <p className="muted">Motivo da sugestão: {account.reason}</p>}

        <fieldset>
          <legend>Tipo de destino</legend>
          {types.map((t) => (
            <label key={t}>
              <input type="radio" name="type" checked={type === t} onChange={() => setType(t)} />
              {targetTypeLabel[t]}
            </label>
          ))}
        </fieldset>

        {type === "BUDGET_LINE" ? (
          <fieldset>
            <legend>Linha da PO</legend>
            <input type="search" placeholder="Buscar por código ou nome (ex.: 1.7.8 ou hidráulico)" value={search} onChange={(e) => setSearch(e.target.value)} />
            <select size={8} value={lineId} onChange={(e) => setLineId(e.target.value)} required aria-label="Linha da PO">
              {found.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.effectiveCode} {l.description}
                  {l.account ? ` (${l.account})` : ""}
                </option>
              ))}
            </select>
          </fieldset>
        ) : (
          <label className="field">
            Detalhe (ex.: estorno, cartão)
            <input value={detail} onChange={(e) => setDetail(e.target.value)} />
          </label>
        )}

        <label className="inline-field">
          <input type="checkbox" checked={confirm} onChange={(e) => setConfirm(e.target.checked)} />
          Confirmar agora (sem marcar, fica como sugerido e não muda nenhum número)
        </label>

        {setMapping.isError && <p className="notice error">{setMapping.error.message}</p>}
        <div className="actions">
          <button className="button secondary" type="button" onClick={onClose}>
            Cancelar
          </button>
          <button className="button" type="submit" disabled={setMapping.isPending || (type === "BUDGET_LINE" && !lineId)}>
            {setMapping.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
