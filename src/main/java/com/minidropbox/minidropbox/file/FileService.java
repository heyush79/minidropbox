package com.minidropbox.minidropbox.file;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.auth.UserRepository;

@Service
public class FileService {
    
    private final String storagePath="C:\\Users\\Lenovo\\minidropbox\\minidropbox-storage";

    private final FileRepository fileRepo;
    private final UserRepository userRepo;

    public FileService(FileRepository fileRepo,
                       UserRepository userRepo) {
        this.fileRepo = fileRepo;
        this.userRepo = userRepo;
    }

    public void uploadFile(MultipartFile file, String email) throws IOException {

        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        String storedFilename = UUID.randomUUID() + "_" + file.getOriginalFilename();

        Path targetPath = Paths.get(storagePath, storedFilename);
        Files.copy(file.getInputStream(), targetPath);

        FileMetadata metadata = new FileMetadata();
        metadata.setOriginalFilename(file.getOriginalFilename());
        metadata.setStoredFilename(storedFilename);
        metadata.setSize(file.getSize()/1000.0); // size in KB
        metadata.setUploadPath(targetPath.toString());
        metadata.setOwner(user);
        metadata.setCreatedAt(LocalDateTime.now());

        fileRepo.save(metadata);
    }

    public List<FileMetadata> listUserFiles(String email) {
        User user = userRepo.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        return fileRepo.findByOwner(user);
    }
}

