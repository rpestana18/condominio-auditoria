package br.com.condominioauditoria.rag.search;

import java.util.UUID;

/**
 * Chunk returned by the search, with what the citation needs: file, location and sha256 of the original. The score
 * only orders (relevance or rank fusion); it is not comparable across different searches.
 */
public record FoundChunk(UUID chunkId, UUID fileId, String fileName, String category,
        Location location, String text, double score, String sha256) {

    FoundChunk withScore(double fresh) {
        return new FoundChunk(chunkId, fileId, fileName, category, location, text, fresh, sha256);
    }
}
