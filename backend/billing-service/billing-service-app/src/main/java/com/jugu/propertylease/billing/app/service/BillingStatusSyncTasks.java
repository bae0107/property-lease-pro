package com.jugu.propertylease.billing.app.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.github.benmanes.caffeine.cache.Scheduler;
import com.jugu.propertylease.billing.app.jooq.tables.pojos.BillingRecordInfo;
import com.jugu.propertylease.billing.app.repository.BillingRecordInfoRepository;
import com.jugu.propertylease.billing.app.wxpay.WxBillingStatusTask;
import com.jugu.propertylease.billing.app.wxpay.WxPayServiceMgr;
import com.jugu.propertylease.billing.common.entity.BillingStatus;
import com.jugu.propertylease.billing.common.entity.wx.WxPayType;
import com.jugu.propertylease.common.utils.Common;
import jakarta.annotation.PostConstruct;
import java.util.List;
import java.util.concurrent.TimeUnit;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

@Component
@Log4j2
public class BillingStatusSyncTasks {

  private final BillingRecordInfoRepository billingRecordInfoRepository;

  private final WxPayServiceMgr wxPayServiceMgr;

  private Cache<String, BillingStatusTask> taskCache;

  public BillingStatusSyncTasks(BillingRecordInfoRepository billingRecordInfoRepository,
      WxPayServiceMgr wxPayServiceMgr) {
    this.billingRecordInfoRepository = billingRecordInfoRepository;
    this.wxPayServiceMgr = wxPayServiceMgr;

  }

  @PostConstruct
  public void init() {
    this.taskCache = Caffeine.newBuilder()
        .maximumSize(10000)
        .expireAfterWrite(600, TimeUnit.SECONDS)
        .scheduler(Scheduler.systemScheduler())
        .removalListener((String key, BillingStatusTask task, RemovalCause cause) -> {
          if (cause == RemovalCause.EXPLICIT) {
            log.info("remove bill sync task id:{}", key);
            return;
          }
          if (task != null) {
            if (!task.run() && task.shouldRetry()) {
              log.info("retry billing status task:{}", key);
              task.addRetry();
              addTask(key, task);
            }
          }
        }).build();

    addRemainingTasks();
  }

  public void addTask(String billingId, BillingStatusTask task) {
    log.info("add bill sync task id:{}", billingId);
    taskCache.put(billingId, task);
  }

  public void cancelTask(String billingId) {
    log.info("cancel bill sync task id:{}", billingId);
    taskCache.invalidate(billingId);
  }

  private void addRemainingTasks() {
    try {
      List<BillingRecordInfo> infos = billingRecordInfoRepository.findBillingsByStatus(
          BillingStatus.PENDING.name());
      if (!Common.isCollectionInValid(infos)) {
        log.info("remaining bill sync tasks size:{}", infos.size());
        infos.forEach(info -> {
          if (BillingService.WX_PAY.equals(info.getPaymethod())) {
            String billingId = info.getBillingid();
            int host = info.getRootindex();
            if (!Common.isStringInValid(billingId) && WxPayType.isRootLegal(host)) {
              WxBillingStatusTask statusTask = new WxBillingStatusTask(billingId, wxPayServiceMgr,
                  host == BillingService.HOST_INDEX, billingRecordInfoRepository);
              addTask(billingId, statusTask);
            }
          }
        });
      }
    } catch (Exception e) {
      log.error("fail to recover billing status cache due to db error");
    }
  }
}
