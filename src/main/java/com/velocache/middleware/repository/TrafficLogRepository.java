package com.velocache.middleware.repository;
import java.util.List;

import com.velocache.middleware.entity.TrafficLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TrafficLogRepository extends JpaRepository<TrafficLog, Long> {
    List<TrafficLog> findByEndpoint(String endpoint);
}