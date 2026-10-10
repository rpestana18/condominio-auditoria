import { useState, type FormEvent } from "react";
import { useCategories, useUploadFile } from "../api/queries";
import type { FileCategory } from "../api/types";
import { useSession } from "../context";

/** File upload (Manager and Admin only). Processing runs on its own; the list shows the progress. */
export function FileUpload({ initialCategory }: { initialCategory?: FileCategory }) {
  const { condominiumId } = useSession();
  const { data: categories = [] } = useCategories();
  const upload = useUploadFile(condominiumId);
  const [category, setCategory] = useState<FileCategory>(initialCategory ?? "TRIAL_BALANCE");
  const [file, setFile] = useState<File | null>(null);

  function submit(event: FormEvent) {
    event.preventDefault();
    if (!file) return;
    upload.mutate({ category, file }, { onSuccess: () => setFile(null) });
  }

  return (
    <form className="upload" onSubmit={submit}>
      <label>
        Categoria
        <select value={category} onChange={(e) => setCategory(e.target.value as FileCategory)}>
          {categories.map((c) => (
            <option key={c.code} value={c.code}>
              {c.label}
            </option>
          ))}
        </select>
      </label>
      <label>
        Arquivo (PDF, Excel ou Word)
        <input
          key={file ? "with-file" : "empty"}
          type="file"
          accept=".pdf,.xlsx,.docx"
          onChange={(e) => setFile(e.target.files?.[0] ?? null)}
        />
      </label>
      <button className="button" type="submit" disabled={!file || upload.isPending}>
        {upload.isPending ? "Enviando…" : "Enviar"}
      </button>
      {upload.isError && <p className="notice error">{upload.error.message}</p>}
      {upload.isSuccess && <p className="notice ok">Arquivo recebido. O processamento aparece na lista abaixo.</p>}
    </form>
  );
}
