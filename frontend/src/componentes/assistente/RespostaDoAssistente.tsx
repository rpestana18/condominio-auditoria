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
  const porNumero = new Map(resposta.citacoes.map((c) => [c.numero, c]));
  const idFonte = (numero: number) => `${prefixo}-fonte-${numero}`;

  /** Clique no [n]: destaca a fonte; se for PDF com página, já abre o original nela (RF-04.9). */
  function abrirCitacao(citacao: CitacaoDocumento) {
    setDestacada(citacao.numero);
    setErroAbertura(null);
    const elemento = document.getElementById(idFonte(citacao.numero));
    elemento?.scrollIntoView({ behavior: "smooth", block: "nearest" });
    elemento?.focus({ preventScroll: true });
    if (paginaDoPdf(citacao)) {
      abrirNaPagina(condominioId, citacao).catch((e: unknown) =>
        setErroAbertura(e instanceof Error ? e.message : "Não foi possível abrir o arquivo."),
      );
    }
  }

  if (resposta.situacao === "NAO_ENCONTRADA") {
    return (
      <div className="resposta">
        <p>
          <strong>Não encontrei nos documentos.</strong>
        </p>
        {resposta.sugestao && <p className="discreto">Pode faltar: {resposta.sugestao}</p>}
        {resposta.aviso && <p className="discreto aviso-resposta">{resposta.aviso}</p>}
      </div>
    );
  }

  return (
    <div className={resposta.citacoes.length > 0 ? "resposta com-fontes" : "resposta"}>
      <div className="resposta-texto">
        {resposta.nosDocumentos.length > 0 && (
          <section className="bloco-resposta" aria-label="Nos documentos">
            <h3>Nos documentos</h3>
            {resposta.nosDocumentos.map((paragrafo, i) => (
              <p key={i}>
                {paragrafo.texto}{" "}
                {paragrafo.citacoes.map((n) => {
                  const citacao = porNumero.get(n);
                  // Citação que o backend descartou (arquivo sem acesso ou excluído) não vira link
                  if (!citacao) return null;
                  return (
                    <button
                      key={n}
                      type="button"
                      className="marcador-citacao"
                      aria-label={`Fonte ${n}: ${citacao.nomeArquivo}, ${formatarLocalizacao(citacao)}`}
                      title={`${citacao.nomeArquivo}, ${formatarLocalizacao(citacao)}`}
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
        {resposta.nosDadosGravados.length > 0 && <DadosGravados dados={resposta.nosDadosGravados} />}
        {resposta.aviso && <p className="discreto aviso-resposta">{resposta.aviso}</p>}
        {erroAbertura && (
          <p className="aviso erro" role="alert">
            {erroAbertura}
          </p>
        )}
        <p className="discreto">Modelo: {resposta.modelo}</p>
      </div>

      {resposta.citacoes.length > 0 && (
        <aside className="fontes" aria-label="Fontes desta resposta">
          <h3>Fontes</h3>
          {resposta.citacoes.map((c) => (
            <FonteDocumento key={c.numero} id={idFonte(c.numero)} trecho={c} numero={c.numero} destacada={destacada === c.numero} />
          ))}
        </aside>
      )}
    </div>
  );
}
