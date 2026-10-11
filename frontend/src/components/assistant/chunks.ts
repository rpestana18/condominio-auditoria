import { openOriginal, downloadFile } from "../../api/client";
import type { DocumentChunk } from "../../api/types";

/** Endpoint of the original, exactly as uploaded (the same as the "Abrir original" button on the Files screen). */
export const originalPath = (condominiumId: string, chunk: DocumentChunk) =>
  `/condominiums/${condominiumId}/files/${chunk.fileId}/content`;

/** PDF opens in the browser on the cited page; Excel and Word have no "page", so we show the chunk and the download. */
export const isPdf = (chunk: DocumentChunk) => chunk.fileName.toLowerCase().endsWith(".pdf");

/** Page for `#page=N`, only for a PDF located by page. */
export const pdfPage = (chunk: DocumentChunk): number | null =>
  isPdf(chunk) && chunk.location.type === "PAGE" ? (chunk.location.page ?? null) : null;

const range = (start?: number | null, end?: number | null) =>
  start == null ? "" : end == null || end === start ? `${start}` : `${start}–${end}`;

/** "p. 4", "aba Junho, linhas 10–14", "parágrafos 3–5". Without the fields, uses the API's ready-made description. */
export function formatLocation(chunk: DocumentChunk): string {
  const l = chunk.location;
  if (l.type === "PAGE" && l.page != null) return `p. ${l.page}`;
  if (l.type === "SHEET" && l.sheet) {
    const rows = range(l.startRow, l.endRow);
    const plural = l.endRow != null && l.endRow !== l.startRow;
    return rows ? `aba ${l.sheet}, ${plural ? "linhas" : "linha"} ${rows}` : `aba ${l.sheet}`;
  }
  if (l.type === "PARAGRAPHS" && l.startParagraph != null) {
    const plural = l.endParagraph != null && l.endParagraph !== l.startParagraph;
    const paragraphs = `${plural ? "parágrafos" : "parágrafo"} ${range(l.startParagraph, l.endParagraph)}`;
    return l.section ? `${l.section}, ${paragraphs}` : paragraphs;
  }
  return l.description;
}

/** Opens the PDF on the cited page, in a new tab (RF-04.9). */
export const openOnPage = (condominiumId: string, chunk: DocumentChunk) =>
  openOriginal(originalPath(condominiumId, chunk), { page: pdfPage(chunk), type: "application/pdf" });

/** Downloads the original with the name it was uploaded with. Explicit user action. */
export const downloadOriginal = (condominiumId: string, chunk: DocumentChunk) =>
  downloadFile(originalPath(condominiumId, chunk), chunk.fileName);
