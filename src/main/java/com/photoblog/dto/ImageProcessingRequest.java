package com.photoblog.dto;


import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.File;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ImageProcessingRequest {

    private File inputFile;
    private String exportUrl;
    private String watermarkText;

}
