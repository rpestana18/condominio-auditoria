import type { Exercicio, ExercicioComparado } from "../../api/tipos";
import { formatarMes } from "../../formato";

interface Props {
  /** Exercícios que a API usou, do mais recente para o mais antigo. */
  comparados: ExercicioComparado[];
  /** Lista do GET /exercicios: diz se uma PO substituiu a coluna impressa (que fica só como conferência). */
  exercicios: Exercicio[];
  /** Abre a conferência da coluna "Orçado anterior" impressa na PO `poId`. */
  aoConferirColuna: (poId: string) => void;
}

const prefixoColuna = "coluna:";

/**
 * PO que imprimiu a coluna "Orçado anterior" de um exercício, quando houver conferência a mostrar (RF-11.5):
 * na coluna impressa, a própria PO do item; numa PO que substituiu a coluna, a do id "coluna:<uuid da PO>".
 */
function poDaColuna(comparado: ExercicioComparado, exercicios: Exercicio[]): string | null {
  if (comparado.tipo === "COLUNA_IMPRESSA") return comparado.poId;
  const substituida = exercicios.find((e) => e.tipo === comparado.tipo && e.poId === comparado.poId)?.colunaImpressa;
  return substituida?.startsWith(prefixoColuna) ? substituida.slice(prefixoColuna.length) : null;
}

/** Cartões com os exercícios comparados: período, meses somados e acesso à conferência da coluna impressa. */
export function ExerciciosComparados({ comparados, exercicios, aoConferirColuna }: Props) {
  return (
    <div className="cartoes">
      {comparados.map((c) => {
        const poColuna = poDaColuna(c, exercicios);
        return (
          <div key={c.id} className="cartao-numero">
            <span className="cartao-titulo">
              {c.tipo === "COLUNA_IMPRESSA" ? "PO anterior pela coluna impressa" : c.versao ? `PO versão ${c.versao}` : "PO"}
            </span>
            <strong className="nome-fundo">{c.rotulo}</strong>
            <span className="discreto">
              {formatarMes(c.inicio)} a {formatarMes(c.fim)}
            </span>
            <span className="discreto">
              {c.tipo === "COLUNA_IMPRESSA"
                ? "Só previsto, sem realizado"
                : `Meses somados: ${c.meses.map(formatarMes).join(", ") || "nenhum"}`}
            </span>
            {poColuna && (
              <button type="button" className="botao-link" onClick={() => aoConferirColuna(poColuna)}>
                {c.tipo === "COLUNA_IMPRESSA" ? "Conferir a coluna impressa" : "Ver a coluna impressa (conferência)"}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
