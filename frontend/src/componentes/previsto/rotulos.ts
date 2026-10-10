// Textos da tela para os códigos da API. Só rótulos: nenhuma regra mora aqui.
import type {
  Achado,
  EstadoAchado,
  EstadoDepara,
  EstadoPrevisao,
  EstadoRubrica,
  EventoRubrica,
  MarcaLinha,
  OrigemDepara,
  OrigemRubrica,
  PrevistoRealizado,
  TipoDestino,
} from "../../api/tipos";

export const rotuloEstadoPo: Record<EstadoPrevisao, string> = {
  READ: "Lida, aguarda confirmação",
  READ_WITH_DISCREPANCY: "Lida com divergência",
  CONFIRMED: "Confirmada",
  SUPERSEDED: "Substituída",
};

export const rotuloMarca: Record<MarcaLinha, string> = {
  SEPARATE_APPORTIONMENT: "rateio à parte",
  NEGOTIATED_EXEMPTION: "negociada isenção",
  NO_AMOUNT: "sem valor",
  FIXED_AMOUNT_NO_REFERENCE: "valor fixo (sem referência)",
};

export const rotuloTipoDestino: Record<TipoDestino, string> = {
  BUDGET_LINE: "Linha da PO",
  ADJUSTMENT: "Ajuste (não é despesa)",
  TO_REALLOCATE: "Meio de pagamento (a realocar)",
  TRANSFER: "Transferência entre fundos",
};

export const rotuloEstadoDepara: Record<EstadoDepara, string> = {
  SUGGESTED: "Sugerido",
  CONFIRMED: "Confirmado",
  REJECTED: "Recusado",
};

export const rotuloOrigem: Record<OrigemDepara, string> = {
  PREVIOUS_VERSION: "versão anterior",
  SPREADSHEET: "planilha",
  NAME: "pelo nome",
  ADMIN: "escolha do Admin",
};

export const rotuloSituacaoFundo: Record<PrevistoRealizado["funds"][number]["status"], string> = {
  COMPARED: "Comparado com a PO",
  NOT_PLANNED_IN_BUDGET: "Sem previsto na PO",
  LINE_WITHOUT_FUND: "Linha sem fundo ligado",
  REPROCESS_CASH_FLOW: "Reprocesse o fluxo para ver a arrecadação",
};

export const rotuloSeveridade: Record<Achado["severity"], string> = {
  INFO: "informativo",
  WARNING: "atenção",
  CRITICAL: "crítico",
};

export const rotuloEstadoAchado: Record<EstadoAchado, string> = {
  OPEN: "aberto",
  NO_LONGER_APPLIES: "não se aplica mais",
  JUSTIFIED: "justificado",
  RESOLVED: "resolvido",
  FALSE_POSITIVE: "falso positivo",
};

// Rubricas (RF-11.7)
export const rotuloEstadoRubrica: Record<EstadoRubrica, string> = {
  SUGGESTED: "Sugerido",
  CONFIRMED: "Confirmado",
  REJECTED: "Recusado",
};

export const rotuloOrigemRubrica: Record<OrigemRubrica, string> = {
  FIRST_BUDGET: "primeira PO do condomínio",
  BUDGET_ACCOUNT: "mesma conta da PO e mesmo grupo",
  PREVIOUS_VERSION: "versão anterior",
  MANUAL: "escolha do Admin",
};

export const rotuloAcaoRubrica: Record<EventoRubrica["action"], string> = {
  CREATED: "rubrica criada",
  RENAMED: "rubrica renomeada",
  SUGGESTED: "sugerido",
  CONFIRMED: "confirmado",
  REJECTED: "recusado",
  CHANGED: "alterado",
};
