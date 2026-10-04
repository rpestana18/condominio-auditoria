import { useEffect, useRef, useState, type FormEvent } from "react";
import { useAlterarCategoria, useCategorias } from "../api/consultas";
import type { ArquivoResumo, Categoria } from "../api/tipos";
import { useSessao } from "../contexto";

/** Troca a categoria de um arquivo já enviado (RF-01.7). Só Gestor e Admin chegam aqui. */
export function EditarCategoria({ arquivo, aoFechar }: { arquivo: ArquivoResumo; aoFechar: () => void }) {
  const { condominioId } = useSessao();
  const { data: categorias = [] } = useCategorias();
  const alterar = useAlterarCategoria(condominioId);
  const [categoria, setCategoria] = useState<Categoria>(arquivo.categoria);
  const janela = useRef<HTMLDialogElement>(null);

  useEffect(() => {
    janela.current?.showModal();
  }, []);

  function salvar(evento: FormEvent) {
    evento.preventDefault();
    if (categoria === arquivo.categoria) {
      aoFechar();
      return;
    }
    alterar.mutate({ id: arquivo.id, categoria }, { onSuccess: aoFechar });
  }

  return (
    <dialog ref={janela} className="janela" onClose={aoFechar} onClick={(e) => e.stopPropagation()}>
      <form onSubmit={salvar}>
        <h2>Editar categoria</h2>
        <p className="discreto">{arquivo.nome}</p>
        <fieldset>
          <legend>Categoria</legend>
          {categorias.map((c) => (
            <label key={c.codigo}>
              <input
                type="radio"
                name="categoria"
                value={c.codigo}
                checked={categoria === c.codigo}
                onChange={() => setCategoria(c.codigo)}
              />
              {c.rotulo}
              {c.codigo === arquivo.categoria && <span className="discreto"> (atual)</span>}
            </label>
          ))}
        </fieldset>
        <p className="aviso alerta">Trocar a categoria reprocessa o arquivo e substitui os dados extraídos dele.</p>
        {alterar.isError && <p className="aviso erro">{alterar.error.message}</p>}
        <div className="acoes">
          <button className="botao secundario" type="button" onClick={aoFechar}>
            Cancelar
          </button>
          <button className="botao" type="submit" disabled={alterar.isPending}>
            {alterar.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
