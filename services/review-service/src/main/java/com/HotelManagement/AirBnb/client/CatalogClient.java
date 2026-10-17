package com.HotelManagement.AirBnb.client;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** catalog-service's internal API: is a hotel public, and the rating read model used by guest search. */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    public record PublicHotel(Long id, String name) {
    }

    /** Absolute values: repeated pushes are idempotent. */
    public record RatingUpdate(Long hotelId, Long ratingSum, Long ratingCount) {
    }

    private static final String SERVICE = "catalog-service";

    private final RestClient client;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient client) {
        this.client = client;
    }

    /** The hotel if guests can see it (active, not deleted); 404 "Hotel not found" otherwise. */
    public PublicHotel requirePublicHotel(Long hotelId) {
        log.debug("Checking with catalog that hotel {} is public", hotelId);
        try {
            return client.get().uri("/internal/hotels/{id}/public", hotelId).retrieve().body(PublicHotel.class);
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "public hotel", e);
        }
    }

    public void pushRatings(List<RatingUpdate> updates) {
        if (updates.isEmpty()) {
            return;
        }
        try {
            client.put().uri("/internal/hotels/ratings").contentType(MediaType.APPLICATION_JSON).body(updates)
                    .retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "push ratings", e);
        }
    }
}
