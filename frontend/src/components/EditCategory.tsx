import { useEffect, useRef, useState, type FormEvent } from "react";
import { useChangeCategory, useCategories } from "../api/queries";
import type { SourceFileSummary, FileCategory } from "../api/types";
import { useSession } from "../context";

/** Changes the category of an already uploaded file (RF-01.7). Only Manager and Admin get here. */
export function EditCategory({ file, onClose }: { file: SourceFileSummary; onClose: () => void }) {
  const { condominiumId } = useSession();
  const { data: categories = [] } = useCategories();
  const change = useChangeCategory(condominiumId);
  const [category, setCategory] = useState<FileCategory>(file.category);
  const dialog = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    dialog.current?.showModal();
  }, []);

  function save(event: FormEvent) {
    event.preventDefault();
    if (category === file.category) {
      onClose();
      return;
    }
    change.mutate({ id: file.id, category }, { onSuccess: onClose });
  }

  return (
    <dialog ref={dialog} className="modal" onClose={onClose} onClick={(e) => e.stopPropagation()}>
      <form onSubmit={save}>
        <h2>Editar categoria</h2>
        <p className="muted">{file.name}</p>
        <fieldset>
          <legend>Categoria</legend>
          {categories.map((c) => (
            <label key={c.code}>
              <input
                type="radio"
                name="category"
                value={c.code}
                checked={category === c.code}
                onChange={() => setCategory(c.code)}
              />
              {c.label}
              {c.code === file.category && <span className="muted"> (atual)</span>}
            </label>
          ))}
        </fieldset>
        <p className="notice warning">Trocar a categoria reprocessa o arquivo e substitui os dados extraídos dele.</p>
        {change.isError && <p className="notice error">{change.error.message}</p>}
        <div className="actions">
          <button className="button secondary" type="button" onClick={onClose}>
            Cancelar
          </button>
          <button className="button" type="submit" disabled={change.isPending}>
            {change.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
