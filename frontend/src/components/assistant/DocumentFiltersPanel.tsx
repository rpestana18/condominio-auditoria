import { useFiles, useCategories } from "../../api/queries";
import type { FileCategory, DocumentFilters } from "../../api/types";
import { useSession } from "../../context";
import { formatDate, formatPeriod } from "../../format";
import { useConversation } from "./conversation";

/** Toggles an item in a list (checking/unchecking a box). */
function toggle<T>(list: T[] | undefined, item: T): T[] {
  const current = list ?? [];
  return current.includes(item) ? current.filter((i) => i !== item) : [...current, item];
}

/** Summary of what is filtered, for the collapsed title: "2 categorias · 01/01/2025 a 31/12/2025". */
function summary(filters: DocumentFilters): string {
  const parts: string[] = [];
  const categories = filters.categories?.length ?? 0;
  const documents = filters.fileIds?.length ?? 0;
  if (categories) parts.push(categories === 1 ? "1 categoria" : `${categories} categorias`);
  if (filters.startDate || filters.endDate) {
    parts.push(`${formatDate(filters.startDate) || "…"} a ${formatDate(filters.endDate) || "…"}`);
  }
  if (documents) parts.push(documents === 1 ? "1 documento" : `${documents} documentos`);
  return parts.length ? parts.join(" · ") : "todos os documentos";
}

/**
 * Optional filters (RF-04.10): categories, period and documents. They apply to the chat and to the search.
 * Only builds the request; the backend is the one that applies it.
 */
export function DocumentFiltersPanel() {
  const { condominiumId } = useSession();
  const { filters, changeFilters } = useConversation();
  const { data: categories = [] } = useCategories();
  const { data: files = [] } = useFiles(condominiumId);
  const active = Boolean(filters.categories?.length || filters.fileIds?.length || filters.startDate || filters.endDate);

  return (
    <details className="block document-filters">
      <summary>
        Filtros: <span className="muted">{summary(filters)}</span>
      </summary>

      <div className="filters-grid">
        <fieldset>
          <legend>Categorias</legend>
          <div className="option-list">
            {categories.map((c) => (
              <label key={c.code}>
                <input
                  type="checkbox"
                  checked={filters.categories?.includes(c.code) ?? false}
                  onChange={() => changeFilters({ ...filters, categories: toggle<FileCategory>(filters.categories, c.code) })}
                />
                {c.label}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend>Período (competência)</legend>
          <label className="field">
            De
            <input
              type="date"
              value={filters.startDate ?? ""}
              onChange={(e) => changeFilters({ ...filters, startDate: e.target.value || null })}
            />
          </label>
          <label className="field">
            Até
            <input
              type="date"
              value={filters.endDate ?? ""}
              min={filters.startDate ?? undefined}
              onChange={(e) => changeFilters({ ...filters, endDate: e.target.value || null })}
            />
          </label>
          <p className="muted">Convenção, regimento e contratos sem competência podem entrar mesmo com período.</p>
        </fieldset>

        <fieldset>
          <legend>Documentos</legend>
          {files.length === 0 ? (
            <p className="muted">Nenhum arquivo enviado ainda.</p>
          ) : (
            <div className="option-list scrollable">
              {files.map((f) => (
                <label key={f.id} title={f.name}>
                  <input
                    type="checkbox"
                    checked={filters.fileIds?.includes(f.id) ?? false}
                    onChange={() => changeFilters({ ...filters, fileIds: toggle(filters.fileIds, f.id) })}
                  />
                  <span className="document-name">{f.name}</span>
                  <span className="muted">
                    {f.categoryLabel}
                    {f.periodStart && ` · ${formatPeriod(f.periodStart, f.periodEnd)}`}
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>
      </div>

      {active && (
        <button type="button" className="link-button" onClick={() => changeFilters({})}>
          Limpar filtros
        </button>
      )}
    </details>
  );
}
