package com.minidropbox.minidropbox.file;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.minidropbox.minidropbox.auth.User;

@Repository
public interface FileChunkRepository extends JpaRepository<FileChunkMetadata, Long> {

    Optional<FileChunkMetadata> findByUploadId(String uploadId);

    Optional<FileChunkMetadata> findByUploadIdAndOwner(String uploadId, User owner);

}
