package com.HotelManagement.AirBnb.client;

import com.HotelManagement.AirBnb.shared.logging.RequestLoggingFilter;
import com.HotelManagement.AirBnb.shared.security.InternalApiFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * RestClients for the internal APIs of other services: Docker DNS base URLs, shared token, short timeouts. Every call
 * carries the current request id (X-Request-Id) so one id traces a request through all services' logs, and is
 * logged at DEBUG with its status and duration.
 */
@Configuration
public class InternalRestClients {

    private static final Logger log = LoggerFactory.getLogger(InternalRestClients.class);

    @Bean
    RestClient catalogRestClient(@Value("${app.services.catalog-url}") String baseUrl,
                                 @Value("${app.internal-api-token}") String token) {
        return build(baseUrl, token, Duration.ofSeconds(3), Duration.ofSeconds(10));
    }

    @Bean
    RestClient authRestClient(@Value("${app.services.auth-url}") String baseUrl,
                              @Value("${app.internal-api-token}") String token) {
        return build(baseUrl, token, Duration.ofSeconds(3), Duration.ofSeconds(5));
    }

    private static RestClient build(String baseUrl, String token, Duration connect, Duration read) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connect);
        factory.setReadTimeout(read);
        return RestClient.builder().baseUrl(baseUrl).requestFactory(factory)
                .defaultHeader(InternalApiFilter.HEADER, token)
                .requestInterceptor((request, body, execution) -> {
                    String requestId = MDC.get(RequestLoggingFilter.MDC_REQUEST_ID);
                    if (requestId != null) {
                        request.getHeaders().set(RequestLoggingFilter.REQUEST_ID_HEADER, requestId);
                    }
                    String userId = MDC.get(RequestLoggingFilter.MDC_USER_ID);
                    if (userId != null) { // for the callee's log lines only
                        request.getHeaders().set(RequestLoggingFilter.ACTING_USER_HEADER, userId);
                    }
                    long start = System.nanoTime();
                    try {
                        var response = execution.execute(request, body);
                        log.debug("Internal call {} {} -> {} in {} ms", request.getMethod(), request.getURI(),
                                response.getStatusCode().value(), (System.nanoTime() - start) / 1_000_000);
                        return response;
                    } catch (java.io.IOException e) {
                        log.warn("Internal call {} {} failed after {} ms: {}", request.getMethod(), request.getURI(),
                                (System.nanoTime() - start) / 1_000_000, e.toString());
                        throw e;
                    }
                })
                .build();
    }
}
