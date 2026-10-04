// Textos da tela para os códigos da API. Só rótulos: nenhuma regra mora aqui.
import type { EstadoDepara, EstadoPrevisao, MarcaLinha, OrigemDepara, PrevistoRealizado, TipoDestino } from "../../api/tipos";

export const rotuloEstadoPo: Record<EstadoPrevisao, string> = {
  LIDA: "Lida, aguarda confirmação",
  LIDA_COM_DIVERGENCIA: "Lida com divergência",
  CONFIRMADA: "Confirmada",
  SUBSTITUIDA: "Substituída",
};

export const rotuloMarca: Record<MarcaLinha, string> = {
  RATEIO_A_PARTE: "rateio à parte",
  NEGOCIADA_ISENCAO: "negociada isenção",
  SEM_VALOR: "sem valor",
  VALOR_FIXO_SEM_REFERENCIA: "valor fixo (sem referência)",
};

export const rotuloTipoDestino: Record<TipoDestino, string> = {
  LINHA_PO: "Linha da PO",
  AJUSTE: "Ajuste (não é despesa)",
  A_REALOCAR: "Meio de pagamento (a realocar)",
  TRANSFERENCIA: "Transferência entre fundos",
};

export const rotuloEstadoDepara: Record<EstadoDepara, string> = {
  SUGERIDO: "Sugerido",
  CONFIRMADO: "Confirmado",
  RECUSADO: "Recusado",
};

export const rotuloOrigem: Record<OrigemDepara, string> = {
  VERSAO_ANTERIOR: "versão anterior",
  PLANILHA: "planilha",
  NOME: "pelo nome",
  ADMIN: "escolha do Admin",
};

export const rotuloSituacaoFundo: Record<PrevistoRealizado["fundos"][number]["situacao"], string> = {
  COMPARADO: "Comparado com a PO",
  SEM_PREVISTO_NA_PO: "Sem previsto na PO",
  LINHA_SEM_FUNDO: "Linha sem fundo ligado",
  REPROCESSAR_FLUXO: "Reprocesse o fluxo para ver a arrecadação",
};
