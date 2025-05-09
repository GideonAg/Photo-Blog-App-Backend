package com.photoblog.dto;

import lombok.*;

import java.io.File;
import java.util.Objects;

/**
 * Represents a request to process an image, including details about the input image,
 * the export path for the processed image, and an optional watermark text.
 * This class provides utility methods to access the input and export file locations
 * as {@link File} objects.
 *
 * The {@code inputFileName} specifies the file path of the input image to be processed.
 * The {@code exportPath} indicates the location where the processed image will be saved.
 * The {@code watermarkText} allows specifying a watermark to be applied on the image.
 *
 * The methods {@code getInputFile()} and {@code getExportFile()} are used to convert
 * the provided paths into {@link File} objects if the paths are not null.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(exclude = "inputFile")
@ToString(exclude = "inputFile")
public class ImageProcessingRequest {
    /**
     * The file path of the input image.
     */
    private String inputFileName;

    /**
     * The file path where the processed image will be saved.
     */
    private String exportPath;

    /**
     * The watermark text to be applied on the image.
     */
    private String watermarkText;

    /**
     * Lazily creates and returns a File object for the input image.
     *
     * @return The File object for the input image, or null if inputFileName is null.
     */
    public File getInputFile() {
        return createFileIfPathExists(inputFileName);
    }

    /**
     * Creates a File object from the given path if the path is not null.
     *
     * @param path The file path
     * @return A File object or null if the path is null
     */
    private File createFileIfPathExists(String path) {
        return path != null ? new File(path) : null;
    }

    /**
     * Returns the export location as a File object.
     *
     * @return The File object for the export location, or null if exportPath is null.
     */
    public File getExportFile() {
        return createFileIfPathExists(exportPath);
    }
}