package br.com.condominioauditoria.rag.leitura.contrato;

import java.util.List;

/**
 * Saída do leitor de documentos (leitor), no contrato contracts/leitor/v1.
 * Só conteúdo bruto com posição. Quem interpreta é o Java.
 */
public record DocumentoLido(
        String versaoContrato,
        String leitor,
        Arquivo arquivo,
        String tipo,
        List<Pagina> paginas,
        List<Planilha> planilhas,
        List<Paragrafo> paragrafos) {

    public record Arquivo(String nome, String sha256, long tamanhoBytes) {
    }

    public record Pagina(int numero, double largura, double altura, String metodo, List<Palavra> palavras) {
    }

    /** Coordenadas em pontos, com origem no canto superior esquerdo da página. */
    public record Palavra(String texto, double x0, double x1, double topo, double base) {
    }

    public record Planilha(String nome, List<Celula> celulas) {
    }

    public record Celula(int linha, int coluna, String valor, String tipo) {
    }

    public record Paragrafo(int ordem, String texto, Integer tabela) {
    }
}
