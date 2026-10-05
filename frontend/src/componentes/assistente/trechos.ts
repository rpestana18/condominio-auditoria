import { abrirOriginal, baixarArquivo } from "../../api/cliente";
import type { TrechoDocumento } from "../../api/tipos";

/** Endpoint do original, exatamente como foi enviado (o mesmo do botão "Abrir original" da tela Arquivos). */
export const caminhoOriginal = (condominioId: string, trecho: TrechoDocumento) =>
  `/condominios/${condominioId}/arquivos/${trecho.arquivoId}/conteudo`;

/** PDF abre no navegador na página citada; Excel e Word não têm "página", então mostramos o trecho e o download. */
export const ehPdf = (trecho: TrechoDocumento) => trecho.nomeArquivo.toLowerCase().endsWith(".pdf");

/** Página para o `#page=N`, só em PDF com a localização por página. */
export const paginaDoPdf = (trecho: TrechoDocumento): number | null =>
  ehPdf(trecho) && trecho.localizacao.tipo === "PAGINA" ? (trecho.localizacao.pagina ?? null) : null;

const intervalo = (inicio?: number | null, fim?: number | null) =>
  inicio == null ? "" : fim == null || fim === inicio ? `${inicio}` : `${inicio}–${fim}`;

/** "p. 4", "aba Junho, linhas 10–14", "parágrafos 3–5". Sem os campos, usa a descrição pronta da API. */
export function formatarLocalizacao(trecho: TrechoDocumento): string {
  const l = trecho.localizacao;
  if (l.tipo === "PAGINA" && l.pagina != null) return `p. ${l.pagina}`;
  if (l.tipo === "PLANILHA" && l.aba) {
    const linhas = intervalo(l.linhaInicio, l.linhaFim);
    const plural = l.linhaFim != null && l.linhaFim !== l.linhaInicio;
    return linhas ? `aba ${l.aba}, ${plural ? "linhas" : "linha"} ${linhas}` : `aba ${l.aba}`;
  }
  if (l.tipo === "PARAGRAFOS" && l.paragrafoInicio != null) {
    const plural = l.paragrafoFim != null && l.paragrafoFim !== l.paragrafoInicio;
    const paragrafos = `${plural ? "parágrafos" : "parágrafo"} ${intervalo(l.paragrafoInicio, l.paragrafoFim)}`;
    return l.secao ? `${l.secao}, ${paragrafos}` : paragrafos;
  }
  return l.descricao;
}

/** Abre o PDF na página citada, em nova aba (RF-04.9). */
export const abrirNaPagina = (condominioId: string, trecho: TrechoDocumento) =>
  abrirOriginal(caminhoOriginal(condominioId, trecho), { pagina: paginaDoPdf(trecho), tipo: "application/pdf" });

/** Baixa o original com o nome com que foi enviado. Ação explícita do usuário. */
export const baixarOriginal = (condominioId: string, trecho: TrechoDocumento) =>
  baixarArquivo(caminhoOriginal(condominioId, trecho), trecho.nomeArquivo);
