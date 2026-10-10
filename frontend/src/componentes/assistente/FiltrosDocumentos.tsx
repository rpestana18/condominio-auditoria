import { useArquivos, useCategorias } from "../../api/consultas";
import type { Categoria, FiltrosDocumentos as Filtros } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarData, formatarPeriodo } from "../../formato";
import { useConversa } from "./conversa";

/** Liga ou desliga um item numa lista (marcar/desmarcar uma caixa). */
function alternar<T>(lista: T[] | undefined, item: T): T[] {
  const atual = lista ?? [];
  return atual.includes(item) ? atual.filter((i) => i !== item) : [...atual, item];
}

/** Resumo do que está filtrado, para o título fechado: "2 categorias · 01/01/2025 a 31/12/2025". */
function resumo(filtros: Filtros): string {
  const partes: string[] = [];
  const categorias = filtros.categories?.length ?? 0;
  const documentos = filtros.fileIds?.length ?? 0;
  if (categorias) partes.push(categorias === 1 ? "1 categoria" : `${categorias} categorias`);
  if (filtros.startDate || filtros.endDate) {
    partes.push(`${formatarData(filtros.startDate) || "…"} a ${formatarData(filtros.endDate) || "…"}`);
  }
  if (documentos) partes.push(documentos === 1 ? "1 documento" : `${documentos} documentos`);
  return partes.length ? partes.join(" · ") : "todos os documentos";
}

/**
 * Filtros opcionais (RF-04.10): categorias, período e documentos. Valem para o chat e para a busca.
 * Só monta o pedido; quem aplica é o backend.
 */
export function FiltrosDocumentos() {
  const { condominioId } = useSessao();
  const { filtros, alterarFiltros } = useConversa();
  const { data: categorias = [] } = useCategorias();
  const { data: arquivos = [] } = useArquivos(condominioId);
  const ativo = Boolean(filtros.categories?.length || filtros.fileIds?.length || filtros.startDate || filtros.endDate);

  return (
    <details className="bloco filtros-documentos">
      <summary>
        Filtros: <span className="discreto">{resumo(filtros)}</span>
      </summary>

      <div className="filtros-grade">
        <fieldset>
          <legend>Categorias</legend>
          <div className="lista-opcoes">
            {categorias.map((c) => (
              <label key={c.code}>
                <input
                  type="checkbox"
                  checked={filtros.categories?.includes(c.code) ?? false}
                  onChange={() => alterarFiltros({ ...filtros, categories: alternar<Categoria>(filtros.categories, c.code) })}
                />
                {c.label}
              </label>
            ))}
          </div>
        </fieldset>

        <fieldset>
          <legend>Período (competência)</legend>
          <label className="campo">
            De
            <input
              type="date"
              value={filtros.startDate ?? ""}
              onChange={(e) => alterarFiltros({ ...filtros, startDate: e.target.value || null })}
            />
          </label>
          <label className="campo">
            Até
            <input
              type="date"
              value={filtros.endDate ?? ""}
              min={filtros.startDate ?? undefined}
              onChange={(e) => alterarFiltros({ ...filtros, endDate: e.target.value || null })}
            />
          </label>
          <p className="discreto">Convenção, regimento e contratos sem competência podem entrar mesmo com período.</p>
        </fieldset>

        <fieldset>
          <legend>Documentos</legend>
          {arquivos.length === 0 ? (
            <p className="discreto">Nenhum arquivo enviado ainda.</p>
          ) : (
            <div className="lista-opcoes rolavel">
              {arquivos.map((a) => (
                <label key={a.id} title={a.name}>
                  <input
                    type="checkbox"
                    checked={filtros.fileIds?.includes(a.id) ?? false}
                    onChange={() => alterarFiltros({ ...filtros, fileIds: alternar(filtros.fileIds, a.id) })}
                  />
                  <span className="nome-documento">{a.name}</span>
                  <span className="discreto">
                    {a.categoryLabel}
                    {a.periodStart && ` · ${formatarPeriodo(a.periodStart, a.periodEnd)}`}
                  </span>
                </label>
              ))}
            </div>
          )}
        </fieldset>
      </div>

      {ativo && (
        <button type="button" className="botao-link" onClick={() => alterarFiltros({})}>
          Limpar filtros
        </button>
      )}
    </details>
  );
}
