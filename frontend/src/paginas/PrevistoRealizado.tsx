import { useState } from "react";
import { useSearchParams } from "react-router";
import { useExercicios } from "../api/consultasAnalisePo";
import { useFundos, usePrevisoes, usePrevistoRealizado } from "../api/consultasOrcamento";
import type { PrevistoRealizado as Resultado } from "../api/tipos";
import { BlocosAParte } from "../componentes/previsto/BlocosAParte";
import { BotoesExportacao } from "../componentes/previsto/BotoesExportacao";
import { EstadoPrevisto } from "../componentes/previsto/EstadoPrevisto";
import { alvoDoEndereco, type AlvoEvidencia } from "../componentes/previsto/evidencia";
import { AchadosDoMes } from "../componentes/previsto/AchadosDoMes";
import { FiltrosPrevisto } from "../componentes/previsto/FiltrosPrevisto";
import { GraficoGrupos } from "../componentes/previsto/GraficoGrupos";
import { GraficoMeses } from "../componentes/previsto/GraficoMeses";
import { IndicadorRegra20 } from "../componentes/previsto/IndicadorRegra20";
import { PainelEvidencia } from "../componentes/previsto/PainelEvidencia";
import { PainelFundos } from "../componentes/previsto/PainelFundos";
import { ResumoExercicio } from "../componentes/previsto/ResumoExercicio";
import { ResumoPrevisto } from "../componentes/previsto/ResumoPrevisto";
import { SemNumeros } from "../componentes/previsto/SemNumeros";
import { TabelaPrevisto } from "../componentes/previsto/TabelaPrevisto";
import { useSessao } from "../contexto";
import { formatarMes } from "../formato";

/**
 * Tela "Previsto × realizado" (RF-03.1.13 e RF-11.4), para todos os perfis. Os filtros ficam no endereço
 * (?periodo=2026-09&po=...&fundo=<id do fundo>), para o link da tela inicial e do de-para abrirem a mesma visão.
 * O exercício é escolhido pela lista do GET /exercicios e vai para a API como o `po` daquele exercício; sem `po`,
 * a API usa o exercício vigente. Com `?alvo=linha:<id>` (vindo de "Comparar exercícios"), a evidência já abre.
 */
export function PrevistoRealizado() {
  const { condominioId } = useSessao();
  const [parametros, setParametros] = useSearchParams();
  const poId = parametros.get("po");
  const fundoId = parametros.get("fundo");
  const alvoUrl = parametros.get("alvo");

  const { data: exercicios = [] } = useExercicios(condominioId);
  const { data: previsoes = [] } = usePrevisoes(condominioId);
  const { data: fundos = [] } = useFundos(condominioId);
  // O acumulado traz os meses do exercício (para o filtro e o gráfico mês a mês)
  const acumulado = usePrevistoRealizado(condominioId, "acumulado", poId);
  const ultimoMes = acumulado.data?.mesesSomados.at(-1);
  const periodo = parametros.get("periodo") ?? (acumulado.isFetched ? (ultimoMes ?? "acumulado") : null);
  // O filtro de fundo vai para a API: ela devolve só o que pertence à visão escolhida
  const consulta = usePrevistoRealizado(condominioId, periodo, poId, fundoId);

  // Sem `po` no endereço, o exercício mostrado é o que a API escolheu (o vigente)
  const poMostrada = poId ?? acumulado.data?.po?.id ?? null;
  const exercicio = exercicios.find((e) => e.tipo === "PO" && e.poId === poMostrada);

  /** Troca um filtro no endereço. A evidência aberta pelo link (?alvo=) fecha junto. */
  const trocar = (mudancas: Record<string, string | null>) =>
    setParametros((atual) => {
      const novo = new URLSearchParams(atual);
      novo.delete("alvo");
      for (const [nome, valor] of Object.entries(mudancas)) {
        if (valor) novo.set(nome, valor);
        else novo.delete(nome);
      }
      return novo;
    });

  return (
    <>
      <header className="titulo-pagina">
        <h1>Previsto × realizado{periodo && periodo !== "acumulado" ? ` · ${formatarMes(periodo)}` : " · acumulado"}</h1>
        {periodo && consulta.data?.situacao === "CALCULADO" && <BotoesExportacao periodo={periodo} poId={poId} fundoId={fundoId} />}
      </header>
      <FiltrosPrevisto
        exercicios={exercicios}
        previsoes={previsoes}
        poId={poMostrada}
        // Outro exercício tem outros meses: o período volta ao padrão (último mês com fluxo)
        aoTrocarPo={(id) => trocar({ po: id, periodo: null })}
        periodo={periodo ?? "acumulado"}
        meses={acumulado.data?.meses ?? []}
        aoTrocarPeriodo={(p) => trocar({ periodo: p })}
        fundos={fundos}
        fundoId={fundoId}
        aoTrocarFundo={(f) => trocar({ fundo: f })}
      />
      {exercicio && <ResumoExercicio exercicio={exercicio} />}
      {consulta.isLoading || !periodo ? (
        <p className="aviso">Carregando…</p>
      ) : consulta.error ? (
        <p className="aviso erro">{consulta.error.message}</p>
      ) : consulta.data ? (
        <Conteudo
          // Um alvo novo no endereço remonta o conteúdo, para o painel abrir já nele
          key={alvoUrl ?? ""}
          resultado={consulta.data}
          periodo={periodo}
          alvoUrl={alvoUrl}
          aoFecharAlvoUrl={() => trocar({})}
          aoEscolherMes={(m) => trocar({ periodo: m })}
        />
      ) : null}
    </>
  );
}

interface PropsConteudo {
  resultado: Resultado;
  periodo: string;
  /** Alvo da evidência vindo do endereço (ex.: clique num valor de "Comparar exercícios"). */
  alvoUrl: string | null;
  aoFecharAlvoUrl: () => void;
  aoEscolherMes: (mes: string) => void;
}

function Conteudo({ resultado, periodo, alvoUrl, aoFecharAlvoUrl, aoEscolherMes }: PropsConteudo) {
  const [evidencia, setEvidencia] = useState<AlvoEvidencia | null>(() => (alvoUrl ? alvoDoEndereco(resultado, alvoUrl) : null));
  const fecharEvidencia = () => {
    setEvidencia(null);
    if (alvoUrl) aoFecharAlvoUrl();
  };
  const { totais } = resultado;

  return (
    <div className={evidencia ? "com-detalhe" : undefined}>
      <div>
        <EstadoPrevisto resultado={resultado} />
        {resultado.avisos.map((a, i) => (
          <p key={`${a.codigo}-${i}`} className="aviso alerta">
            {a.texto}
          </p>
        ))}

        {resultado.situacao !== "CALCULADO" ? (
          <SemNumeros resultado={resultado} />
        ) : (
          <>
            {/* Com o filtro de outro fundo, a API devolve totais e grupos vazios: só o painel do fundo aparece */}
            {totais && <ResumoPrevisto resultado={resultado} totais={totais} aoAbrirEvidencia={setEvidencia} />}
            {resultado.regra20 && <IndicadorRegra20 regra={resultado.regra20} />}
            {resultado.grupos.length > 0 &&
              (periodo === "acumulado" ? (
                <GraficoMeses meses={resultado.meses} aoEscolherMes={aoEscolherMes} />
              ) : (
                <GraficoGrupos grupos={resultado.grupos} />
              ))}
            {resultado.grupos.length > 0 && <TabelaPrevisto grupos={resultado.grupos} aoAbrirEvidencia={setEvidencia} />}
            {totais && <BlocosAParte resultado={resultado} aoAbrirEvidencia={setEvidencia} />}
            {resultado.fundos.length > 0 && <PainelFundos fundos={resultado.fundos} aoAbrirEvidencia={setEvidencia} />}
          </>
        )}
        {periodo !== "acumulado" && <AchadosDoMes competencia={periodo} />}
        <p className="discreto">
          As diferenças são indícios para conferência, com os lançamentos de origem. Cálculo: versão {resultado.versaoCalculo}.
        </p>
      </div>
      {evidencia && resultado.po && (
        <PainelEvidencia periodo={periodo} po={resultado.po} alvo={evidencia} aoFechar={fecharEvidencia} />
      )}
    </div>
  );
}
