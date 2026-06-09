package com.douyin.api.repository;

import com.douyin.api.model.RequestLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface RequestLogRepository extends JpaRepository<RequestLog, Long> {

    // 今日请求量
    long countByTimestampAfter(LocalDateTime startOfDay);

    // Top 10 慢接口（按平均耗时降序）
    @Query(value = """
        SELECT url, AVG(duration_ms) AS avgCostMs, COUNT(*) AS requestCount
        FROM request_logs
        GROUP BY url
        ORDER BY avgCostMs DESC
        LIMIT 10
        """, nativeQuery = true)
    List<Object[]> findTop10SlowApis();

    // 分页查询所有日志（按时间倒序）
    Page<RequestLog> findAllByOrderByTimestampDesc(Pageable pageable);

    // 查询最近 N 条日志（不翻页，简单查询）
    List<RequestLog> findTop100ByOrderByTimestampDesc();

    // 查询最近 limit 条日志（动态数量）
    List<RequestLog> findTopNByOrderByTimestampDesc(Pageable pageable);

    // 获取平均响应耗时（所有接口）
    @Query("SELECT AVG(r.durationMs) FROM RequestLog r")
    Double getAverageDurationMs();

    @Query(value = """
        SELECT AVG(recent.duration_ms)
        FROM (
            SELECT duration_ms
            FROM request_logs
            ORDER BY timestamp DESC
            LIMIT 100
        ) recent
        """, nativeQuery = true)
    Double getRecentAverageDurationMs();

    // 获取总请求数
    long count();

    // 按时间范围查询（用于统计）
    List<RequestLog> findByTimestampBetween(LocalDateTime start, LocalDateTime end);

    // 按 URL 统计调用次数
    @Query("SELECT r.url, COUNT(r) FROM RequestLog r GROUP BY r.url ORDER BY COUNT(r) DESC")
    List<Object[]> countRequestsByUrl();

    // 按状态码统计
    @Query("SELECT r.statusCode, COUNT(r) FROM RequestLog r GROUP BY r.statusCode")
    List<Object[]> countRequestsByStatusCode();

    // 查询某时间段内的慢接口（耗时超过指定值）
    @Query("SELECT r FROM RequestLog r WHERE r.durationMs > :threshold ORDER BY r.durationMs DESC")
    List<RequestLog> findSlowApis(@Param("threshold") long threshold, Pageable pageable);

    // 查询平均耗时最高的 Top N 接口（JPQL版本，不依赖 native）
    @Query("""
        SELECT r.url, AVG(r.durationMs) as avgCost, COUNT(r) as requestCount 
        FROM RequestLog r 
        GROUP BY r.url 
        ORDER BY avgCost DESC
        """)
    List<Object[]> findTopSlowApis(Pageable pageable);
}
