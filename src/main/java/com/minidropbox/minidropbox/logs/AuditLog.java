package com.minidropbox.minidropbox.logs;

import java.time.LocalDateTime;

import com.minidropbox.minidropbox.auth.User;
import com.minidropbox.minidropbox.file.FileMetadata;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // WHO
    @ManyToOne(optional = false)
    private User user;

    // WHAT
    @Enumerated(EnumType.STRING)
    private ActionType action;

    // WHICH FILE (nullable for login etc later)
    @ManyToOne
    private FileMetadata file;

    // WHEN
    private LocalDateTime timestamp;

    // OPTIONAL: extra info
    private String details;
}

