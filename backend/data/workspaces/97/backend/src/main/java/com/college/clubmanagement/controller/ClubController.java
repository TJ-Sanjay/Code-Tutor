package com.college.clubmanagement.controller;

import com.college.clubmanagement.dto.ClubDTO;
import com.college.clubmanagement.dto.ClubMemberDTO;
import com.college.clubmanagement.dto.MessageResponse;
import com.college.clubmanagement.entity.ClubCategory;
import com.college.clubmanagement.service.ClubService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/clubs")
@CrossOrigin(origins = "*", maxAge = 3600)
public class ClubController {

    @Autowired
    private ClubService clubService;

    @GetMapping
    public ResponseEntity<List<ClubDTO>> getAllClubs(@RequestParam(required = false) String category,
            @RequestParam(required = false) String search,
            Authentication authentication) {
        String username = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            username = authentication.getName();
        }

        if (search != null && !search.isEmpty()) {
            return ResponseEntity.ok(clubService.searchClubs(search, username));
        } else if (category != null) {
            return ResponseEntity
                    .ok(clubService.getClubsByCategory(ClubCategory.valueOf(category.toUpperCase()), username));
        } else {
            return ResponseEntity.ok(clubService.getActiveClubs(username));
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ClubDTO> getClubById(@PathVariable Long id, Authentication authentication) {
        String username = null;
        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            username = authentication.getName();
        }
        return ResponseEntity.ok(clubService.getClubById(id, username));
    }

    @PostMapping
    public ResponseEntity<ClubDTO> createClub(@Valid @RequestBody ClubDTO clubDTO,
            Authentication authentication) {
        String username = authentication.getName();
        ClubDTO created = clubService.createClub(clubDTO, username);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ClubDTO> updateClub(@PathVariable Long id,
            @Valid @RequestBody ClubDTO clubDTO) {
        ClubDTO updated = clubService.updateClub(id, clubDTO);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<MessageResponse> deleteClub(@PathVariable Long id) {
        clubService.deleteClub(id);
        return ResponseEntity.ok(new MessageResponse("Club deleted successfully"));
    }

    @PostMapping("/{id}/join-request")
    public ResponseEntity<MessageResponse> submitJoinRequest(@PathVariable Long id,
            @RequestBody Map<String, String> request,
            Authentication authentication) {
        String username = authentication.getName();
        clubService.submitJoinRequest(
                id,
                username,
                request.get("areaOfInterest"),
                request.get("skills"),
                request.get("reason"),
                request.get("preferredRole"));
        return ResponseEntity.ok(new MessageResponse("Join request submitted successfully"));
    }

    // Club Head Member Management Endpoints

    @GetMapping("/my-clubs")
    public ResponseEntity<List<ClubDTO>> getMyClubs(Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(clubService.getClubsForClubHead(username));
    }

    @GetMapping("/{id}/members")
    public ResponseEntity<List<ClubMemberDTO>> getClubMembers(@PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        return ResponseEntity.ok(clubService.getClubMembers(id, username));
    }

    @DeleteMapping("/{clubId}/members/{memberId}")
    public ResponseEntity<MessageResponse> removeMember(@PathVariable Long clubId,
            @PathVariable Long memberId,
            Authentication authentication) {
        String username = authentication.getName();
        clubService.removeMember(clubId, memberId, username);
        return ResponseEntity.ok(new MessageResponse("Member removed successfully"));
    }

    @DeleteMapping("/{id}/leave")
    public ResponseEntity<MessageResponse> leaveClub(@PathVariable Long id,
            Authentication authentication) {
        String username = authentication.getName();
        clubService.leaveClub(id, username);
        return ResponseEntity.ok(new MessageResponse("You have left the club successfully"));
    }
}
