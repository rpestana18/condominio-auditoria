// Screen texts for the API codes. Labels only: no rule lives here.
import type {
  Finding,
  FindingStatus,
  AccountMappingStatus,
  BudgetStatus,
  BudgetItemStatus,
  BudgetItemEvent,
  LineMark,
  AccountMappingSource,
  BudgetItemSource,
  BudgetVsActual,
  MappingTargetType,
  SpendingProfile,
  SpendingProfileStatus,
  PartialMonthEvent,
} from "../../api/types";

export const budgetStatusLabel: Record<BudgetStatus, string> = {
  READ: "Lida, aguarda confirmação",
  READ_WITH_DISCREPANCY: "Lida com divergência",
  CONFIRMED: "Confirmada",
  SUPERSEDED: "Substituída",
};

export const markLabel: Record<LineMark, string> = {
  SEPARATE_APPORTIONMENT: "rateio à parte",
  NEGOTIATED_EXEMPTION: "negociada isenção",
  NO_AMOUNT: "sem valor",
  FIXED_AMOUNT_NO_REFERENCE: "valor fixo (sem referência)",
};

export const targetTypeLabel: Record<MappingTargetType, string> = {
  BUDGET_LINE: "Linha da PO",
  ADJUSTMENT: "Ajuste (não é despesa)",
  TO_REALLOCATE: "Meio de pagamento (a realocar)",
  TRANSFER: "Transferência entre fundos",
  WITHOUT_LINE: "Sem linha da PO (decisão do Admin)",
};

export const accountMappingStatusLabel: Record<AccountMappingStatus, string> = {
  AUTOMATIC: "Automático",
  SUGGESTED: "Automático",
  CONFIRMED: "Confirmado",
  REJECTED: "Recusado",
};

export const sourceLabel: Record<AccountMappingSource, string> = {
  PREVIOUS_VERSION: "automático: versão anterior",
  SPREADSHEET: "automático: planilha guardada",
  BUDGET_SHEET: "automático: planilha de apoio da PO",
  NAME: "automático: nome",
  ADMIN: "escolha do Admin",
};

export const fundStatusLabel: Record<BudgetVsActual["funds"][number]["status"], string> = {
  COMPARED: "Comparado com a PO",
  NOT_PLANNED_IN_BUDGET: "Sem previsto na PO",
  LINE_WITHOUT_FUND: "Linha sem fundo ligado",
  REPROCESS_CASH_FLOW: "Reprocesse o fluxo para ver a arrecadação",
};

export const severityLabel: Record<Finding["severity"], string> = {
  INFO: "informativo",
  WARNING: "atenção",
  CRITICAL: "crítico",
};

export const findingStatusLabel: Record<FindingStatus, string> = {
  OPEN: "aberto",
  NO_LONGER_APPLIES: "não se aplica mais",
  JUSTIFIED: "justificado",
  RESOLVED: "resolvido",
  FALSE_POSITIVE: "falso positivo",
};

// Budget items (RF-11.7)
export const budgetItemStatusLabel: Record<BudgetItemStatus, string> = {
  SUGGESTED: "Sugerido",
  CONFIRMED: "Confirmado",
  REJECTED: "Recusado",
};

export const budgetItemSourceLabel: Record<BudgetItemSource, string> = {
  FIRST_BUDGET: "primeira PO do condomínio",
  BUDGET_ACCOUNT: "mesma conta da PO e mesmo grupo",
  PREVIOUS_VERSION: "automático: versão anterior",
  MANUAL: "escolha do Admin",
};

export const budgetItemActionLabel: Record<BudgetItemEvent["action"], string> = {
  CREATED: "rubrica criada",
  RENAMED: "rubrica renomeada",
  SUGGESTED: "sugerido",
  CONFIRMED: "confirmado",
  REJECTED: "recusado",
  CHANGED: "alterado",
};

// Spending profile of the line (RF-13.14, ADR 0009) and partial month (RF-13.10)
export const spendingProfileLabel: Record<SpendingProfile, string> = {
  RECURRING: "recorrente",
  OCCASIONAL: "eventual",
  SEASONAL: "sazonal",
  ANNUAL: "anual",
  PROVISION: "provisão",
  SEPARATE_APPORTIONMENT: "rateio à parte",
};

/** Profiles compared over the fiscal year, not month by month (O5, O6, D4): the line balance is the main number. */
export const YEARLY_PROFILES: ReadonlySet<SpendingProfile> = new Set<SpendingProfile>(["OCCASIONAL", "SEASONAL", "ANNUAL"]);

export const spendingProfileStatusLabel: Record<SpendingProfileStatus, string> = {
  SUGGESTED: "sugerido",
  CONFIRMED: "confirmado",
};

export const partialMonthActionLabel: Record<PartialMonthEvent["action"], string> = {
  MARKED_PARTIAL: "marcado como parcial",
  MARKED_COMPLETE: "marcado como completo",
  AUTOMATIC: "voltou à regra automática",
  SUPERSEDED: "substituído pelo extrato completo",
};
