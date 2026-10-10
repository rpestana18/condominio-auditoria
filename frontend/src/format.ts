// Brazilian formatting: R$ 1.234,56 and dd/mm/aaaa.

const money = new Intl.NumberFormat("pt-BR", { style: "currency", currency: "BRL" });
const moneyShort = new Intl.NumberFormat("pt-BR", { notation: "compact", maximumFractionDigits: 1 });

export const formatMoney = (value: number) => money.format(value);

/** For chart axes: "R$ 120 mil". */
export const formatMoneyShort = (value: number) => `R$ ${moneyShort.format(value)}`;

/** "2026-09-30" becomes "30/09/2026", without going through a time zone. */
export function formatDate(iso: string | null | undefined): string {
  if (!iso) return "";
  const [year, month, day] = iso.slice(0, 10).split("-");
  return `${day}/${month}/${year}`;
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("pt-BR", { dateStyle: "short", timeStyle: "short" });
}

export function formatPeriod(start?: string | null, end?: string | null): string {
  if (!start || !end) return "";
  const month = new Date(`${start}T12:00:00`).toLocaleDateString("pt-BR", { month: "long", year: "numeric" });
  return start.slice(0, 7) === end.slice(0, 7) ? month : `${formatDate(start)} a ${formatDate(end)}`;
}

export function formatSize(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / 1024 / 1024).toFixed(1).replace(".", ",")} MB`;
}

const monthAbbreviations = ["jan", "fev", "mar", "abr", "mai", "jun", "jul", "ago", "set", "out", "nov", "dez"];

/** "2026-09" becomes "09/2026". The API's "cumulative" period becomes "acumulado". */
export function formatMonth(yearMonth: string): string {
  if (yearMonth === "cumulative") return "acumulado";
  const [year, month] = yearMonth.split("-");
  return month ? `${month}/${year}` : yearMonth;
}

/** "2026-09" becomes "set/26" (chart axes). */
export function formatMonthShort(yearMonth: string): string {
  const [year, month] = yearMonth.split("-");
  return month ? `${monthAbbreviations[Number(month) - 1]}/${year.slice(2)}` : yearMonth;
}

/** Percentage already calculated by the API (98.8 becomes "98,8%"). Null becomes "—" (e.g. zero planned). */
export function formatPercentage(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return `${value.toLocaleString("pt-BR", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}%`;
}

/** Difference with an explicit sign: "+R$ 6.793,38" or "-R$ 880,00". */
export function formatDifference(value: number): string {
  return value > 0 ? `+${money.format(value)}` : money.format(value);
}

/** Color class of the difference: above planned only gets an attention highlight (it is an indication, not a conclusion). */
export const differenceClass = (value: number) => (value > 0 ? "above" : "");

/** Start of the hash, to show it without taking the whole line (the full one goes in the title). */
export const shortHash = (sha256: string) => sha256.slice(0, 12);
const integer = new Intl.NumberFormat("pt-BR", { maximumFractionDigits: 0 });

/** 12345 becomes "12.345". */
export const formatInteger = (value: number) => integer.format(value);

/** "2026-10" becomes "out/2026". */
export function formatMonthAbbreviated(yearMonth: string | undefined): string {
  if (!yearMonth) return "";
  const [year, month] = yearMonth.split("-");
  const name = new Date(Number(year), Number(month) - 1, 15).toLocaleDateString("pt-BR", { month: "short" }).replace(".", "");
  return `${name}/${year}`;
}

/** Today's date in the browser's time zone, as YYYY-MM-DD (the format of <input type="date"> and of the API). */
export function todayIso(): string {
  const today = new Date();
  const twoDigits = (n: number) => String(n).padStart(2, "0");
  return `${today.getFullYear()}-${twoDigits(today.getMonth() + 1)}-${twoDigits(today.getDate())}`;
}

/**
 * "1234.50" becomes "US$ 1.234,50" by touching only the text: the API's exact decimal value never goes through
 * a floating-point number (money must not lose cents).
 */
export function formatDollarText(decimal: string): string {
  const negative = decimal.startsWith("-");
  const [integers, cents = ""] = (negative ? decimal.slice(1) : decimal).split(".");
  const grouped = integers.replace(/\B(?=(\d{3})+(?!\d))/g, ".");
  return `${negative ? "-" : ""}US$ ${grouped},${cents.padEnd(2, "0")}`;
}

/** Amount in reais that may be missing (e.g. fiscal year without actuals): null becomes "—", never "R$ 0,00". */
export function formatMoneyOrDash(value: number | null | undefined): string {
  return value === null || value === undefined ? "—" : money.format(value);
}

/** Percentage already calculated, with an explicit sign: 7.7 becomes "+7,7%", -3.5 becomes "-3,5%". Null becomes "—". */
export function formatSignedPercentage(value: number | null | undefined): string {
  if (value === null || value === undefined) return "—";
  return value > 0 ? `+${formatPercentage(value)}` : formatPercentage(value);
}
