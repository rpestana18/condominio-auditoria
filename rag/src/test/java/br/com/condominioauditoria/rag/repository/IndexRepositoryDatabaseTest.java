package br.com.condominioauditoria.rag.repository;

import static org.assertj.core.api.Assertions.assertThat;

import br.com.condominioauditoria.rag.messaging.IndexFileMessage;
import br.com.condominioauditoria.rag.repository.IndexRepository.Hit;
import br.com.condominioauditoria.rag.repository.IndexRepository.SearchFilters;
import br.com.condominioauditoria.rag.search.Chunk;
import br.com.condominioauditoria.rag.search.ChunkedDocument;
import br.com.condominioauditoria.rag.search.EmbeddingGenerator;
import br.com.condominioauditoria.rag.search.FoundChunk;
import br.com.condominioauditoria.rag.search.Location;
import br.com.condominioauditoria.rag.search.SearchRestrictions;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Index SQL against a real PostgreSQL with pgvector (image pgvector/pgvector:pg17). Runs only with the variable
 * RAG_TESTE_BANCO_URL pointing to a disposable database, for example:
 *
 * <pre>
 * docker run -d --name rag-pg-teste -e POSTGRES_USER=condominio -e POSTGRES_PASSWORD=condominio \
 *     -e POSTGRES_DB=condominio -p 55432:5432 pgvector/pgvector:pg17
 * RAG_TESTE_BANCO_URL=jdbc:postgresql://localhost:55432/condominio ./gradlew :rag:test --no-parallel
 * </pre>
 *
 * Each test uses a new condominium, so it can run again on the same database.
 */
@EnabledIfEnvironmentVariable(named = "RAG_TESTE_BANCO_URL", matches = ".+")
public class IndexRepositoryDatabaseTest {

    private static IndexRepository repository;
    private static JdbcClient jdbc;

    @BeforeAll
    public static void migrate() {
        String url = System.getenv("RAG_TESTE_BANCO_URL") + "?currentSchema=rag,public";
        var data = new DriverManagerDataSource(url, "condominio", "condominio");
        Flyway.configure().dataSource(data).schemas("rag").defaultSchema("rag").load().migrate();
        jdbc = JdbcClient.create(data);
        repository = new IndexRepository(jdbc, new JdbcTemplate(data),
                new TransactionTemplate(new DataSourceTransactionManager(data)));
    }

    @Test
    public void indexesSearchesByUnaccentedKeywordAndByVectorWithFilters() {
        UUID condominium = UUID.randomUUID();
        IndexFileMessage minutes = request(condominium, "ATA", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));
        IndexFileMessage contract = request(condominium, "CONTRATO", null, null);
        save(minutes, List.of(
                chunk(1, new Location.Page(1), "A assembleia aprovou a manutenção do elevador."),
                chunk(2, new Location.Page(2), "Multa de 2% por atraso no pagamento da cota.")), 0);
        save(contract, List.of(
                chunk(1, new Location.Sheet("Valores", 2, 31), "Item | Valor\nManutencao mensal | 1500.00"),
                chunk(2, new Location.Paragraphs(3, 5, ""), "Reajuste anual pelo IGP-M.")), 2);

        var all = new SearchFilters(condominium, null, null, null, null);

        // Without accents finds with accents and vice versa; regular plural by the Portuguese stem ("pagamentos").
        // PostgreSQL's stemmer does not join -ção and -ções (known limitation, with or without unaccent).
        List<Hit> maintenance = repository.findByKeyword(all, "manutencao", 10);
        assertThat(maintenance).hasSize(2);
        assertThat(repository.findByKeyword(all, "Manutenção", 10)).hasSize(2);
        assertThat(repository.findByKeyword(all, "pagamentos", 10)).hasSize(1);
        // Exclusion with "-" and quoted phrase (websearch_to_tsquery)
        assertThat(repository.findByKeyword(all, "manutenção -elevador", 10)).hasSize(1);
        assertThat(repository.findByKeyword(all, "\"multa de 2%\"", 10)).hasSize(1);

        // Filters in the WHERE: category, period (document without reference period is left out), files, other
        // condominium
        assertThat(repository.findByKeyword(new SearchFilters(condominium, List.of("CONTRATO"), null, null, null),
                "manutencao", 10)).hasSize(1);
        assertThat(repository.findByKeyword(new SearchFilters(condominium, null, LocalDate.of(2026, 9, 15), null,
                null), "manutencao", 10)).hasSize(1);
        assertThat(repository.findByKeyword(new SearchFilters(condominium, null, null, LocalDate.of(2026, 8, 31),
                null), "manutencao", 10)).isEmpty();
        assertThat(repository.findByKeyword(new SearchFilters(condominium, null, null, null,
                List.of(contract.fileId())), "manutencao", 10)).hasSize(1);
        assertThat(repository.findByKeyword(new SearchFilters(UUID.randomUUID(), null, null, null, null),
                "manutencao", 10)).isEmpty();

        // Vector: the most similar first (test vectors = axis of each chunk)
        List<UUID> byVector = repository.findByVector(all, axis(2), "bge-m3", 10);
        assertThat(byVector).hasSize(4);
        List<FoundChunk> loaded = repository.load(byVector);
        assertThat(loaded.getFirst().location()).isEqualTo(new Location.Sheet("Valores", 2, 31));
        assertThat(loaded.getFirst().sha256()).isEqualTo(contract.sha256());
        assertThat(repository.findByVector(new SearchFilters(condominium, List.of("ATA"), null, null, null),
                axis(2), "bge-m3", 10)).hasSize(2);

        // Logical deletion: disappears from both searches, but stays in the database
        assertThat(repository.withdraw(minutes.fileId(), UUID.randomUUID())).isTrue();
        assertThat(repository.findByKeyword(all, "elevador", 10)).isEmpty();
        assertThat(repository.findByVector(all, axis(0), "bge-m3", 10)).hasSize(2);
        assertThat(repository.find(minutes.fileId()).orElseThrow().withdrawn()).isTrue();
    }

    @Test
    public void reindexReplacesChunksAndNoTextLeavesNothing() {
        UUID condominium = UUID.randomUUID();
        IndexFileMessage minutes = request(condominium, "ATA", null, null);
        save(minutes, List.of(chunk(1, new Location.Page(1), "primeira versão"),
                chunk(2, new Location.Page(2), "segunda página")), 0);
        var all = new SearchFilters(condominium, null, null, null, null);
        assertThat(repository.findByKeyword(all, "página", 10)).hasSize(1);

        save(minutes, List.of(chunk(1, new Location.Page(1), "texto novo")), 0);
        assertThat(repository.findByKeyword(all, "página", 10)).isEmpty();
        assertThat(repository.findByKeyword(all, "novo", 10)).hasSize(1);
        assertThat(repository.find(minutes.fileId()).orElseThrow().chunks()).isEqualTo(1);

        repository.replace(minutes, new ChunkedDocument(3, List.of(), "PDF sem texto extraível"), null, null);
        var doc = repository.find(minutes.fileId()).orElseThrow();
        assertThat(doc.state()).isEqualTo("sem_texto");
        assertThat(doc.reason()).isEqualTo("PDF sem texto extraível");
        assertThat(doc.embeddingModel()).isNull();
        assertThat(jdbc.sql("select count(*) from trecho where arquivo_id = :a").param("a", minutes.fileId())
                .query(Integer.class).single()).isZero();
    }

    @Test
    public void errorKeepsReasonWithoutTouchingOldChunks() {
        UUID condominium = UUID.randomUUID();
        IndexFileMessage minutes = request(condominium, "ATA", null, null);
        save(minutes, List.of(chunk(1, new Location.Page(1), "conteúdo antigo")), 0);

        repository.markIndexing(minutes);
        repository.markError(minutes.fileId(), minutes.indexingId(), "Ollama fora do ar");

        var doc = repository.find(minutes.fileId()).orElseThrow();
        assertThat(doc.state()).isEqualTo("erro");
        assertThat(doc.reason()).isEqualTo("Ollama fora do ar");
        assertThat(repository.findByKeyword(new SearchFilters(condominium, null, null, null, null), "antigo", 10))
                .hasSize(1);
    }

    @Test
    public void slashCountsAsSpaceAndExclusionAndPhraseApplyOnVectorSide() {
        UUID condominium = UUID.randomUUID();
        IndexFileMessage payroll = request(condominium, "FOLHA", null, null);
        save(payroll, List.of(
                chunk(1, new Location.Page(1), "Salário base do zelador"),
                chunk(2, new Location.Page(2), "Salário e Vale Transporte"),
                chunk(3, new Location.Page(3), "Despesas Transporte/Combustível do salário"),
                chunk(4, new Location.Page(4), "Folha de pagamento: encargos")), 0);
        var all = new SearchFilters(condominium, null, null, null, null);

        // Keyword: "Transporte/Combustível" now matches transporte and combustivel
        assertThat(repository.findByKeyword(all, "combustivel", 10)).hasSize(1);
        assertThat(repository.findByKeyword(all, "transporte", 10)).hasSize(2);
        assertThat(repository.findByKeyword(all, "salário -transporte", 10)).hasSize(1);

        // Vector: without restriction all 4 come; with exclusion, those with transporte go out; with a phrase, only
        // those containing it
        assertThat(repository.findByVector(all, axis(1), "bge-m3", 10)).hasSize(4);
        List<UUID> withoutTransport = repository.findByVector(all, axis(1), "bge-m3", 10,
                SearchRestrictions.extract("salário -transporte"));
        assertThat(repository.load(withoutTransport)).extracting(FoundChunk::location)
                .containsExactlyInAnyOrder(new Location.Page(1), new Location.Page(4));
        List<UUID> phrase = repository.findByVector(all, axis(1), "bge-m3", 10,
                SearchRestrictions.extract("\"folha de pagamento\" encargos"));
        assertThat(repository.load(phrase)).extracting(FoundChunk::location)
                .containsExactly(new Location.Page(4));
        // A restriction with a stop word only does not empty the search
        assertThat(repository.findByVector(all, axis(1), "bge-m3", 10, "-de")).hasSize(4);
    }

    @Test
    public void indexedWithoutVectorEntersKeywordSearchWithWarning() {
        UUID condominium = UUID.randomUUID();
        IndexFileMessage minutes = request(condominium, "ATA", null, null);
        repository.markIndexing(minutes);
        repository.replace(minutes, new ChunkedDocument(1, List.of(chunk(1, new Location.Page(1),
                "Multa por atraso")), null), null, null, "Indexado só para a busca por palavra: Ollama fora");

        var doc = repository.find(minutes.fileId()).orElseThrow();
        assertThat(doc.state()).isEqualTo("indexado");
        assertThat(doc.embeddingModel()).isNull();
        assertThat(doc.reason()).contains("Ollama fora");
        var all = new SearchFilters(condominium, null, null, null, null);
        assertThat(repository.findByKeyword(all, "multa", 10)).hasSize(1);
        assertThat(repository.findByVector(all, axis(0), "bge-m3", 10)).isEmpty();
    }

    private static void save(IndexFileMessage request, List<Chunk> chunks, int firstAxis) {
        repository.markIndexing(request);
        List<float[]> vectors = new java.util.ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            vectors.add(axis(firstAxis + i));
        }
        repository.replace(request, new ChunkedDocument(2, chunks, null), vectors, "bge-m3");
    }

    private static float[] axis(int i) {
        float[] v = new float[EmbeddingGenerator.DIMENSIONS];
        v[i] = 1f;
        v[EmbeddingGenerator.DIMENSIONS - 1] = 0.1f; // no null vector (undefined cosine)
        return v;
    }

    private static Chunk chunk(int sequence, Location local, String text) {
        return new Chunk(sequence, local, text);
    }

    private static IndexFileMessage request(UUID condominium, String category, LocalDate start, LocalDate end) {
        UUID file = UUID.randomUUID();
        return new IndexFileMessage(1, IndexFileMessage.Operation.INDEXAR, UUID.randomUUID(), file, condominium,
                category, category.toLowerCase() + ".pdf", "c/" + file + ".pdf",
                UUID.randomUUID().toString().replace("-", "").repeat(2), start, end, 1, null, null);
    }
}
