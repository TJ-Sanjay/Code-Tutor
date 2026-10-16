package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Club;
import com.college.clubmanagement.entity.ClubCategory;
import com.college.clubmanagement.entity.ClubStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ClubRepository extends JpaRepository<Club, Long> {

    List<Club> findByCategory(ClubCategory category);

    List<Club> findByStatus(ClubStatus status);

    Long countByStatus(ClubStatus status);

    List<Club> findByCategoryAndStatus(ClubCategory category, ClubStatus status);

    @Query("SELECT c FROM Club c WHERE LOWER(c.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
            "OR LOWER(c.description) LIKE LOWER(CONCAT('%', :keyword, '%'))")
    List<Club> searchByKeyword(@Param("keyword") String keyword);

    Boolean existsByName(String name);

    List<Club> findByStatusAndUpdatedAtBefore(ClubStatus status, java.time.LocalDateTime cutoff);
}
