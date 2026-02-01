package com.minidropbox.minidropbox.file.dto;
import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;


@AllArgsConstructor
@Getter
public class FileResponseDto {
    private Long id;
    private String originalFilename;
    private double size;
    private LocalDateTime createdAt;
}
