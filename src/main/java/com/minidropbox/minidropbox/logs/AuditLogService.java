package com.minidropbox.minidropbox.logs;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.file.FileMetadata;

import lombok.RequiredArgsConstructor;
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogRepository auditLogRepository;

    public void log(User user, ActionType action, FileMetadata file, String details) {

        AuditLog log = AuditLog.builder()
                .user(user)
                .action(action)
                .file(file)
                .timestamp(LocalDateTime.now())
                .details(details)
                .build();

        auditLogRepository.save(log);
    }
}

