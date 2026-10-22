package com.aloute.security.principal;

import com.aloute.entity.User;
import com.aloute.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {
    private final UserRepository userRepository;
    @Override
    public UserDetails loadUserByUsername(String identifier) throws UsernameNotFoundException {
        if (identifier == null || identifier.isBlank()) {
            throw new UsernameNotFoundException("User not found");
        }
        String clean = identifier.trim();
        User user = userRepository.findByEmailIgnoreCase(clean)
                .or(() -> userRepository.findByUsernameIgnoreCase(clean))
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + clean));
        return UserPrincipal.from(user);
    }

    public UserPrincipal loadById(Long id) {
        return UserPrincipal.from(userRepository.findById(id)
                .orElseThrow(() -> new UsernameNotFoundException("User not found")));
    }
}
