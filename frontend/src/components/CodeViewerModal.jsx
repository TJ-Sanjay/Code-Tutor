import React, { useState } from 'react';
import Editor from '@monaco-editor/react';
import { X, FileCode, Folder, ChevronRight, Play } from 'lucide-react';

const SAMPLE_FILES = {
  'UserController.java': {
    language: 'java',
    code: `package com.codetutor.backend.auth.controller;

import com.codetutor.backend.auth.dto.*;
import com.codetutor.backend.auth.service.AuthService;
import com.codetutor.backend.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(
            @Valid @RequestBody RegisterRequest request
    ) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(201).body(ApiResponse.success("Account created", response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getCurrentUser(
            @AuthenticationPrincipal UserDetails userDetails
    ) {
        UserProfileResponse profile = authService.getProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", profile));
    }
}`
  },
  'AuthService.java': {
    language: 'java',
    code: `package com.codetutor.backend.auth.service;

import com.codetutor.backend.auth.model.User;
import com.codetutor.backend.auth.repository.UserRepository;
import com.codetutor.backend.security.jwt.JwtUtil;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtUtil jwtUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
    }

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("Email already taken");
        }
        User user = new User(request.getUsername(), request.getEmail(), passwordEncoder.encode(request.getPassword()));
        userRepository.save(user);
        String token = jwtUtil.generateAccessToken(user.getEmail(), user.getRole().name());
        return new AuthResponse(token, user);
    }
}`
  },
  'Project.java': {
    language: 'java',
    code: `package com.codetutor.backend.project.model;

import com.codetutor.backend.auth.model.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "projects")
public class Project {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    private SourceType sourceType;

    @ManyToOne(fetch = FetchType.LAZY)
    private User owner;

    public enum SourceType { GITHUB, ZIP }
}`
  }
};

export default function CodeViewerModal({ project, onClose }) {
  const [selectedFile, setSelectedFile] = useState('UserController.java');
  const [theme, setTheme] = useState('vs-dark');

  if (!project) return null;

  const currentFileObj = SAMPLE_FILES[selectedFile] || SAMPLE_FILES['UserController.java'];

  return (
    <div className="modal-overlay">
      <div className="modal-content" style={{ maxWidth: '1000px', width: '92vw', height: '85vh', display: 'flex', flexDirection: 'column', padding: 0, overflow: 'hidden' }}>
        
        {/* Header */}
        <div style={{
          padding: '1rem 1.5rem', borderBottom: '1px solid var(--border-color)',
          display: 'flex', justifyContent: 'space-between', alignItems: 'center', background: '#0f172a'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
            <FileCode size={22} color="var(--primary)" />
            <div>
              <div style={{ fontWeight: 700, fontSize: '1.05rem', color: 'white' }}>{project.name} Code Viewer</div>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>Read-only Tree-Sitter Monaco Editor View • {selectedFile}</div>
            </div>
          </div>

          <div style={{ display: 'flex', alignItems: 'center', gap: '1rem' }}>
            <select 
              value={theme} onChange={e => setTheme(e.target.value)}
              className="form-input" style={{ width: 'auto', padding: '0.35rem 0.75rem', fontSize: '0.8rem' }}
            >
              <option value="vs-dark">VS Dark Theme</option>
              <option value="light">VS Light Theme</option>
            </select>
            <button onClick={onClose} style={{ background: 'transparent', border: 'none', color: 'var(--text-muted)', cursor: 'pointer' }}>
              <X size={22} />
            </button>
          </div>
        </div>

        {/* Content Body: Left File Explorer Sidebar + Right Monaco Editor */}
        <div style={{ flex: 1, display: 'flex', overflow: 'hidden' }}>
          
          {/* File Explorer */}
          <div style={{
            width: '260px', background: '#0b0f19', borderRight: '1px solid var(--border-color)',
            padding: '1rem', overflowY: 'auto'
          }}>
            <div style={{ fontSize: '0.75rem', fontWeight: 700, color: 'var(--text-muted)', marginBottom: '0.75rem', textTransform: 'uppercase' }}>
              PROJECT FILES (JAVA / SPRING)
            </div>

            <div style={{ display: 'flex', flexDirection: 'column', gap: '0.25rem' }}>
              {Object.keys(SAMPLE_FILES).map(fileName => (
                <button
                  key={fileName}
                  onClick={() => setSelectedFile(fileName)}
                  style={{
                    display: 'flex', alignItems: 'center', gap: '0.5rem',
                    padding: '0.5rem 0.75rem', borderRadius: '6px', border: 'none',
                    background: selectedFile === fileName ? 'rgba(99, 102, 241, 0.2)' : 'transparent',
                    color: selectedFile === fileName ? '#a5b4fc' : 'var(--text-main)',
                    fontWeight: selectedFile === fileName ? 600 : 400,
                    fontSize: '0.85rem', cursor: 'pointer', textAlign: 'left'
                  }}
                >
                  <FileCode size={16} color={selectedFile === fileName ? '#818cf8' : '#9ca3af'} />
                  {fileName}
                </button>
              ))}
            </div>
          </div>

          {/* Monaco Editor */}
          <div style={{ flex: 1, height: '100%' }}>
            <Editor
              height="100%"
              language={currentFileObj.language}
              theme={theme}
              value={currentFileObj.code}
              options={{
                readOnly: true,
                fontSize: 14,
                fontFamily: "'Fira Code', monospace",
                minimap: { enabled: true },
                scrollBeyondLastLine: false,
                automaticLayout: true,
                smoothScrolling: true
              }}
            />
          </div>

        </div>

      </div>
    </div>
  );
}
