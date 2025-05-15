package com.photoblog.processing;

import com.photoblog.dto.ImageProcessingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

public class ImageProcessorTest {

    private ImageProcessor imageProcessor;

    @TempDir
    Path tempDir;

    private File inputFile;
    private File outputFile;

    @BeforeEach
    void setUp() throws IOException {
        imageProcessor = new ImageProcessor();

        // Create a simple test image
        inputFile = tempDir.resolve("input.jpg").toFile();
        BufferedImage testImage = new BufferedImage(400, 300, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = testImage.createGraphics();
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, 400, 300);
        g.dispose();
        ImageIO.write(testImage, "jpg", inputFile);

        outputFile = tempDir.resolve("output.jpg").toFile();
    }

    @Test
    void testAddWatermark_WithDefaultSettings() throws IOException {
        // Create request
        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(inputFile)
                .watermarkText("Test Watermark")
                .exportPath(outputFile.getAbsolutePath())
                .build();

        // Execute
        File result = imageProcessor.addWatermark(request);

        // Verify
        assertTrue(result.exists());
        assertEquals(outputFile.getCanonicalPath(), result.getCanonicalPath());

        // Verify the image has a watermark by checking dimensions and format
        BufferedImage processedImage = ImageIO.read(result);
        assertNotNull(processedImage);

        // Check the image size matches expected defaults
        assertEquals(800, processedImage.getWidth());
        assertEquals(600, processedImage.getHeight());
    }

    @Test
    void testAddWatermark_WithCustomSettings() throws IOException {
        // Create custom watermark settings
        ImageProcessor.WatermarkSettings settings = ImageProcessor.WatermarkSettings.builder()
                .font(new Font("Serif", Font.ITALIC, 30))
                .color(Color.RED)
                .opacity(0.5f)
                .width(300)
                .height(150)
                .build();

        // Create request
        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(inputFile)
                .watermarkText("Custom Watermark")
                .exportPath(outputFile.getAbsolutePath())
                .build();

        // Execute
        File result = imageProcessor.addWatermark(request, settings);

        // Verify
        assertTrue(result.exists());

        // Verify image was created
        BufferedImage processedImage = ImageIO.read(result);
        assertNotNull(processedImage);
    }

    @Test
    void testAddWatermark_WithEmptyWatermarkText() throws IOException {
        // Create request with empty text
        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(inputFile)
                .watermarkText("")
                .exportPath(outputFile.getAbsolutePath())
                .build();

        // Execute
        File result = imageProcessor.addWatermark(request);

        // Verify file was created even with empty text
        assertTrue(result.exists());
    }

    @Test
    void testAddWatermark_WithLongWatermarkText() throws IOException {
        // Create request with very long text
        String longText = "This is a very long watermark text that will test how the processor handles lengthy text strings that may exceed normal boundaries";

        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(inputFile)
                .watermarkText(longText)
                .exportPath(outputFile.getAbsolutePath())
                .build();

        // Execute
        File result = imageProcessor.addWatermark(request);

        // Verify
        assertTrue(result.exists());
    }

    @Test
    void testWatermarkSettings_BuilderDefaults() {
        // Test that the builder defaults work as expected
        ImageProcessor.WatermarkSettings settings = ImageProcessor.WatermarkSettings.builder().build();

        assertEquals(Color.BLACK, settings.getColor());
        assertEquals(0.5f, settings.getOpacity());
        assertEquals(400, settings.getWidth());
        assertEquals(100, settings.getHeight());
        assertNotNull(settings.getFont());
    }

    @Test
    void testAddWatermark_WithNonExistentInputFile() {
        // Create request with non-existent file
        File nonExistentFile = new File("does-not-exist.jpg");

        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(nonExistentFile)
                .watermarkText("Test")
                .exportPath(outputFile.getAbsolutePath())
                .build();

        // Execute and verify exception
        assertThrows(IOException.class, () -> {
            imageProcessor.addWatermark(request);
        });
    }

    @Test
    void testAddWatermark_WithInvalidOutputPath() throws IOException {
        // Create request with invalid output path
        ImageProcessingRequest request = ImageProcessingRequest.builder()
                .inputFile(inputFile)
                .watermarkText("Test")
                .exportPath("/invalid/path/that/does/not/exist/output.jpg")
                .build();

        // Execute and verify exception
        assertThrows(IOException.class, () -> {
            imageProcessor.addWatermark(request);
        });
    }

    @Test
    void testCreateTextWatermark() throws Exception {
        // This is a private method, so we need to use reflection to test it
        java.lang.reflect.Method method = ImageProcessor.class.getDeclaredMethod(
                "createTextWatermark",
                String.class, Font.class, Color.class, int.class, int.class);
        method.setAccessible(true);

        // Create parameters
        String text = "Test Watermark";
        Font font = new Font("Arial", Font.BOLD, 20);
        Color color = Color.BLUE;
        int width = 300;
        int height = 100;

        // Execute
        BufferedImage result = (BufferedImage) method.invoke(
                imageProcessor, text, font, color, width, height);

        // Verify
        assertNotNull(result);
        assertEquals(width, result.getWidth());
        assertEquals(height, result.getHeight());
        assertEquals(BufferedImage.TYPE_INT_ARGB, result.getType());
    }
}