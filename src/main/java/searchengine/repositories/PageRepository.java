package searchengine.repositories;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.Page;

import java.util.List;
import java.util.Set;

@Repository
@Transactional
public interface PageRepository extends JpaRepository<Page, Long> {
    @NotNull List<Page> findAll();
    Page findDistinctBySiteIdAndPath(long siteId, String path);
    Page findByPath(String path);
    void deleteBySiteId(long siteId);
    int countBySiteId(long siteId);
    Page findById(long id);
    List<Page> findByIdIn(List<Long> ids);
    @Query(value =
        "select p.id, " +
        "       count(1) " +
        "  from page p, " +
        "       `index` i, " +
        "       lemma l " +
        " where p.id = i.page_id " +
        "   and l.id = i.lemma_id " +
        "   and l.lemma in (?1) " +
        "  group by p.id " +
        "  order by count(1) desc " +
        "  limit 0, 10",
        nativeQuery = true
    )
    List<Long> findIdsByLemmaName(Set<String> lemmaNames);
    @Query(value =
        "select p.id, " +
        "       count(1) " +
        "  from page p, " +
        "       `index` i, " +
        "       lemma l " +
        " where p.id = i.page_id " +
        "   and l.id = i.lemma_id " +
        "   and l.lemma in (?1) " +
        "   and p.site_id = ?2" +
        "  group by p.id " +
        "  order by count(1) desc " +
        "  limit 0, 10",
        nativeQuery = true
    )
    List<Long> findIdsByLemmaNameAndSiteId(Set<String> lemmaNames, long siteId);
}
