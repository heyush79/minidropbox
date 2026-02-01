package com.minidropbox.minidropbox.file;   

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.logs.ActionType;
import com.minidropbox.minidropbox.logs.AuditLogService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class FileShareService {

    private final FileMetadataRepository fileRepository;
    private final FileShareRepository fileShareRepository;
    private final AuditLogService auditLogService;

    public void shareFile(Long fileId, User owner, User targetUser) {

        FileMetadata file = fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Only owner can share file");
        }

        boolean alreadyShared = fileShareRepository
                .findByFileAndSharedWith(file, targetUser)
                .isPresent();

        if (alreadyShared) {
            throw new RuntimeException("File already shared with this user");
        }

        FileShare share = FileShare.builder()
                .file(file)
                .sharedWith(targetUser)
                .sharedAt(LocalDateTime.now())
                .build();
        auditLogService.log(
            owner,
            ActionType.SHARE,
            file,
            "Shared with " + targetUser.getEmail()
        );

        fileShareRepository.save(share);
    }

    public List<FileMetadata> getFilesSharedWith(User user) {

        return fileShareRepository.findBySharedWith(user)
                .stream()
                .map(FileShare::getFile)
                .filter(file -> !file.isDeleted())
                .toList();
    }

    public void revokeShare(Long fileId, User owner, User targetUser) {

        FileMetadata file = fileRepository.findByIdAndDeletedFalse(fileId)
                .orElseThrow(() -> new RuntimeException("File not found"));

        if (!file.getOwner().getId().equals(owner.getId())) {
            throw new RuntimeException("Only owner can revoke access");
        }

        FileShare share = fileShareRepository
                .findByFileAndSharedWith(file, targetUser)
                .orElseThrow(() -> new RuntimeException("File not shared with this user"));
        auditLogService.log(
            owner,
            ActionType.UNSHARE,
            file,
            "Revoked access from " + targetUser.getEmail()
        );

        fileShareRepository.delete(share);
    }
}

