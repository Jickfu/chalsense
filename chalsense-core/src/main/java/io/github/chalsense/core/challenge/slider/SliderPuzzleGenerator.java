package io.github.chalsense.core.challenge.slider;

import io.github.chalsense.core.challenge.ChallengeGenerationRequest;
import io.github.chalsense.core.challenge.ChallengeGenerator;
import io.github.chalsense.core.challenge.ChallengeResource;
import io.github.chalsense.core.challenge.ChallengeResourceRole;
import io.github.chalsense.core.challenge.GeneratedChallenge;
import io.github.chalsense.core.challenge.resource.ChallengeBinaryResource;
import io.github.chalsense.core.challenge.resource.ChallengeResourceBundle;
import io.github.chalsense.core.challenge.resource.ChallengeResourcePublisher;
import io.github.chalsense.core.coordinate.CoordinateMath;
import io.github.chalsense.core.state.SliderPuzzleGeometry;

import javax.imageio.ImageIO;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Semaphore;

/** JDK-only bounded PNG generator for the v0.1 slider puzzle. */
public final class SliderPuzzleGenerator implements ChallengeGenerator {
    public static final int LOGICAL_WIDTH = 320;
    public static final int LOGICAL_HEIGHT = 180;
    public static final int PIECE_SIZE = 50;
    public static final int MAXIMUM_SOURCE_EDGE = 4096;
    public static final long MAXIMUM_SOURCE_PIXELS = 16_000_000L;
    public static final int MAXIMUM_BACKGROUND_BYTES = 1024 * 1024;
    public static final int MAXIMUM_PIECE_BYTES = 256 * 1024;

    private final BackgroundImageSource imageSource;
    private final ChallengeResourcePublisher publisher;
    private final BoundedRandom random;
    private final Semaphore capacity;

    public SliderPuzzleGenerator(
            BackgroundImageSource imageSource,
            ChallengeResourcePublisher publisher,
            BoundedRandom random,
            int maximumConcurrentGenerations) {
        this.imageSource = Objects.requireNonNull(imageSource, "imageSource");
        this.publisher = Objects.requireNonNull(publisher, "publisher");
        this.random = Objects.requireNonNull(random, "random");
        if (maximumConcurrentGenerations <= 0 || maximumConcurrentGenerations > 1024) {
            throw new IllegalArgumentException("maximumConcurrentGenerations must be between 1 and 1024");
        }
        this.capacity = new Semaphore(maximumConcurrentGenerations);
    }

    @Override
    public GeneratedChallenge generate(ChallengeGenerationRequest request) {
        Objects.requireNonNull(request, "request");
        if (!capacity.tryAcquire()) throw new IllegalStateException("slider generator capacity exhausted");
        try {
            BufferedImage source = Objects.requireNonNull(imageSource.load(request), "imageSource returned null");
            validateSource(source);
            BufferedImage background = scaleAndCrop(source);

            int targetX = 150 + bounded(109);
            int pieceY = 20 + bounded(81);
            int startX = 12 + bounded(59);
            Area mask = puzzleMask();
            BufferedImage piece = extractPiece(background, mask, targetX, pieceY);
            markHole(background, mask, targetX, pieceY);

            byte[] backgroundBytes = encodePng(background, MAXIMUM_BACKGROUND_BYTES, "background");
            byte[] pieceBytes = encodePng(piece, MAXIMUM_PIECE_BYTES, "piece");
            List<ChallengeBinaryResource> binaries = List.of(
                    new ChallengeBinaryResource(ChallengeResourceRole.BACKGROUND, "image/png",
                            LOGICAL_WIDTH, LOGICAL_HEIGHT, backgroundBytes),
                    new ChallengeBinaryResource(ChallengeResourceRole.PIECE, "image/png",
                            PIECE_SIZE, PIECE_SIZE, pieceBytes));
            List<ChallengeResource> resources = publisher.publish(new ChallengeResourceBundle(
                    request.site().siteKey(), request.challengeId(), request.expiresAt(), binaries));
            try {
                validatePublished(resources);
                long pieceWidth = CoordinateMath.sourceToNormalized(PIECE_SIZE, LOGICAL_WIDTH);
                long pieceHeight = CoordinateMath.sourceToNormalized(PIECE_SIZE, LOGICAL_HEIGHT);
                SliderPuzzleGeometry geometry = new SliderPuzzleGeometry(
                        CoordinateMath.sourceToNormalized(startX, LOGICAL_WIDTH),
                        CoordinateMath.sourceToNormalized(targetX, LOGICAL_WIDTH),
                        CoordinateMath.sourceToNormalized(pieceY, LOGICAL_HEIGHT),
                        pieceWidth,
                        pieceHeight,
                        CoordinateMath.tolerance(pieceWidth, 8, 100, 6_250, 18_750));
                return new GeneratedChallenge(geometry, LOGICAL_WIDTH, LOGICAL_HEIGHT, resources);
            } catch (RuntimeException exception) {
                try {
                    publisher.delete(resources);
                } catch (RuntimeException ignored) {
                    // Publisher hard TTL remains the final cleanup boundary.
                }
                throw exception;
            }
        } finally {
            capacity.release();
        }
    }

    @Override
    public void discard(ChallengeGenerationRequest request, GeneratedChallenge generated) {
        publisher.delete(generated.resources());
    }

    private int bounded(int bound) {
        int value = random.nextInt(bound);
        if (value < 0 || value >= bound) throw new IllegalStateException("random source returned an out-of-range value");
        return value;
    }

    private static void validateSource(BufferedImage source) {
        int width = source.getWidth();
        int height = source.getHeight();
        long pixels = (long) width * height;
        if (width <= 0 || height <= 0 || width > MAXIMUM_SOURCE_EDGE || height > MAXIMUM_SOURCE_EDGE
                || pixels > MAXIMUM_SOURCE_PIXELS) {
            throw new IllegalArgumentException("background source exceeds image limits");
        }
    }

    private static void validatePublished(List<ChallengeResource> resources) {
        Objects.requireNonNull(resources, "publisher returned null");
        if (resources.size() != 2) throw new IllegalStateException("publisher must return exactly two resources");
        for (ChallengeResource resource : resources) {
            if (!"image/png".equals(resource.mediaType())) {
                throw new IllegalStateException("publisher changed the resource media type");
            }
            if (resource.role() == ChallengeResourceRole.BACKGROUND
                    && (resource.pixelWidth() != LOGICAL_WIDTH || resource.pixelHeight() != LOGICAL_HEIGHT)) {
                throw new IllegalStateException("publisher changed background dimensions");
            }
            if (resource.role() == ChallengeResourceRole.PIECE
                    && (resource.pixelWidth() != PIECE_SIZE || resource.pixelHeight() != PIECE_SIZE)) {
                throw new IllegalStateException("publisher changed piece dimensions");
            }
        }
    }

    private static BufferedImage scaleAndCrop(BufferedImage source) {
        BufferedImage output = new BufferedImage(LOGICAL_WIDTH, LOGICAL_HEIGHT, BufferedImage.TYPE_INT_RGB);
        double scale = Math.max((double) LOGICAL_WIDTH / source.getWidth(), (double) LOGICAL_HEIGHT / source.getHeight());
        int width = (int) Math.ceil(source.getWidth() * scale);
        int height = (int) Math.ceil(source.getHeight() * scale);
        int x = (LOGICAL_WIDTH - width) / 2;
        int y = (LOGICAL_HEIGHT - height) / 2;
        Graphics2D graphics = output.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.drawImage(source, x, y, width, height, null);
        } finally {
            graphics.dispose();
        }
        return output;
    }

    private static Area puzzleMask() {
        Area mask = new Area(new RoundRectangle2D.Double(4, 4, 42, 42, 7, 7));
        mask.add(new Area(new Ellipse2D.Double(18, 0, 14, 14)));
        mask.subtract(new Area(new Ellipse2D.Double(39, 18, 14, 14)));
        return mask;
    }

    private static BufferedImage extractPiece(BufferedImage background, Area mask, int targetX, int pieceY) {
        BufferedImage piece = new BufferedImage(PIECE_SIZE, PIECE_SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = piece.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.setClip(mask);
            graphics.drawImage(background, -targetX, -pieceY, null);
            graphics.setClip(null);
            graphics.setColor(new Color(255, 255, 255, 210));
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(mask);
        } finally {
            graphics.dispose();
        }
        return piece;
    }

    private static void markHole(BufferedImage background, Area mask, int targetX, int pieceY) {
        Graphics2D graphics = background.createGraphics();
        try {
            graphics.translate(targetX, pieceY);
            graphics.setColor(new Color(18, 32, 48, 130));
            graphics.fill(mask);
            graphics.setColor(new Color(255, 255, 255, 180));
            graphics.setStroke(new BasicStroke(2));
            graphics.draw(mask);
        } finally {
            graphics.dispose();
        }
    }

    private static byte[] encodePng(BufferedImage image, int maximumBytes, String role) {
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            if (!ImageIO.write(image, "png", output)) throw new IllegalStateException("PNG writer is unavailable");
            byte[] bytes = output.toByteArray();
            if (bytes.length == 0 || bytes.length > maximumBytes) {
                throw new IllegalStateException(role + " PNG exceeds encoded size limit");
            }
            return bytes;
        } catch (IOException exception) {
            throw new IllegalStateException("PNG encoding failed", exception);
        }
    }
}
