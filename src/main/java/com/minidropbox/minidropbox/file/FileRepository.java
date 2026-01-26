package com.minidropbox.minidropbox.file;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import com.minidropbox.minidropbox.auth.User;

public interface FileRepository extends JpaRepository<FileMetadata, Long> {

    List<FileMetadata> findByOwner(User owner);
}

