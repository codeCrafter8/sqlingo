package com.codecrafter8.nl2sql.repository;

import com.codecrafter8.nl2sql.model.SchemaTable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TableRepository extends JpaRepository<SchemaTable, Long> {
    Optional<SchemaTable> findByName(String name);
}
