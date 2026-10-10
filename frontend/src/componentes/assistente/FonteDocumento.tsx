import { useState } from "react";
import { useCategorias } from "../../api/consultas";
import type { TrechoDocumento } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { abrirNaPagina, baixarOriginal, ehPdf, formatarLocalizacao, paginaDoPdf } from "./trechos";

interface Props {
  trecho: TrechoDocumento;
  /** Número da citação na resposta ([1], [2]…). Na busca por palavra não há número. */
  numero?: number;
  /** Destacada porque o usuário clicou no marcador [n] da resposta. */
  destacada?: boolean;
  /** Id do elemento, para o marcador [n] rolar até a fonte. */
  id?: string;
}

/**
 * Uma fonte citada: arquivo, categoria, localização e o trecho literal (não conferido).
 * PDF: "Abrir na página N". Excel e Word: o trecho fica visível aqui e o original pode ser baixado (RF-04.9).
 */
export function FonteDocumento({ trecho, numero, destacada = false, id }: Props) {
  const { condominioId } = useSessao();
  const { data: categorias = [] } = useCategorias();
  const [erro, setErro] = useState<string | null>(null);
  const categoria = categorias.find((c) => c.code === trecho.category)?.label ?? trecho.category;
  const pagina = paginaDoPdf(trecho);

  function executar(acao: () => Promise<void>) {
    setErro(null);
    acao().catch((e: unknown) => setErro(e instanceof Error ? e.message : "Não foi possível abrir o arquivo."));
  }

  return (
    <article
      className={`fonte ${destacada ? "destacada" : ""}`}
      id={id}
      tabIndex={-1}
      aria-label={`Fonte${numero ? ` ${numero}` : ""}: ${trecho.fileName}, ${formatarLocalizacao(trecho)}`}
    >
      <header>
        {numero !== undefined && <span className="numero-citacao">{numero}</span>}
        <div>
          <strong className="nome-documento" title={trecho.fileName}>
            {trecho.fileName}
          </strong>
          <span className="discreto">
            {categoria} · {formatarLocalizacao(trecho)}
          </span>
        </div>
      </header>
      <blockquote title="Texto literal do documento, não conferido">{trecho.text}</blockquote>
      <div className="acoes">
        {ehPdf(trecho) ? (
          <button type="button" className="botao-link" onClick={() => executar(() => abrirNaPagina(condominioId, trecho))}>
            {pagina ? `Abrir na página ${pagina}` : "Abrir original"}
          </button>
        ) : (
          <button type="button" className="botao-link" onClick={() => executar(() => baixarOriginal(condominioId, trecho))}>
            Baixar original
          </button>
        )}
      </div>
      {erro && (
        <p className="aviso erro" role="alert">
          {erro}
        </p>
      )}
    </article>
  );
}
