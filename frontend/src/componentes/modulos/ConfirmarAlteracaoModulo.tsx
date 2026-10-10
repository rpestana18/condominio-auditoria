import { useState, type FormEvent } from "react";
import { useAlterarModulo } from "../../api/consultas";
import type { ModuloDoCondominio } from "../../api/tipos";
import { useSessao } from "../../contexto";

interface Props {
  modulo: ModuloDoCondominio;
  aoFechar: () => void;
}

/**
 * Confirmação na própria página (sem confirm() do navegador), com motivo opcional que vai para a trilha.
 * Quem decide se pode é o backend: perfil sem permissão recebe 403 e a mensagem aparece aqui.
 */
export function ConfirmarAlteracaoModulo({ modulo, aoFechar }: Props) {
  const { condominioId, condominioNome } = useSessao();
  const alterar = useAlterarModulo(condominioId);
  const [motivo, setMotivo] = useState("");
  const ligar = !modulo.enabled;
  const idTitulo = `confirmar-${modulo.code}`;

  function confirmar(evento: FormEvent) {
    evento.preventDefault();
    // Motivo é opcional: em branco vai como null
    alterar.mutate({ codigo: modulo.code, alteracao: { enabled: ligar, reason: motivo.trim() || null } }, { onSuccess: aoFechar });
  }

  return (
    <form className="confirmacao" onSubmit={confirmar} aria-labelledby={idTitulo}>
      <p id={idTitulo}>
        <strong>
          {ligar ? "Ligar" : "Desligar"} o módulo {modulo.name} em {condominioNome}?
        </strong>
      </p>
      <p className="discreto">
        {ligar
          ? "Os arquivos já enviados entram na fila de indexação; o andamento aparece na tela Arquivos."
          : "Os arquivos originais e o índice ficam guardados; as funções do módulo deixam de responder até religar."}
      </p>
      <label className="campo">
        Motivo (opcional, fica registrado na trilha)
        <textarea value={motivo} onChange={(e) => setMotivo(e.target.value)} rows={2} maxLength={500} autoFocus />
      </label>
      {alterar.isError && (
        <p className="aviso erro" role="alert">
          {alterar.error.message}
        </p>
      )}
      <div className="acoes">
        <button className="botao secundario" type="button" onClick={aoFechar} disabled={alterar.isPending}>
          Cancelar
        </button>
        <button className="botao" type="submit" disabled={alterar.isPending}>
          {alterar.isPending ? "Gravando…" : ligar ? "Confirmar e ligar" : "Confirmar e desligar"}
        </button>
      </div>
    </form>
  );
}
