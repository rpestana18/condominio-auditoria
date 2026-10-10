import type { BudgetVsActual } from "../../api/types";

/** What the evidence panel must show: the API target and a title for the person. */
export interface EvidenceTarget {
  /** "line:<id>", "group:<id>", "total", "fund:<id>", ADJUSTMENTS, TO_REALLOCATE, NO_BUDGET_LINE or TRANSFERS */
  target: string;
  title: string;
  /** When the target is a budget line: page and printed notes, to open the budget at the right place. */
  budgetLine?: { page: number; notes?: string | null };
}

export type OpenEvidence = (target: EvidenceTarget) => void;

const fixedTitles: Record<string, string> = {
  total: "Despesa realizada",
  ADJUSTMENTS: "Ajustes (não são despesa)",
  TO_REALLOCATE: "A realocar",
  NO_BUDGET_LINE: "Sem linha da PO",
  TRANSFERS: "Transferências entre fundos",
};

/**
 * Builds the panel target from the text that came in the address (?alvo=line:<id>), used when another screen
 * (e.g. "Comparar exercícios") opens budget vs actual straight on the evidence. Only looks up the title and the page
 * in the result the API already returned; the target goes to the API unchanged.
 */
export function targetFromAddress(result: BudgetVsActual, target: string): EvidenceTarget {
  const [type, id] = target.split(":");
  if (type === "line") {
    for (const group of result.groups) {
      const line = group.lines.find((l) => l.lineId === id);
      if (line) {
        return {
          target,
          title: `${line.code} ${line.description}`,
          budgetLine: { page: line.page, notes: line.notes },
        };
      }
    }
  }
  if (type === "group") {
    const group = result.groups.find((g) => g.lineId === id);
    if (group) return { target, title: `${group.code} ${group.description}` };
  }
  if (type === "fund") {
    const fund = result.funds.find((f) => f.fundId === id);
    if (fund) return { target, title: fund.lineCode ? `${fund.lineCode} ${fund.fund ?? ""}` : (fund.fund ?? "Fundo") };
  }
  return { target, title: fixedTitles[target] ?? "Lançamentos" };
}
