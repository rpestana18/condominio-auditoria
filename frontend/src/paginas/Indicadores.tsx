import { useSearchParams } from "react-router";
import { useExercicios, useIndicadores } from "../api/consultasAnalisePo";
import { useFundos } from "../api/consultasOrcamento";
import type { Indicadores as SeriesIndicadores } from "../api/tipos";
import { NaoSeAplica } from "../componentes/indicadores/CartaoIndicador";
import { useAbrirNoPrevisto, type ContextoGrafico } from "../componentes/indicadores/contexto";
import { GraficoAcumulado } from "../componentes/indicadores/GraficoAcumulado";
import { GraficoEntreExercicios } from "../componentes/indicadores/GraficoEntreExercicios";
import { GraficoExecucaoMensal } from "../componentes/indicadores/GraficoExecucaoMensal";
import { GraficoFundos } from "../componentes/indicadores/GraficoFundos";
import { GraficoMaioresDiferencas } from "../componentes/indicadores/GraficoMaioresDiferencas";
import { GraficoRealizadoPorGrupo } from "../componentes/indicadores/GraficoRealizadoPorGrupo";
import { GraficoRegra20 } from "../componentes/indicadores/GraficoRegra20";
import { SeletorExercicio } from "../componentes/previsto/SeletorExercicio";
import { SeletorFundo } from "../componentes/previsto/SeletorFundo";
import { useSessao } from "../contexto";
import { formatarMes } from "../formato";

/**
 * Tela "Indicadores" (RF-11.10 a RF-11.13), para todos os perfis. Os filtros ficam no endereço
 * (?po=<uuid>&fundo=<uuid>); sem `po`, o backend usa o exercício mais recente. Os 7 gráficos só desenham
 * as séries do GET /indicadores: nenhum número é calculado aqui.
 */
export function Indicadores() {
  const { condominioId } = useSessao();
  const [parametros, setParametros] = useSearchParams();
  const poId = parametros.get("po");
  const fundoId = parametros.get("fundo");

  const { data: exercicios = [] } = useExercicios(condominioId);
  const { data: fundos = [] } = useFundos(condominioId);
  const consulta = useIndicadores(condominioId, poId, fundoId);

  const trocar = (nome: string, valor: string | null) =>
    setParametros((atual) => {
      const novo = new URLSearchParams(atual);
      if (valor) novo.set(nome, valor);
      else novo.delete(nome);
      return novo;
    });

  return (
    <>
      <header className="titulo-pagina">
        <h1>Indicadores</h1>
      </header>
      <div className="filtros">
        {/* Sem `po` no endereço, o exercício mostrado é o que a API escolheu (o mais recente) */}
        <SeletorExercicio exercicios={exercicios} poId={poId ?? consulta.data?.poId ?? null} aoTrocar={(id) => trocar("po", id)} />
        <SeletorFundo fundos={fundos} fundoId={fundoId} aoTrocar={(f) => trocar("fundo", f)} />
      </div>

      {consulta.isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : consulta.error ? (
        <p className="aviso erro">{consulta.error.message}</p>
      ) : consulta.data ? (
        <Graficos indicadores={consulta.data} fundoId={fundoId} />
      ) : (
        <p className="aviso">Nenhum exercício confirmado para mostrar indicadores.</p>
      )}
    </>
  );
}

interface PropsGraficos {
  indicadores: SeriesIndicadores;
  fundoId: string | null;
}

/** Os 7 gráficos do RF-11.11. Série nula = não se aplica ao fundo escolhido (a API decide). */
function Graficos({ indicadores: ind, fundoId }: PropsGraficos) {
  const abrir = useAbrirNoPrevisto();
  const contexto: ContextoGrafico = {
    poId: ind.poId,
    fundoId,
    periodo: `${formatarMes(ind.inicio)} a ${formatarMes(ind.fim)} (${ind.rotulo})`,
    dadosDe: ind.dadosDe,
    abrir,
  };
  // Gráficos 1 a 5 são do fundo Condomínio: com outro fundo no filtro, a API manda as séries nulas
  const semCondominio = !ind.execucaoMensal && !ind.regra20 && !ind.acumulado && !ind.realizadoPorGrupo && !ind.maioresDiferencas;

  return (
    <>
      <p className="discreto">
        Exercício {ind.rotulo}: {formatarMes(ind.inicio)} a {formatarMes(ind.fim)}. Os números são os mesmos da tela "Previsto ×
        realizado"; clique num valor para abri-lo lá, com os lançamentos de origem.
      </p>
      {ind.avisos.length > 0 && (
        <ul className="avisos-discretos" aria-label="Avisos">
          {ind.avisos.map((a, i) => (
            <li key={i}>{a}</li>
          ))}
        </ul>
      )}

      {semCondominio && (
        <NaoSeAplica
          titulo="1 a 5. Gráficos do fundo Condomínio"
          motivo="Os gráficos 1 a 5 são do fundo Condomínio e não se aplicam ao fundo escolhido no filtro."
        />
      )}
      {ind.execucaoMensal && <GraficoExecucaoMensal pontos={ind.execucaoMensal} contexto={contexto} />}
      {ind.regra20 && <GraficoRegra20 pontos={ind.regra20} limitePercentual={ind.limitePercentual} contexto={contexto} />}
      {ind.acumulado && <GraficoAcumulado pontos={ind.acumulado} contexto={contexto} />}
      {ind.realizadoPorGrupo && <GraficoRealizadoPorGrupo series={ind.realizadoPorGrupo} contexto={contexto} />}
      {ind.maioresDiferencas && <GraficoMaioresDiferencas diferencas={ind.maioresDiferencas} contexto={contexto} />}

      {ind.fundos ? (
        <GraficoFundos series={ind.fundos} contexto={contexto} />
      ) : (
        <NaoSeAplica
          titulo="6. Fundos: arrecadação × previsto"
          motivo="Este gráfico é dos fundos ligados às linhas 1.9 e não se aplica ao filtro do fundo Condomínio."
        />
      )}

      {ind.comparacao ? (
        <GraficoEntreExercicios comparacao={ind.comparacao} contexto={contexto} />
      ) : (
        <NaoSeAplica titulo="7. Comparação entre exercícios" motivo="Sem comparação para este exercício (veja os avisos acima)." />
      )}

      <p className="discreto">As diferenças são indícios para conferência, sempre com os lançamentos de origem.</p>
    </>
  );
}
