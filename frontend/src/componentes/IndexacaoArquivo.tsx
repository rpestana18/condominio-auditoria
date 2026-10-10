import type { IndexacaoArquivo as Indexacao, SituacaoIndexacao } from "../api/tipos";

/** Rótulos em português de cada situação (RF-04.7). Também usados no filtro da tela Arquivos. */
export const rotulosIndexacao: Record<SituacaoIndexacao, string> = {
  QUEUED: "Na fila",
  INDEXING: "Indexando",
  INDEXED: "Pronto para busca",
  NO_TEXT: "Sem texto",
  WITHDRAWN: "Fora da busca",
  ERROR: "Erro",
};

const plural = (n: number, singular: string, plural: string) => `${n.toLocaleString("pt-BR")} ${n === 1 ? singular : plural}`;

interface Props {
  /** Nulo ou ausente = ainda não indexado (só chega assim com o módulo Assistente ligado). */
  indexacao: Indexacao | null | undefined;
  /** No detalhe mostramos também as páginas lidas. */
  completo?: boolean;
}

/**
 * Estado da indexação para a busca, de forma discreta. A tela só mostra o que a API manda:
 * o texto do motivo vem pronto do backend (ex.: quando o arquivo ficou indexado só por palavra).
 */
export function IndexacaoArquivo({ indexacao, completo = false }: Props) {
  if (!indexacao) return <span className="indexing">Não indexado</span>;
  const { status: situacao, reason: motivo, pages: paginas, chunks: trechos } = indexacao;

  const partes = [rotulosIndexacao[situacao]];
  if (situacao === "INDEXED") {
    if (trechos != null) partes.push(plural(trechos, "trecho", "trechos"));
    if (completo && paginas != null) partes.push(plural(paginas, "página lida", "páginas lidas"));
  }
  const texto = partes.join(" · ");

  // INDEXED com motivo = indexado com ressalva (ex.: só por palavra); vira aviso discreto
  const comAviso = situacao === "INDEXED" && !!motivo;
  const motivoEntreParenteses = (situacao === "NO_TEXT" || situacao === "ERROR") && motivo;

  return (
    <span className={`indexacao indexacao-${situacao.toLowerCase()}${comAviso ? " indexacao-ressalva" : ""}`} title={motivo ?? undefined}>
      {texto}
      {motivoEntreParenteses && (
        <>
          {" "}
          <span className="indexacao-motivo">({motivo})</span>
        </>
      )}
      {comAviso && (
        <span className="indexacao-motivo">
          <span aria-hidden="true">⚠ </span>
          {motivo}
        </span>
      )}
    </span>
  );
}
