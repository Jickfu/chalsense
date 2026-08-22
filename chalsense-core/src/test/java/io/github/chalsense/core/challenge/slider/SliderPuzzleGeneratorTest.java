package io.github.chalsense.core.challenge.slider;

import io.github.chalsense.core.challenge.ChallengeGenerationRequest;
import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.core.challenge.GeneratedChallenge;
import io.github.chalsense.core.challenge.resource.ChallengeBinaryResource;
import io.github.chalsense.core.challenge.resource.ChallengeResourceBundle;
import io.github.chalsense.core.challenge.resource.ChallengeResourcePublisher;
import io.github.chalsense.core.site.SitePolicy;
import io.github.chalsense.core.site.SiteRegistration;
import io.github.chalsense.core.site.SiteStatus;
import io.github.chalsense.protocol.ActionName;
import io.github.chalsense.protocol.ChallengeId;
import io.github.chalsense.protocol.SiteKey;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SliderPuzzleGeneratorTest {
    @Test
    void generatesDeterministicBoundedPngResourcesAndServerGeometry() throws Exception {
        RecordingPublisher publisher = new RecordingPublisher();
        ArrayDeque<Integer> values = new ArrayDeque<>(List.of(0, 1, 2));
        SliderPuzzleGenerator generator = new SliderPuzzleGenerator(
                ignored -> gradient(640, 360), publisher, bound -> values.remove(), 2);

        GeneratedChallenge generated = generator.generate(request());

        assertEquals(320, generated.logicalWidth());
        assertEquals(180, generated.logicalHeight());
        assertEquals(43_750, generated.geometry().pieceStartX());
        assertEquals(468_750, generated.geometry().pieceTargetX());
        assertEquals(116_667, generated.geometry().pieceStartY());
        assertEquals(156_250, generated.geometry().pieceWidth());
        assertEquals(277_778, generated.geometry().pieceHeight());
        assertEquals(12_500, generated.geometry().tolerance());
        assertEquals(request().expiresAt(), publisher.bundle.expiresAt());

        ChallengeBinaryResource background = binary(publisher.bundle, ChallengeResourceRole.BACKGROUND);
        ChallengeBinaryResource piece = binary(publisher.bundle, ChallengeResourceRole.PIECE);
        assertTrue(background.byteLength() <= SliderPuzzleGenerator.MAXIMUM_BACKGROUND_BYTES);
        assertTrue(piece.byteLength() <= SliderPuzzleGenerator.MAXIMUM_PIECE_BYTES);
        BufferedImage decodedBackground = ImageIO.read(new ByteArrayInputStream(background.bytes()));
        BufferedImage decodedPiece = ImageIO.read(new ByteArrayInputStream(piece.bytes()));
        assertEquals(320, decodedBackground.getWidth());
        assertEquals(180, decodedBackground.getHeight());
        assertEquals(50, decodedPiece.getWidth());
        assertEquals(50, decodedPiece.getHeight());
        assertEquals(0, decodedPiece.getRGB(0, 0) >>> 24);
        assertNotEquals(0, decodedPiece.getRGB(25, 25) >>> 24);
    }

    @Test
    void rejectsOversizedSourcesBeforePublishing() {
        RecordingPublisher publisher = new RecordingPublisher();
        SliderPuzzleGenerator generator = new SliderPuzzleGenerator(
                ignored -> new BufferedImage(4097, 1, BufferedImage.TYPE_INT_RGB), publisher, bound -> 0, 1);

        assertThrows(IllegalArgumentException.class, () -> generator.generate(request()));
        assertEquals(0, publisher.publishCalls.get());
    }

    @Test
    void sameControlledInputAndRandomSequenceProduceIdenticalPngBytes() {
        RecordingPublisher firstPublisher = new RecordingPublisher();
        RecordingPublisher secondPublisher = new RecordingPublisher();
        new SliderPuzzleGenerator(ignored -> gradient(320, 180), firstPublisher, bound -> 0, 1).generate(request());
        new SliderPuzzleGenerator(ignored -> gradient(320, 180), secondPublisher, bound -> 0, 1).generate(request());

        assertArrayEquals(binary(firstPublisher.bundle, ChallengeResourceRole.BACKGROUND).bytes(),
                binary(secondPublisher.bundle, ChallengeResourceRole.BACKGROUND).bytes());
        assertArrayEquals(binary(firstPublisher.bundle, ChallengeResourceRole.PIECE).bytes(),
                binary(secondPublisher.bundle, ChallengeResourceRole.PIECE).bytes());
    }

    @Test
    void rejectsOutOfContractRandomSourcesBeforePublishing() {
        RecordingPublisher publisher = new RecordingPublisher();
        SliderPuzzleGenerator generator = new SliderPuzzleGenerator(
                ignored -> gradient(320, 180), publisher, bound -> bound, 1);

        assertThrows(IllegalStateException.class, () -> generator.generate(request()));
        assertEquals(0, publisher.publishCalls.get());
    }

    @Test
    void removesPublishedBundleWhenPublisherMetadataViolatesTheContract() {
        RecordingPublisher publisher = new RecordingPublisher();
        publisher.invalidMetadata = true;
        SliderPuzzleGenerator generator = new SliderPuzzleGenerator(
                ignored -> gradient(320, 180), publisher, bound -> 0, 1);

        assertThrows(IllegalStateException.class, () -> generator.generate(request()));
        assertEquals(1, publisher.deleteCalls.get());
    }

    @Test
    void discardDeletesOnlyTheExactPublishedResourceReferences() {
        RecordingPublisher publisher = new RecordingPublisher();
        SliderPuzzleGenerator generator = new SliderPuzzleGenerator(
                ignored -> gradient(320, 180), publisher, bound -> 0, 1);
        GeneratedChallenge generated = generator.generate(request());

        generator.discard(request(), generated);

        assertEquals(generated.resources(), publisher.deletedResources);
    }

    @Test
    void binaryResourceDefensivelyCopiesBytes() {
        byte[] original = { 1, 2, 3 };
        ChallengeBinaryResource resource = new ChallengeBinaryResource(
                ChallengeResourceRole.PIECE, "image/png", 1, 1, original);
        original[0] = 9;
        byte[] returned = resource.bytes();
        returned[1] = 9;
        assertEquals(1, resource.bytes()[0]);
        assertEquals(2, resource.bytes()[1]);
    }

    @Test
    void configurationRejectsUnboundedConcurrency() {
        assertThrows(IllegalArgumentException.class, () -> new SliderPuzzleGenerator(
                ignored -> gradient(320, 180), new RecordingPublisher(), bound -> 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new SliderPuzzleGenerator(
                ignored -> gradient(320, 180), new RecordingPublisher(), bound -> 0, 1025));
    }

    @Test
    void rejectsExcessConcurrentGenerationWithoutQueuing() throws Exception {
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        SliderPuzzleGenerator generator = new SliderPuzzleGenerator(ignored -> {
            entered.countDown();
            try {
                release.await();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(exception);
            }
            return gradient(320, 180);
        }, new RecordingPublisher(), bound -> 0, 1);

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<GeneratedChallenge> first = executor.submit(() -> generator.generate(request()));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            assertThrows(IllegalStateException.class, () -> generator.generate(request()));
            release.countDown();
            first.get(5, TimeUnit.SECONDS);
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    private static ChallengeGenerationRequest request() {
        ActionName action = new ActionName("login");
        SiteKey siteKey = new SiteKey("site_demo_01");
        SitePolicy policy = new SitePolicy(Duration.ofSeconds(120), Duration.ofSeconds(60), "p1",
                Set.of(action), Set.of(), false);
        SiteRegistration site = new SiteRegistration(siteKey, "demo", SiteStatus.ACTIVE, policy);
        return new ChallengeGenerationRequest(site, action,
                new ChallengeId("AAAAAAAAAAAAAAAAAAAAAA"), 1_000, 121_000);
    }

    private static BufferedImage gradient(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, new Color(x % 256, y % 256, (x + y) % 256).getRGB());
            }
        }
        return image;
    }

    private static ChallengeBinaryResource binary(ChallengeResourceBundle bundle, ChallengeResourceRole role) {
        return bundle.resources().stream().filter(resource -> resource.role() == role).findFirst().orElseThrow();
    }

    private static final class RecordingPublisher implements ChallengeResourcePublisher {
        private final AtomicInteger publishCalls = new AtomicInteger();
        private final AtomicInteger deleteCalls = new AtomicInteger();
        private ChallengeResourceBundle bundle;
        private List<ChallengeResource> deletedResources;
        private boolean invalidMetadata;

        @Override
        public List<ChallengeResource> publish(ChallengeResourceBundle bundle) {
            publishCalls.incrementAndGet();
            this.bundle = bundle;
            List<ChallengeResource> resources = new ArrayList<>();
            for (ChallengeBinaryResource resource : bundle.resources()) {
                int width = invalidMetadata && resource.role() == ChallengeResourceRole.BACKGROUND
                        ? resource.pixelWidth() - 1 : resource.pixelWidth();
                resources.add(new ChallengeResource(resource.role(),
                        "/resources/" + resource.role().name().toLowerCase(), resource.mediaType(),
                        width, resource.pixelHeight()));
            }
            return resources;
        }

        @Override
        public void delete(List<ChallengeResource> publishedResources) {
            deleteCalls.incrementAndGet();
            deletedResources = List.copyOf(publishedResources);
        }
    }
}
