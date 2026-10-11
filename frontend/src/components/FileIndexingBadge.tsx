import type { FileIndexing, IndexingStatus } from "../api/types";

/** Portuguese labels for each status (RF-04.7). Also used in the filter of the Files screen. */
export const indexingLabels: Record<IndexingStatus, string> = {
  QUEUED: "Na fila",
  INDEXING: "Indexando",
  INDEXED: "Pronto para busca",
  NO_TEXT: "Sem texto",
  WITHDRAWN: "Fora da busca",
  ERROR: "Erro",
};

const plural = (n: number, singular: string, plural: string) => `${n.toLocaleString("pt-BR")} ${n === 1 ? singular : plural}`;

interface Props {
  /** Null or missing = not indexed yet (only arrives like this with the Assistant feature enabled). */
  indexing: FileIndexing | null | undefined;
  /** In the detail we also show the pages read. */
  full?: boolean;
}

/**
 * Indexing status for search, shown discreetly. The screen only shows what the API sends:
 * the reason text comes ready from the backend (e.g. when the file was indexed by keyword only).
 */
export function FileIndexingBadge({ indexing, full = false }: Props) {
  if (!indexing) return <span className="indexing">Não indexado</span>;
  const { status, reason, pages, chunks } = indexing;

  const parts = [indexingLabels[status]];
  if (status === "INDEXED") {
    if (chunks != null) parts.push(plural(chunks, "trecho", "trechos"));
    if (full && pages != null) parts.push(plural(pages, "página lida", "páginas lidas"));
  }
  const text = parts.join(" · ");

  // INDEXED with a reason = indexed with a caveat (e.g. keyword only); becomes a discreet warning
  const withWarning = status === "INDEXED" && !!reason;
  const reasonInParentheses = (status === "NO_TEXT" || status === "ERROR") && reason;

  return (
    <span className={`indexing indexing-${status.toLowerCase()}${withWarning ? " indexing-caveat" : ""}`} title={reason ?? undefined}>
      {text}
      {reasonInParentheses && (
        <>
          {" "}
          <span className="indexing-reason">({reason})</span>
        </>
      )}
      {withWarning && (
        <span className="indexing-reason">
          <span aria-hidden="true">⚠ </span>
          {reason}
        </span>
      )}
    </span>
  );
}
