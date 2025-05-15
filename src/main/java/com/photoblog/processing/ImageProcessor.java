package com.photoblog.processing;

import com.photoblog.dto.ImageProcessingRequest;
import lombok.Data;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

@Slf4j
public class ImageProcessor {
    // Extract constants for better readability and maintainability
    private static final int DEFAULT_WATERMARK_WIDTH = 400;
    private static final int DEFAULT_WATERMARK_HEIGHT = 100;
    private static final int DEFAULT_IMAGE_WIDTH = 800;
    private static final int DEFAULT_IMAGE_HEIGHT = 600;
    private static final float DEFAULT_WATERMARK_OPACITY = 0.5f;
    private static final double DEFAULT_OUTPUT_QUALITY = 0.9;

    /**
     * Configuration class to encapsulate watermark settings
     */
    @Data
    @Builder
    public static class WatermarkSettings {
        @Builder.Default
        private Font font = new Font("Arial", Font.BOLD, 15);

        @Builder.Default
        private Color color = Color.BLACK;

        @Builder.Default
        private float opacity = DEFAULT_WATERMARK_OPACITY;

        @Builder.Default
        private int width = DEFAULT_WATERMARK_WIDTH;

        @Builder.Default
        private int height = DEFAULT_WATERMARK_HEIGHT;
    }

    /**
     * Adds a watermark to an image based on the given request
     *
     * @param request The image processing request containing input and output file information
     * @return The canonical path of the processed image file
     * @throws IOException If there's an error processing the image
     */
    public File addWatermark(ImageProcessingRequest request) throws IOException {
        WatermarkSettings settings = WatermarkSettings.builder().build();
        return addWatermark(request, settings);
    }

    /**
     * Adds a watermark to an image with custom watermark settings
     *
     * @param request The image processing request
     * @param settings Custom watermark settings
     * @return The canonical path of the processed image file
     * @throws IOException If there's an error processing the image
     */
    public File addWatermark(ImageProcessingRequest request, WatermarkSettings settings) throws IOException {
        // Create text watermark with provided settings
        BufferedImage textWaterMark = createTextWatermark(
                request.getWatermarkText(),
                settings.getFont(),
                settings.getColor(),
                settings.getWidth(),
                settings.getHeight());

        // Create output file
        File outputImage = request.getExportFile();

        // Apply watermark
        Thumbnails.of(request.getInputFile())
                .size(DEFAULT_IMAGE_WIDTH, DEFAULT_IMAGE_HEIGHT)
                .watermark(Positions.TOP_CENTER, textWaterMark, settings.getOpacity())
                .outputQuality(DEFAULT_OUTPUT_QUALITY)
                .toFile(outputImage);

        log.info("Watermark added successfully: {}", outputImage.getAbsolutePath());
        return outputImage;
    }

    /**
     * Creates a text watermark image
     *
     * @param text The text to display in the watermark
     * @param font The font to use for the text
     * @param color The color of the text
     * @param width The width of the watermark image
     * @param height The height of the watermark image
     * @return A BufferedImage containing the text watermark
     */
    private BufferedImage createTextWatermark(String text, Font font, Color color, int width, int height) {
        BufferedImage watermarkImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = (Graphics2D) watermarkImage.getGraphics();

        // Set anti-aliasing for smooth text
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Set text properties
        g2d.setFont(font);
        g2d.setColor(color);

        // Calculate coordinates to center the text
        FontMetrics fontMetrics = g2d.getFontMetrics();
        int textWidth = fontMetrics.stringWidth(text);
        int textHeight = fontMetrics.getAscent();
        int x = (width - textWidth) / 2;
        int y = (height + textHeight) / 2 - fontMetrics.getDescent();

        // Draw the text
        g2d.drawString(text, x, y);
        g2d.dispose();

        return watermarkImage;
    }
}