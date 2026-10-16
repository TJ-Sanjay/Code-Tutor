package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Club;
import com.college.clubmanagement.entity.MembershipRequest;
import com.college.clubmanagement.entity.RequestStatus;
import com.college.clubmanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MembershipRequestRepository extends JpaRepository<MembershipRequest, Long> {
    
    List<MembershipRequest> findByClub(Club club);
    
    List<MembershipRequest> findByStudent(User student);
    
    List<MembershipRequest> findByClubAndStatus(Club club, RequestStatus status);
    
    List<MembershipRequest> findByStatus(RequestStatus status);
    
    Optional<MembershipRequest> findByClubAndStudentAndStatus(Club club, User student, RequestStatus status);
    
    Boolean existsByClubAndStudentAndStatus(Club club, User student, RequestStatus status);
}
