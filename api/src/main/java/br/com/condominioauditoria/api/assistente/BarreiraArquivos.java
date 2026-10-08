package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.arquivo.Arquivo;
import br.com.condominioauditoria.api.arquivo.ArquivoRepository;
import br.com.condominioauditoria.contratos.assistente.v1.Trecho;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * Segunda barreira na volta do rag (ADR 0003, Decisão 5.2, passo 3): só ficam trechos de arquivos que existem no
 * backend e são do condomínio pedido. O acesso do usuário ao condomínio já foi conferido antes de chamar o rag; dentro
 * do condomínio, todo perfil vê todos os arquivos (§4). Arquivo que não existe mais no backend (excluído) sai aqui.
 */
@Component
class BarreiraArquivos {

    private final ArquivoRepository arquivos;

    BarreiraArquivos(ArquivoRepository arquivos) {
        this.arquivos = arquivos;
    }

    /** Ids (como texto) dos arquivos citados que podem aparecer na resposta. */
    Set<String> visiveis(UUID condominioId, Collection<Trecho> trechos) {
        Set<UUID> citados = new HashSet<>();
        for (Trecho t : trechos) {
            uuid(t.getArquivoId()).ifPresent(citados::add);
        }
        if (citados.isEmpty()) {
            return Set.of();
        }
        return arquivos.findByCondominioIdAndIdIn(condominioId, citados).stream()
                .map(Arquivo::getId).map(UUID::toString).collect(Collectors.toSet());
    }

    static boolean permitido(Trecho t, Set<String> visiveis) {
        return uuid(t.getArquivoId()).map(UUID::toString).filter(visiveis::contains).isPresent();
    }

    private static java.util.Optional<UUID> uuid(String valor) {
        try {
            return java.util.Optional.of(UUID.fromString(valor.strip()));
        } catch (IllegalArgumentException e) {
            return java.util.Optional.empty();
        }
    }
}
