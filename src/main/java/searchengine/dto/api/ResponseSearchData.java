package searchengine.dto.api;

import lombok.Data;

@Data
public class ResponseSearchData {
    private String site;
    private String siteName;
    private String uri;
    private String title;
    private String snippet;
    private float relevance;
}
