import { useState } from "react";
import { useSearchParams } from "react-router";
import { useFundos, usePrevisoes, usePrevistoRealizado } from "../api/consultasOrcamento";
import type { PrevistoRealizado as Resultado } from "../api/tipos";
import { BlocosAParte } from "../componentes/previsto/BlocosAParte";
import { BotoesExportacao } from "../componentes/previsto/BotoesExportacao";
import { EstadoPrevisto } from "../componentes/previsto/EstadoPrevisto";
import type { AlvoEvidencia } from "../componentes/previsto/evidencia";
import { AchadosDoMes } from "../componentes/previsto/AchadosDoMes";
import { FiltrosPrevisto } from "../componentes/previsto/FiltrosPrevisto";
import { GraficoGrupos } from "../componentes/previsto/GraficoGrupos";
import { GraficoMeses } from "../componentes/previsto/GraficoMeses";
import { IndicadorRegra20 } from "../componentes/previsto/IndicadorRegra20";
import { PainelEvidencia } from "../componentes/previsto/PainelEvidencia";
import { PainelFundos } from "../componentes/previsto/PainelFundos";
import { ResumoPrevisto } from "../componentes/previsto/ResumoPrevisto";
import { SemNumeros } from "../componentes/previsto/SemNumeros";
import { TabelaPrevisto } from "../componentes/previsto/TabelaPrevisto";
import { useSessao } from "../contexto";
import { formatarMes } from "../formato";

/**
 * Tela "Previsto × realizado" (RF-03.1.13), para todos os perfis. Os filtros ficam no endereço
 * (?periodo=2026-09&po=...&fundo=<id do fundo>), para o link da tela inicial e do de-para abrirem a mesma visão.
 */
export function PrevistoRealizado() {
  const { condominioId } = useSessao();
  const [parametros, setParametros] = useSearchParams();
  const poId = parametros.get("po");
  const fundoId = parametros.get("fundo");

  const { data: previsoes = [] } = usePrevisoes(condominioId);
  const { data: fundos = [] } = useFundos(condominioId);
  // O acumulado traz os meses do exercício (para o filtro e o gráfico mês a mês)
  const acumulado = usePrevistoRealizado(condominioId, "acumulado", poId);
  const ultimoMes = acumulado.data?.mesesSomados.at(-1);
  const periodo = parametros.get("periodo") ?? (acumulado.isFetched ? (ultimoMes ?? "acumulado") : null);
  // O filtro de fundo vai para a API: ela devolve só o que pertence à visão escolhida
  const consulta = usePrevistoRealizado(condominioId, periodo, poId, fundoId);

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
        <h1>Previsto × realizado{periodo && periodo !== "acumulado" ? ` · ${formatarMes(periodo)}` : " · acumulado"}</h1>
        {periodo && consulta.data?.situacao === "CALCULADO" && <BotoesExportacao periodo={periodo} poId={poId} fundoId={fundoId} />}
      </header>
      <FiltrosPrevisto
        previsoes={previsoes}
        poId={poId}
        aoTrocarPo={(id) => trocar("po", id)}
        periodo={periodo ?? "acumulado"}
        meses={acumulado.data?.meses ?? []}
        aoTrocarPeriodo={(p) => trocar("periodo", p)}
        fundos={fundos}
        fundoId={fundoId}
        aoTrocarFundo={(f) => trocar("fundo", f)}
      />
      {consulta.isLoading || !periodo ? (
        <p className="aviso">Carregando…</p>
      ) : consulta.error ? (
        <p className="aviso erro">{consulta.error.message}</p>
      ) : consulta.data ? (
        <Conteudo resultado={consulta.data} periodo={periodo} aoEscolherMes={(m) => trocar("periodo", m)} />
      ) : null}
    </>
  );
}

interface PropsConteudo {
  resultado: Resultado;
  periodo: string;
  aoEscolherMes: (mes: string) => void;
}

function Conteudo({ resultado, periodo, aoEscolherMes }: PropsConteudo) {
  const [evidencia, setEvidencia] = useState<AlvoEvidencia | null>(null);
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
        <PainelEvidencia periodo={periodo} po={resultado.po} alvo={evidencia} aoFechar={() => setEvidencia(null)} />
      )}
    </div>
  );
}
