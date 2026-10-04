import { useState } from "react";
import { useSearchParams } from "react-router";
import { usePrevisoes, usePrevistoRealizado } from "../api/consultasOrcamento";
import type { PrevistoRealizado as Resultado } from "../api/tipos";
import { BlocosAParte } from "../componentes/previsto/BlocosAParte";
import { BotoesExportacao } from "../componentes/previsto/BotoesExportacao";
import { EstadoPrevisto } from "../componentes/previsto/EstadoPrevisto";
import type { AlvoEvidencia } from "../componentes/previsto/evidencia";
import { FiltrosPrevisto, type VisaoFundo } from "../componentes/previsto/FiltrosPrevisto";
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
 * (?periodo=2026-09&po=...&fundo=DEMAIS), para o link da tela inicial e do de-para abrirem a mesma visão.
 */
export function PrevistoRealizado() {
  const { condominioId } = useSessao();
  const [parametros, setParametros] = useSearchParams();
  const poId = parametros.get("po");
  const fundo: VisaoFundo = parametros.get("fundo") === "DEMAIS" ? "DEMAIS" : "CONDOMINIO";

  const { data: previsoes = [] } = usePrevisoes(condominioId);
  // O acumulado traz os meses do exercício (para o filtro e o gráfico mês a mês)
  const acumulado = usePrevistoRealizado(condominioId, "acumulado", poId);
  const ultimoMes = acumulado.data?.mesesSomados.at(-1);
  const periodo = parametros.get("periodo") ?? (acumulado.isFetched ? (ultimoMes ?? "acumulado") : null);
  const consulta = usePrevistoRealizado(condominioId, periodo, poId);

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
        {periodo && consulta.data?.situacao === "CALCULADO" && <BotoesExportacao periodo={periodo} poId={poId} />}
      </header>
      <FiltrosPrevisto
        previsoes={previsoes}
        poId={poId}
        aoTrocarPo={(id) => trocar("po", id)}
        periodo={periodo ?? "acumulado"}
        meses={acumulado.data?.meses ?? []}
        aoTrocarPeriodo={(p) => trocar("periodo", p)}
        fundo={fundo}
        aoTrocarFundo={(f) => trocar("fundo", f === "CONDOMINIO" ? null : f)}
      />
      {consulta.isLoading || !periodo ? (
        <p className="aviso">Carregando…</p>
      ) : consulta.error ? (
        <p className="aviso erro">{consulta.error.message}</p>
      ) : consulta.data ? (
        <Conteudo resultado={consulta.data} periodo={periodo} fundo={fundo} aoEscolherMes={(m) => trocar("periodo", m)} />
      ) : null}
    </>
  );
}

interface PropsConteudo {
  resultado: Resultado;
  periodo: string;
  fundo: VisaoFundo;
  aoEscolherMes: (mes: string) => void;
}

function Conteudo({ resultado, periodo, fundo, aoEscolherMes }: PropsConteudo) {
  const [evidencia, setEvidencia] = useState<AlvoEvidencia | null>(null);
  const calculado = resultado.situacao === "CALCULADO" && resultado.totais;

  return (
    <div className={evidencia ? "com-detalhe" : undefined}>
      <div>
        <EstadoPrevisto resultado={resultado} />
        {resultado.avisos.map((a) => (
          <p key={a.codigo} className="aviso alerta">
            {a.texto}
          </p>
        ))}

        {!calculado ? (
          <SemNumeros resultado={resultado} />
        ) : fundo === "DEMAIS" ? (
          <PainelFundos fundos={resultado.fundos} aoAbrirEvidencia={setEvidencia} />
        ) : (
          <>
            <ResumoPrevisto resultado={resultado} totais={resultado.totais!} />
            {resultado.regra20 && <IndicadorRegra20 regra={resultado.regra20} />}
            {periodo === "acumulado" ? (
              <GraficoMeses meses={resultado.meses} aoEscolherMes={aoEscolherMes} />
            ) : (
              <GraficoGrupos grupos={resultado.grupos} />
            )}
            <TabelaPrevisto grupos={resultado.grupos} aoAbrirEvidencia={setEvidencia} />
            <BlocosAParte resultado={resultado} aoAbrirEvidencia={setEvidencia} />
          </>
        )}
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
