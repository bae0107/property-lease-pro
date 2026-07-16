package com.jugu.propertylease.main.schedule.repo.jooq;

import static com.jugu.propertylease.main.jooq.Tables.SCHEDULE_TASK_LOG;
import static org.jooq.impl.DSL.trueCondition;

import com.jugu.propertylease.main.jooq.tables.pojos.ScheduleTaskLog;
import com.jugu.propertylease.main.schedule.repo.ScheduleTaskLogRepository;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class JooqScheduleTaskLogRepository implements ScheduleTaskLogRepository {

    private final DSLContext dsl;

    public JooqScheduleTaskLogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public Long insert(String taskType, LocalDate scheduledDate, String status,
                       String triggeredBy, OffsetDateTime startedAt) {
        return dsl.insertInto(SCHEDULE_TASK_LOG)
                .set(SCHEDULE_TASK_LOG.TASK_TYPE, taskType)
                .set(SCHEDULE_TASK_LOG.SCHEDULED_DATE, scheduledDate)
                .set(SCHEDULE_TASK_LOG.STATUS, status)
                .set(SCHEDULE_TASK_LOG.TRIGGERED_BY, triggeredBy)
                .set(SCHEDULE_TASK_LOG.STARTED_AT, startedAt)
                .set(SCHEDULE_TASK_LOG.TOTAL_COUNT, 0)
                .set(SCHEDULE_TASK_LOG.SUCCESS_COUNT, 0)
                .set(SCHEDULE_TASK_LOG.FAILURE_COUNT, 0)
                .set(SCHEDULE_TASK_LOG.CREATED_AT, OffsetDateTime.now())
                .returning(SCHEDULE_TASK_LOG.ID)
                .fetchOne(SCHEDULE_TASK_LOG.ID);
    }

    @Override
    public Optional<Object> findByTypeAndDate(String taskType, LocalDate scheduledDate) {
        return Optional.ofNullable(
                dsl.selectFrom(SCHEDULE_TASK_LOG)
                        .where(SCHEDULE_TASK_LOG.TASK_TYPE.eq(taskType))
                        .and(SCHEDULE_TASK_LOG.SCHEDULED_DATE.eq(scheduledDate))
                        .fetchOneInto(ScheduleTaskLog.class));
    }

    @Override
    public void updateToRunning(Long id, OffsetDateTime startedAt, String triggeredBy) {
        dsl.update(SCHEDULE_TASK_LOG)
                .set(SCHEDULE_TASK_LOG.STATUS, "RUNNING")
                .set(SCHEDULE_TASK_LOG.STARTED_AT, startedAt)
                .set(SCHEDULE_TASK_LOG.TRIGGERED_BY, triggeredBy)
                .set(SCHEDULE_TASK_LOG.FINISHED_AT, (OffsetDateTime) null)
                .set(SCHEDULE_TASK_LOG.TOTAL_COUNT, 0)
                .set(SCHEDULE_TASK_LOG.SUCCESS_COUNT, 0)
                .set(SCHEDULE_TASK_LOG.FAILURE_COUNT, 0)
                .set(SCHEDULE_TASK_LOG.ERROR_SUMMARY, (String) null)
                .where(SCHEDULE_TASK_LOG.ID.eq(id))
                .execute();
    }

    @Override
    public void updateFinished(Long id, String status, int total, int success,
                               int failure, String errorSummary, OffsetDateTime finishedAt) {
        dsl.update(SCHEDULE_TASK_LOG)
                .set(SCHEDULE_TASK_LOG.STATUS, status)
                .set(SCHEDULE_TASK_LOG.TOTAL_COUNT, total)
                .set(SCHEDULE_TASK_LOG.SUCCESS_COUNT, success)
                .set(SCHEDULE_TASK_LOG.FAILURE_COUNT, failure)
                .set(SCHEDULE_TASK_LOG.ERROR_SUMMARY, errorSummary)
                .set(SCHEDULE_TASK_LOG.FINISHED_AT, finishedAt)
                .where(SCHEDULE_TASK_LOG.ID.eq(id))
                .execute();
    }

    @Override
    public List<Object> findAll(String taskType, String status, LocalDate startDate,
                                LocalDate endDate, int offset, int limit) {
        Condition c = trueCondition();
        if (taskType  != null) c = c.and(SCHEDULE_TASK_LOG.TASK_TYPE.eq(taskType));
        if (status    != null) c = c.and(SCHEDULE_TASK_LOG.STATUS.eq(status));
        if (startDate != null) c = c.and(SCHEDULE_TASK_LOG.SCHEDULED_DATE.ge(startDate));
        if (endDate   != null) c = c.and(SCHEDULE_TASK_LOG.SCHEDULED_DATE.le(endDate));
        return dsl.selectFrom(SCHEDULE_TASK_LOG).where(c)
                .orderBy(SCHEDULE_TASK_LOG.SCHEDULED_DATE.desc())
                .limit(limit).offset(offset)
                .fetchInto(ScheduleTaskLog.class)
                .stream().map(x -> (Object) x).toList();
    }

    @Override
    public int countAll(String taskType, String status, LocalDate startDate, LocalDate endDate) {
        Condition c = trueCondition();
        if (taskType  != null) c = c.and(SCHEDULE_TASK_LOG.TASK_TYPE.eq(taskType));
        if (status    != null) c = c.and(SCHEDULE_TASK_LOG.STATUS.eq(status));
        if (startDate != null) c = c.and(SCHEDULE_TASK_LOG.SCHEDULED_DATE.ge(startDate));
        if (endDate   != null) c = c.and(SCHEDULE_TASK_LOG.SCHEDULED_DATE.le(endDate));
        return dsl.fetchCount(SCHEDULE_TASK_LOG, c);
    }

    @Override
    public String getStatus(Object record) {
        return ((ScheduleTaskLog) record).getStatus();
    }

    @Override
    public Long getId(Object record) {
        return ((ScheduleTaskLog) record).getId();
    }
}
