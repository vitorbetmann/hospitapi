package com.vitorbetmann.hospitapi.scheduling.repository;

import com.vitorbetmann.hospitapi.scheduling.domain.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    @EntityGraph(attributePaths = "patient")
    Optional<User> findByUsername(String username);
}