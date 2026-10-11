import { useState } from "react";
import type { DocumentCitation, AssistantAnswer } from "../../api/types";
import { useSession } from "../../context";
import { StoredDataSection } from "./StoredDataSection";
import { DocumentSource } from "./DocumentSource";
import { openOnPage, formatLocation, pdfPage } from "./chunks";

interface Props {
  answer: AssistantAnswer;
  /** Unique prefix of the source ids of this answer (there are several answers on the same screen). */
  prefix: string;
}

/**
 * An assistant answer in two blocks (RF-04.14): "Nos documentos", with clickable [n] markers,
 * and "Nos dados gravados". The sources sit beside it (wide screen) or below (narrow screen).
 */
export function AssistantAnswerView({ answer, prefix }: Props) {
  const { condominiumId } = useSession();
  const [highlighted, setHighlighted] = useState<number | null>(null);
  const [openError, setOpenError] = useState<string | null>(null);
  const byNumber = new Map(answer.citations.map((c) => [c.number, c]));
  const sourceId = (number: number) => `${prefix}-source-${number}`;

  /** Click on [n]: highlights the source; if it is a PDF with a page, opens the original on it right away (RF-04.9). */
  function openCitation(citation: DocumentCitation) {
    setHighlighted(citation.number);
    setOpenError(null);
    const element = document.getElementById(sourceId(citation.number));
    element?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    element?.focus({ preventScroll: true });
    if (pdfPage(citation)) {
      openOnPage(condominiumId, citation).catch((e: unknown) =>
        setOpenError(e instanceof Error ? e.message : "Não foi possível abrir o arquivo."),
      );
    }
  }

  if (answer.status === "NOT_FOUND") {
    return (
      <div className="answer">
        <p>
          <strong>Não encontrei nos documentos.</strong>
        </p>
        {answer.suggestion && <p className="muted">Pode faltar: {answer.suggestion}</p>}
        {answer.warning && <p className="muted answer-warning">{answer.warning}</p>}
      </div>
    );
  }

  return (
    <div className={answer.citations.length > 0 ? "answer with-sources" : "answer"}>
      <div className="answer-text">
        {answer.fromDocuments.length > 0 && (
          <section className="answer-block" aria-label="Nos documentos">
            <h3>Nos documentos</h3>
            {answer.fromDocuments.map((paragraph, i) => (
              <p key={i}>
                {paragraph.text}{" "}
                {paragraph.citations.map((n) => {
                  const citation = byNumber.get(n);
                  // A citation the backend discarded (file without access or deleted) does not become a link
                  if (!citation) return null;
                  return (
                    <button
                      key={n}
                      type="button"
                      className="citation-marker"
                      aria-label={`Fonte ${n}: ${citation.fileName}, ${formatLocation(citation)}`}
                      title={`${citation.fileName}, ${formatLocation(citation)}`}
                      onClick={() => openCitation(citation)}
                    >
                      [{n}]
                    </button>
                  );
                })}
              </p>
            ))}
          </section>
        )}
        {answer.fromStoredData.length > 0 && <StoredDataSection items={answer.fromStoredData} />}
        {answer.warning && <p className="muted answer-warning">{answer.warning}</p>}
        {openError && (
          <p className="notice error" role="alert">
            {openError}
          </p>
        )}
        <p className="muted">Modelo: {answer.model}</p>
      </div>

      {answer.citations.length > 0 && (
        <aside className="sources" aria-label="Fontes desta resposta">
          <h3>Fontes</h3>
          {answer.citations.map((c) => (
            <DocumentSource key={c.number} id={sourceId(c.number)} chunk={c} number={c.number} highlighted={highlighted === c.number} />
          ))}
        </aside>
      )}
    </div>
  );
}
