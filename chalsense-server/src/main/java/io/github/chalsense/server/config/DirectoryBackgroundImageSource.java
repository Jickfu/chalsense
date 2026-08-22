package io.github.chalsense.server.config;

import io.github.chalsense.core.challenge.ChallengeGenerationRequest;
import io.github.chalsense.core.challenge.slider.BackgroundImageSource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

final class DirectoryBackgroundImageSource implements BackgroundImageSource {
    private final List<Path> images;
    private final SecureRandom random;

    DirectoryBackgroundImageSource(String directory, SecureRandom random) {
        if (directory == null || directory.isBlank()) throw new IllegalArgumentException("chalsense.background-directory is required");
        Path root = Path.of(directory).toAbsolutePath().normalize();
        if (!Files.isDirectory(root)) throw new IllegalArgumentException("background directory does not exist");
        try (var paths = Files.list(root)) {
            images = paths.filter(Files::isRegularFile).filter(DirectoryBackgroundImageSource::supported)
                    .sorted().toList();
        } catch (IOException exception) {
            throw new IllegalArgumentException("background directory cannot be read", exception);
        }
        if (images.isEmpty()) throw new IllegalArgumentException("background directory contains no PNG/JPEG images");
        this.random = random;
    }

    @Override
    public BufferedImage load(ChallengeGenerationRequest request) {
        Path selected = images.get(random.nextInt(images.size()));
        try {
            BufferedImage image = ImageIO.read(selected.toFile());
            if (image == null) throw new IllegalStateException("unsupported background image");
            return image;
        } catch (IOException exception) {
            throw new IllegalStateException("background image cannot be read", exception);
        }
    }

    private static boolean supported(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg");
    }
}
