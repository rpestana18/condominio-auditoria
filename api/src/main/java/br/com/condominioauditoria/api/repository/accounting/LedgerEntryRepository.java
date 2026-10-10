package br.com.condominioauditoria.api.repository.accounting;

import br.com.condominioauditoria.api.model.accounting.LedgerEntry;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {
    List<LedgerEntry> findByFileIdOrderBySequence(UUID fileId);

    /** Largest real outflows of the period (without transfers between funds). */
    List<LedgerEntry> findByFileIdAndInterFundTransferFalseOrderByDebitDesc(UUID fileId, Limit limit);

    /**
     * Filtered query for gRPC (mcp). No nulls in the parameters: the caller passes wide date limits, empty text and the
     * list of funds already resolved.
     */
    @Query("""
            select l from LedgerEntry l
            where l.condominiumId = :condominiumId
              and l.date >= :start and l.date <= :end
              and (:allFunds = true or l.fundId in :funds)
              and (:text = '' or lower(l.memo) like :text
                   or lower(coalesce(l.supplier, '')) like :text
                   or lower(coalesce(l.accountName, '')) like :text)
              and (:outflowsOnly = false or (l.debit > 0 and l.interFundTransfer = false))
            order by l.date, l.fileId, l.sequence""")
    List<LedgerEntry> search(UUID condominiumId, java.time.LocalDate start, java.time.LocalDate end,
            boolean allFunds, java.util.Collection<UUID> funds, String text, boolean outflowsOnly, Limit limit);

    /**
     * Debits of a fund in the period, only from files in the trial balance and cash flow category, without transfers
     * between funds and only with a cash flow account: the accounts that go into the account mapping (RF-03.1.4).
     */
    @Query("""
            select l from LedgerEntry l
            where l.condominiumId = :condominiumId and l.fundId = :fundId
              and l.date >= :start and l.date <= :end
              and l.debit <> 0 and l.interFundTransfer = false and l.accountCode is not null
              and l.fileId in (select f.id from SourceFile f
                               where f.category = br.com.condominioauditoria.api.model.enums.FileCategory.TRIAL_BALANCE)
            order by l.date, l.fileId, l.sequence""")
    List<LedgerEntry> debitsWithAccount(UUID condominiumId, UUID fundId, java.time.LocalDate start,
            java.time.LocalDate end);

    /** Entries of the chosen cash flows in the period, from every fund (budget vs. actual). */
    List<LedgerEntry> findByFileIdInAndDateBetween(java.util.Collection<UUID> files, java.time.LocalDate start,
            java.time.LocalDate end);

    @Modifying
    @Query("delete from LedgerEntry l where l.fileId = :fileId")
    void deleteByFileId(UUID fileId);
}
