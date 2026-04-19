package com.jugu.propertylease.device.app.repository;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.device.app.jooq.Tables;
import com.jugu.propertylease.device.app.jooq.tables.daos.DeviceScheduleTaskInfoDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.DeviceScheduleTaskInfo;
import com.jugu.propertylease.device.app.schedule.ScheduleResultE;
import com.jugu.propertylease.device.app.schedule.ScheduleStatusE;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.jooq.DSLContext;
import org.springframework.stereotype.Repository;

@SuppressWarnings("ClassCanBeRecord")
@Repository
@RequiredArgsConstructor
public class ScheduleTaskRepository {

  private final DeviceScheduleTaskInfoDao deviceScheduleTaskInfoDao;

  private final DSLContext dslContext;

  public void addNewTask(String id, String name) {
    DeviceScheduleTaskInfo taskInfo = new DeviceScheduleTaskInfo();
    taskInfo.setId(id)
        .setTaskname(name)
        .setTasksttime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()))
        .setTaskstatus(ScheduleStatusE.CREATED.name())
        .setTaskresult(ScheduleResultE.PENDING.name());
    deviceScheduleTaskInfoDao.insert(taskInfo);
  }

  public void processTask(String id, String detail) {
    dslContext.update(Tables.DEVICE_SCHEDULE_TASK_INFO)
        .set(Tables.DEVICE_SCHEDULE_TASK_INFO.TASKSTATUS, ScheduleStatusE.PROCESSING.name())
        .set(Tables.DEVICE_SCHEDULE_TASK_INFO.TASKDETAIL, detail)
        .where(Tables.DEVICE_SCHEDULE_TASK_INFO.ID.eq(id))
        .execute();
  }

  public List<DeviceScheduleTaskInfo> findPendingTasksByType(String taskName) {
    return dslContext.selectFrom(Tables.DEVICE_SCHEDULE_TASK_INFO)
        .where(Tables.DEVICE_SCHEDULE_TASK_INFO.TASKRESULT.eq(ScheduleResultE.PENDING.name()))
        .and(Tables.DEVICE_SCHEDULE_TASK_INFO.TASKNAME.eq(taskName))
        .fetchInto(DeviceScheduleTaskInfo.class);
  }

  public void update(DeviceScheduleTaskInfo taskInfo) {
    deviceScheduleTaskInfoDao.update(taskInfo);
  }
}
