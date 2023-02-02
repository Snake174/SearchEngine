package searchengine.dto.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.util.List;

@Data
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class Response {
    private boolean result;
    @Builder.Default
    private String error = null;
    @Builder.Default
    private Integer count = null;
    @Builder.Default
    private List<ResponseSearchData> data = null;
}
