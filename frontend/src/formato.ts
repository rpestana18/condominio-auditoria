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

const meses = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

/** "2026-09" vira "09/2026". "acumulado" continua "acumulado". */
export function formatarMes(aaaamm: string): string {
  const [ano, mes] = aaaamm.split("-");
  return mes ? `${mes}/${ano}` : aaaamm;
}

/** "2026-09" vira "set/26" (eixos de gráfico). */
export function formatarMesCurto(aaaamm: string): string {
  const [ano, mes] = aaaamm.split("-");
  return mes ? `${meses[Number(mes) - 1]}/${ano.slice(2)}` : aaaamm;
}

/** Percentual que já vem calculado da API (98.8 vira "98,8%"). Nulo vira "—" (ex.: previsto zero). */
export function formatarPercentual(valor: number | null | undefined): string {
  if (valor === null || valor === undefined) return "—";
  return `${valor.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
}

/** Diferença com sinal explícito: "+R$ 6.793,38" ou "-R$ 880,00". */
export function formatarDiferenca(valor: number): string {
  return valor > 0 ? `+${moeda.format(valor)}` : moeda.format(valor);
}

/** Classe de cor da diferença: acima do previsto ganha só um destaque de atenção (é indício, não conclusão). */
export const classeDiferenca = (valor: number) => (valor > 0 ? "acima" : "");

/** Início do hash, para mostrar sem ocupar a linha toda (o completo vai no title). */
export const hashCurto = (sha256: string) => sha256.slice(0, 12);
