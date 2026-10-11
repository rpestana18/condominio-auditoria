import { useState } from "react";
import { useSearchParams } from "react-router";
import { useFiles, useCategories } from "../api/queries";
import type { SourceFileSummary, FileCategory, IndexingStatus } from "../api/types";
import { FileDetail } from "../components/FileDetail";
import { EditCategory } from "../components/EditCategory";
import { FileUpload } from "../components/FileUpload";
import { FileIndexingBadge, indexingLabels } from "../components/FileIndexingBadge";
import { FileStatusBadge } from "../components/FileStatusBadge";
import { useSession } from "../context";
import { formatDateTime, formatPeriod } from "../format";

/** Filter of the "Busca" column (RF-04.7): an API status or "não indexado" (null field). */
type IndexingFilter = "ALL" | "NOT_INDEXED" | IndexingStatus;

/** Only hides rows that already came from the API; calculates nothing. */
const passesFilter = (f: SourceFileSummary, filter: IndexingFilter) =>
  filter === "ALL" || (filter === "NOT_INDEXED" ? !f.indexing : f.indexing?.status === filter);

/** Files split by category, from the most recent to the oldest. */
export function Files() {
  const { condominiumId, can, isFeatureEnabled } = useSession();
  const { data: categories = [] } = useCategories();
  const [category, setCategory] = useState<FileCategory | undefined>();
  // ?arquivo=<id> opens the detail directly (the Assistant's "Ver conferências do arquivo" link)
  const [parameters] = useSearchParams();
  const [selected, setSelected] = useState<string | null>(() => parameters.get("arquivo"));
  const [editing, setEditing] = useState<SourceFileSummary | null>(null);
  const canEdit = can("MANAGER", "ADMIN");
  const [indexingFilter, setIndexingFilter] = useState<IndexingFilter>("ALL");
  const { data: all = [], isLoading } = useFiles(condominiumId, category);
  // The "Busca" column only exists with the Assistant feature enabled (without it the API does not send the status)
  const withSearch = isFeatureEnabled("ASSISTANT") || all.some((f) => f.indexing);
  const files = withSearch ? all.filter((f) => passesFilter(f, indexingFilter)) : all;

  return (
    <div className={selected ? "with-detail" : undefined}>
      <div>
        <header className="page-title">
          <h1>Arquivos</h1>
        </header>
        {canEdit && <FileUpload initialCategory={category} />}

        <div className="tabs" role="tablist">
          <button role="tab" aria-selected={!category} onClick={() => setCategory(undefined)}>
            Todas
          </button>
          {categories.map((c) => (
            <button key={c.code} role="tab" aria-selected={category === c.code} onClick={() => setCategory(c.code)}>
              {c.label}
            </button>
          ))}
        </div>

        {withSearch && (
          <label className="filter">
            Busca nos documentos
            <select value={indexingFilter} onChange={(e) => setIndexingFilter(e.target.value as IndexingFilter)}>
              <option value="ALL">Todos os estados</option>
              <option value="NOT_INDEXED">Não indexado</option>
              {(Object.keys(indexingLabels) as IndexingStatus[]).map((s) => (
                <option key={s} value={s}>
                  {indexingLabels[s]}
                </option>
              ))}
            </select>
          </label>
        )}

        {isLoading ? (
          <p className="notice">Carregando…</p>
        ) : files.length === 0 ? (
          <p className="notice">
            {indexingFilter !== "ALL" && all.length > 0
              ? "Nenhum arquivo neste estado de indexação."
              : "Nenhum arquivo nesta categoria ainda."}
          </p>
        ) : (
          <table className="table clickable">
            <thead>
              <tr>
                <th>Arquivo</th>
                {!category && <th>Categoria</th>}
                <th>Período</th>
                <th>Enviado em</th>
                <th>Situação</th>
                {withSearch && <th>Busca</th>}
                {canEdit && <th aria-label="Ações" />}
              </tr>
            </thead>
            <tbody>
              {files.map((f) => (
                <tr key={f.id} onClick={() => setSelected(f.id)} className={f.id === selected ? "selected" : undefined}>
                  <td>{f.name}</td>
                  {!category && <td>{f.categoryLabel}</td>}
                  <td>{formatPeriod(f.periodStart, f.periodEnd)}</td>
                  <td>
                    {formatDateTime(f.uploadedAt)} <span className="muted">por {f.uploadedBy}</span>
                  </td>
                  <td>
                    <FileStatusBadge status={f.status} />
                  </td>
                  {withSearch && (
                    <td>
                      <FileIndexingBadge indexing={f.indexing} />
                    </td>
                  )}
                  {canEdit && (
                    <td>
                      <button
                        className="link-button"
                        disabled={f.status === "PROCESSING"}
                        title={f.status === "PROCESSING" ? "O arquivo já está sendo processado" : "Mudar a categoria"}
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditing(f);
                        }}
                      >
                        Editar
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {editing && <EditCategory file={editing} onClose={() => setEditing(null)} />}
      {selected && <FileDetail id={selected} onClose={() => setSelected(null)} />}
    </div>
  );
}
