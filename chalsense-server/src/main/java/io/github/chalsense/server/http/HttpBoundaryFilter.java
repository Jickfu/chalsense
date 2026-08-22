package io.github.chalsense.server.http;

import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.WebOrigin;
import io.github.chalsense.protocol.SiteKey;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.ReadListener;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.Locale;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public final class HttpBoundaryFilter extends OncePerRequestFilter {
    private static final Pattern PUBLIC_SITE = Pattern.compile("/v1/public/sites/([A-Za-z0-9_-]{8,64})/.*");
    private final SiteRegistry sites;

    public HttpBoundaryFilter(SiteRegistry sites) {
        this.sites = sites;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        response.setHeader("X-Content-Type-Options", "nosniff");
        String path = request.getRequestURI();
        Matcher publicSite = PUBLIC_SITE.matcher(path);
        if (publicSite.matches() && request.getHeader("Origin") == null
                && ("POST".equals(request.getMethod()) || "OPTIONS".equals(request.getMethod()))) {
            reject(response, 400, "INVALID_REQUEST");
            return;
        }
        if (publicSite.matches() && request.getHeader("Origin") != null) {
            if (!applyCors(request, response, publicSite.group(1))) {
                reject(response, 403, "ORIGIN_NOT_ALLOWED");
                return;
            }
            if ("OPTIONS".equals(request.getMethod())) {
                response.setStatus(204);
                return;
            }
        }
        int maximum = maximumBody(path);
        if (maximum > 0 && "POST".equals(request.getMethod())) {
            String encoding = request.getHeader("Content-Encoding");
            if (encoding != null && !encoding.equalsIgnoreCase("identity")) {
                reject(response, 415, "INVALID_REQUEST");
                return;
            }
            String contentType = request.getContentType();
            if (contentType == null || !contentType.toLowerCase(Locale.ROOT)
                    .matches("application/json(?:\\s*;\\s*charset=utf-8)?")) {
                reject(response, 415, "INVALID_REQUEST");
                return;
            }
            byte[] body = request.getInputStream().readNBytes(maximum + 1);
            if (body.length > maximum) {
                reject(response, 413, "INVALID_REQUEST");
                return;
            }
            chain.doFilter(new BufferedRequest(request, body), response);
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean applyCors(HttpServletRequest request, HttpServletResponse response, String rawSiteKey) {
        try {
            if ("OPTIONS".equals(request.getMethod())) {
                if (!"POST".equals(request.getHeader("Access-Control-Request-Method"))) return false;
                String requestedHeaders = request.getHeader("Access-Control-Request-Headers");
                if (requestedHeaders != null && !requestedHeaders.trim().equalsIgnoreCase("content-type")) return false;
            }
            WebOrigin origin = WebOrigin.parse(request.getHeader("Origin"));
            boolean allowed = sites.find(new SiteKey(rawSiteKey))
                    .map(site -> site.policy().allowedOrigins().contains(origin)).orElse(false);
            if (!allowed) return false;
            response.setHeader("Access-Control-Allow-Origin", origin.value());
            response.setHeader("Access-Control-Allow-Methods", "POST, OPTIONS");
            response.setHeader("Access-Control-Allow-Headers", "Content-Type");
            response.setHeader("Access-Control-Max-Age", "300");
            response.addHeader("Vary", "Origin");
            response.addHeader("Vary", "Access-Control-Request-Method");
            response.addHeader("Vary", "Access-Control-Request-Headers");
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static int maximumBody(String path) {
        if (path.matches("/v1/public/sites/[^/]+/challenges")) return 2 * 1024;
        if (path.matches("/v1/public/sites/[^/]+/challenges/[^/]+/verify")) return 64 * 1024;
        if (path.matches("/v1/trusted/sites/[^/]+/verification-tickets/consume")) return 4 * 1024;
        return 0;
    }

    private static void reject(HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setHeader("Cache-Control", "no-store");
        response.getWriter().write("{\"protocolVersion\":\"1\",\"error\":{\"code\":\"" + code
                + "\",\"requestId\":\"" + RequestIds.currentOrNext() + "\"}}");
    }

    private static final class BufferedRequest extends HttpServletRequestWrapper {
        private final byte[] body;
        private BufferedRequest(HttpServletRequest request, byte[] body) { super(request); this.body = body; }
        @Override public int getContentLength() { return body.length; }
        @Override public long getContentLengthLong() { return body.length; }
        @Override public ServletInputStream getInputStream() {
            ByteArrayInputStream input = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public boolean isFinished() { return input.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { throw new UnsupportedOperationException(); }
                @Override public int read() { return input.read(); }
                @Override public int read(byte[] bytes, int offset, int length) { return input.read(bytes, offset, length); }
            };
        }
    }
}
