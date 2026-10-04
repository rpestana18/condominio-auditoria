import type { FundoFluxo, MesPrevistoRealizado, PrevisaoResumo } from "../../api/tipos";
import { formatarMes } from "../../formato";
import { rotuloEstadoPo } from "./rotulos";

interface Props {
  previsoes: PrevisaoResumo[];
  poId: string | null;
  aoTrocarPo: (id: string | null) => void;
  periodo: string;
  /** Os meses do exercício, vindos do acumulado (com a situação de cada um). */
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

/** Filtros do RF-03.1.13: versão da PO, mês ou acumulado, e fundo. */
export function FiltrosPrevisto(props: Props) {
  const { previsoes, poId, aoTrocarPo, periodo, meses, aoTrocarPeriodo, fundos, fundoId, aoTrocarFundo } = props;
  // O fundo ordinário (fundo Condomínio) vem primeiro; os demais, na ordem de nome da API
  const ordenados = [...fundos.filter((f) => f.ordinario), ...fundos.filter((f) => !f.ordinario)];
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
      <label>
        Fundo
        <select value={fundoId ?? ""} onChange={(e) => aoTrocarFundo(e.target.value || null)}>
          <option value="">Todos os fundos</option>
          {ordenados.map((f) => (
            <option key={f.id} value={f.id}>
              {f.ordinario ? `Fundo Condomínio (${f.nome})` : f.nome}
            </option>
          ))}
        </select>
      </label>
    </div>
  );
}
