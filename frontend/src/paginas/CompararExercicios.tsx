import { useState } from "react";
import { useSearchParams } from "react-router";
import { useComparacaoExercicios, useExercicios } from "../api/consultasAnalisePo";
import { useFundos } from "../api/consultasOrcamento";
import type { ComparacaoExercicios, Exercicio } from "../api/tipos";
import { ConferenciaColunaImpressa } from "../componentes/comparacao/ConferenciaColunaImpressa";
import { ExerciciosComparados } from "../componentes/comparacao/ExerciciosComparados";
import { FiltrosComparacao } from "../componentes/comparacao/FiltrosComparacao";
import { useAbridorEvidencia } from "../componentes/comparacao/navegacao";
import { ResumoComparacao } from "../componentes/comparacao/ResumoComparacao";
import { SemCorrespondencia } from "../componentes/comparacao/SemCorrespondencia";
import { TabelaComparada, type LinhaComparada, type ModoValores } from "../componentes/comparacao/TabelaComparada";
import { useSessao } from "../contexto";

type Visao = "resumo" | "grupos" | "linhas";

const visoes: { codigo: Visao; rotulo: string }[] = [
  { codigo: "resumo", rotulo: "Resumo por exercício" },
  { codigo: "grupos", rotulo: "Por grupo" },
  { codigo: "linhas", rotulo: "Por linha" },
];

/**
 * Tela "Comparar exercícios" (RF-11.6), para todos os perfis. Os filtros ficam no endereço
 * (?exercicios=po:...,coluna:...&fundo=...&mesmosMeses=true&visao=grupos). Sem `exercicios`, o backend
 * escolhe os dois mais recentes. Todo número e toda variação vêm prontos do GET /comparacao-exercicios.
 */
export function CompararExercicios() {
  const { condominioId } = useSessao();
  const [parametros, setParametros] = useSearchParams();
  const escolhidos = parametros.get("exercicios")?.split(",").filter(Boolean) ?? null;
  const fundoId = parametros.get("fundo");
  const mesmosMeses = parametros.get("mesmosMeses") === "true";
  const visao = (parametros.get("visao") as Visao | null) ?? "resumo";

  const { data: exercicios = [] } = useExercicios(condominioId);
  const { data: fundos = [] } = useFundos(condominioId);
  const consulta = useComparacaoExercicios(condominioId, { exercicios: escolhidos, fundoId, mesmosMeses });
  const resposta = consulta.data ?? null;
  // PO cuja coluna "Orçado anterior" está aberta no painel de conferência
  const [colunaAberta, setColunaAberta] = useState<string | null>(null);

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
        <h1>Comparar exercícios</h1>
      </header>
      <FiltrosComparacao
        exercicios={exercicios}
        marcados={escolhidos ?? idsUsados(resposta, exercicios)}
        aoTrocarExercicios={(ids) => trocar("exercicios", ids.join(","))}
        escolhaPropria={escolhidos !== null}
        aoVoltarAoPadrao={() => trocar("exercicios", null)}
        fundos={fundos}
        fundoId={fundoId}
        aoTrocarFundo={(f) => trocar("fundo", f)}
        mesmosMeses={mesmosMeses}
        aoTrocarMesmosMeses={(m) => trocar("mesmosMeses", m ? "true" : null)}
        comparando={resposta?.comparando}
      />

      {consulta.isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : consulta.error ? (
        <p className="aviso erro">{consulta.error.message}</p>
      ) : resposta ? (
        <div className={colunaAberta ? "com-detalhe" : undefined}>
          <div>
            <ExerciciosComparados comparados={resposta.exercicios} exercicios={exercicios} aoConferirColuna={setColunaAberta} />
            {resposta.avisos.map((a, i) => (
              <p key={i} className="aviso alerta">
                {a}
              </p>
            ))}
            <div className="abas" role="tablist">
              {visoes.map((v) => (
                <button
                  key={v.codigo}
                  role="tab"
                  aria-selected={visao === v.codigo}
                  onClick={() => trocar("visao", v.codigo === "resumo" ? null : v.codigo)}
                >
                  {v.rotulo}
                </button>
              ))}
            </div>
            <Visoes resposta={resposta} visao={visao} fundoId={fundoId} />
            <p className="discreto">
              Variação contra o exercício seguinte da lista. Clique num valor para abrir o previsto × realizado daquele exercício
              com os lançamentos.
            </p>
          </div>
          {colunaAberta && <ConferenciaColunaImpressa poId={colunaAberta} aoFechar={() => setColunaAberta(null)} />}
        </div>
      ) : null}
    </>
  );
}

/**
 * Sem escolha no endereço, os marcados são os que o backend usou. Casa pelo tipo e pela PO, para não
 * depender do formato do id ("po:<uuid>" ou só o uuid).
 */
function idsUsados(resposta: ComparacaoExercicios | null, exercicios: Exercicio[]): string[] {
  if (!resposta) return [];
  return exercicios.filter((e) => resposta.exercicios.some((r) => r.tipo === e.tipo && r.poId === e.poId)).map((e) => e.id);
}

interface PropsVisoes {
  resposta: ComparacaoExercicios;
  visao: Visao;
  fundoId: string | null;
}

/** As três visões do RF-11.6. O modo dos valores (previsto do mês ou meses comparados) vale para grupos e linhas. */
function Visoes({ resposta, visao, fundoId }: PropsVisoes) {
  const [modo, setModo] = useState<ModoValores>("PREVISTO_MES");
  const abridor = useAbridorEvidencia(resposta.exercicios, fundoId);
  const { exercicios } = resposta;

  if (visao === "resumo") return <ResumoComparacao exercicios={exercicios} resumo={resposta.resumo} abridor={abridor} />;

  const seletorModo = (
    <label className="discreto">
      Mostrar{" "}
      <select value={modo} onChange={(e) => setModo(e.target.value as ModoValores)}>
        <option value="PREVISTO_MES">previsto do mês</option>
        <option value="MESES">previsto e realizado dos meses comparados</option>
      </select>
    </label>
  );

  if (visao === "grupos") {
    const linhas: LinhaComparada[] = resposta.grupos.map((g) => ({
      chave: g.codigo,
      titulo: `${g.codigo} ${g.descricao}`,
      observacao: g.fundos ? "fundos: realizado = arrecadação" : null,
      valores: g.valores,
    }));
    return (
      <section className="bloco">
        <header className="titulo-bloco">
          <h2>Por grupo</h2>
          {seletorModo}
        </header>
        <TabelaComparada exercicios={exercicios} linhas={linhas} modo={modo} abridor={abridor} />
      </section>
    );
  }

  const linhas: LinhaComparada[] = resposta.linhas.map((r) => ({
    chave: r.rubricaId,
    titulo: r.nome,
    observacao: r.grupo ? `grupo ${r.grupo}` : null,
    valores: r.valores,
  }));
  return (
    <>
      <section className="bloco">
        <header className="titulo-bloco">
          <h2>Por linha (rubricas confirmadas)</h2>
          {seletorModo}
        </header>
        {linhas.length === 0 ? (
          <p className="aviso">Nenhuma rubrica confirmada nos exercícios escolhidos.</p>
        ) : (
          <TabelaComparada exercicios={exercicios} linhas={linhas} modo={modo} abridor={abridor} mostrarLinhasDaPo />
        )}
      </section>
      <SemCorrespondencia exercicios={exercicios} linhas={resposta.semCorrespondencia} abridor={abridor} />
    </>
  );
}
