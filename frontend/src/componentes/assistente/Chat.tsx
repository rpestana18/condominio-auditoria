import { useState, type FormEvent, type KeyboardEvent } from "react";
import { useConversa } from "./conversa";
import { RespostaDoAssistente } from "./RespostaDoAssistente";

/** Limite do contrato (PedidoPergunta.pergunta). O backend recusa acima disso (400). */
const MAXIMO_CARACTERES = 2000;

/** Conversa com o assistente (RF-04.8, RF-04.11): só em memória, enviada pergunta a pergunta. */
export function Chat() {
  const { trocas, pendente, perguntar, novaConversa } = useConversa();
  const [texto, setTexto] = useState("");
  const pergunta = texto.trim();

  function enviar(evento?: FormEvent) {
    evento?.preventDefault();
    if (!pergunta || pendente) return;
    perguntar(pergunta);
    setTexto("");
  }

  /** Enter envia; Shift+Enter quebra a linha. */
  function aoTeclar(evento: KeyboardEvent<HTMLTextAreaElement>) {
    if (evento.key === "Enter" && !evento.shiftKey) {
      evento.preventDefault();
      enviar();
    }
  }

  return (
    <section className="chat" aria-label="Conversa com o assistente">
      <div className="conversa" aria-live="polite" aria-busy={pendente}>
        {trocas.length === 0 && (
          <p className="aviso">
            Pergunte sobre os documentos do condomínio. As respostas trazem as fontes; os números vêm dos dados gravados.
          </p>
        )}
        {trocas.map((troca) => (
          <article key={troca.id} className="troca">
            <p className="pergunta">
              <span className="sr-only">Você perguntou: </span>
              {troca.pergunta}
            </p>
            {troca.resposta ? (
              <RespostaDoAssistente resposta={troca.resposta} prefixo={`troca-${troca.id}`} />
            ) : troca.erro ? (
              <p className="aviso erro" role="alert">
                {troca.erro}
              </p>
            ) : (
              <p className="aviso pesquisando">Pesquisando nos documentos…</p>
            )}
          </article>
        ))}
      </div>

      <form className="pergunta-form" onSubmit={enviar}>
        <label className="campo">
          Sua pergunta
          <textarea
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            onKeyDown={aoTeclar}
            maxLength={MAXIMO_CARACTERES}
            rows={3}
            placeholder="Ex.: qual o índice de reajuste do contrato de limpeza?"
            aria-describedby="contador-pergunta"
          />
        </label>
        <div className="pergunta-acoes">
          <span id="contador-pergunta" className="discreto">
            {texto.length} de {MAXIMO_CARACTERES} caracteres · Enter envia, Shift+Enter quebra a linha
          </span>
          <button type="button" className="botao secundario" onClick={novaConversa} disabled={trocas.length === 0}>
            Nova conversa
          </button>
          <button type="submit" className="botao" disabled={!pergunta || pendente}>
            {pendente ? "Pesquisando…" : "Perguntar"}
          </button>
        </div>
      </form>
    </section>
  );
}
