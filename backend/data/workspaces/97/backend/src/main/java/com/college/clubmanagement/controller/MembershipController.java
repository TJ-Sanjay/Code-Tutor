package com.college.clubmanagement.controller;

import com.college.clubmanagement.dto.MessageResponse;
import com.college.clubmanagement.dto.MembershipRequestDTO;

import com.college.clubmanagement.service.MembershipService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/membership")
public class MembershipController {

    @Autowired
    private MembershipService membershipService;

    @GetMapping("/clubs/{clubId}/requests")
    public ResponseEntity<List<MembershipRequestDTO>> getClubRequests(@PathVariable Long clubId) {
        return ResponseEntity.ok(membershipService.getClubRequests(clubId));
    }

    @GetMapping("/clubs/{clubId}/requests/pending")
    public ResponseEntity<List<MembershipRequestDTO>> getPendingRequests(@PathVariable Long clubId) {
        return ResponseEntity.ok(membershipService.getPendingRequests(clubId));
    }

    @PutMapping("/requests/{id}/approve")
    public ResponseEntity<MessageResponse> approveRequest(
            @PathVariable Long id,
            Authentication authentication) {
        membershipService.approveRequest(id, authentication.getName());
        return ResponseEntity.ok(new MessageResponse("Request approved successfully"));
    }

    @PutMapping("/requests/{id}/reject")
    public ResponseEntity<MessageResponse> rejectRequest(
            @PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String reason = request.getOrDefault("reason", "Not specified");
        membershipService.rejectRequest(id, authentication.getName(), reason);
        return ResponseEntity.ok(new MessageResponse("Request rejected"));
    }

    @GetMapping("/my-requests")
    public ResponseEntity<List<MembershipRequestDTO>> getMyRequests(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(membershipService.getRequestsByUser(username));
    }

    @GetMapping("/count/pending")
    public ResponseEntity<Long> getPendingRequestsCount(Authentication authentication) {
        return ResponseEntity.ok(membershipService.getPendingRequestsCountForClubHead(authentication.getName()));
    }
}
