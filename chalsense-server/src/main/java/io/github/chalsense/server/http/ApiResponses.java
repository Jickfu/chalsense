package io.github.chalsense.server.http;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;

final class ApiResponses {
    private ApiResponses() {
    }

    static ResponseEntity<ApiError> error(int status, String code) {
        return ResponseEntity.status(status)
                .cacheControl(CacheControl.noStore())
                .body(new ApiError("1", new ApiError.ErrorDetail(code, RequestIds.next())));
    }
}
