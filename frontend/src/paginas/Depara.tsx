import { useState } from "react";
import { Link, useSearchParams } from "react-router";
import { useDepara, usePrevisao, usePrevisoes } from "../api/consultasOrcamento";
import type { ContaDepara, FiltroDepara, PrevisaoResumo } from "../api/tipos";
import { AcoesDepara } from "../componentes/depara/AcoesDepara";
import { EditorDestino } from "../componentes/depara/EditorDestino";
import { TabelaDepara } from "../componentes/depara/TabelaDepara";
import { TrilhaDepara } from "../componentes/depara/TrilhaDepara";
import { rotuloEstadoPo } from "../componentes/previsto/rotulos";
import { useSessao } from "../contexto";

const filtros: { codigo: FiltroDepara; rotulo: string }[] = [
  { codigo: "TODAS", rotulo: "Todas" },
  { codigo: "PENDENTES", rotulo: "Pendentes" },
  { codigo: "IGUAIS_VERSAO_ANTERIOR", rotulo: "Iguais à versão anterior" },
  { codigo: "SUGERIDO", rotulo: "Sugeridas" },
  { codigo: "CONFIRMADO", rotulo: "Confirmadas" },
  { codigo: "RECUSADO", rotulo: "Recusadas" },
  { codigo: "SEM_DEPARA", rotulo: "Sem de-para" },
];

/** A PO padrão é a confirmada mais recente (a lista já vem da mais recente para a mais antiga). */
const poPadrao = (previsoes: PrevisaoResumo[]) => previsoes.find((p) => p.estado === "CONFIRMADA") ?? previsoes[0];

/**
 * Tela "De-para" (RF-03.1.13): todos consultam; só o Admin edita, confirma e recusa.
 * O filtro fica no endereço (?filtro=PENDENTES), para o aviso do previsto × realizado trazer o Admin direto aqui.
 */
export function Depara() {
  const { condominioId, pode } = useSessao();
  const admin = pode("ADMIN");
  const [parametros, setParametros] = useSearchParams();
  const { data: previsoes = [], isLoading: carregandoPos } = usePrevisoes(condominioId);
  const poId = parametros.get("po") ?? poPadrao(previsoes)?.id;
  const filtro = (parametros.get("filtro") as FiltroDepara | null) ?? "TODAS";
  const { data: lista, isLoading, error } = useDepara(condominioId, poId, filtro);
  const { data: previsao } = usePrevisao(condominioId, poId);
  const [selecionadas, setSelecionadas] = useState<Set<string>>(new Set());
  const [editando, setEditando] = useState<ContaDepara | null>(null);

  const trocar = (nome: string, valor: string | null) => {
    setSelecionadas(new Set());
    setParametros((atual) => {
      const novo = new URLSearchParams(atual);
      if (valor) novo.set(nome, valor);
      else novo.delete(nome);
      return novo;
    });
  };

  if (carregandoPos) return <p className="aviso">Carregando…</p>;
  if (!poId) {
    return (
      <section className="vazio">
        <h1>Nenhuma PO lida ainda</h1>
        <p>O de-para aparece depois que a PO aprovada for enviada na categoria PO e lida.</p>
      </section>
    );
  }

  const poConfirmada = previsao?.previsao.estado === "CONFIRMADA";
  // Linhas que podem ser destino: só as de despesa (sem total, grupo nem fundos)
  const linhasDestino = previsao?.linhas.filter((l) => l.tipo === "LINHA" && !l.linhaDeFundo) ?? [];
  const contas = lista?.contas ?? [];

  return (
    <>
      <header className="titulo-pagina">
        <h1>De-para das contas do fluxo</h1>
        {lista && (
          <span className={lista.resumo.confirmadas === lista.resumo.contas ? "selo ok" : "selo alerta"}>
            {lista.resumo.confirmadas} de {lista.resumo.contas} contas confirmadas
          </span>
        )}
        <Link className="botao-link" to={`/previsto-realizado?po=${poId}`}>
          Ver o previsto × realizado
        </Link>
      </header>

      <div className="filtros">
        <label>
          PO
          <select value={poId} onChange={(e) => trocar("po", e.target.value)}>
            {previsoes.map((p) => (
              <option key={p.id} value={p.id}>
                {p.versao ? `Versão ${p.versao}` : "Sem versão"} · {p.exercicioImpresso ?? p.arquivoNome} · {rotuloEstadoPo[p.estado]}
              </option>
            ))}
          </select>
        </label>
      </div>

      {lista && (
        <p className="discreto">
          {lista.resumo.sugeridas} sugeridas · {lista.resumo.recusadas} recusadas · {lista.resumo.semDepara} sem de-para. Só as
          confirmadas entram no previsto × realizado; sugestão nunca muda número.
        </p>
      )}
      {!poConfirmada && previsao && (
        <p className="aviso alerta">
          A PO ainda não foi confirmada. <Link to={`/previsoes/${poId}`}>Conferir a PO</Link> antes de editar o de-para.
        </p>
      )}

      {admin && poConfirmada && (
        <AcoesDepara poId={poId} selecionadas={[...selecionadas]} aoConcluirLote={() => setSelecionadas(new Set())} />
      )}

      <div className="abas" role="tablist">
        {filtros.map((f) => (
          <button key={f.codigo} role="tab" aria-selected={filtro === f.codigo} onClick={() => trocar("filtro", f.codigo === "TODAS" ? null : f.codigo)}>
            {f.rotulo}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : error ? (
        <p className="aviso erro">{error.message}</p>
      ) : contas.length === 0 ? (
        <p className="aviso">Nenhuma conta neste filtro.</p>
      ) : (
        <TabelaDepara
          contas={contas}
          edicao={
            admin && poConfirmada
              ? {
                  selecionadas,
                  aoSelecionar: (conta, marcada) =>
                    setSelecionadas((atual) => {
                      const novo = new Set(atual);
                      if (marcada) novo.add(conta);
                      else novo.delete(conta);
                      return novo;
                    }),
                  aoSelecionarTodas: (marcadas) => setSelecionadas(marcadas ? new Set(contas.map((c) => c.conta)) : new Set()),
                  aoEditar: setEditando,
                }
              : undefined
          }
        />
      )}

      <TrilhaDepara poId={poId} />
      {editando && <EditorDestino poId={poId} conta={editando} linhas={linhasDestino} aoFechar={() => setEditando(null)} />}
    </>
  );
}
