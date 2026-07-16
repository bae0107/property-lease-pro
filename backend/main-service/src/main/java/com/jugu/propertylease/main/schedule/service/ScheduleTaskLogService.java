package com.jugu.propertylease.main.schedule.service;

import com.jugu.propertylease.main.schedule.repo.ScheduleTaskLogRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 定时任务日志服务（防重核心）。
 *
 * <p>使用 {@code REQUIRES_NEW} 独立事务，确保日志记录操作不受外层业务事务影响：
 * <ul>
 *   <li>业务执行失败时，日志仍能正确写入 FAILED</li>
 *   <li>日志写入失败不会回滚业务操作</li>
 * </ul>
 */
@Service
public class ScheduleTaskLogService {

    private final ScheduleTaskLogRepository repo;

    public ScheduleTaskLogService(ScheduleTaskLogRepository repo) {
        this.repo = repo;
    }

    /**
     * 尝试开启任务执行（防重检查 + 创建 RUNNING 记录）。
     *
     * <p>决策逻辑：
     * <ol>
     *   <li>无记录 → INSERT RUNNING，返回 logId</li>
     *   <li>已有 RUNNING → 返回 null（跳过，防并发重入）</li>
     *   <li>已有 SUCCESS 且 force=false → 返回 null（跳过）</li>
     *   <li>已有 SUCCESS/FAILED/PARTIAL_FAILURE 且 force=true → UPDATE 为 RUNNING，返回 logId</li>
     * </ol>
     *
     * @return logId（需继续执行），或 null（应跳过）
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Long tryStart(String taskType, LocalDate date, boolean force, String triggeredBy) {
        return repo.findByTypeAndDate(taskType, date)
                .map(existing -> {
                    String status = repo.getStatus(existing);
                    Long   id     = repo.getId(existing);

                    if ("RUNNING".equals(status)) {
                        return null;                          // 正在运行，跳过
                    }
                    if ("SUCCESS".equals(status) && !force) {
                        return null;                          // 已成功且未强制，跳过
                    }
                    // FAILED / PARTIAL_FAILURE / SUCCESS(force) → 重置
                    repo.updateToRunning(id, OffsetDateTime.now(), triggeredBy);
                    return id;
                })
                .orElseGet(() -> {
                    try {
                        return repo.insert(taskType, date, "RUNNING",
                                triggeredBy, OffsetDateTime.now());
                    } catch (DuplicateKeyException e) {
                        // 并发竞争：另一节点已插入，本节点跳过
                        return null;
                    }
                });
    }

    /**
     * 完成任务：更新日志状态（SUCCESS / PARTIAL_FAILURE / FAILED）。
     * 使用独立事务，即使调用方出现异常也能写入日志。
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void finish(Long logId, String status, int total, int success,
                       int failure, String errorSummary) {
        repo.updateFinished(logId, status, total, success, failure,
                errorSummary, OffsetDateTime.now());
    }
}
