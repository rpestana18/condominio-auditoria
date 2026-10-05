/** O que o painel de evidência deve mostrar: o alvo da API e um título para a pessoa. */
export interface AlvoEvidencia {
  /** "linha:<id>", "fundo:<id>", AJUSTES, A_REALOCAR, SEM_LINHA_PO ou TRANSFERENCIAS */
  alvo: string;
  titulo: string;
  /** Quando o alvo é uma linha da PO: página e observações impressas, para abrir a PO no lugar certo. */
  linhaPo?: { pagina: number; observacoes?: string | null };
}

export type AbrirEvidencia = (alvo: AlvoEvidencia) => void;
