package com.minidropbox.minidropbox.file;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.auth.UserRepository;
import com.minidropbox.minidropbox.logs.ActionType;
import com.minidropbox.minidropbox.logs.AuditLogService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    private final FileMetadataRepository fileRepository;
    private final UserRepository userRepository;
    private final FileShareRepository fileShareRepository;
    private final AuditLogService auditLogService;

    @Value("${file.upload-dir}")
    private String uploadDir;

    public FileMetadata uploadFile(MultipartFile file, User user) throws IOException {

        if (file.isEmpty()) {
           throw new IllegalArgumentException("File is empty");
        }

        // 1. Ensure upload directory exists
        Path userDir = Paths.get(uploadDir, user.getId().toString());
        Files.createDirectories(userDir);

        // 2. Generate stored filename (UUID avoids collisions)
        String originalName = Paths.get(file.getOriginalFilename()).getFileName().toString();
        String storedFilename = UUID.randomUUID() + "_" + originalName;
        Path targetPath = userDir.resolve(storedFilename);

        // 3. Save file to disk
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        // 4. Save metadata
        FileMetadata metadata = FileMetadata.builder()
                .originalFilename(file.getOriginalFilename())
                .storedFilename(storedFilename)
                .size(file.getSize()/1000.0) // size in KB
                .uploadPath(targetPath.toString())
                .owner(user)
                .createdAt(LocalDateTime.now())
                .build();

        // 1. Save metadata FIRST
        FileMetadata savedMetadata = fileRepository.save(metadata);

        // 2. Then log audit
        auditLogService.log(
                user,
                ActionType.UPLOAD,
                savedMetadata,
                "Uploaded file " + savedMetadata.getOriginalFilename()
        );
        log.info("Uploaded file {} for user {}", savedMetadata.getId(), user.getId());
        return savedMetadata;
    }

    public Resource downloadFile(Long fileId, User user) throws IOException {

        // 1️⃣ Fetch file (NOT owner-restricted)
        FileMetadata file = fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // 2️⃣ Authorization: owner OR shared
        boolean isOwner = file.getOwner().getId().equals(user.getId());

        boolean isShared = fileShareRepository
                .findByFileAndSharedWith(file, user)
                .isPresent();

        if (!isOwner && !isShared) {
            throw new AccessDeniedException("Access denied");
        }

        // 3️⃣ Load file from disk
        Path filePath = Paths.get(file.getUploadPath());

        if (!Files.exists(filePath)) {
                    throw new FileNotFoundException("File not found on disk");
                }
                auditLogService.log(
            user,
            ActionType.DOWNLOAD,
            file,
            "Downloaded file"
        );

        log.info("Downloading file {} for user {}", fileId, user.getId());

        return new UrlResource(filePath.toUri());
    }

    public List<FileMetadata> getFilesForUser(User user) {
        log.info("Fetching files for user {}", user.getId());
        return fileRepository.findByOwnerAndDeletedFalse(user);
    }

    public void deleteFile(Long fileId, User user) {

        FileMetadata file = fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        // 🔐 Ownership validation
        if (!file.getOwner().getId().equals(user.getId())) {
            throw new RuntimeException("You are not allowed to delete this file");
        }
        log.info("Deleting file {} for user {}", fileId, user.getId());

        fileRepository.save(file);//
         // 🧹 Soft delete 
        file.setDeleted(true);// mark as deleted
        file.setDeletedAt(LocalDateTime.now());// set deletion timestamp
        auditLogService.log(
            user,
            ActionType.DELETE,
            file,
            "Soft deleted file"
        );
    }
}
