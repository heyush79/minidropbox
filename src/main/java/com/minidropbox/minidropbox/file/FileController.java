package com.minidropbox.minidropbox.file;

import java.io.IOException;
import java.nio.file.Files;

import org.springframework.http.HttpHeaders;
import org.springframework.core.io.Resource;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.auth.UserRepository;
import com.minidropbox.minidropbox.file.dto.FileResponseDto;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/files")
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;
    private final UserRepository userRepository;

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFile(
            @RequestParam("file") MultipartFile file,
            Authentication authentication) throws IOException {

        String email = authentication.getName();
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        FileMetadata savedFile = fileService.uploadFile(file, user);

        return ResponseEntity.ok(
                new FileResponseDto(
                        savedFile.getId(),
                        savedFile.getOriginalFilename(),
                        savedFile.getSize(),
                        savedFile.getCreatedAt()
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<Resource> downloadFile(
        @PathVariable Long id,
        Authentication authentication) throws IOException {

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User not found"));

        Resource resource = (Resource) fileService.downloadFile(id, user);
        // String contentType = Files.probeContentType(filePath);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
}

}
