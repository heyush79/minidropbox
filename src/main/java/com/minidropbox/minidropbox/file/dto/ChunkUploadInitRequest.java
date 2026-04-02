package com.minidropbox.minidropbox.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkUploadInitRequest {

    private String originalFilename;
    private Long totalSize; // Total file size in bytes
    private Long chunkSize; // Size of each chunk (e.g., 5MB)

}
