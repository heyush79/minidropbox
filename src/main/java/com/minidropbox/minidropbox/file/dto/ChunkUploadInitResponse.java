package com.minidropbox.minidropbox.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadInitResponse {

    private String uploadId; // Unique ID for this upload session
    private Integer totalChunks; // Number of chunks expected
    private Long chunkSize; // Size of each chunk in bytes
    private String message;

}
