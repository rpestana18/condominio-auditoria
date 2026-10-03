// Formatação no padrão brasileiro: R$ 1.234,56 e dd/mm/aaaa.

const moeda = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const moedaCurta = new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 });

export const formatarMoeda = (valor: number) => moeda.format(valor);

/** Para eixos de gráfico: "R$ 120 mil". */
export const formatarMoedaCurta = (valor: number) => `R$ ${moedaCurta.format(valor)}`;

/** "2026-09-30" vira "30/09/2026", sem passar por fuso horário. */
export function formatarData(iso: string | null | undefined): string {
  if (!iso) return "";
  const [ano, mes, dia] = iso.slice(0, 10).split("-");
  return `${dia}/${mes}/${ano}`;
}

export function formatarDataHora(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export function formatarPeriodo(inicio?: string | null, fim?: string | null): string {
  if (!inicio || !fim) return "";
  const mes = new Date(`${inicio}T12:00:00`).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  return inicio.slice(0, 7) === fim.slice(0, 7) ? mes : `${formatarData(inicio)} a ${formatarData(fim)}`;
}

export function formatarTamanho(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}
