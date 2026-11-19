package com.caifan.admin.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * 菜品缩略图生成器：将卡片图片压缩为 480x480 JPEG，避免客户端加载原图。
 */
@Slf4j
@Service
public class GoodsThumbnailService {

    static final int THUMBNAIL_SIZE = 480;
    private static final float JPEG_QUALITY = 0.78f;

    private final Path goodsImageRoot;

    public GoodsThumbnailService(@Value("${app.goods-img-dir:img}") String goodsImgDir) {
        this.goodsImageRoot = Paths.get(goodsImgDir).toAbsolutePath().normalize();
    }

    public Path ensureThumbnail(Path source) throws IOException {
        Path normalizedSource = source.toAbsolutePath().normalize();
        if (!normalizedSource.startsWith(goodsImageRoot)) {
            throw new IOException("图片路径不在菜品图片目录内");
        }
        Path thumbnail = thumbnailFile(normalizedSource);
        if (!Files.isRegularFile(normalizedSource)) {
            throw new IOException("原图不存在：" + normalizedSource);
        }
        if (Files.isRegularFile(thumbnail)
                && Files.getLastModifiedTime(thumbnail).compareTo(Files.getLastModifiedTime(normalizedSource)) >= 0) {
            return thumbnail;
        }

        BufferedImage original = ImageIO.read(normalizedSource.toFile());
        if (original == null) {
            throw new IOException("无法读取图片：" + normalizedSource);
        }
        BufferedImage resized = resizeToSquare(original);
        Files.createDirectories(thumbnail.getParent());
        Path temporary = Files.createTempFile(thumbnail.getParent(), thumbnail.getFileName().toString(), ".tmp");
        try {
            writeJpeg(resized, temporary);
            moveReplacing(temporary, thumbnail);
            return thumbnail;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    public int generateMissing() {
        if (!Files.isDirectory(goodsImageRoot)) {
            return 0;
        }
        int generated = 0;
        try (Stream<Path> paths = Files.walk(goodsImageRoot)) {
            List<Path> sources = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSourceImage)
                    .sorted(Comparator.comparing(Path::toString))
                    .toList();
            for (Path source : sources) {
                Path thumbnail = thumbnailFile(source);
                if (Files.isRegularFile(thumbnail)
                        && Files.getLastModifiedTime(thumbnail).compareTo(Files.getLastModifiedTime(source)) >= 0) {
                    continue;
                }
                try {
                    ensureThumbnail(source);
                    generated++;
                } catch (IOException exception) {
                    log.warn("生成菜品缩略图失败: {}", source, exception);
                }
            }
        } catch (IOException exception) {
            log.warn("扫描菜品图片目录失败: {}", goodsImageRoot, exception);
        }
        return generated;
    }

    Path thumbnailFile(Path source) {
        String name = source.getFileName().toString();
        int extensionIndex = name.lastIndexOf('.');
        String thumbnailName = extensionIndex > 0
                ? name.substring(0, extensionIndex) + ".thumb.jpg"
                : name + ".thumb.jpg";
        return source.resolveSibling(thumbnailName);
    }

    private boolean isSourceImage(Path path) {
        String name = path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".thumb.jpg")) {
            return false;
        }
        return name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".gif");
    }

    private BufferedImage resizeToSquare(BufferedImage original) {
        int side = Math.min(original.getWidth(), original.getHeight());
        int sourceX = (original.getWidth() - side) / 2;
        int sourceY = (original.getHeight() - side) / 2;
        BufferedImage resized = new BufferedImage(
                THUMBNAIL_SIZE, THUMBNAIL_SIZE, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(
                    original,
                    0, 0, THUMBNAIL_SIZE, THUMBNAIL_SIZE,
                    sourceX, sourceY, sourceX + side, sourceY + side,
                    null);
        } finally {
            graphics.dispose();
        }
        return resized;
    }

    private void writeJpeg(BufferedImage image, Path target) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam parameters = writer.getDefaultWriteParam();
        if (parameters.canWriteCompressed()) {
            parameters.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            parameters.setCompressionQuality(JPEG_QUALITY);
        }
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            writer.write(null, new IIOImage(image, null, null), parameters);
        } finally {
            writer.dispose();
        }
    }

    private void moveReplacing(Path source, Path target) throws IOException {
        try {
            Files.move(source, target,
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }
}
