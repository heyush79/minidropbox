package com.minidropbox.minidropbox.file;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minidropbox.minidropbox.auth.User;

public interface FileShareRepository extends JpaRepository<FileShare, Long> {

    Optional<FileShare> findByFileAndSharedWith(FileMetadata file, User user);

    List<FileShare> findBySharedWith(User user);
}

