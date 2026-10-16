package com.college.clubmanagement.security;

import com.college.clubmanagement.entity.User;
import com.college.clubmanagement.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.stream.Collectors;

@Service
public class CustomUserDetailsService implements UserDetailsService {

        @Autowired
        private UserRepository userRepository;

        @Override
        @Transactional
        public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
                User user = userRepository.findByUsername(username)
                                .orElseThrow(() -> new UsernameNotFoundException(
                                                "User not found with username: " + username));

                return new org.springframework.security.core.userdetails.User(
                                user.getUsername(),
                                user.getPassword(),
                                user.getActive(),
                                true,
                                true,
                                true,
                                mapRolesToAuthorities(user));
        }

        @Transactional
        public UserDetails loadUserById(Long id) {
                if (id == null)
                        throw new IllegalArgumentException("ID cannot be null");
                User user = userRepository.findById(id)
                                .orElseThrow(() -> new UsernameNotFoundException("User not found with id: " + id));

                return new org.springframework.security.core.userdetails.User(
                                user.getUsername(),
                                user.getPassword(),
                                user.getActive(),
                                true,
                                true,
                                true,
                                mapRolesToAuthorities(user));
        }

        private Collection<? extends GrantedAuthority> mapRolesToAuthorities(User user) {
                return user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                                .collect(Collectors.toList());
        }
}
