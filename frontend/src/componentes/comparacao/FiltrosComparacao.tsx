import type { Exercicio, FundoFluxo } from "../../api/tipos";
import { formatarMes } from "../../formato";
import { SeletorFundo } from "../previsto/SeletorFundo";

interface Props {
  /** Lista do GET /exercicios, do mais recente para o mais antigo, inclusive a coluna impressa. */
  exercicios: Exercicio[];
  /** Ids marcados: os escolhidos pela pessoa ou, sem escolha, os que o backend usou (os dois mais recentes). */
  marcados: string[];
  /** Recebe a nova lista na ordem da lista de exercícios. Nunca menos de dois. */
  aoTrocarExercicios: (ids: string[]) => void;
  /** Há escolha da pessoa no endereço (mostra "voltar ao padrão"). */
  escolhaPropria: boolean;
  aoVoltarAoPadrao: () => void;
  fundos: FundoFluxo[];
  fundoId: string | null;
  aoTrocarFundo: (fundoId: string | null) => void;
  mesmosMeses: boolean;
  aoTrocarMesmosMeses: (marcado: boolean) => void;
  /** Texto pronto da API (ex.: "comparando: setembro"), só com "mesmos meses". */
  comparando: string | null | undefined;
}

/** Filtros do RF-11.6: exercícios (dois ou mais), fundo e "mesmos meses". */
export function FiltrosComparacao(props: Props) {
  const { exercicios, marcados, aoTrocarExercicios, escolhaPropria, aoVoltarAoPadrao } = props;
  const conjunto = new Set(marcados);

  const alternar = (id: string) => {
    const novo = exercicios.filter((e) => (e.id === id ? !conjunto.has(id) : conjunto.has(e.id))).map((e) => e.id);
    if (novo.length >= 2) aoTrocarExercicios(novo);
  };

  return (
    <div className="filtros">
      <fieldset className="filtro-exercicios">
        <legend>Exercícios (dois ou mais)</legend>
        <div className="lista-opcoes">
          {exercicios.map((e) => {
            const marcado = conjunto.has(e.id);
            return (
              <label key={e.id}>
                <input
                  type="checkbox"
                  checked={marcado}
                  // Com só dois marcados, nenhum pode sair: a comparação precisa de dois
                  disabled={marcado && marcados.length <= 2}
                  onChange={() => alternar(e.id)}
                />
                {e.rotulo}
                <span className="discreto">
                  {formatarMes(e.inicio)} a {formatarMes(e.fim)}
                  {e.tipo === "COLUNA_IMPRESSA" && " · só previsto"}
                </span>
              </label>
            );
          })}
        </div>
        {escolhaPropria && (
          <button type="button" className="botao-link" onClick={aoVoltarAoPadrao}>
            Voltar aos dois mais recentes
          </button>
        )}
      </fieldset>
      <SeletorFundo fundos={props.fundos} fundoId={props.fundoId} aoTrocar={props.aoTrocarFundo} />
      <div className="mesmos-meses">
        <label className="campo-linha">
          <input type="checkbox" checked={props.mesmosMeses} onChange={(e) => props.aoTrocarMesmosMeses(e.target.checked)} />
          Mesmos meses (só os meses com fluxo em todos os exercícios)
        </label>
        {props.mesmosMeses && props.comparando && <span className="selo neutro">{props.comparando}</span>}
      </div>
    </div>
  );
}
