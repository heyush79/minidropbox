package com.minidropbox.minidropbox.file;

import java.time.LocalDateTime;

import com.minidropbox.minidropbox.auth.User;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "file_chunks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileChunkMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String uploadId; // Unique identifier for the upload session

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User owner; // User who initiated the upload

    @Column(nullable = false)
    private String originalFilename;

    @Column(nullable = false)
    private Long totalSize; // Total file size in bytes

    @Column(nullable = false)
    private Long chunkSize; // Size of each chunk in bytes

    @Column(nullable = false)
    private Integer totalChunks; // Total number of chunks

    @Column(nullable = false)
    private Integer uploadedChunks; // Number of chunks received so far

    @Column(nullable = false)
    private String tempStoragePath; // Temporary directory storing chunks

    @Column(nullable = false)
    private String status; // "IN_PROGRESS", "COMPLETED", "FAILED", "CANCELLED"

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    private LocalDateTime completedAt; // When the upload was completed

    @Column(columnDefinition = "TEXT")
    private String errorMessage; // Error details if status is FAILED
}
