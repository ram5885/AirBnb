package com.HotelManagement.AirBnb.client;

import org.slf4j.LoggerFactory;
import org.slf4j.Logger;
import com.HotelManagement.AirBnb.client.CatalogDtos.AttachRequest;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldRequest;
import com.HotelManagement.AirBnb.client.CatalogDtos.HoldResponse;
import com.HotelManagement.AirBnb.client.CatalogDtos.HotelCounts;
import com.HotelManagement.AirBnb.client.CatalogDtos.ReacquireResponse;
import com.HotelManagement.AirBnb.client.CatalogDtos.ReleaseResponse;
import com.HotelManagement.AirBnb.client.CatalogDtos.SummariesRequest;
import com.HotelManagement.AirBnb.client.CatalogDtos.SummariesResponse;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * catalog-service's internal API: inventory holds (all idempotent per hold key) and current hotel/room facts.
 * Failures are translated by DownstreamErrors (400/404/409 keep their message, the rest is 503).
 */
@Component
public class CatalogClient {

    private static final Logger log = LoggerFactory.getLogger(CatalogClient.class);

    private static final String SERVICE = "catalog-service";

    private final RestClient client;

    public CatalogClient(@Qualifier("catalogRestClient") RestClient client) {
        this.client = client;
    }

    public HoldResponse hold(HoldRequest request) {
        log.debug("Asking catalog to hold rooms (hold {})", request.holdKey());
        try {
            return client.post().uri("/internal/inventory/holds").contentType(MediaType.APPLICATION_JSON)
                    .body(request).retrieve().body(HoldResponse.class);
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "hold", e);
        }
    }

    public void attach(UUID holdKey, Long bookingId) {
        try {
            client.post().uri("/internal/inventory/holds/{key}/attach", holdKey).contentType(MediaType.APPLICATION_JSON)
                    .body(new AttachRequest(bookingId)).retrieve().toBodilessEntity();
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "attach", e);
        }
    }

    /** RELEASED, or NOT_FOUND when catalog has no hold for the key. */
    public String release(UUID holdKey) {
        try {
            ReleaseResponse r = client.post().uri("/internal/inventory/holds/{key}/release", holdKey)
                    .retrieve().body(ReleaseResponse.class);
            return r == null ? null : r.status();
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "release", e);
        }
    }

    public boolean reacquire(UUID holdKey) {
        try {
            ReacquireResponse r = client.post().uri("/internal/inventory/holds/{key}/reacquire", holdKey)
                    .retrieve().body(ReacquireResponse.class);
            return r != null && r.reacquired();
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "reacquire", e);
        }
    }

    public List<HoldResponse> unattached(int olderThanMinutes) {
        try {
            List<HoldResponse> holds = client.get()
                    .uri(u -> u.path("/internal/inventory/holds/unattached").queryParam("olderThanMinutes", olderThanMinutes).build())
                    .retrieve().body(new ParameterizedTypeReference<>() {
                    });
            return holds == null ? List.of() : holds;
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "list unattached holds", e);
        }
    }

    public SummariesResponse summaries(Collection<Long> hotelIds, Collection<Long> roomIds) {
        try {
            SummariesResponse r = client.post().uri("/internal/hotels/summaries").contentType(MediaType.APPLICATION_JSON)
                    .body(new SummariesRequest(List.copyOf(hotelIds), List.copyOf(roomIds)))
                    .retrieve().body(SummariesResponse.class);
            return r == null ? new SummariesResponse(List.of(), List.of()) : r;
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "hotel summaries", e);
        }
    }

    public HotelCounts hotelCounts(Long ownerId) {
        try {
            return client.get().uri("/internal/owners/{id}/hotel-counts", ownerId).retrieve().body(HotelCounts.class);
        } catch (RestClientException e) {
            throw DownstreamErrors.translate(SERVICE, "hotel counts", e);
        }
    }
}
