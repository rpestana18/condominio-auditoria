import { useState } from "react";
import { useArquivos, useCategorias } from "../api/consultas";
import type { ArquivoResumo, Categoria } from "../api/tipos";
import { DetalheArquivo } from "../componentes/DetalheArquivo";
import { EditarCategoria } from "../componentes/EditarCategoria";
import { EnvioArquivo } from "../componentes/EnvioArquivo";
import { StatusArquivo } from "../componentes/StatusArquivo";
import { useSessao } from "../contexto";
import { formatarDataHora, formatarPeriodo } from "../formato";

/** Arquivos separados por categoria, do mais recente para o mais antigo. */
export function Arquivos() {
  const { condominioId, pode } = useSessao();
  const { data: categorias = [] } = useCategorias();
  const [categoria, setCategoria] = useState<Categoria | undefined>();
  const [selecionado, setSelecionado] = useState<string | null>(null);
  const [editando, setEditando] = useState<ArquivoResumo | null>(null);
  const podeEditar = pode("GESTOR", "ADMIN");
  const { data: arquivos = [], isLoading } = useArquivos(condominioId, categoria);

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

        {isLoading ? (
          <p className="aviso">Carregando…</p>
        ) : arquivos.length === 0 ? (
          <p className="aviso">Nenhum arquivo nesta categoria ainda.</p>
        ) : (
          <table className="tabela clicavel">
            <thead>
              <tr>
                <th>Arquivo</th>
                {!categoria && <th>Categoria</th>}
                <th>Período</th>
                <th>Enviado em</th>
                <th>Situação</th>
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
