import { useEffect, useMemo, useRef, useState, type FormEvent } from "react";
import { useDefinirDepara } from "../../api/consultasOrcamento";
import type { ContaDepara, LinhaPo, TipoDestino } from "../../api/tipos";
import { useSessao } from "../../contexto";
import { formatarMoeda } from "../../formato";
import { rotuloTipoDestino } from "../previsto/rotulos";

interface Props {
  poId: string;
  conta: ContaDepara;
  /** Linhas de despesa da PO (grupos 1.1 a 1.8), únicas que podem ser destino. */
  linhas: LinhaPo[];
  aoFechar: () => void;
}

const tipos: TipoDestino[] = ["LINHA_PO", "AJUSTE", "A_REALOCAR", "TRANSFERENCIA"];

/**
 * Admin escolhe o destino de uma conta do fluxo (RF-03.1.4 e RF-03.1.5). A busca é por código e por nome
 * da linha; o número da conta do fluxo nunca é usado para casar.
 */
export function EditorDestino({ poId, conta, linhas, aoFechar }: Props) {
  const { condominioId } = useSessao();
  const definir = useDefinirDepara(condominioId, poId);
  const janela = useRef<HTMLDialogElement>(null);
  const [tipo, setTipo] = useState<TipoDestino>(conta.destino?.tipo ?? "LINHA_PO");
  const [linhaId, setLinhaId] = useState<string>(conta.destino?.linhaId ?? "");
  const [detalhe, setDetalhe] = useState(conta.destino?.detalhe ?? "");
  const [busca, setBusca] = useState("");
  const [confirmar, setConfirmar] = useState(true);

  useEffect(() => {
    janela.current?.showModal();
  }, []);

  // Filtro da lista só para a pessoa achar a linha (por código ou por texto)
  const encontradas = useMemo(() => {
    const termo = busca.trim().toLowerCase();
    if (!termo) return linhas;
    return linhas.filter((l) =>
      [l.codigoEfetivo, l.descricao, l.conta ?? "", l.contaTexto ?? ""].some((t) => t.toLowerCase().includes(termo)),
    );
  }, [busca, linhas]);

  function salvar(evento: FormEvent) {
    evento.preventDefault();
    definir.mutate(
      {
        conta: conta.conta,
        pedido: { tipo, linhaId: tipo === "LINHA_PO" ? linhaId : null, detalhe: tipo === "LINHA_PO" ? null : detalhe || null, confirmar },
      },
      { onSuccess: aoFechar },
    );
  }

  return (
    <dialog ref={janela} className="janela larga" onClose={aoFechar}>
      <form onSubmit={salvar}>
        <h2>
          Destino da conta {conta.conta} {conta.nome}
        </h2>
        <p className="discreto">
          {conta.lancamentos} lançamentos · {formatarMoeda(conta.debitos)} no exercício
          {conta.destino && ` · atual: ${conta.destino.texto}`}
        </p>
        {conta.motivo && <p className="discreto">Motivo da sugestão: {conta.motivo}</p>}

        <fieldset>
          <legend>Tipo de destino</legend>
          {tipos.map((t) => (
            <label key={t}>
              <input type="radio" name="tipo" checked={tipo === t} onChange={() => setTipo(t)} />
              {rotuloTipoDestino[t]}
            </label>
          ))}
        </fieldset>

        {tipo === "LINHA_PO" ? (
          <fieldset>
            <legend>Linha da PO</legend>
            <input type="search" placeholder="Buscar por código ou nome (ex.: 1.7.8 ou hidráulico)" value={busca} onChange={(e) => setBusca(e.target.value)} />
            <select size={8} value={linhaId} onChange={(e) => setLinhaId(e.target.value)} required aria-label="Linha da PO">
              {encontradas.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.codigoEfetivo} {l.descricao}
                  {l.conta ? ` (${l.conta})` : ""}
                </option>
              ))}
            </select>
          </fieldset>
        ) : (
          <label className="campo">
            Detalhe (ex.: estorno, cartão)
            <input value={detalhe} onChange={(e) => setDetalhe(e.target.value)} />
          </label>
        )}

        <label className="campo-linha">
          <input type="checkbox" checked={confirmar} onChange={(e) => setConfirmar(e.target.checked)} />
          Confirmar agora (sem marcar, fica como sugerido e não muda nenhum número)
        </label>

        {definir.isError && <p className="aviso erro">{definir.error.message}</p>}
        <div className="acoes">
          <button className="botao secundario" type="button" onClick={aoFechar}>
            Cancelar
          </button>
          <button className="botao" type="submit" disabled={definir.isPending || (tipo === "LINHA_PO" && !linhaId)}>
            {definir.isPending ? "Salvando…" : "Salvar"}
          </button>
        </div>
      </form>
    </dialog>
  );
}
