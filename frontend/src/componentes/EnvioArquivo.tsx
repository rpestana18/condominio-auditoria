import { useState, type FormEvent } from "react";
import { useCategorias, useEnviarArquivo } from "../api/consultas";
import type { Categoria } from "../api/tipos";
import { useSessao } from "../contexto";

/** Envio de arquivo (só Gestor e Admin). O processamento segue sozinho; a lista mostra o andamento. */
export function EnvioArquivo({ categoriaInicial }: { categoriaInicial?: Categoria }) {
  const { condominioId } = useSessao();
  const { data: categorias = [] } = useCategorias();
  const envio = useEnviarArquivo(condominioId);
  const [categoria, setCategoria] = useState<Categoria>(categoriaInicial ?? "BALANCETE");
  const [arquivo, setArquivo] = useState<File | null>(null);

  function enviar(evento: FormEvent) {
    evento.preventDefault();
    if (!arquivo) return;
    envio.mutate({ categoria, arquivo }, { onSuccess: () => setArquivo(null) });
  }

  return (
    <form className="envio" onSubmit={enviar}>
      <label>
        Categoria
        <select value={categoria} onChange={(e) => setCategoria(e.target.value as Categoria)}>
          {categorias.map((c) => (
            <option key={c.codigo} value={c.codigo}>
              {c.rotulo}
            </option>
          ))}
        </select>
      </label>
      <label>
        Arquivo (PDF, Excel ou Word)
        <input
          key={arquivo ? "com-arquivo" : "vazio"}
          type="file"
          accept=".pdf,.xlsx,.docx"
          onChange={(e) => setArquivo(e.target.files?.[0] ?? null)}
        />
      </label>
      <button className="botao" type="submit" disabled={!arquivo || envio.isPending}>
        {envio.isPending ? "Enviando…" : "Enviar"}
      </button>
      {envio.isError && <p className="aviso erro">{envio.error.message}</p>}
      {envio.isSuccess && <p className="aviso ok">Arquivo recebido. O processamento aparece na lista abaixo.</p>}
    </form>
  );
}
