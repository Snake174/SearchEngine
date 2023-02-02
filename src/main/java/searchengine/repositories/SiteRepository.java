package searchengine.repositories;

import org.jetbrains.annotations.NotNull;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import searchengine.models.Site;

import java.util.List;

@Repository
@Transactional
public interface SiteRepository extends JpaRepository<Site, Long> {
    @NotNull List<Site> findAll();
    Site findByUrl(String url);
}
