package br.com.condominioauditoria.rag.indice;

import java.util.List;

/**
 * Resultado do corte de um documento. {@code paginas} = páginas (PDF), abas (Excel) ou 1 (Word), como no contrato
 * ResultadoIndexacao. Sem trechos, {@code motivoSemTexto} explica por quê.
 */
public record DocumentoCortado(int paginas, List<TrechoCortado> trechos, String motivoSemTexto) {

    public boolean semTexto() {
        return trechos.isEmpty();
    }
}
