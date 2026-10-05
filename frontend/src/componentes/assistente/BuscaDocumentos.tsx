import { useState, type FormEvent } from "react";
import { useBuscarDocumentos } from "../../api/consultas";
import { useSessao } from "../../contexto";
import { filtrosParaApi, useConversa } from "./conversa";
import { mensagemErroAssistente } from "./erros";
import { FonteDocumento } from "./FonteDocumento";

/** Limite do contrato (PedidoBuscaDocumentos.texto). */
const MAXIMO_CARACTERES = 500;
const LIMITE_RESULTADOS = 20;

/**
 * "Busca nos documentos" por palavra, sem IA (RF-04.18): devolve trechos citados e clicáveis, sem resposta redigida.
 * Funciona em qualquer modo de IA, com o módulo Assistente ligado. Usa os mesmos filtros do chat.
 */
export function BuscaDocumentos() {
  const { condominioId } = useSessao();
  const { filtros } = useConversa();
  const buscar = useBuscarDocumentos(condominioId);
  const [texto, setTexto] = useState("");
  const [buscado, setBuscado] = useState("");

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    const termo = texto.trim();
    if (!termo) return;
    setBuscado(termo);
    buscar.mutate({ texto: termo, filtros: filtrosParaApi(filtros), limite: LIMITE_RESULTADOS });
  }

  return (
    <section className="busca-documentos" aria-label="Busca nos documentos">
      <form className="filtros" onSubmit={enviar} role="search">
        <label className="campo busca-campo">
          Palavra ou expressão
          <input
            type="search"
            value={texto}
            onChange={(e) => setTexto(e.target.value)}
            maxLength={MAXIMO_CARACTERES}
            placeholder='Ex.: portão, "fundo de reserva", limpeza -jardim'
          />
        </label>
        <button type="submit" className="botao" disabled={!texto.trim() || buscar.isPending}>
          {buscar.isPending ? "Buscando…" : "Buscar"}
        </button>
      </form>
      <p className="discreto">Use aspas para uma frase exata e - para excluir uma palavra. A busca não usa IA.</p>

      <div aria-live="polite" aria-busy={buscar.isPending}>
        {buscar.isError && (
          <p className="aviso erro" role="alert">
            {mensagemErroAssistente(buscar.error)}
          </p>
        )}
        {buscar.isSuccess &&
          (buscar.data.length === 0 ? (
            <p className="aviso">Nenhum trecho encontrado para “{buscado}” nos documentos{filtrosParaApi(filtros) ? " filtrados" : ""}.</p>
          ) : (
            <>
              <p className="discreto">
                {buscar.data.length === 1 ? "1 trecho encontrado" : `${buscar.data.length} trechos encontrados`} para “{buscado}”,
                do mais para o menos relevante.
              </p>
              <div className="lista-trechos">
                {buscar.data.map((t) => (
                  <FonteDocumento key={t.trechoId} trecho={t} />
                ))}
              </div>
            </>
          ))}
      </div>
    </section>
  );
}
