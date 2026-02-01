package com.minidropbox.minidropbox.file;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
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
        private final FileShareService fileShareService;

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

                User user = getCurrentUser(authentication);

                Resource resource = (Resource) fileService.downloadFile(id, user);

                return ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
                }

                private User getCurrentUser(Authentication authentication) {
                return userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        }

        @GetMapping
        public ResponseEntity<List<FileResponseDto>> listUserFiles(Authentication authentication) {

                String email = authentication.getName();
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                List<FileResponseDto> files = fileService.getFilesForUser(user)
                        .stream()
                        .map(f -> new FileResponseDto(
                                f.getId(),
                                f.getOriginalFilename(),
                                f.getSize(),
                                f.getCreatedAt()
                        ))
                        .toList();

                return ResponseEntity.ok(files);
        }

        @DeleteMapping("/{id}")
        public ResponseEntity<?> deleteFile(
                        @PathVariable Long id,
                        Authentication authentication) {

                String email = authentication.getName();
                User user = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("User not found"));

                fileService.deleteFile(id, user);

                return ResponseEntity.ok(Map.of(
                        "message", "File deleted successfully"
                ));
        }

        @PostMapping("/{id}/share")//
        public ResponseEntity<?> shareFile(
                        @PathVariable Long id,
                        @RequestParam String email,
                        Authentication authentication) {

                User owner = userRepository.findByEmail(authentication.getName())
                        .orElseThrow(() -> new RuntimeException("User not found"));

                User targetUser = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Target user not found"));

                fileShareService.shareFile(id, owner, targetUser);

                return ResponseEntity.ok(Map.of("message", "File shared successfully"));
        }

        @GetMapping("/shared")
        public ResponseEntity<List<FileResponseDto>> sharedWithMe(
                Authentication authentication) {

        User user = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));

        List<FileResponseDto> files = fileShareService
                .getFilesSharedWith(user)
                .stream()
                .map(f -> new FileResponseDto(
                        f.getId(),
                        f.getOriginalFilename(),
                        f.getSize(),
                        f.getCreatedAt()
                ))
                .toList();

        return ResponseEntity.ok(files);
        }

        @DeleteMapping("/{id}/share")
        public ResponseEntity<?> revokeShare(
        @PathVariable Long id,
        @RequestParam String email,
        Authentication authentication) {

                User owner = userRepository.findByEmail(authentication.getName())
                        .orElseThrow(() -> new RuntimeException("User not found"));

                User targetUser = userRepository.findByEmail(email)
                        .orElseThrow(() -> new RuntimeException("Target user not found"));

                fileShareService.revokeShare(id, owner, targetUser);

                return ResponseEntity.ok(Map.of("message", "Access revoked"));
        }



}