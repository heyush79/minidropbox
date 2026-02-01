package com.minidropbox.minidropbox.file;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.minidropbox.minidropbox.auth.User;

public interface FileMetadataRepository extends JpaRepository<FileMetadata, Long> {

    Optional<FileMetadata> findByIdAndOwner(Long id, User owner);
    List<FileMetadata> findByOwner(User owner);
    List<FileMetadata> findByOwnerAndDeletedFalse(User owner);

    Optional<FileMetadata> findByIdAndDeletedFalse(Long id);
}



