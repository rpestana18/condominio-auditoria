import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useSetBudgetItem, useBudgetItems } from "../../api/budgetAnalysisQueries";
import type { BudgetLineItem } from "../../api/types";
import { useSession } from "../../context";
import { formatMoney } from "../../format";

interface Props {
  budgetId: string;
  line: BudgetLineItem;
  onClose: () => void;
}

type Mode = "EXISTING" | "NEW";

/**
 * Admin chooses the budget item of a budget line (RF-11.7): one from the condominium's catalog or a new one,
 * created from the line. The search only helps find the item in the list; the Admin is the one who decides.
 */
export function BudgetItemEditor({ budgetId, line, onClose }: Props) {
  const { condominiumId } = useSession();
  const setItem = useSetBudgetItem(condominiumId, budgetId);
  const { data: catalog = [], isLoading } = useBudgetItems(condominiumId);
  const dialog = useRef<HTMLDialogElement>(null);
  const [mode, setMode] = useState<Mode>("EXISTING");
  const [budgetItemId, setBudgetItemId] = useState(line.budgetItem?.id ?? "");
  // Name suggestion for the new item: what the line shows as the budget account (e.g. "1682 - Sindicatura Profissional")
  const [name, setName] = useState(line.account);
  const [search, setSearch] = useState("");
  const [confirm, setConfirm] = useState(true);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  const found = useMemo(() => {
    const term = search.trim().toLowerCase();
    if (!term) return catalog;
    return catalog.filter((i) => [i.name, i.group ?? ""].some((t) => t.toLowerCase().includes(term)));
  }, [search, catalog]);

  const nameValid = name.trim().length > 0 && name.trim().length <= 300;
  const canSave = mode === "EXISTING" ? !!budgetItemId : nameValid;

  function save(event: FormEvent) {
    event.preventDefault();
    setItem.mutate(
      {
        lineId: line.lineId,
        // The contract accepts a catalog item or a new one, never both
        request: mode === "EXISTING" ? { budgetItemId, confirm } : { newBudgetItem: name.trim(), confirm },
      },
      { onSuccess: onClose },
    );
  }

  return (
    <dialog ref={dialog} className="modal wide" onClose={onClose}>
      <form onSubmit={save}>
        <h2>
          Rubrica da linha {line.code} {line.description}
        </h2>
        <p className="muted">
          Conta da PO: {line.account} · {formatMoney(line.budgeted)} orçado
          {line.budgetItem && ` · atual: ${line.budgetItem.name}`}
        </p>
        {line.reason && <p className="muted">Motivo da sugestão: {line.reason}</p>}

        <fieldset>
          <legend>Rubrica</legend>
          <label>
            <input type="radio" name="mode" checked={mode === "EXISTING"} onChange={() => setMode("EXISTING")} />
            Escolher uma rubrica existente
          </label>
          <label>
            <input type="radio" name="mode" checked={mode === "NEW"} onChange={() => setMode("NEW")} />
            Criar uma rubrica nova a partir desta linha
          </label>
        </fieldset>

        {mode === "EXISTING" ? (
          <fieldset>
            <legend>Catálogo do condomínio</legend>
            <input type="search" placeholder="Buscar por nome ou grupo (ex.: 1682 ou 1.3)" value={search} onChange={(e) => setSearch(e.target.value)} />
            {isLoading ? (
              <p className="notice">Carregando…</p>
            ) : (
              <select size={8} value={budgetItemId} onChange={(e) => setBudgetItemId(e.target.value)} required aria-label="Rubrica">
                {found.map((i) => (
                  <option key={i.id} value={i.id}>
                    {i.name}
                    {i.group ? ` (grupo ${i.group})` : ""}
                  </option>
                ))}
              </select>
            )}
          </fieldset>
        ) : (
          <label className="field">
            Nome da rubrica nova
            <input value={name} maxLength={300} onChange={(e) => setName(e.target.value)} required />
          </label>
        )}

        <label className="inline-field">
          <input type="checkbox" checked={confirm} onChange={(e) => setConfirm(e.target.checked)} />
          Confirmar agora (sem marcar, fica como sugerido e não entra na comparação por linha)
        </label>

        {setItem.isError && <p className="notice error">{setItem.error.message}</p>}
        <div className="actions">
          <button className="button secondary" type="button" onClick={onClose}>
            Cancelar
          </button>
          <button className="button" type="submit" disabled={setItem.isPending || !canSave}>
            {setItem.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
