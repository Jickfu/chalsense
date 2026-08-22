package io.github.chalsense.server.http;

import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteRegistry;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.core.site.WebOrigin;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.SiteKey;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class HttpBoundaryFilterTest {
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new StubController())
            .addFilters(new HttpBoundaryFilter(registry())).build();

    @Test
    void permitsOnlyExactConfiguredCorsPreflight() throws Exception {
        mvc.perform(options("/v1/public/sites/site_test/challenges")
                        .header("Origin", "https://app.example.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isNoContent())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.example.test"))
                .andExpect(header().string("Access-Control-Max-Age", "300"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/v1/public/sites/site_test/challenges")
                        .header("Origin", "https://evil.example.test")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void enforcesMediaTypeEncodingAndCreateBodyLimit() throws Exception {
        mvc.perform(post("/v1/public/sites/site_test/challenges")
                        .header("Origin", "https://app.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(new byte[2049]))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_REQUEST")));
        mvc.perform(post("/v1/public/sites/site_test/challenges")
                        .header("Origin", "https://app.example.test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Content-Encoding", "gzip").content("{}"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_REQUEST")));
        mvc.perform(post("/v1/public/sites/site_test/challenges")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    private static SiteRegistry registry() {
        SiteKey key = new SiteKey("site_test");
        SiteRegistration registration = new SiteRegistration(key, "Test", SiteStatus.ACTIVE,
                SitePolicy.defaults("1", Set.of(new ActionName("login")),
                        Set.of(WebOrigin.parse("https://app.example.test"))));
        return requested -> requested.equals(key) ? Optional.of(registration) : Optional.empty();
    }

    @RestController
    private static final class StubController {
        @PostMapping("/v1/public/sites/{siteKey}/challenges")
        String create() { return "ok"; }
    }
}
