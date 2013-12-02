package com.lowtuna.gymclasscal.jersey;

import com.sun.jersey.spi.container.ContainerRequest;
import com.sun.jersey.spi.container.ContainerRequestFilter;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.MDC;

public class RequestIdFilter implements ContainerRequestFilter {
    private static final String REQUEST_ID_HEADER = "Heroku-Request-ID";
    private static final String REQUEST_ID_LOG_NAME = "requestId";

    @Override
    public ContainerRequest filter(ContainerRequest request) {
        String requestId = request.getHeaderValue(REQUEST_ID_HEADER);
        if (StringUtils.isNotEmpty(requestId)) {
            MDC.put(REQUEST_ID_LOG_NAME, requestId);
        }
        return request;
    }

}
