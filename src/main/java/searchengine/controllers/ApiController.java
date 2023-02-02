package searchengine.controllers;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import searchengine.LemmaFinder;
import searchengine.dto.api.Response;
import searchengine.dto.api.ResponseSearchData;
import searchengine.dto.statistics.StatisticsResponse;
import searchengine.models.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.services.IndexationService;
import searchengine.services.StatisticsService;

import java.net.HttpURLConnection;
import java.net.URL;
import java.text.BreakIterator;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class ApiController {
    private final StatisticsService statisticsService;
    private final IndexationService indexationService;
    @Autowired
    private final SiteRepository siteRepository;
    @Autowired
    private final IndexRepository indexRepository;
    @Autowired
    private final LemmaRepository lemmaRepository;
    @Autowired
    private final PageRepository pageRepository;

    @Value("${search-bot.user-agent}")
    private String userAgent;

    @Autowired
    public ApiController(StatisticsService statisticsService,
                         IndexationService indexationService,
                         SiteRepository siteRepository,
                         IndexRepository indexRepository,
                         LemmaRepository lemmaRepository,
                         PageRepository pageRepository) {
        this.statisticsService = statisticsService;
        this.indexationService = indexationService;
        this.siteRepository = siteRepository;
        this.indexRepository = indexRepository;
        this.lemmaRepository = lemmaRepository;
        this.pageRepository = pageRepository;
    }

    @GetMapping("/statistics")
    public ResponseEntity<StatisticsResponse> statistics() {
        return ResponseEntity.ok(statisticsService.getStatistics());
    }

    @GetMapping("/startIndexing")
    public ResponseEntity<Response> startIndexing() {
        Response response = new Response();

        if (indexationService.isIndexingStarted()) {
            response.setError("Индексация уже запущена");
        } else {
            indexationService.scan();
            response.setResult(true);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/stopIndexing")
    public ResponseEntity<Response> stopIndexing() {
        Response response = new Response();

        if (!indexationService.isIndexingStarted()) {
            response.setError("Индексация не запущена");
        } else {
            List<Site> sites = siteRepository.findAll();

            for (Site site : sites) {
                site.setStatusTime(new Date());
                site.setStatus(SiteStatus.INDEXED);
                site.setLastError("");

                siteRepository.save(site);
            }

            indexationService.setIndexingStarted(false);
            response.setResult(true);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PostMapping("/indexPage")
    public ResponseEntity<Response> indexPage(@RequestParam String url) {
        Response response = new Response();

        if (!isValidURL(url)) {
            response.setError("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        } else {
            indexationService.rescanPage(url);
            response.setResult(true);
        }

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @GetMapping("/search")
    public ResponseEntity<Response> search(
        @RequestParam String query,
        @RequestParam(required = false, defaultValue = "") String site,
        @RequestParam(required = false, defaultValue = "0") String offset,
        @RequestParam(required = false, defaultValue = "20") String limit) {
        Response response = new Response();

        if (query.isBlank()) {
            response.setError("Задан пустой поисковый запрос");

            return new ResponseEntity<>(response, HttpStatus.OK);
        }

        List<ResponseSearchData> data = getSearchData(query, site, offset, limit);

        response.setResult(true);
        response.setCount(data.size());
        response.setData(data);

        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    private boolean isValidURL(String url) {
        try {
            URL curl = new URL(url);

            if (!isSiteInList(curl)) {
                return false;
            }

            HttpURLConnection connection = (HttpURLConnection) curl.openConnection();
            connection.setRequestMethod("HEAD");
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("User-Agent", userAgent);
            connection.connect();

            return connection.getResponseCode() == 200 && connection.getContentType().contains("text/html");
        } catch (Exception ignored) {
        }

        return false;
    }

    private boolean isSiteInList(URL url) {
        String host = url.getProtocol() + "://" + url.getHost();
        searchengine.models.Site siteModel = siteRepository.findByUrl(host);

        return siteModel != null;
    }

    private List<ResponseSearchData> getSearchData(String query, String site, String offset, String limit) {
        List<ResponseSearchData> data = new ArrayList<>();

        LemmaFinder lemmaFinder = new LemmaFinder();
        HashMap<String, Integer> lemmas = lemmaFinder.textToLemmas(query);
        Site siteModel = null;

        if (!site.isBlank()) {
            siteModel = siteRepository.findByUrl(site);
        }

        LinkedHashMap<String, Integer> sortedLemmas = getSortedLemmas(lemmas);

        if (sortedLemmas.size() == 0) {
            return data;
        }

        Set<Page> foundedPages = getFoundedPages(sortedLemmas, siteModel);
        float rMax = 0f;

        for (Page foundedPage : foundedPages) {
            float rAbs = getAbsoluteRelevance(lemmas, foundedPage);

            if (rAbs > rMax) {
                rMax = rAbs;
            }

            ResponseSearchData searchData = new ResponseSearchData();
            searchData.setSite(foundedPage.getSite().getUrl());
            searchData.setUri(foundedPage.getPath());
            searchData.setTitle(getPageTitle(foundedPage));
            searchData.setSiteName(foundedPage.getSite().getName());
            searchData.setSnippet(getSnippet(query, foundedPage));
            searchData.setRelevance(rAbs);

            data.add(searchData);
        }

        if (rMax > 0) {
            for (ResponseSearchData rsd : data) {
                rsd.setRelevance(rsd.getRelevance() / rMax);
            }
        }

        data.sort(Comparator.comparing(ResponseSearchData::getRelevance).reversed());

        return data;
    }

    private Set<Page> getFoundedPages(LinkedHashMap<String, Integer> sortedLemmas, Site siteModel) {
        String firstLemma = sortedLemmas.entrySet().iterator().next().getKey();
        List<Long> pageIds = getPageIds(firstLemma, siteModel);
        sortedLemmas.remove(firstLemma);

        for (Map.Entry<String, Integer> entry : sortedLemmas.entrySet()) {
            if (pageIds.size() > 3) {
                pageIds.retainAll(getPageIds(entry.getKey(), siteModel));
            }
        }

        return new HashSet<>(pageRepository.findByIdIn(pageIds));
    }

    private List<Long> getPageIds(String lemma, Site siteModel) {
        List<Long> pageIds;

        if (siteModel == null) {
            pageIds = pageRepository.findIdsByLemmaName(
                Collections.singleton(lemma)
            );
        } else {
            pageIds = pageRepository.findIdsByLemmaNameAndSiteId(
                Collections.singleton(lemma),
                siteModel.getId()
            );
        }

        return pageIds;
    }

    private String getPageTitle(Page page) {
        Pattern p = Pattern.compile("<title>(.*?)</title>");
        Matcher m = p.matcher(page.getContent());
        String pageTitle = "";

        while (m.find()) {
            pageTitle = m.group(1);
        }

        return pageTitle;
    }

    private String getSnippet(String query, Page page) {
        Set<String> sentencies = new HashSet<>();
        String content = "";

        try {
            Document doc = Jsoup.parse(page.getContent());
            content = doc.body().text();
        } catch (Exception ignored) {
        }

        for (String word : query.trim().split("\\s+")) {
            sentencies.add(getSentence(content, word));
        }

        return getMostMatchedSentence(sentencies, query);
    }

    private String getMostMatchedSentence(Set<String> sentencies, String query) {
        String result = "";
        int maxCounts = 0;

        for (String s : sentencies) {
            int count = 0;

            for (String word : query.trim().split("\\s+")) {
                if (s.toLowerCase().contains(word.toLowerCase())) {
                    s = StringUtils.replaceIgnoreCase(s, word, "<b>" + word + "</b>");
                    count++;
                }
            }

            if (count > maxCounts) {
                maxCounts = count;
                result = s;
            }
        }

        return result;
    }

    private String getSentence(String input, String word) {
        BreakIterator bi = BreakIterator.getSentenceInstance();
        bi.setText(input);
        int lastIndex = bi.first();

        while (lastIndex != BreakIterator.DONE) {
            int firstIndex = lastIndex;
            lastIndex = bi.next();

            if (lastIndex != BreakIterator.DONE) {
                String sentence = input.substring(firstIndex, lastIndex);

                if (sentence.toLowerCase().contains(word.toLowerCase())) {
                    return sentence;
                }
            }
        }

        return "";
    }

    private float getAbsoluteRelevance(HashMap<String, Integer> lemmas, Page page) {
        float rAbs = 0f;

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            Lemma l = lemmaRepository.findByLemma(entry.getKey());

            if (l != null) {
                Index index = indexRepository.findByPageIdAndLemmaId(page.getId(), l.getId());

                if (index != null) {
                    rAbs += index.getRank();
                }
            }
        }

        return rAbs;
    }

    private LinkedHashMap<String, Integer> getSortedLemmas(HashMap<String, Integer> lemmas) {
        SortedMap<String, Integer> lemmasFrequency = new TreeMap<>();

        for (Map.Entry<String, Integer> entry : lemmas.entrySet()) {
            String l = entry.getKey();
            Lemma lemmaModel = lemmaRepository.findByLemma(l);

            if (lemmaModel == null) {
                continue;
            }

            lemmasFrequency.put(l, lemmaModel.getFrequency());
        }

        return lemmasFrequency.entrySet().stream()
            .sorted(Map.Entry.comparingByValue())
            .collect(Collectors.toMap(Map.Entry::getKey,
                Map.Entry::getValue,
                (e1, e2) -> e1, LinkedHashMap::new)
            );
    }
}
