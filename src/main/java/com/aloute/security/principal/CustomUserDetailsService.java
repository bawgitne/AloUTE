package com.aloute.security.principal;

import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    public CustomUserDetailsService(UserRepository userRepository) { this.userRepository=userRepository; }
    @Override public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException { return UserPrincipal.from(loadByEmail(email)); }
    public UserPrincipal loadById(Long id) { return UserPrincipal.from(userRepository.findById(id).orElseThrow(() -> new UsernameNotFoundException("User not found"))); }
    private User loadByEmail(String email) { return userRepository.findByEmailIgnoreCase(email).orElseThrow(() -> new UsernameNotFoundException("User not found")); }
}
