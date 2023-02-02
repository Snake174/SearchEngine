package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.dto.statistics.DetailedStatisticsItem;
import searchengine.dto.statistics.StatisticsData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.dto.statistics.TotalStatistics;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.util.*;

@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {
    private final Random random = new Random();
    @Autowired
    private final SitesList sites;

    @Autowired
    private final IndexationService indexationService;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Override
    public StatisticsResponse getStatistics() {
        TotalStatistics total = new TotalStatistics();
        total.setSites(sites.getSites().size());
        total.setIndexing(indexationService.isIndexingStarted());

        List<DetailedStatisticsItem> detailed = new ArrayList<>();
        List<Site> sitesList = sites.getSites();

        for (Site site : sitesList) {
            searchengine.models.Site siteModel = siteRepository.findByUrl(site.getUrl());
            int pages = 0;
            int lemmas = 0;
            String status = "FAILED";
            long statusTime = 0;

            if (siteModel != null) {
                pages = pageRepository.countBySiteId(siteModel.getId());
                lemmas = lemmaRepository.countBySiteId(siteModel.getId());
                status = siteModel.getStatus().name();
                statusTime = siteModel.getStatusTime().getTime();
            }

            DetailedStatisticsItem item = new DetailedStatisticsItem();
            item.setName(site.getName());
            item.setUrl(site.getUrl());
            item.setPages(pages);
            item.setLemmas(lemmas);
            item.setStatus(status);
            item.setError(indexationService.getLastError() == null ? "" : indexationService.getLastError());
            item.setStatusTime(statusTime);

            total.setPages(total.getPages() + pages);
            total.setLemmas(total.getLemmas() + lemmas);

            detailed.add(item);
        }

        StatisticsResponse response = new StatisticsResponse();
        StatisticsData data = new StatisticsData();
        data.setTotal(total);
        data.setDetailed(detailed);
        response.setStatistics(data);
        response.setResult(true);

        return response;
    }
}
