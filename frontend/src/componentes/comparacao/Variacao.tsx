import type { VariacaoExercicio } from "../../api/tipos";
import { formatarDiferenca, formatarPercentualComSinal } from "../../formato";

/**
 * Variação contra o exercício anterior da lista, já calculada pelo backend: R$ e %.
 * Base zero vem marcada como "nova no exercício", sem percentual. Sem variação (exercício mais antigo), "—".
 * Cor neutra de propósito: a variação é um fato, não um alerta.
 */
export function Variacao({ variacao }: { variacao: VariacaoExercicio | null | undefined }) {
  if (!variacao) return <span className="discreto">—</span>;
  return (
    <>
      {formatarDiferenca(variacao.valor)}
      {variacao.novaNoExercicio ? (
        <small className="observacao">nova no exercício</small>
      ) : (
        variacao.percentual !== null &&
        variacao.percentual !== undefined && <small className="observacao">{formatarPercentualComSinal(variacao.percentual)}</small>
      )}
    </>
  );
}
