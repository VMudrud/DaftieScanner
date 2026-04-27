package com.vmudrud.daftiescanner.client;

import com.vmudrud.daftiescanner.client.dto.ListingResult;
import com.vmudrud.daftiescanner.client.dto.SearchResult;
import com.vmudrud.daftiescanner.config.dto.FilterSpec;
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
            List.of("42", "43")
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

    private void expectFixture() {
        server.expect(requestTo(containsString("/api/v2/ads/listings")))
              .andExpect(method(HttpMethod.POST))
              .andRespond(withSuccess(new ClassPathResource("daft/sample-response.json"), MediaType.APPLICATION_JSON));
    }
}
