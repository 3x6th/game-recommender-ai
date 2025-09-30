package ru.perevalov.gamerecommenderai.util;

import lombok.experimental.UtilityClass;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Map;

@Slf4j
@UtilityClass
public class UrlHelper {
    /**
     * Builds a URI from the given components.
     *
     * <p>Example usage:
     * <pre>
     * Map<String, String> params = Map.of("key", "value");
     * URI uri = UrlHelper.buildUri("https", "example.com", "/api/resource", params);
     * </pre>
     *
     * @param scheme      the URI scheme (e.g., "http" or "https")
     * @param host        the host (e.g., "example.com")
     * @param path        the path of the endpoint (e.g., "/api/resource")
     * @param queryParams optional query parameters; can be null or empty
     * @return the constructed {@link URI}
     */
    public URI buildUri(String scheme, String host, String path, Map<String, ?> queryParams) {
        UriComponentsBuilder builder = UriComponentsBuilder.newInstance()
                .scheme(scheme)
                .host(host)
                .path(path);

        if (queryParams != null && !queryParams.isEmpty()) {
            queryParams.forEach(builder::queryParam);
        }
        URI uri = builder.build().toUri();
        log.debug("Constructed URI: {}", uri);
        return uri;
    }
}
