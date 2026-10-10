import type { Exercicio, PrevisaoResumo } from "../../api/tipos";
import { formatarMes } from "../../formato";
import { rotuloEstadoPo } from "./rotulos";

interface Props {
  /** Lista do GET /exercicios (do mais recente para o mais antigo). Aqui entram só os de tipo PO. */
  exercicios: Exercicio[];
  /** Todas as POs lidas: servem só para dar nome a uma versão que não está na lista de exercícios (ex.: link da tela "PO"). */
  previsoes?: PrevisaoResumo[];
  /** PO do exercício mostrado: a do endereço ou, sem ela, a que o backend usou (exercício vigente). */
  poId: string | null;
  aoTrocar: (poId: string) => void;
}

/**
 * Filtro "Exercício" (RF-11.4), usado no previsto × realizado e em "Indicadores". O valor é o `po` do
 * exercício, que vai para a API como está. A coluna impressa não tem realizado nem exercício próprio:
 * só aparece em "Comparar exercícios".
 */
export function SeletorExercicio({ exercicios, previsoes = [], poId, aoTrocar }: Props) {
  const comPo = exercicios.filter((e) => e.type === "PO");
  // Uma versão de PO que não está na lista (ex.: ainda não confirmada) continua podendo ser aberta pelo link
  const foraDaLista = poId && !comPo.some((e) => e.budgetId === poId) ? previsoes.find((p) => p.id === poId) : undefined;

  return (
    <label>
      Exercício
      <select value={poId ?? ""} onChange={(e) => aoTrocar(e.target.value)} disabled={comPo.length === 0 && !foraDaLista}>
        {comPo.length === 0 && !foraDaLista && <option value="">Nenhum exercício confirmado</option>}
        {comPo.map((e) => (
          <option key={e.id} value={e.budgetId}>
            {e.label}
            {e.version ? ` · versão ${e.version}` : ""}
            {e.extension ? ` · prorrogada até ${formatarMes(e.extension.until)}` : ""}
          </option>
        ))}
        {foraDaLista && (
          <option value={foraDaLista.id}>
            {foraDaLista.printedFiscalYear ?? foraDaLista.fileName} · {rotuloEstadoPo[foraDaLista.status]}
          </option>
        )}
      </select>
    </label>
  );
}
