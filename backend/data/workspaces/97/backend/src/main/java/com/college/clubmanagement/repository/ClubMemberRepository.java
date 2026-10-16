package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Club;
import com.college.clubmanagement.entity.ClubMember;
import com.college.clubmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ClubMemberRepository extends JpaRepository<ClubMember, Long> {
    
    List<ClubMember> findByClub(Club club);
    
    List<ClubMember> findByUser(User user);
    
    Optional<ClubMember> findByClubAndUser(Club club, User user);
    
    Boolean existsByClubAndUser(Club club, User user);
    
    Long countByClub(Club club);
}
