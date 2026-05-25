package com.vmudrud.daftiescanner.search.client;

import com.vmudrud.daftiescanner.common.listing.ListingResult;
import com.vmudrud.daftiescanner.common.listing.SearchResult;
import com.vmudrud.daftiescanner.common.tenant.FilterSpec;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DaftClientTest {

    private MockRestServiceServer server;
    private DaftClient client;

    private static final FilterSpec FILTER = new FilterSpec(
            "residential-to-rent",
            new FilterSpec.Range(1200, 2300),
            new FilterSpec.Range(1, 2),
            List.of("42", "43"), List.of()
    );

    @BeforeEach
    void setUp() {
        var builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        client = new DaftClient(builder.build());
    }

    @Test
    void search_parsesListingsAndPaging() {
        expectFixture();
        SearchResult result = client.search(FILTER);
        assertThat(result.listings()).hasSize(1);
        assertThat(result.paging().totalResults()).isEqualTo(1);
    }

    @Test
    void search_parsesListingFields() {
        expectFixture();
        ListingResult listing = client.search(FILTER).listings().get(0).listing();
        assertThat(listing.id()).isEqualTo(12345678L);
        assertThat(listing.price()).isEqualTo("€2,100 per month");
        assertThat(listing.seoFriendlyPath()).isEqualTo("/for-rent/apartment/somewhere-dublin-7/12345678");
        assertThat(listing.seller().showContactForm()).isTrue();
        assertThat(listing.media().images()).hasSize(1);
        assertThat(listing.ber().rating()).isEqualTo("C3");
        assertThat(listing.facilities()).hasSize(2);
    }

    @Test
    void search_withShapeIds_sendsStoredShapesGeoSearchType() {
        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.containsString("\"geoSearchType\":\"STORED_SHAPES\"")))
              .andExpect(content().string(Matchers.containsString("\"storedShapeIds\":[\"42\",\"43\"]")))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(FILTER);
    }

    @Test
    void search_withNoShapeIds_omitsGeoSearchType() {
        var ireland = new FilterSpec("residential-to-rent",
                new FilterSpec.Range(1200, 2300),
                new FilterSpec.Range(1, 2),
                List.of(), List.of());

        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.containsString("\"storedShapeIds\":[]")))
              .andExpect(content().string(Matchers.not(Matchers.containsString("\"geoSearchType\""))))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(ireland);
    }

    @Test
    void search_withNumBedsRange_sendsBothRanges() {
        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.containsString("\"name\":\"rentalPrice\"")))
              .andExpect(content().string(Matchers.containsString("\"name\":\"numBeds\"")))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(FILTER);
    }

    @Test
    void search_withMissingNumBedsMin_omitsNumBedsRange() {
        var noMin = new FilterSpec("residential-to-rent",
                new FilterSpec.Range(1200, 2300),
                new FilterSpec.Range(null, 3),
                List.of("42"), List.of());

        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.containsString("\"name\":\"rentalPrice\"")))
              .andExpect(content().string(Matchers.not(Matchers.containsString("\"name\":\"numBeds\""))))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(noMin);
    }

    @Test
    void search_withMissingNumBedsMax_omitsNumBedsRange() {
        var noMax = new FilterSpec("residential-to-rent",
                new FilterSpec.Range(1200, 2300),
                new FilterSpec.Range(1, null),
                List.of("42"), List.of());

        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.not(Matchers.containsString("\"name\":\"numBeds\""))))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(noMax);
    }

    @Test
    void search_withBothNumBedsBoundsMissing_omitsNumBedsRange() {
        var noBeds = new FilterSpec("residential-to-rent",
                new FilterSpec.Range(1200, 2300),
                new FilterSpec.Range(null, null),
                List.of("42"), List.of());

        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.not(Matchers.containsString("\"name\":\"numBeds\""))))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(noBeds);
    }

    @Test
    void search_withBlankShapeIds_treatedAsEmpty() {
        var blanks = new FilterSpec("residential-to-rent",
                new FilterSpec.Range(1200, 2300),
                new FilterSpec.Range(1, 2),
                List.of("", " "), List.of());

        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andExpect(content().string(Matchers.containsString("\"storedShapeIds\":[]")))
              .andExpect(content().string(Matchers.not(Matchers.containsString("\"geoSearchType\""))))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));

        client.search(blanks);
    }

    private void expectFixture() {
        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));
    }
}
