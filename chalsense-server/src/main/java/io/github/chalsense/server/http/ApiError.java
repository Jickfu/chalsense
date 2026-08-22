package io.github.chalsense.server.http;

public record ApiError(String protocolVersion, ErrorDetail error) {
    public record ErrorDetail(String code, String requestId) {
    }
}
