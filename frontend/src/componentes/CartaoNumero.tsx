import { formatarMoeda } from "../formato";

interface Props {
  titulo: string;
  valor: number;
  destaque?: "positivo" | "negativo";
  dica?: string;
}

export function CartaoNumero({ titulo, valor, destaque, dica }: Props) {
  return (
    <div className="cartao-numero" title={dica}>
      <span className="cartao-titulo">{titulo}</span>
      <strong className={destaque}>{formatarMoeda(valor)}</strong>
    </div>
  );
}
