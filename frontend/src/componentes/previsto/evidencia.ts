import type { PrevistoRealizado } from "../../api/tipos";

/** O que o painel de evidência deve mostrar: o alvo da API e um título para a pessoa. */
export interface AlvoEvidencia {
  /** "linha:<id>", "grupo:<id>", "total", "fundo:<id>", AJUSTES, A_REALOCAR, SEM_LINHA_PO ou TRANSFERENCIAS */
  alvo: string;
  titulo: string;
  /** Quando o alvo é uma linha da PO: página e observações impressas, para abrir a PO no lugar certo. */
  linhaPo?: { pagina: number; observacoes?: string | null };
}

export type AbrirEvidencia = (alvo: AlvoEvidencia) => void;

const titulosFixos: Record<string, string> = {
  total: "Despesa realizada",
  AJUSTES: "Ajustes (não são despesa)",
  A_REALOCAR: "A realocar",
  SEM_LINHA_PO: "Sem linha da PO",
  TRANSFERENCIAS: "Transferências entre fundos",
};

/**
 * Monta o alvo do painel a partir do texto que veio no endereço (?alvo=linha:<id>), usado quando outra tela
 * (ex.: "Comparar exercícios") abre o previsto × realizado direto na evidência. Só procura o título e a página
 * no resultado que a API já devolveu; o alvo vai para a API sem mudança.
 */
export function alvoDoEndereco(resultado: PrevistoRealizado, alvo: string): AlvoEvidencia {
  const [tipo, id] = alvo.split(":");
  if (tipo === "linha") {
    for (const grupo of resultado.grupos) {
      const linha = grupo.linhas.find((l) => l.linhaId === id);
      if (linha) {
        return {
          alvo,
          titulo: `${linha.codigo} ${linha.descricao}`,
          linhaPo: { pagina: linha.pagina, observacoes: linha.observacoes },
        };
      }
    }
  }
  if (tipo === "grupo") {
    const grupo = resultado.grupos.find((g) => g.linhaId === id);
    if (grupo) return { alvo, titulo: `${grupo.codigo} ${grupo.descricao}` };
  }
  if (tipo === "fundo") {
    const fundo = resultado.fundos.find((f) => f.fundoId === id);
    if (fundo) return { alvo, titulo: fundo.linhaCodigo ? `${fundo.linhaCodigo} ${fundo.fundo ?? ""}` : (fundo.fundo ?? "Fundo") };
  }
  return { alvo, titulo: titulosFixos[alvo] ?? "Lançamentos" };
}
