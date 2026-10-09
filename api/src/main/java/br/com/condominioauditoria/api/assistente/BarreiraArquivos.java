package br.com.condominioauditoria.api.assistente;

import br.com.condominioauditoria.api.model.file.SourceFile;
import br.com.condominioauditoria.api.repository.file.SourceFileRepository;
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

    private final SourceFileRepository arquivos;

    BarreiraArquivos(SourceFileRepository arquivos) {
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
        return arquivos.findByCondominiumIdAndIdIn(condominioId, citados).stream()
                .map(SourceFile::getId).map(UUID::toString).collect(Collectors.toSet());
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
