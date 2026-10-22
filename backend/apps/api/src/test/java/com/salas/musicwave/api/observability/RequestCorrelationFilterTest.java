package com.salas.musicwave.api.observability;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class RequestCorrelationFilterTest {

    private final RequestCorrelationFilter filter = new RequestCorrelationFilter();

    @Test
    void preservesABoundedSafeRequestIdAndPlacesItInMdcForTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/system/status");
        request.addHeader(RequestCorrelationFilter.HEADER, "client-request_42");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> duringRequest = new AtomicReference<>();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) ->
                duringRequest.set(MDC.get("requestId")));

        assertEquals("client-request_42", response.getHeader(RequestCorrelationFilter.HEADER));
        assertEquals("client-request_42", duringRequest.get());
        assertNull(MDC.get("requestId"));
    }

    @Test
    void replacesUnsafeOrOversizedValuesInsteadOfReflectingThem() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        request.addHeader(RequestCorrelationFilter.HEADER, "token\nsecret-" + "x".repeat(80));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (ignoredRequest, ignoredResponse) -> { });

        String generated = response.getHeader(RequestCorrelationFilter.HEADER);
        assertFalse(generated.contains("token"));
        assertEquals(36, generated.length());
    }
}
