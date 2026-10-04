import type { MesPrevistoRealizado, PrevisaoResumo } from "../../api/tipos";
import { formatarMes } from "../../formato";
import { rotuloEstadoPo } from "./rotulos";

export type VisaoFundo = "CONDOMINIO" | "DEMAIS";

interface Props {
  previsoes: PrevisaoResumo[];
  poId: string | null;
  aoTrocarPo: (id: string | null) => void;
  periodo: string;
  /** Os meses do exercício, vindos do acumulado (com a situação de cada um). */
  meses: MesPrevistoRealizado[];
  aoTrocarPeriodo: (periodo: string) => void;
  fundo: VisaoFundo;
  aoTrocarFundo: (fundo: VisaoFundo) => void;
}

const situacaoMes: Record<MesPrevistoRealizado["situacao"], string> = {
  COM_FLUXO: "",
  SEM_FLUXO: " (sem fluxo carregado)",
  DOIS_FLUXOS: " (dois fluxos)",
};

/** Filtros do RF-03.1.13: versão da PO, mês ou acumulado, e fundo. */
export function FiltrosPrevisto(props: Props) {
  const { previsoes, poId, aoTrocarPo, periodo, meses, aoTrocarPeriodo, fundo, aoTrocarFundo } = props;
  return (
    <div className="filtros">
      <label>
        PO
        <select value={poId ?? ""} onChange={(e) => aoTrocarPo(e.target.value || null)}>
          <option value="">A que vale no período</option>
          {previsoes.map((p) => (
            <option key={p.id} value={p.id}>
              {p.versao ? `Versão ${p.versao}` : "Sem versão"} · {p.exercicioImpresso ?? p.arquivoNome} · {rotuloEstadoPo[p.estado]}
            </option>
          ))}
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
            </option>
          ))}
        </select>
      </label>
      <div className="abas" role="tablist" aria-label="Fundo">
        <button role="tab" aria-selected={fundo === "CONDOMINIO"} onClick={() => aoTrocarFundo("CONDOMINIO")}>
          Fundo Condomínio
        </button>
        <button role="tab" aria-selected={fundo === "DEMAIS"} onClick={() => aoTrocarFundo("DEMAIS")}>
          Reserva, obras e demais fundos
        </button>
      </div>
    </div>
  );
}
