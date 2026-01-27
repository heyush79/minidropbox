package com.minidropbox.minidropbox.file;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.util.UUID;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.auth.UserRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileService {

    private final FileMetadataRepository fileRepository;
    private final UserRepository userRepository;

    
    private final String uploadDir= "C:\\Users\\Lenovo\\minidropbox\\minidropbox-storage";

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

        return fileRepository.save(metadata);
    }

    public Resource downloadFile(Long fileId, User user) throws IOException {

        FileMetadata metadata = fileRepository.findByIdAndOwner(fileId, user)
                .orElseThrow(() -> new AccessDeniedException("File not found or access denied"));

        Path filePath = Paths.get(metadata.getUploadPath());

        if (!Files.exists(filePath)) {
            throw new FileNotFoundException("File not found on disk");
        }

        return new UrlResource(filePath.toUri());// return the file as a Resource
}
}
