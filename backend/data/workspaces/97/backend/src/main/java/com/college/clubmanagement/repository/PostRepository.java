package com.college.clubmanagement.repository;

import com.college.clubmanagement.entity.Club;
import com.college.clubmanagement.entity.Post;
import com.college.clubmanagement.entity.PostType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PostRepository extends JpaRepository<Post, Long> {
    
    List<Post> findByClub(Club club);
    
    List<Post> findByClubAndPublishedTrue(Club club);
    
    List<Post> findByType(PostType type);
    
    List<Post> findByClubAndType(Club club, PostType type);
    
    List<Post> findByPublishedTrueOrderByCreatedAtDesc();
}
