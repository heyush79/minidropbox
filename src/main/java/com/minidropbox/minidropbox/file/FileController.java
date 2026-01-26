package com.minidropbox.minidropbox.file;

import java.io.IOException;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping("/upload")
    public String upload(@RequestParam("file") MultipartFile file,
                         Authentication authentication) throws IOException {

        fileService.uploadFile(file, authentication.getName());
        return "File uploaded successfully";
    }

    @GetMapping
    public List<FileMetadata> list(Authentication authentication) {
        return fileService.listUserFiles(authentication.getName());
    }
}

