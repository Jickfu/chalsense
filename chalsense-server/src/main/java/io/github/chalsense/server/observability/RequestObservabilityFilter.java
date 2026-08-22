package io.github.chalsense.server.observability;

import io.github.chalsense.server.http.RequestIds;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class RequestObservabilityFilter extends OncePerRequestFilter {
    private static final Pattern CREATE = Pattern.compile("/v1/public/sites/[^/]+/challenges");
    private static final Pattern VERIFY = Pattern.compile("/v1/public/sites/[^/]+/challenges/[^/]+/verify");
    private static final Pattern CONSUME = Pattern.compile("/v1/trusted/sites/[^/]+/verification-tickets/consume");
    private static final Pattern RESOURCE = Pattern.compile("/v1/public/resources/[^/]+/[^/]+");

    private final ServerObservability observability;

    public RequestObservabilityFilter(ServerObservability observability) {
        this.observability = observability;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        ObservedOperation operation = operation(request);
        if (operation == null) {
            chain.doFilter(request, response);
            return;
        }
        String requestId = RequestIds.begin();
        long started = System.nanoTime();
        try {
            chain.doFilter(request, response);
        } finally {
            int status = response.getStatus();
            observability.complete(requestId, operation, outcome(status), status, System.nanoTime() - started);
            RequestIds.end();
        }
    }

    private static ObservedOperation operation(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if ("POST".equals(method) && CREATE.matcher(path).matches()) return ObservedOperation.CHALLENGE_CREATE;
        if ("POST".equals(method) && VERIFY.matcher(path).matches()) return ObservedOperation.CHALLENGE_VERIFY;
        if ("POST".equals(method) && CONSUME.matcher(path).matches()) return ObservedOperation.TICKET_CONSUME;
        if (("GET".equals(method) || "HEAD".equals(method)) && RESOURCE.matcher(path).matches()) {
            return ObservedOperation.RESOURCE_READ;
        }
        if ("GET".equals(method) && "/livez".equals(path)) return ObservedOperation.LIVENESS;
        if ("GET".equals(method) && "/readyz".equals(path)) return ObservedOperation.READINESS;
        return null;
    }

    private static String outcome(int status) {
        if (status >= 200 && status < 300) return "success";
        return switch (status) {
            case 400, 413, 415 -> "invalid_request";
            case 401, 403 -> "unauthorized";
            case 404 -> "not_found";
            case 409 -> "unavailable";
            case 422 -> "rejected";
            case 429 -> "rate_limited";
            case 503 -> "dependency_unavailable";
            default -> status >= 500 ? "server_error" : "other";
        };
    }
}
