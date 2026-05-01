package com.vmudrud.daftiescanner.client;

import com.vmudrud.daftiescanner.client.dto.SearchRequest;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.GeoFilter;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.NameValues;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.PagingParam;
import com.vmudrud.daftiescanner.client.dto.SearchRequest.RangeParam;
import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.client.dto.SearchResult;
import com.vmudrud.daftiescanner.config.dto.FilterSpec;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.util.List;

@Slf4j
@RequiredArgsConstructor
public class DaftClient {

    private static final String GEO_SEARCH_TYPE = "STORED_SHAPES";
    private static final String SORT = "publishDateDesc";
    private static final String AD_STATE = "published";
    private static final String FILTER_AD_STATE = "adState";
    private static final String RANGE_RENTAL_PRICE = "rentalPrice";
    private static final String RANGE_NUM_BEDS = "numBeds";
    private static final String PAGE_FROM = "0";
    private static final String PAGE_SIZE = "20";
    private static final String MEDIA_SIZE_PARAM = "mediaSizes=size720x480&mediaSizes=size72x52";

    // Pre-built absolute URI — Spring's RestClient.uri(URI) uses it as-is, bypassing
    // UriComponentsBuilder which would strip the leading empty query param ("?&...").
    // Daft's gateway appears to require this exact format.
    private static final URI LISTINGS_URI = URI.create(
            "https://gateway.daft.ie/api/v2/ads/listings?&" + MEDIA_SIZE_PARAM);

    private final RestClient restClient;

    @Retryable(retryFor = ResourceAccessException.class, maxAttempts = 2, backoff = @Backoff(500))
    public SearchResult search(FilterSpec filter) {
        SearchResult result = restClient.post()
                .uri(LISTINGS_URI)
                .contentType(MediaType.APPLICATION_JSON)
                .body(toRequest(filter))
                .retrieve()
                .body(SearchResult.class);
        logListings(result);
        return result;
    }

    // Toggle via: logging.level.com.vmudrud.daftiescanner.client.DaftClient: DEBUG
    private static void logListings(SearchResult result) {
        if (!log.isDebugEnabled() || result == null || result.listings() == null) {
            return;
        }
        result.listings().forEach(wrapper -> {
            ListingResult l = wrapper.listing();
            log.debug("listing id={} title=\"{}\" price=\"{}\" path={}",
                    l.id(), l.title(), l.price(), l.seoFriendlyPath());
        });
    }

    private SearchRequest toRequest(FilterSpec filter) {
        return new SearchRequest(
                filter.section(),
                List.of(new NameValues(FILTER_AD_STATE, List.of(AD_STATE))),
                List.of(),
                List.of(
                        new RangeParam(str(filter.rentalPrice().from()), str(filter.rentalPrice().to()), RANGE_RENTAL_PRICE),
                        new RangeParam(str(filter.numBeds().from()), str(filter.numBeds().to()), RANGE_NUM_BEDS)
                ),
                new PagingParam(PAGE_FROM, PAGE_SIZE),
                new GeoFilter(filter.storedShapeIds(), GEO_SEARCH_TYPE),
                StringUtils.EMPTY,
                SORT
        );
    }

    private static String str(int value) {
        return String.valueOf(value);
    }
}
