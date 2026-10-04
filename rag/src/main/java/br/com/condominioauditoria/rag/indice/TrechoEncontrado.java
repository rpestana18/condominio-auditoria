package br.com.condominioauditoria.rag.indice;

import java.util.UUID;

/**
 * Trecho devolvido pela busca, com o que a citação precisa: arquivo, localização e sha256 do original. A pontuação
 * só ordena (relevância ou fusão de posições); não é comparável entre buscas diferentes.
 */
public record TrechoEncontrado(UUID trechoId, UUID arquivoId, String nomeArquivo, String categoria,
        Localizacao localizacao, String texto, double pontuacao, String sha256) {

    TrechoEncontrado comPontuacao(double nova) {
        return new TrechoEncontrado(trechoId, arquivoId, nomeArquivo, categoria, localizacao, texto, nova, sha256);
    }
}
