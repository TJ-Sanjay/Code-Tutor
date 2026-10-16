package com.codetutor.backend.project.repository;

import com.codetutor.backend.project.model.Project;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** JPA repository for Project entity. Every user-facing lookup is scoped by owner. */
@Repository
public interface ProjectRepository extends JpaRepository<Project, Long> {

    List<Project> findByOwnerEmailOrderByCreatedAtDesc(String ownerEmail);

    Optional<Project> findByIdAndOwnerEmail(Long id, String ownerEmail);

    boolean existsByIdAndOwnerEmail(Long id, String ownerEmail);

    long countByOwnerEmail(String ownerEmail);

    List<Project> findByStatusIn(Collection<Project.AnalysisStatus> statuses);

    @Query("select coalesce(sum(p.sizeBytes), 0) from Project p where p.owner.id = :ownerId")
    long sumSizeByOwner(Long ownerId);
}
