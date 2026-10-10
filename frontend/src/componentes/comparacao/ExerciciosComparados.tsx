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

const prefixoColuna = "column:";

/**
 * PO que imprimiu a coluna "Orçado anterior" de um exercício, quando houver conferência a mostrar (RF-11.5):
 * na coluna impressa, a própria PO do item; numa PO que substituiu a coluna, a do id "column:<uuid da PO>".
 */
function poDaColuna(comparado: ExercicioComparado, exercicios: Exercicio[]): string | null {
  if (comparado.type === "PRINTED_COLUMN") return comparado.budgetId;
  const substituida = exercicios.find((e) => e.type === comparado.type && e.budgetId === comparado.budgetId)?.printedColumn;
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
              {c.type === "PRINTED_COLUMN" ? "PO anterior pela coluna impressa" : c.version ? `PO versão ${c.version}` : "PO"}
            </span>
            <strong className="nome-fundo">{c.label}</strong>
            <span className="discreto">
              {formatarMes(c.start)} a {formatarMes(c.end)}
            </span>
            <span className="discreto">
              {c.type === "PRINTED_COLUMN"
                ? "Só previsto, sem realizado"
                : `Meses somados: ${c.months.map(formatarMes).join(", ") || "nenhum"}`}
            </span>
            {poColuna && (
              <button type="button" className="botao-link" onClick={() => aoConferirColuna(poColuna)}>
                {c.type === "PRINTED_COLUMN" ? "Conferir a coluna impressa" : "Ver a coluna impressa (conferência)"}
              </button>
            )}
          </div>
        );
      })}
    </div>
  );
}
