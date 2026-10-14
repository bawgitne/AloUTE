package com.aloute.repository;

import com.aloute.entity.Report;
import com.aloute.entity.ReportStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ReportRepository extends JpaRepository<Report, Long>, JpaSpecificationExecutor<Report> {

    long countByStatus(ReportStatus status);

    long countByReportedPostId(Long reportedPostId);

    long countByReportedUserId(Long reportedUserId);

    List<Report> findByReportedPostId(Long reportedPostId);

    List<Report> findByReportedUserId(Long reportedUserId);

    List<Report> findTop10ByOrderByCreatedAtDesc();
}
