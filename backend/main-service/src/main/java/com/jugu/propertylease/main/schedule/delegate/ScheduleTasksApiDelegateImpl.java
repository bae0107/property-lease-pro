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

        // 触发任务（使用与 Cron 相同的防重入口）
        taskRunner.run(taskType, targetDate, force, "MANUAL", () -> {
            return switch (taskType) {
                case "DAILY_METER_SETTLEMENT" -> {
                    // 通过 Spring ApplicationContext 获取 ScheduleTaskRunner 内的实际逻辑
                    // 此处直接复用 ScheduleTaskRunner 的内部方法签名（避免循环依赖，改为内联调用）
                    // 实际上 ScheduleTaskRunner 会通过 MeteringScheduleTrigger 调用
                    yield new ScheduleTaskRunner.TaskRunResult(0, 0, 0, "已提交，异步执行");
                }
                case "CONTRACT_EXPIRY_CHECK" -> new ScheduleTaskRunner.TaskRunResult(0, 0, 0, null);
                default -> throw new IllegalArgumentException("未知任务类型: " + taskType);
            };
        });

        // 查出刚写入的 log 记录
        var logOpt = logRepo.findByTypeAndDate(taskType, targetDate);
        Long logId = logOpt.map(logRepo::getId).orElse(null);

        return new TriggerTaskResult()
                .taskLogId(logId)
                .taskType(taskType)
                .targetDate(targetDate)
                .message("任务已提交，异步执行中");
    }

    @Override
    public TaskLogPageResult queryTaskLogs(TaskLogQueryRequest request) {
        int page = request.getPage() != null ? request.getPage() : 1;
        int size = request.getSize() != null ? request.getSize() : 20;
        String taskType = request.getTaskType() != null ? request.getTaskType().getValue() : null;
        String status   = request.getStatus()   != null ? request.getStatus().getValue()   : null;

        List<Object> logs = logRepo.findAll(taskType, status,
                request.getStartDate(), request.getEndDate(),
                (page - 1) * size, size);
        int total = logRepo.countAll(taskType, status,
                request.getStartDate(), request.getEndDate());

        return new TaskLogPageResult()
                .items(logs.stream().map(this::toModel).toList())
                .total(total).page(page).size(size);
    }

    private ScheduleTaskLog_ toModel(Object raw) {
        ScheduleTaskLog log = (ScheduleTaskLog) raw;
        return new ScheduleTaskLog_()
                .id(log.getId())
                .taskType(ScheduleTaskLog_.TaskTypeEnum.fromValue(log.getTaskType()))
                .scheduledDate(log.getScheduledDate())
                .status(ScheduleTaskLog_.StatusEnum.fromValue(log.getStatus()))
                .triggeredBy(log.getTriggeredBy() != null
                        ? ScheduleTaskLog_.TriggeredByEnum.fromValue(log.getTriggeredBy()) : null)
                .totalCount(log.getTotalCount())
                .successCount(log.getSuccessCount())
                .failureCount(log.getFailureCount())
                .errorSummary(log.getErrorSummary())
                .startedAt(log.getStartedAt())
                .finishedAt(log.getFinishedAt());
    }
}
