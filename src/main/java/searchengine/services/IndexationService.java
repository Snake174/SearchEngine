package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import searchengine.LemmaFinder;
import searchengine.config.Site;
import searchengine.config.SitesList;
import searchengine.models.Index;
import searchengine.models.Lemma;
import searchengine.models.Page;
import searchengine.models.SiteStatus;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexationService {
    @Autowired
    private final SitesList sites;
    @Autowired
    private final SiteRepository siteRepository;

    @Autowired
    private final PageRepository pageRepository;

    @Autowired
    private final LemmaRepository lemmaRepository;

    @Autowired
    private final IndexRepository indexRepository;

    private final Object lock = new Object();

    private final Set<String> excludedProtocols = Collections.synchronizedSet(new HashSet<>(
        Arrays.asList("tel", "mailto", "file")
    ));

    private final Set<String> excludedExtensions = Collections.synchronizedSet(new HashSet<>(
        Arrays.asList(
            "pdf", "txt", "djv", "djvu", "chm",
            "doc", "docx", "csv", "xls", "xlsx",
            "zip", "nc", "jpg", "ppt", "fig",
            "m", "png", "tiff", "bmp", "jpeg",
            "rar", "7z"
        )
    ));

    private volatile boolean indexingStarted;
    private String lastError;

    @Value("${search-bot.user-agent}")
    private String userAgent;

    @Value("${search-bot.referrer}")
    private String referrer;

    @Value("${search-bot.timeout}")
    private int timeout;

    HashMap<String, Integer> indexingResults = new HashMap<>();

    private final LemmaFinder lemmaFinder = new LemmaFinder();

    public void scan() {
        indexingStarted = true;

        for (Site site : sites.getSites()) {
            indexingResults.put(normalizeHost(site.getUrl()), -1);
        }

        for (Site site : sites.getSites()) {
            checkSite(site);
        }
    }

    public void rescanPage(String url) {
        try {
            URL curl = new URL(url);
            String host = normalizeHost(curl.getProtocol() + "://" + curl.getHost());
            searchengine.models.Site siteModel = siteRepository.findByUrl(host);

            Document doc = Jsoup.connect(curl.toString())
                .userAgent(userAgent)
                .referrer(referrer)
                .get();

            savePage(curl, doc.outerHtml(), doc.connection().response().statusCode(), siteModel);
        } catch (Exception ignored) {
        }
    }

    public void setIndexingStarted(boolean indexingStarted) {
        this.indexingStarted = indexingStarted;
    }

    public boolean isIndexingStarted() {
        return indexingStarted;
    }

    public String getLastError() {
        return lastError;
    }

    private void checkSite(Site site) {
        String host = normalizeHost(site.getUrl());

        try {
            searchengine.models.Site siteModel = saveSite(site, host);
            lemmaRepository.deleteBySiteId(siteModel.getId());

            List<Long> pageIds = siteModel.getPages().stream().map(Page::getId).toList();

            if (pageIds.size() > 0) {
                indexRepository.deleteByPageIdIn(pageIds);
            }

            pageRepository.deleteBySiteId(siteModel.getId());

            URL url = new URL(host);

            CompletableFuture
                .runAsync(() -> scanPages(siteModel, url), ForkJoinPool.commonPool())
                .thenAccept(s -> {
                    siteModel.setStatus(SiteStatus.INDEXED);
                    siteModel.setStatusTime(new Date());
                    siteRepository.save(siteModel);
                    indexingResults.put(host, 0);
                    lastError = "";

                    if (!indexingResults.containsValue(-1)) {
                        indexingStarted = false;
                    }
                });
        } catch (Exception ignored) {
        }
    }

    private void scanPages(searchengine.models.Site siteModel, URL rootURL) {
        try {
            Document doc = Jsoup.connect(rootURL.toString())
                .userAgent(userAgent)
                .referrer(referrer)
                .get();

            savePage(rootURL, doc.outerHtml(), doc.connection().response().statusCode(), siteModel);

            Elements links = doc.select("a[href]");

            for (Element link : links) {
                if (!indexingStarted) {
                    return;
                }

                Thread.sleep(timeout);
                URL url = getURL(link.attr("abs:href"), siteModel);

                if (url != null) {
                    scanPages(siteModel, url);
                }
            }
        } catch (Exception ignored) {
        }
    }

    @Transactional
    private void savePage(URL url, String content, int statusCode, searchengine.models.Site siteModel) {
        synchronized (lock) {
            Page pageModel = new Page();
            pageModel.setSite(siteModel);
            pageModel.setSiteId(siteModel.getId());
            pageModel.setPath(url.getPath());
            pageModel.setContent(content);
            pageModel.setCode(statusCode);

            pageRepository.save(pageModel);
            lemmatize(content, pageModel.getId(), siteModel.getId());

            System.err.println(url);
        }
    }

    @Transactional
    private void lemmatize(String content, long pageId, long siteId) {
        synchronized (lock) {
            HashMap<String, Integer> lemmas = lemmaFinder.textToLemmas(content);

            for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
                String l = entry.getKey();
                Integer f = entry.getValue();
                Lemma lemma = lemmaRepository.findByLemma(l);

                if (lemma == null) {
                    lemma = new Lemma();
                    lemma.setFrequency(1);
                    lemma.setLemma(l);
                    lemma.setSiteId(siteId);
                } else {
                    lemma.setFrequency(lemma.getFrequency() + 1);
                }

                lemmaRepository.save(lemma);
                saveIndex(pageId, lemma.getId(), f);
            }
        }
    }

    @Transactional
    private void saveIndex(long pageId, long lemmaId, int frequency) {
        synchronized (lock) {
            Index index = indexRepository.findByPageIdAndLemmaId(pageId, lemmaId);

            if (index == null) {
                index = new Index();
                index.setPageId(pageId);
                index.setLemmaId(lemmaId);
            }

            index.setRank(frequency);
            indexRepository.save(index);
        }
    }

    private URL getURL(String href, searchengine.models.Site siteModel) {
        synchronized (lock) {
            if (href.contains("#") || href.contains("?")) {
                return null;
            }

            URL url;

            try {
                url = new URL(normalizeHost(href));
            } catch (MalformedURLException ignored) {
                return null;
            }

            if (!siteModel.getUrl().contains(url.getHost()) ||
                excludedProtocols.contains(url.getProtocol()) ||
                excludedExtensions.contains(getFileExtension(url.getFile()))) {
                return null;
            }

            Page pageModel = pageRepository.findDistinctBySiteIdAndPath(siteModel.getId(), url.getPath());

            if (pageModel != null) {
                return null;
            }

            return url;
        }
    }

    private String getFileExtension(String fileName) {
        synchronized (lock) {
            return Optional.ofNullable(fileName)
                .filter(f -> f.contains("."))
                .map(f -> f.substring(fileName.lastIndexOf(".") + 1).toLowerCase())
                .orElse("");
        }
    }

    private searchengine.models.Site saveSite(Site site, String host) {
        searchengine.models.Site siteModel = siteRepository.findByUrl(host);

        if (siteModel == null) {
            siteModel = new searchengine.models.Site();
            siteModel.setUrl(host);
            siteModel.setName(site.getName());
        }

        siteModel.setStatus(SiteStatus.INDEXING);
        siteModel.setLastError("");
        siteModel.setStatusTime(new Date());

        siteRepository.save(siteModel);

        return siteModel;
    }

    private String normalizeHost(String host) {
        return host.endsWith("/") ? host.substring(0, host.length() - 1) : host;
    }
}
