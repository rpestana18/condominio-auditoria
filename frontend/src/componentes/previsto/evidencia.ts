import type { PrevistoRealizado } from "../../api/tipos";

/** O que o painel de evidência deve mostrar: o alvo da API e um título para a pessoa. */
export interface AlvoEvidencia {
  /** "line:<id>", "group:<id>", "total", "fund:<id>", ADJUSTMENTS, TO_REALLOCATE, NO_BUDGET_LINE ou TRANSFERS */
  alvo: string;
  titulo: string;
  /** Quando o alvo é uma linha da PO: página e observações impressas, para abrir a PO no lugar certo. */
  linhaPo?: { pagina: number; observacoes?: string | null };
}

export type AbrirEvidencia = (alvo: AlvoEvidencia) => void;

const titulosFixos: Record<string, string> = {
  total: "Despesa realizada",
  ADJUSTMENTS: "Ajustes (não são despesa)",
  TO_REALLOCATE: "A realocar",
  NO_BUDGET_LINE: "Sem linha da PO",
  TRANSFERS: "Transferências entre fundos",
};

/**
 * Monta o alvo do painel a partir do texto que veio no endereço (?alvo=line:<id>), usado quando outra tela
 * (ex.: "Comparar exercícios") abre o previsto × realizado direto na evidência. Só procura o título e a página
 * no resultado que a API já devolveu; o alvo vai para a API sem mudança.
 */
export function alvoDoEndereco(resultado: PrevistoRealizado, alvo: string): AlvoEvidencia {
  const [tipo, id] = alvo.split(":");
  if (tipo === "line") {
    for (const grupo of resultado.groups) {
      const linha = grupo.lines.find((l) => l.lineId === id);
      if (linha) {
        return {
          alvo,
          titulo: `${linha.code} ${linha.description}`,
          linhaPo: { pagina: linha.page, observacoes: linha.notes },
        };
      }
    }
  }
  if (tipo === "group") {
    const grupo = resultado.groups.find((g) => g.lineId === id);
    if (grupo) return { alvo, titulo: `${grupo.code} ${grupo.description}` };
  }
  if (tipo === "fund") {
    const fundo = resultado.funds.find((f) => f.fundId === id);
    if (fundo) return { alvo, titulo: fundo.lineCode ? `${fundo.lineCode} ${fundo.fund ?? ""}` : (fundo.fund ?? "Fundo") };
  }
  return { alvo, titulo: titulosFixos[alvo] ?? "Lançamentos" };
}
