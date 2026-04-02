package com.minidropbox.minidropbox.file.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChunkStatusResponse {

    private String uploadId;
    private String status; // "IN_PROGRESS", "COMPLETED", "FAILED"
    private Integer uploadedChunks;
    private Integer totalChunks;
    private Double percentageComplete;
    private String message;

}
