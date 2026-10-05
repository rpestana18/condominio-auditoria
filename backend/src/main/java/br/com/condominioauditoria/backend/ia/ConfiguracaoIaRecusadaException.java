package br.com.condominioauditoria.backend.ia;

import java.util.List;

/** Configuração de IA recusada (422 na API), com todos os motivos de uma vez. Nenhum motivo leva a chave. */
public class ConfiguracaoIaRecusadaException extends RuntimeException {

    private final List<String> motivos;

    public ConfiguracaoIaRecusadaException(List<String> motivos) {
        super(motivos.size() == 1 ? motivos.getFirst() : "Configuração de IA recusada: " + motivos.size() + " motivos");
        this.motivos = List.copyOf(motivos);
    }

    public List<String> motivos() {
        return motivos;
    }
}
