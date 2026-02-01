package com.minidropbox.minidropbox.logs;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.file.FileMetadata;


public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {

    List<AuditLog> findByUser(User user);

    List<AuditLog> findByFile(FileMetadata file);
}

