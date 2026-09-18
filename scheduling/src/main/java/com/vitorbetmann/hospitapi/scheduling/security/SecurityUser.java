package com.vitorbetmann.hospitapi.scheduling.security;

import com.vitorbetmann.hospitapi.scheduling.domain.Role;
import com.vitorbetmann.hospitapi.scheduling.domain.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;

public record SecurityUser(
        Long userId,
        String username,
        String passwordHash,
        Role role,
        Long patientId) implements UserDetails {

    public static SecurityUser from(User user) {
        Long patientId = user.getPatient() == null ? null : user.getPatient().getId();
        return new SecurityUser(user.getId(), user.getUsername(),
                user.getPasswordHash(), user.getRole(), patientId);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return username;
    }
}
