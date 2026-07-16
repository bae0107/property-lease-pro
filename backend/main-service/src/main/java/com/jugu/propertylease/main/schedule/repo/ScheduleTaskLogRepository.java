package com.jugu.propertylease.main.schedule.repo;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface ScheduleTaskLogRepository {

    /**
     * 插入新任务日志记录（status=RUNNING）。
     * 若 (task_type, scheduled_date) 已存在则抛 DuplicateKeyException，
     * 调用方捕获后判断是否需要重跑。
     */
    Long insert(String taskType, LocalDate scheduledDate, String status,
                String triggeredBy, OffsetDateTime startedAt);

    Optional<Object> findByTypeAndDate(String taskType, LocalDate scheduledDate);

    /** 将已有记录重置为 RUNNING（force 重跑时使用）。*/
    void updateToRunning(Long id, OffsetDateTime startedAt, String triggeredBy);

    void updateFinished(Long id, String status, int total, int success, int failure,
                        String errorSummary, OffsetDateTime finishedAt);

    /** 查询状态和最新 N 条（外部 API 用）。*/
    List<Object> findAll(String taskType, String status, LocalDate startDate,
                         LocalDate endDate, int offset, int limit);

    int countAll(String taskType, String status, LocalDate startDate, LocalDate endDate);

    String getStatus(Object record);

    Long getId(Object record);
}
