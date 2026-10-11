package com.codetutor.backend.project.repository;

import com.codetutor.backend.project.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * JPA repository for Project entity.
 */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    Optional<Project> findByIdAndOwnerEmail(Long id, String ownerEmail);

    boolean existsByIdAndOwnerEmail(Long id, String ownerEmail);

    long countByOwnerEmail(String ownerEmail);
}
