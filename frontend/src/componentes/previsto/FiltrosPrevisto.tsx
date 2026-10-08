import type { Exercicio, FundoFluxo, MesPrevistoRealizado, PrevisaoResumo } from "../../api/tipos";
import { formatarMes } from "../../formato";
import { rotuloEstadoPo } from "./rotulos";
import { SeletorFundo } from "./SeletorFundo";

interface Props {
  /** Lista do GET /exercicios (do mais recente para o mais antigo). Aqui entram só os de tipo PO. */
  exercicios: Exercicio[];
  /** Todas as POs lidas: servem só para dar nome a uma versão que não está na lista de exercícios (ex.: link da tela "PO"). */
  previsoes: PrevisaoResumo[];
  /** PO do exercício mostrado: a do endereço ou, sem ela, a que o backend usou (exercício vigente). */
  poId: string | null;
  aoTrocarPo: (id: string) => void;
  periodo: string;
  /** Os meses do exercício, vindos do acumulado (com a situação de cada um e a marca de prorrogado). */
  meses: MesPrevistoRealizado[];
  aoTrocarPeriodo: (periodo: string) => void;
  /** Fundos do fluxo (GET /fundos). O filtro vai para a API; a tela não separa nada sozinha. */
  fundos: FundoFluxo[];
  fundoId: string | null;
  aoTrocarFundo: (fundoId: string | null) => void;
}

const situacaoMes: Record<MesPrevistoRealizado["situacao"], string> = {
  COM_FLUXO: "",
  SEM_FLUXO: " (sem fluxo carregado)",
  DOIS_FLUXOS: " (dois fluxos)",
};

/** Filtros do RF-03.1.13 e do RF-11.4: exercício, mês ou acumulado, e fundo. */
export function FiltrosPrevisto(props: Props) {
  const { exercicios, previsoes, poId, aoTrocarPo, periodo, meses, aoTrocarPeriodo, fundos, fundoId, aoTrocarFundo } = props;
  // A coluna impressa não tem realizado nem exercício próprio: só aparece em "Comparar exercícios"
  const comPo = exercicios.filter((e) => e.tipo === "PO");
  // Uma versão de PO que não está na lista (ex.: ainda não confirmada) continua podendo ser aberta pelo link
  const foraDaLista = poId && !comPo.some((e) => e.poId === poId) ? previsoes.find((p) => p.id === poId) : undefined;

  return (
    <div className="filtros">
      <label>
        Exercício
        <select value={poId ?? ""} onChange={(e) => aoTrocarPo(e.target.value)} disabled={comPo.length === 0 && !foraDaLista}>
          {comPo.length === 0 && !foraDaLista && <option value="">Nenhum exercício confirmado</option>}
          {comPo.map((e) => (
            <option key={e.id} value={e.poId}>
              {e.rotulo}
              {e.versao ? ` · versão ${e.versao}` : ""}
              {e.prorrogacao ? ` · prorrogada até ${formatarMes(e.prorrogacao.ate)}` : ""}
            </option>
          ))}
          {foraDaLista && (
            <option value={foraDaLista.id}>
              {foraDaLista.exercicioImpresso ?? foraDaLista.arquivoNome} · {rotuloEstadoPo[foraDaLista.estado]}
            </option>
          )}
        </select>
      </label>
      <label>
        Período
        <select value={periodo} onChange={(e) => aoTrocarPeriodo(e.target.value)}>
          <option value="acumulado">Acumulado do exercício</option>
          {meses.map((m) => (
            <option key={m.mes} value={m.mes}>
              {formatarMes(m.mes)}
              {situacaoMes[m.situacao]}
              {m.prorrogado ? " · prorrogado" : ""}
            </option>
          ))}
        </select>
      </label>
      <SeletorFundo fundos={fundos} fundoId={fundoId} aoTrocar={aoTrocarFundo} />
    </div>
  );
}
