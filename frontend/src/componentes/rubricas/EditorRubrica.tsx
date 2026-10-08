import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useDefinirRubrica, useRubricas } from "../../api/consultasAnalisePo";
import type { LinhaComRubrica } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarMoeda } from "../../formato";

interface Props {
  poId: string;
  linha: LinhaComRubrica;
  aoFechar: () => void;
}

type Modo = "EXISTENTE" | "NOVA";

/**
 * Admin escolhe a rubrica de uma linha da PO (RF-11.7): uma do catálogo do condomínio ou uma nova,
 * criada a partir da linha. A busca é só para achar a rubrica na lista; quem decide é o Admin.
 */
export function EditorRubrica({ poId, linha, aoFechar }: Props) {
  const { condominioId } = useSessao();
  const definir = useDefinirRubrica(condominioId, poId);
  const { data: catalogo = [], isLoading } = useRubricas(condominioId);
  const janela = useRef<HTMLDialogElement>(null);
  const [modo, setModo] = useState<Modo>("EXISTENTE");
  const [rubricaId, setRubricaId] = useState(linha.rubrica?.id ?? "");
  // Sugestão de nome para a rubrica nova: o que a linha mostra como conta da PO (ex.: "1682 - Sindicatura Profissional")
  const [nome, setNome] = useState(linha.conta);
  const [busca, setBusca] = useState("");
  const [confirmar, setConfirmar] = useState(true);

  useEffect(() => {
    janela.current?.showModal();
  }, []);

  const encontradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return catalogo;
    return catalogo.filter((r) => [r.nome, r.grupo ?? ""].some((t) => t.toLowerCase().includes(termo)));
  }, [busca, catalogo]);

  const nomeValido = nome.trim().length > 0 && nome.trim().length <= 300;
  const podeSalvar = modo === "EXISTENTE" ? !!rubricaId : nomeValido;

  function salvar(evento: FormEvent) {
    evento.preventDefault();
    definir.mutate(
      {
        linhaId: linha.linhaId,
        // O contrato aceita uma rubrica do catálogo ou uma nova, nunca as duas
        pedido: modo === "EXISTENTE" ? { rubricaId, confirmar } : { novaRubrica: nome.trim(), confirmar },
      },
      { onSuccess: aoFechar },
    );
  }

  return (
    <dialog ref={janela} className="janela larga" onClose={aoFechar}>
      <form onSubmit={salvar}>
        <h2>
          Rubrica da linha {linha.codigo} {linha.descricao}
        </h2>
        <p className="discreto">
          Conta da PO: {linha.conta} · {formatarMoeda(linha.orcado)} orçado
          {linha.rubrica && ` · atual: ${linha.rubrica.nome}`}
        </p>
        {linha.motivo && <p className="discreto">Motivo da sugestão: {linha.motivo}</p>}

        <fieldset>
          <legend>Rubrica</legend>
          <label>
            <input type="radio" name="modo" checked={modo === "EXISTENTE"} onChange={() => setModo("EXISTENTE")} />
            Escolher uma rubrica existente
          </label>
          <label>
            <input type="radio" name="modo" checked={modo === "NOVA"} onChange={() => setModo("NOVA")} />
            Criar uma rubrica nova a partir desta linha
          </label>
        </fieldset>

        {modo === "EXISTENTE" ? (
          <fieldset>
            <legend>Catálogo do condomínio</legend>
            <input type="search" placeholder="Buscar por nome ou grupo (ex.: 1682 ou 1.3)" value={busca} onChange={(e) => setBusca(e.target.value)} />
            {isLoading ? (
              <p className="aviso">Carregando…</p>
            ) : (
              <select size={8} value={rubricaId} onChange={(e) => setRubricaId(e.target.value)} required aria-label="Rubrica">
                {encontradas.map((r) => (
                  <option key={r.id} value={r.id}>
                    {r.nome}
                    {r.grupo ? ` (grupo ${r.grupo})` : ""}
                  </option>
                ))}
              </select>
            )}
          </fieldset>
        ) : (
          <label className="campo">
            Nome da rubrica nova
            <input value={nome} maxLength={300} onChange={(e) => setNome(e.target.value)} required />
          </label>
        )}

        <label className="campo-linha">
          <input type="checkbox" checked={confirmar} onChange={(e) => setConfirmar(e.target.checked)} />
          Confirmar agora (sem marcar, fica como sugerido e não entra na comparação por linha)
        </label>

        {definir.isError && <p className="aviso erro">{definir.error.message}</p>}
        <div className="acoes">
          <button className="botao secundario" type="button" onClick={aoFechar}>
            Cancelar
          </button>
          <button className="botao" type="submit" disabled={definir.isPending || !podeSalvar}>
            {definir.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
