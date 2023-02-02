package searchengine.repositories;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.Index;

import java.util.List;

@Repository
@Transactional
public interface IndexRepository extends JpaRepository<Index, Long> {
    @NotNull List<Index> findAll();
    Index findByPageIdAndLemmaId(long pageId, long lemmaId);
    void deleteByPageId(long pageId);
    void deleteByPageIdIn(List<Long> ids);
    List<Index> findByLemmaId(long lemmaId);
    @Query(value =
        "select i.* " +
        "  from `index` i " +
        "  join `page` p " +
        "    on p.`id` = i.`page_id` " +
        " where i.`lemma_id` = ?1" +
        "   and p.`site_id` = ?2",
        nativeQuery = true
    )
    List<Index> findByLemmaIdAndSiteId(long lemmaId, long siteId);
}
