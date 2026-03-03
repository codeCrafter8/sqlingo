package com.codecrafter8.nl2sql.repository;

import com.codecrafter8.nl2sql.model.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
    Page<QueryLog> findByStatus(QueryLog.QueryStatus status, Pageable pageable);
    Page<QueryLog> findByNaturalLanguageQueryContaining(String query, Pageable pageable);
}

