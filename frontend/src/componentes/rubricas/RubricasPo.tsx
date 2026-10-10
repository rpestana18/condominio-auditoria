import { useState } from "react";
import { useRubricasDaPo } from "../../api/consultasAnalisePo";
import type { FiltroRubrica, LinhaComRubrica, PrevisaoResumo } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { AcoesRubricas } from "./AcoesRubricas";
import { EditorRubrica } from "./EditorRubrica";
import { TabelaRubricas } from "./TabelaRubricas";
import { TrilhaRubricas } from "./TrilhaRubricas";

const filtros: { codigo: FiltroRubrica; rotulo: string }[] = [
  { codigo: "ALL", rotulo: "Todas" },
  { codigo: "PENDING", rotulo: "Pendentes" },
  { codigo: "SUGGESTED", rotulo: "Sugeridas" },
  { codigo: "CONFIRMED", rotulo: "Confirmadas" },
  { codigo: "REJECTED", rotulo: "Recusadas" },
  { codigo: "NO_ITEM", rotulo: "Sem rubrica" },
];

/**
 * Rubricas das linhas da PO (RF-11.7), na tela "PO". Todos os perfis veem a lista; só o Admin escolhe,
 * cria, confirma ou recusa, e só com a PO confirmada. O backend recusa (403) os demais de qualquer forma.
 */
export function RubricasPo({ previsao }: { previsao: PrevisaoResumo }) {
  const { condominioId, pode } = useSessao();
  const [filtro, setFiltro] = useState<FiltroRubrica>("ALL");
  const { data: lista, isLoading, error } = useRubricasDaPo(condominioId, previsao.id, filtro);
  const [selecionadas, setSelecionadas] = useState<Set<string>>(new Set());
  const [editando, setEditando] = useState<LinhaComRubrica | null>(null);
  const edita = pode("ADMIN") && previsao.status === "CONFIRMED";
  const linhas = lista?.lines ?? [];

  const trocarFiltro = (novo: FiltroRubrica) => {
    setSelecionadas(new Set());
    setFiltro(novo);
  };

  return (
    <section className="bloco">
      <header className="titulo-bloco">
        <h2>Rubricas (correspondência entre exercícios)</h2>
        {lista && (
          <span className={lista.summary.confirmed === lista.summary.lines ? "selo ok" : "selo alerta"}>
            {lista.summary.confirmed} de {lista.summary.lines} linhas confirmadas
          </span>
        )}
      </header>
      {lista && (
        <p className="discreto">
          {lista.summary.suggested} sugeridas · {lista.summary.rejected} recusadas · {lista.summary.withoutItem} sem rubrica. Linhas de
          exercícios diferentes com a mesma rubrica confirmada são comparadas em "Comparar exercícios"; sugestão nunca vale
          sozinha.
        </p>
      )}

      {edita && (
        <AcoesRubricas
          poId={previsao.id}
          linhas={linhas}
          selecionadas={[...selecionadas]}
          aoConcluirLote={() => setSelecionadas(new Set())}
        />
      )}

      <div className="abas" role="tablist">
        {filtros.map((f) => (
          <button key={f.codigo} role="tab" aria-selected={filtro === f.codigo} onClick={() => trocarFiltro(f.codigo)}>
            {f.rotulo}
          </button>
        ))}
      </div>

      {isLoading ? (
        <p className="aviso">Carregando…</p>
      ) : error ? (
        <p className="aviso erro">{error.message}</p>
      ) : linhas.length === 0 ? (
        <p className="aviso">Nenhuma linha neste filtro.</p>
      ) : (
        <TabelaRubricas
          linhas={linhas}
          edicao={
            edita
              ? {
                  selecionadas,
                  aoSelecionar: (linhaId, marcada) =>
                    setSelecionadas((atual) => {
                      const novo = new Set(atual);
                      if (marcada) novo.add(linhaId);
                      else novo.delete(linhaId);
                      return novo;
                    }),
                  aoSelecionarTodas: (marcadas) => setSelecionadas(marcadas ? new Set(linhas.map((l) => l.lineId)) : new Set()),
                  aoEditar: setEditando,
                }
              : undefined
          }
        />
      )}

      <TrilhaRubricas poId={previsao.id} />
      {editando && <EditorRubrica poId={previsao.id} linha={editando} aoFechar={() => setEditando(null)} />}
    </section>
  );
}
