package com.jugu.propertylease.main.schedule.delegate;

import com.jugu.propertylease.main.api.ScheduleTasksApiDelegate;
import com.jugu.propertylease.main.api.model.*;
import com.jugu.propertylease.main.jooq.tables.pojos.ScheduleTaskLog;
import com.jugu.propertylease.main.schedule.repo.ScheduleTaskLogRepository;
import com.jugu.propertylease.main.schedule.service.ScheduleTaskRunner;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * schedule 模块外部 API Delegate。
 *
 * <p>手动触发复用 {@link ScheduleTaskRunner#run} 以确保防重逻辑一致，
 * 与 Cron 触发走同一套幂等控制。
 */
@Service
public class ScheduleTasksApiDelegateImpl implements ScheduleTasksApiDelegate {

    private final ScheduleTaskRunner taskRunner;
    private final ScheduleTaskLogRepository logRepo;

    public ScheduleTasksApiDelegateImpl(ScheduleTaskRunner taskRunner,
                                         ScheduleTaskLogRepository logRepo) {
        this.taskRunner = taskRunner;
        this.logRepo    = logRepo;
    }

    @Override
    public TriggerTaskResult triggerTask(String taskType, TriggerTaskRequest request) {
        LocalDate targetDate = request.getTargetDate() != null
                ? request.getTargetDate()
                : LocalDate.now().minusDays(1);
        boolean force = Boolean.TRUE.equals(request.getForce());

        // 触发任务（与 Cron 共用同一套业务逻辑与防重入口，同步执行）
        switch (taskType) {
            case "DAILY_METER_SETTLEMENT" ->
                    taskRunner.runDailyMeterSettlement(targetDate, force, "MANUAL");
            case "CONTRACT_EXPIRY_CHECK" ->
                    taskRunner.runContractExpiryCheck(targetDate, force, "MANUAL");
            default -> throw new IllegalArgumentException("未知任务类型: " + taskType);
        }

        // 查出刚写入的 log 记录
        var logOpt = logRepo.findByTypeAndDate(taskType, targetDate);
        Long logId = logOpt.map(logRepo::getId).orElse(null);

        return new TriggerTaskResult()
                .taskLogId(logId)
                .taskType(taskType)
                .targetDate(targetDate)
                .message("任务已同步执行完成");
    }

    @Override
    public TaskLogPageResult queryTaskLogs(TaskLogQueryRequest request) {
        int page = request.getPageNo() != null ? request.getPageNo() : 1;
        int size = request.getPageSize() != null ? request.getPageSize() : 20;
        String taskType = request.getTaskType() != null ? request.getTaskType().getValue() : null;
        String status   = request.getStatus()   != null ? request.getStatus().getValue()   : null;

        List<Object> logs = logRepo.findAll(taskType, status,
                request.getStartDate(), request.getEndDate(),
                (page - 1) * size, size);
        int total = logRepo.countAll(taskType, status,
                request.getStartDate(), request.getEndDate());

        return new TaskLogPageResult()
                .items(logs.stream().map(this::toModel).toList())
                .total((long) total).pageNo(page).pageSize(size);
    }

    private com.jugu.propertylease.main.api.model.ScheduleTaskLog toModel(Object raw) {
        ScheduleTaskLog log = (ScheduleTaskLog) raw;
        return new com.jugu.propertylease.main.api.model.ScheduleTaskLog()
                .id(log.getId())
                .taskType(log.getTaskType())
                .scheduledDate(log.getScheduledDate())
                .status(com.jugu.propertylease.main.api.model.ScheduleTaskLog.StatusEnum
                        .fromValue(log.getStatus()))
                .triggeredBy(log.getTriggeredBy() != null
                        ? com.jugu.propertylease.main.api.model.ScheduleTaskLog.TriggeredByEnum
                                .fromValue(log.getTriggeredBy()) : null)
                .totalCount(log.getTotalCount())
                .successCount(log.getSuccessCount())
                .failureCount(log.getFailureCount())
                .errorSummary(log.getErrorSummary())
                .startedAt(log.getStartedAt())
                .finishedAt(log.getFinishedAt());
    }
}
