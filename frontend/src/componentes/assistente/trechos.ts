import { abrirOriginal, baixarArquivo } from "../../api/cliente";
import type { TrechoDocumento } from "../../api/tipos";

/** Endpoint do original, exatamente como foi enviado (o mesmo do botão "Abrir original" da tela Arquivos). */
export const caminhoOriginal = (condominioId: string, trecho: TrechoDocumento) =>
  `/condominiums/${condominioId}/files/${trecho.fileId}/content`;

/** PDF abre no navegador na página citada; Excel e Word não têm "página", então mostramos o trecho e o download. */
export const ehPdf = (trecho: TrechoDocumento) => trecho.fileName.toLowerCase().endsWith(".pdf");

/** Página para o `#page=N`, só em PDF com a localização por página. */
export const paginaDoPdf = (trecho: TrechoDocumento): number | null =>
  ehPdf(trecho) && trecho.location.type === "PAGE" ? (trecho.location.page ?? null) : null;

const intervalo = (inicio?: number | null, fim?: number | null) =>
  inicio == null ? "" : fim == null || fim === inicio ? `${inicio}` : `${inicio}–${fim}`;

/** "p. 4", "aba Junho, linhas 10–14", "parágrafos 3–5". Sem os campos, usa a descrição pronta da API. */
export function formatarLocalizacao(trecho: TrechoDocumento): string {
  const l = trecho.location;
  if (l.type === "PAGE" && l.page != null) return `p. ${l.page}`;
  if (l.type === "SHEET" && l.sheet) {
    const linhas = intervalo(l.startRow, l.endRow);
    const plural = l.endRow != null && l.endRow !== l.startRow;
    return linhas ? `aba ${l.sheet}, ${plural ? "linhas" : "linha"} ${linhas}` : `aba ${l.sheet}`;
  }
  if (l.type === "PARAGRAPHS" && l.startParagraph != null) {
    const plural = l.endParagraph != null && l.endParagraph !== l.startParagraph;
    const paragrafos = `${plural ? "parágrafos" : "parágrafo"} ${intervalo(l.startParagraph, l.endParagraph)}`;
    return l.section ? `${l.section}, ${paragrafos}` : paragrafos;
  }
  return l.description;
}

/** Abre o PDF na página citada, em nova aba (RF-04.9). */
export const abrirNaPagina = (condominioId: string, trecho: TrechoDocumento) =>
  abrirOriginal(caminhoOriginal(condominioId, trecho), { pagina: paginaDoPdf(trecho), tipo: "application/pdf" });

/** Baixa o original com o nome com que foi enviado. Ação explícita do usuário. */
export const baixarOriginal = (condominioId: string, trecho: TrechoDocumento) =>
  baixarArquivo(caminhoOriginal(condominioId, trecho), trecho.fileName);
