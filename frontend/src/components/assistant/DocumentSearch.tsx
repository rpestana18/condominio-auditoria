import { useState, type FormEvent } from "react";
import { useSearchDocuments } from "../../api/queries";
import { useSession } from "../../context";
import { filtersForApi, useConversation } from "./conversation";
import { assistantErrorMessage } from "./errors";
import { DocumentSource } from "./DocumentSource";

/** Contract limit (DocumentSearchRequest.text). */
const MAX_CHARACTERS = 500;
const RESULT_LIMIT = 20;

/**
 * "Busca nos documentos" by keyword, without AI (RF-04.18): returns quoted, clickable chunks, with no written answer.
 * Works in any AI mode, with the Assistant feature enabled. Uses the same filters as the chat.
 */
export function DocumentSearch() {
  const { condominiumId } = useSession();
  const { filters } = useConversation();
  const search = useSearchDocuments(condominiumId);
  const [text, setText] = useState("");
  const [searched, setSearched] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    const term = text.trim();
    if (!term) return;
    setSearched(term);
    search.mutate({ text: term, filters: filtersForApi(filters), limit: RESULT_LIMIT });
  }

  return (
    <section className="document-search" aria-label="Busca nos documentos">
      <form className="filters" onSubmit={submit} role="search">
        <label className="field search-field">
          Palavra ou expressão
          <input
            type="search"
            value={text}
            onChange={(e) => setText(e.target.value)}
            maxLength={MAX_CHARACTERS}
            placeholder='Ex.: portão, "fundo de reserva", limpeza -jardim'
          />
        </label>
        <button type="submit" className="button" disabled={!text.trim() || search.isPending}>
          {search.isPending ? "Buscando…" : "Buscar"}
        </button>
      </form>
      <p className="muted">Use aspas para uma frase exata e - para excluir uma palavra. A busca não usa IA.</p>

      <div aria-live="polite" aria-busy={search.isPending}>
        {search.isError && (
          <p className="notice error" role="alert">
            {assistantErrorMessage(search.error)}
          </p>
        )}
        {search.isSuccess &&
          (search.data.length === 0 ? (
            <p className="notice">Nenhum trecho encontrado para “{searched}” nos documentos{filtersForApi(filters) ? " filtrados" : ""}.</p>
          ) : (
            <>
              <p className="muted">
                {search.data.length === 1 ? "1 trecho encontrado" : `${search.data.length} trechos encontrados`} para “{searched}”,
                do mais para o menos relevante.
              </p>
              <div className="chunk-list">
                {search.data.map((c) => (
                  <DocumentSource key={c.chunkId} chunk={c} />
                ))}
              </div>
            </>
          ))}
      </div>
    </section>
  );
}
