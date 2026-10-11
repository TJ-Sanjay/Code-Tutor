package com.codetutor.backend.chat;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    List<ChatMessage> findByProjectIdAndUserIdOrderByIdAsc(Long projectId, Long userId);

    @Transactional
    void deleteByProjectIdAndUserId(Long projectId, Long userId);

    @Transactional
    void deleteByProjectId(Long projectId);
}
