import { useState } from "react";
import { useSearchParams } from "react-router";
import { useArquivos, useCategorias } from "../api/consultas";
import type { ArquivoResumo, Categoria, SituacaoIndexacao } from "../api/tipos";
import { DetalheArquivo } from "../componentes/DetalheArquivo";
import { EditarCategoria } from "../componentes/EditarCategoria";
import { EnvioArquivo } from "../componentes/EnvioArquivo";
import { IndexacaoArquivo, rotulosIndexacao } from "../componentes/IndexacaoArquivo";
import { StatusArquivo } from "../componentes/StatusArquivo";
import { useSessao } from "../contexto";
import { formatarDataHora, formatarPeriodo } from "../formato";

/** Filtro da coluna "Busca" (RF-04.7): uma situação da API ou "não indexado" (campo nulo). */
type FiltroIndexacao = "TODOS" | "NAO_INDEXADO" | SituacaoIndexacao;

/** Só esconde linhas que já vieram da API; não calcula nada. */
const passaNoFiltro = (a: ArquivoResumo, filtro: FiltroIndexacao) =>
  filtro === "TODOS" || (filtro === "NAO_INDEXADO" ? !a.indexacao : a.indexacao?.situacao === filtro);

/** Arquivos separados por categoria, do mais recente para o mais antigo. */
export function Arquivos() {
  const { condominioId, pode, moduloLigado } = useSessao();
  const { data: categorias = [] } = useCategorias();
  const [categoria, setCategoria] = useState<Categoria | undefined>();
  // ?arquivo=<id> abre o detalhe direto (link "Ver conferências do arquivo" do Assistente)
  const [parametros] = useSearchParams();
  const [selecionado, setSelecionado] = useState<string | null>(() => parametros.get("arquivo"));
  const [editando, setEditando] = useState<ArquivoResumo | null>(null);
  const podeEditar = pode("GESTOR", "ADMIN");
  const [filtroIndexacao, setFiltroIndexacao] = useState<FiltroIndexacao>("TODOS");
  const { data: todos = [], isLoading } = useArquivos(condominioId, categoria);
  // A coluna "Busca" só existe com o módulo Assistente ligado (sem ele a API não manda o estado)
  const comBusca = moduloLigado("ASSISTENTE") || todos.some((a) => a.indexacao);
  const arquivos = comBusca ? todos.filter((a) => passaNoFiltro(a, filtroIndexacao)) : todos;

  return (
    <div className={selecionado ? "com-detalhe" : undefined}>
      <div>
        <header className="titulo-pagina">
          <h1>Arquivos</h1>
        </header>
        {podeEditar && <EnvioArquivo categoriaInicial={categoria} />}

        <div className="abas" role="tablist">
          <button role="tab" aria-selected={!categoria} onClick={() => setCategoria(undefined)}>
            Todas
          </button>
          {categorias.map((c) => (
            <button key={c.codigo} role="tab" aria-selected={categoria === c.codigo} onClick={() => setCategoria(c.codigo)}>
              {c.rotulo}
            </button>
          ))}
        </div>

        {comBusca && (
          <label className="filtro">
            Busca nos documentos
            <select value={filtroIndexacao} onChange={(e) => setFiltroIndexacao(e.target.value as FiltroIndexacao)}>
              <option value="TODOS">Todos os estados</option>
              <option value="NAO_INDEXADO">Não indexado</option>
              {(Object.keys(rotulosIndexacao) as SituacaoIndexacao[]).map((s) => (
                <option key={s} value={s}>
                  {rotulosIndexacao[s]}
                </option>
              ))}
            </select>
          </label>
        )}

        {isLoading ? (
          <p className="aviso">Carregando…</p>
        ) : arquivos.length === 0 ? (
          <p className="aviso">
            {filtroIndexacao !== "TODOS" && todos.length > 0
              ? "Nenhum arquivo neste estado de indexação."
              : "Nenhum arquivo nesta categoria ainda."}
          </p>
        ) : (
          <table className="tabela clicavel">
            <thead>
              <tr>
                <th>Arquivo</th>
                {!categoria && <th>Categoria</th>}
                <th>Período</th>
                <th>Enviado em</th>
                <th>Situação</th>
                {comBusca && <th>Busca</th>}
                {podeEditar && <th aria-label="Ações" />}
              </tr>
            </thead>
            <tbody>
              {arquivos.map((a) => (
                <tr key={a.id} onClick={() => setSelecionado(a.id)} className={a.id === selecionado ? "selecionado" : undefined}>
                  <td>{a.nome}</td>
                  {!categoria && <td>{a.categoriaRotulo}</td>}
                  <td>{formatarPeriodo(a.periodoInicio, a.periodoFim)}</td>
                  <td>
                    {formatarDataHora(a.enviadoEm)} <span className="discreto">por {a.enviadoPor}</span>
                  </td>
                  <td>
                    <StatusArquivo status={a.status} />
                  </td>
                  {comBusca && (
                    <td>
                      <IndexacaoArquivo indexacao={a.indexacao} />
                    </td>
                  )}
                  {podeEditar && (
                    <td>
                      <button
                        className="botao-link"
                        disabled={a.status === "PROCESSANDO"}
                        title={a.status === "PROCESSANDO" ? "O arquivo já está sendo processado" : "Mudar a categoria"}
                        onClick={(e) => {
                          e.stopPropagation();
                          setEditando(a);
                        }}
                      >
                        Editar
                      </button>
                    </td>
                  )}
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      {editando && <EditarCategoria arquivo={editando} aoFechar={() => setEditando(null)} />}
      {selecionado && <DetalheArquivo id={selecionado} aoFechar={() => setSelecionado(null)} />}
    </div>
  );
}
