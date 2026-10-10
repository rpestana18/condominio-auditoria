import { useState } from "react";
import type { CitacaoDocumento, RespostaAssistente } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { DadosGravados } from "./DadosGravados";
import { FonteDocumento } from "./FonteDocumento";
import { abrirNaPagina, formatarLocalizacao, paginaDoPdf } from "./trechos";

interface Props {
  resposta: RespostaAssistente;
  /** Prefixo único dos ids das fontes desta resposta (há várias respostas na mesma tela). */
  prefixo: string;
}

/**
 * Uma resposta do assistente em dois blocos (RF-04.14): "Nos documentos", com marcadores [n] clicáveis,
 * e "Nos dados gravados". As fontes ficam ao lado (tela larga) ou abaixo (tela estreita).
 */
export function RespostaDoAssistente({ resposta, prefixo }: Props) {
  const { condominioId } = useSessao();
  const [destacada, setDestacada] = useState<number | null>(null);
  const [erroAbertura, setErroAbertura] = useState<string | null>(null);
  const porNumero = new Map(resposta.citations.map((c) => [c.number, c]));
  const idFonte = (numero: number) => `${prefixo}-fonte-${numero}`;

  /** Clique no [n]: destaca a fonte; se for PDF com página, já abre o original nela (RF-04.9). */
  function abrirCitacao(citacao: CitacaoDocumento) {
    setDestacada(citacao.number);
    setErroAbertura(null);
    const elemento = document.getElementById(idFonte(citacao.number));
    elemento?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    elemento?.focus({ preventScroll: true });
    if (paginaDoPdf(citacao)) {
      abrirNaPagina(condominioId, citacao).catch((e: unknown) =>
        setErroAbertura(e instanceof Error ? e.message : "Não foi possível abrir o arquivo."),
      );
    }
  }

  if (resposta.status === "NOT_FOUND") {
    return (
      <div className="resposta">
        <p>
          <strong>Não encontrei nos documentos.</strong>
        </p>
        {resposta.suggestion && <p className="discreto">Pode faltar: {resposta.suggestion}</p>}
        {resposta.warning && <p className="discreto aviso-resposta">{resposta.warning}</p>}
      </div>
    );
  }

  return (
    <div className={resposta.citations.length > 0 ? "resposta com-fontes" : "resposta"}>
      <div className="resposta-texto">
        {resposta.fromDocuments.length > 0 && (
          <section className="bloco-resposta" aria-label="Nos documentos">
            <h3>Nos documentos</h3>
            {resposta.fromDocuments.map((paragrafo, i) => (
              <p key={i}>
                {paragrafo.text}{" "}
                {paragrafo.citations.map((n) => {
                  const citacao = porNumero.get(n);
                  // Citação que o backend descartou (arquivo sem acesso ou excluído) não vira link
                  if (!citacao) return null;
                  return (
                    <button
                      key={n}
                      type="button"
                      className="marcador-citacao"
                      aria-label={`Fonte ${n}: ${citacao.fileName}, ${formatarLocalizacao(citacao)}`}
                      title={`${citacao.fileName}, ${formatarLocalizacao(citacao)}`}
                      onClick={() => abrirCitacao(citacao)}
                    >
                      [{n}]
                    </button>
                  );
                })}
              </p>
            ))}
          </section>
        )}
        {resposta.fromStoredData.length > 0 && <DadosGravados dados={resposta.fromStoredData} />}
        {resposta.warning && <p className="discreto aviso-resposta">{resposta.warning}</p>}
        {erroAbertura && (
          <p className="aviso erro" role="alert">
            {erroAbertura}
          </p>
        )}
        <p className="discreto">Modelo: {resposta.model}</p>
      </div>

      {resposta.citations.length > 0 && (
        <aside className="fontes" aria-label="Fontes desta resposta">
          <h3>Fontes</h3>
          {resposta.citations.map((c) => (
            <FonteDocumento key={c.number} id={idFonte(c.number)} trecho={c} numero={c.number} destacada={destacada === c.number} />
          ))}
        </aside>
      )}
    </div>
  );
}
