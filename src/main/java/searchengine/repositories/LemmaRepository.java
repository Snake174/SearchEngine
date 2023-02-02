package searchengine.repositories;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.Lemma;

import java.util.List;

@Repository
@Transactional
public interface LemmaRepository extends JpaRepository<Lemma, Long> {
    @NotNull List<Lemma> findAll();
    Lemma findByLemma(String lemma);
    int countBySiteId(long siteId);
    void deleteBySiteId(long siteId);
}
