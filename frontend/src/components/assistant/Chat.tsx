import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useConversation } from "./conversation";
import { AssistantAnswerView } from "./AssistantAnswerView";

/** Contract limit (QuestionRequest.question). The backend refuses anything above it (400). */
const MAX_CHARACTERS = 2000;

/** Conversation with the assistant (RF-04.8, RF-04.11): in memory only, sent question by question. */
export function Chat() {
  const { turns, pending, ask, newConversation } = useConversation();
  const [text, setText] = useState("");
  const question = text.trim();

  function submit(event?: FormEvent) {
    event?.preventDefault();
    if (!question || pending) return;
    ask(question);
    setText("");
  }

  /** Enter sends; Shift+Enter breaks the line. */
  function onKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key === "Enter" && !event.shiftKey) {
      event.preventDefault();
      submit();
    }
  }

  return (
    <section className="chat" aria-label="Conversa com o assistente">
      <div className="conversation" aria-live="polite" aria-busy={pending}>
        {turns.length === 0 && (
          <p className="notice">
            Pergunte sobre os documentos do condomínio. As respostas trazem as fontes; os números vêm dos dados gravados.
          </p>
        )}
        {turns.map((turn) => (
          <article key={turn.id} className="turn">
            <p className="question">
              <span className="sr-only">Você perguntou: </span>
              {turn.question}
            </p>
            {turn.answer ? (
              <AssistantAnswerView answer={turn.answer} prefix={`turn-${turn.id}`} />
            ) : turn.error ? (
              <p className="notice error" role="alert">
                {turn.error}
              </p>
            ) : (
              <p className="notice searching">Pesquisando nos documentos…</p>
            )}
          </article>
        ))}
      </div>

      <form className="question-form" onSubmit={submit}>
        <label className="field">
          Sua pergunta
          <textarea
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={onKeyDown}
            maxLength={MAX_CHARACTERS}
            rows={3}
            placeholder="Ex.: qual o índice de reajuste do contrato de limpeza?"
            aria-describedby="question-counter"
          />
        </label>
        <div className="question-actions">
          <span id="question-counter" className="muted">
            {text.length} de {MAX_CHARACTERS} caracteres · Enter envia, Shift+Enter quebra a linha
          </span>
          <button type="button" className="button secondary" onClick={newConversation} disabled={turns.length === 0}>
            Nova conversa
          </button>
          <button type="submit" className="button" disabled={!question || pending}>
            {pending ? "Pesquisando…" : "Perguntar"}
          </button>
        </div>
      </form>
    </section>
  );
}
