package com.photoblog.processing;

import com.photoblog.dto.ImageProcessingRequest;
import net.coobird.thumbnailator.Thumbnails;
import net.coobird.thumbnailator.geometry.Positions;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

public class ImageProcessor {

    private String addWatermark(ImageProcessingRequest request) throws IOException {



        // Default settings for watermark
        Font font = new Font("Arial", Font.BOLD, 20);
        Color color = Color.BLACK;
        float opacity = 0.2f;

        // Create text watermark with default settings
        BufferedImage textWaterMark = createTextWatermark(
                request.getWatermarkText(),
                font,
                color,
                200,
                100);

        // Create output file
        File outputImage = new File(request.getExportUrl());

        // Apply watermark
        Thumbnails.of(request.getInputFile())
                .size(800, 600) // Resize if needed (can use original size)
                .watermark(Positions.TOP_CENTER, textWaterMark, opacity)
                .outputQuality(0.9) // Set output quality (90%)
                .toFile(outputImage);

        return outputImage.getAbsolutePath();

    }


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
