package br.com.condominioauditoria.armazenamento;

import java.io.IOException;
import java.io.InputStream;

/**
 * Onde ficam os arquivos originais. Nunca no banco. No MVP é uma pasta local; na nuvem, um storage de objetos,
 * trocado por parâmetro sem mudar quem usa esta interface.
 */
public interface Armazenamento {

    /** Grava o conteúdo no caminho relativo (ex.: "{condominio}/BALANCETE/2026/abc-fluxo.pdf"). Não sobrescreve. */
    void guardar(String caminhoRelativo, InputStream conteudo) throws IOException;

    InputStream abrir(String caminhoRelativo) throws IOException;

    boolean existe(String caminhoRelativo);
}
