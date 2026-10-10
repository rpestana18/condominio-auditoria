import { useState } from "react";
import { useCategories } from "../../api/queries";
import type { DocumentChunk } from "../../api/types";
import { useSession } from "../../context";
import { openOnPage, downloadOriginal, isPdf, formatLocation, pdfPage } from "./chunks";

interface Props {
  chunk: DocumentChunk;
  /** Citation number in the answer ([1], [2]…). The keyword search has no number. */
  number?: number;
  /** Highlighted because the user clicked the answer's [n] marker. */
  highlighted?: boolean;
  /** Element id, so the [n] marker can scroll to the source. */
  id?: string;
}

/**
 * A cited source: file, category, location and the literal chunk (not checked).
 * PDF: "Abrir na página N". Excel and Word: the chunk stays visible here and the original can be downloaded (RF-04.9).
 */
export function DocumentSource({ chunk, number, highlighted = false, id }: Props) {
  const { condominiumId } = useSession();
  const { data: categories = [] } = useCategories();
  const [error, setError] = useState<string | null>(null);
  const category = categories.find((c) => c.code === chunk.category)?.label ?? chunk.category;
  const page = pdfPage(chunk);

  function run(action: () => Promise<void>) {
    setError(null);
    action().catch((e: unknown) => setError(e instanceof Error ? e.message : "Não foi possível abrir o arquivo."));
  }

  return (
    <article
      className={`source ${highlighted ? "highlighted" : ""}`}
      id={id}
      tabIndex={-1}
      aria-label={`Fonte${number ? ` ${number}` : ""}: ${chunk.fileName}, ${formatLocation(chunk)}`}
    >
      <header>
        {number !== undefined && <span className="citation-number">{number}</span>}
        <div>
          <strong className="document-name" title={chunk.fileName}>
            {chunk.fileName}
          </strong>
          <span className="muted">
            {category} · {formatLocation(chunk)}
          </span>
        </div>
      </header>
      <blockquote title="Texto literal do documento, não conferido">{chunk.text}</blockquote>
      <div className="actions">
        {isPdf(chunk) ? (
          <button type="button" className="link-button" onClick={() => run(() => openOnPage(condominiumId, chunk))}>
            {page ? `Abrir na página ${page}` : "Abrir original"}
          </button>
        ) : (
          <button type="button" className="link-button" onClick={() => run(() => downloadOriginal(condominiumId, chunk))}>
            Baixar original
          </button>
        )}
      </div>
      {error && (
        <p className="notice error" role="alert">
          {error}
        </p>
      )}
    </article>
  );
}
