package com.minidropbox.minidropbox.file;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.logs.ActionType;
import com.minidropbox.minidropbox.logs.AuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j//logging framework
@Service
@RequiredArgsConstructor
public class ChunkUploadService {

    private final FileChunkRepository fileChunkRepository;
    private final FileMetadataRepository fileMetadataRepository;
    private final FileService fileService;
    private final AuditLogService auditLogService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    @Value("${file.chunk-temp-dir:#{null}}")
    private String chunkTempDir;

    /**
     * Initialize a chunked upload session
     */
    public String initializeChunkedUpload(
            String originalFilename,
            Long totalSize,
            Long chunkSize,
            User user) throws IOException {

        String uploadId = UUID.randomUUID().toString();
        Integer totalChunks = Math.toIntExact((totalSize + chunkSize - 1) / chunkSize);

        // Create temp directory for this upload
        String tempDir = chunkTempDir != null ? chunkTempDir : uploadDir;
        Path tempUploadPath = Paths.get(tempDir, "chunks", uploadId);
        Files.createDirectories(tempUploadPath);

        FileChunkMetadata chunkMetadata = FileChunkMetadata.builder()
                .uploadId(uploadId)
                .owner(user)
                .originalFilename(originalFilename)
                .totalSize(totalSize)
                .chunkSize(chunkSize)
                .totalChunks(totalChunks)
                .uploadedChunks(0)
                .tempStoragePath(tempUploadPath.toString())
                .status("IN_PROGRESS")
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        fileChunkRepository.save(chunkMetadata);

        log.info("Initialized chunk upload {} for user {} | totalChunks: {}", 
                uploadId, user.getId(), totalChunks);

        return uploadId;
    }

    /**
     * Upload a single chunk
     */
    public void uploadChunk(
            String uploadId,
            Integer chunkNumber,
            MultipartFile chunkFile,
            User user) throws IOException {

        FileChunkMetadata chunkMetadata = fileChunkRepository
                .findByUploadIdAndOwner(uploadId, user)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        if (!"IN_PROGRESS".equals(chunkMetadata.getStatus())) {
            throw new RuntimeException("Upload session is not in progress");
        }

        // Validate chunk number
        if (chunkNumber < 0 || chunkNumber >= chunkMetadata.getTotalChunks()) {
            throw new RuntimeException("Invalid chunk number");
        }

        // Save chunk file
        String chunkFileName = "chunk_" + chunkNumber;
        Path chunkPath = Paths.get(chunkMetadata.getTempStoragePath(), chunkFileName);
        Files.copy(chunkFile.getInputStream(), chunkPath);

        // Update metadata
        chunkMetadata.setUploadedChunks(chunkMetadata.getUploadedChunks() + 1);
        chunkMetadata.setUpdatedAt(LocalDateTime.now());
        fileChunkRepository.save(chunkMetadata);

        log.info("Uploaded chunk {} for upload session {}", chunkNumber, uploadId);
    }

    /**
     * Complete the chunked upload by assembling all chunks
     */
    public FileMetadata completeChunkedUpload(String uploadId, User user) throws IOException {

        FileChunkMetadata chunkMetadata = fileChunkRepository
                .findByUploadIdAndOwner(uploadId, user)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        // Verify all chunks have been uploaded
        if (!chunkMetadata.getUploadedChunks().equals(chunkMetadata.getTotalChunks())) {
            throw new RuntimeException("Not all chunks have been uploaded yet");
        }

        try {
            // Create user directory
            Path userDir = Paths.get(uploadDir, user.getId().toString());
            Files.createDirectories(userDir);

            // Create final file path
            String storedFilename = UUID.randomUUID() + "_" + chunkMetadata.getOriginalFilename();
            Path finalFilePath = userDir.resolve(storedFilename);

            // Assemble chunks into final file
            assembleChunks(chunkMetadata, finalFilePath);

            // Create FileMetadata
            FileMetadata fileMetadata = FileMetadata.builder()
                    .originalFilename(chunkMetadata.getOriginalFilename())
                    .storedFilename(storedFilename)
                    .size(chunkMetadata.getTotalSize() / 1000.0) // Convert to KB
                    .uploadPath(finalFilePath.toString())
                    .owner(user)
                    .createdAt(LocalDateTime.now())
                    .build();

            FileMetadata savedMetadata = fileMetadataRepository.save(fileMetadata);

            // Log audit
            auditLogService.log(
                    user,
                    ActionType.UPLOAD,
                    savedMetadata,
                    "Uploaded file via chunks: " + savedMetadata.getOriginalFilename()
            );

            // Update chunk metadata status
            chunkMetadata.setStatus("COMPLETED");
            chunkMetadata.setCompletedAt(LocalDateTime.now());
            fileChunkRepository.save(chunkMetadata);

            // Clean up temp chunks
            cleanupChunks(chunkMetadata);

            log.info("Completed chunked upload {} for user {}", uploadId, user.getId());

            return savedMetadata;

        } catch (Exception e) {
            log.error("Error completing chunked upload {}: {}", uploadId, e.getMessage());
            chunkMetadata.setStatus("FAILED");
            chunkMetadata.setErrorMessage(e.getMessage());
            fileChunkRepository.save(chunkMetadata);
            throw new RuntimeException("Failed to complete upload: " + e.getMessage());
        }
    }

    /**
     * Assemble all chunks into a single file
     */
    private void assembleChunks(FileChunkMetadata chunkMetadata, Path finalFilePath) throws IOException {

        try (FileOutputStream fos = new FileOutputStream(finalFilePath.toFile())) {

            for (int i = 0; i < chunkMetadata.getTotalChunks(); i++) {
                String chunkFileName = "chunk_" + i;
                Path chunkPath = Paths.get(chunkMetadata.getTempStoragePath(), chunkFileName);

                try (FileInputStream fis = new FileInputStream(chunkPath.toFile())) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = fis.read(buffer)) != -1) {
                        fos.write(buffer, 0, bytesRead);
                    }
                }

                log.debug("Assembled chunk {} for upload {}", i, chunkMetadata.getUploadId());
            }
        }
    }

    /**
     * Get status of a chunked upload
     */
    public FileChunkMetadata getUploadStatus(String uploadId, User user) {

        return fileChunkRepository
                .findByUploadIdAndOwner(uploadId, user)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));
    }

    /**
     * Cancel a chunked upload
     */
    public void cancelChunkedUpload(String uploadId, User user) throws IOException {

        FileChunkMetadata chunkMetadata = fileChunkRepository
                .findByUploadIdAndOwner(uploadId, user)
                .orElseThrow(() -> new RuntimeException("Upload session not found"));

        chunkMetadata.setStatus("CANCELLED");
        chunkMetadata.setUpdatedAt(LocalDateTime.now());
        fileChunkRepository.save(chunkMetadata);

        cleanupChunks(chunkMetadata);

        log.info("Cancelled chunked upload {} for user {}", uploadId, user.getId());
    }

    /**
     * Clean up temporary chunk files
     */
    private void cleanupChunks(FileChunkMetadata chunkMetadata) throws IOException {

        Path tempPath = Paths.get(chunkMetadata.getTempStoragePath());

        if (Files.exists(tempPath)) {
            Files.walk(tempPath)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (IOException e) {
                            log.warn("Failed to delete chunk file: {}", path);
                        }
                    });
            log.info("Cleaned up chunk files for upload {}", chunkMetadata.getUploadId());
        }
    }
}
