package com.jugu.propertylease.billing.app.service;

import com.google.gson.JsonSyntaxException;
import com.jugu.propertylease.billing.app.jooq.tables.pojos.BillingRecordInfo;
import com.jugu.propertylease.billing.app.repository.BillingRecordInfoRepository;
import com.jugu.propertylease.billing.app.util.BillingIdTools;
import com.jugu.propertylease.billing.app.wxpay.WxBillingStatusTask;
import com.jugu.propertylease.billing.app.wxpay.WxPayDes;
import com.jugu.propertylease.billing.app.wxpay.WxPayProcessor;
import com.jugu.propertylease.billing.app.wxpay.WxPayServiceMgr;
import com.jugu.propertylease.billing.common.entity.BillingRecordDTO;
import com.jugu.propertylease.billing.common.entity.BillingRequest;
import com.jugu.propertylease.billing.common.entity.BillingStatus;
import com.jugu.propertylease.billing.common.entity.wx.WxPayRequest;
import com.jugu.propertylease.billing.common.entity.wx.WxPayResponse;
import com.jugu.propertylease.billing.common.entity.wx.WxPayType;
import com.jugu.propertylease.common.info.ErrorType;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.GsonFactory;
import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@SuppressWarnings("ClassCanBeRecord")
@Service
@RequiredArgsConstructor
@Log4j2
public class BillingService {

  public static final int HOST_INDEX = 1;
  public static final String WX_PAY = "wx";
  private static final ReentrantLock billingIdLock = new ReentrantLock();
  private static final int EXPIRE_TIME = 5;
  private final BillingRecordInfoRepository billingRecordInfoRepository;
  private final BillingStatusSyncTasks billingStatusSyncTasks;
  private final WxPayServiceMgr wxPayServiceMgr;

  public ReturnDataInfo<String> createBill(RequestDataInfo<BillingRequest> billingRequest) {
    if (billingRequest.isValid()) {
      BillingRequest data = billingRequest.getData();
      if (data != null) {
        String userId = data.getUserId();
        int cents = data.getAmountCent();
        String type = data.getBillingType();
        if (!Common.isStringInValid(userId) && !Common.isStringInValid(type) && cents > 0) {
          BillingRecordInfo recordInfo = new BillingRecordInfo();
          recordInfo.setAmountcent(cents)
              .setBillingstatus(BillingStatus.CREATED.name())
              .setBillingtype(type)
              .setCreateuserid(billingRequest.getUserId())
              .setCreateusername(billingRequest.getUserName())
              .setCreatetime(Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()));
          try {
            billingIdLock.lock();
            int count = billingRecordInfoRepository.countSize();
            String billingId = BillingIdTools.generateBillingId(userId, count);
            recordInfo.setBillingid(billingId);
            billingRecordInfoRepository.insert(recordInfo);
            return ReturnDataInfo.successData(billingId);
          } catch (DuplicateKeyException e) {
            log.error("primary key for billing id repeat:{}", e.getMessage());
            return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_BUSY);
          } catch (Exception e) {
            log.error("billing create failed due to db error:{}", e.getMessage());
            return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
          } finally {
            billingIdLock.unlock();
          }
        }
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  @Transactional
  public ReturnInfo cancelBill(String billingId) {
    if (!Common.isStringInValid(billingId)) {
      try {
        BillingRecordInfo recordInfo = billingRecordInfoRepository.findBillForUpdate(billingId);
        if (recordInfo != null) {
          String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
          String curStatus = recordInfo.getBillingstatus();
          String payMethod = recordInfo.getPaymethod();
          if (BillingStatus.CREATED.name().equals(curStatus) || BillingStatus.PENDING.name()
              .equals(curStatus)) {
            if (BillingStatus.PENDING.name().equals(curStatus)) {
              if (WX_PAY.equals(payMethod)) {
                if (wxPayServiceMgr.closeBill(billingId, recordInfo.getRootindex() == HOST_INDEX)) {
                  recordInfo.setBillingstatus(BillingStatus.CANCELED.name());
                } else {
                  return ReturnInfo.failByType(ErrorType.OUT_ERROR, "订单取消失败，请稍后重试");
                }
              } else {
                return ReturnInfo.failByType(ErrorType.SERIOUS_ERROR, "订单取消失败, 参数异常");
              }
            } else {
              recordInfo.setBillingstatus(BillingStatus.CANCELED.name());
            }
            recordInfo.setCompletetime(time);
            recordInfo.setUpdatetime(time);
            billingRecordInfoRepository.update(recordInfo);
            billingStatusSyncTasks.cancelTask(billingId);
            return ReturnInfo.success();
          }
        }
      } catch (Exception e) {
        log.error("cancel bill:{} failed due to db error", billingId);
        return ReturnInfo.failByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnInfo.failByType(ErrorType.INPUT_ERROR);
  }

  @Transactional
  public ReturnDataInfo<WxPayResponse> processWxPay(WxPayRequest wxPayRequest) {
    if (wxPayRequest.getPayType().getChecker().isValid(wxPayRequest)) {
      String billingId = wxPayRequest.getBillingId();
      String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
      try {
        BillingRecordInfo recordInfo = billingRecordInfoRepository.findBillForUpdate(billingId);
        if (recordInfo != null) {
          String curStatus = recordInfo.getBillingstatus();
          if (BillingStatus.CREATED.name().equals(curStatus) || BillingStatus.PENDING.name()
              .equals(curStatus)) {
            String billingType = recordInfo.getBillingtype();
            if (BillingStatus.CREATED.name().equals(curStatus)) {
              return processCreatedPayRequest(wxPayRequest, recordInfo, billingType, billingId,
                  time);
            } else {
              return processPendingPayRequest(recordInfo, billingType, billingId, time);
            }
          }
        }
      } catch (Exception e) {
        log.error("billing pay failed due to db error:{}", e.getMessage());
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  @Transactional
  public String processWxPayNotify(HttpServletRequest request) {
    PayNotifyTasks notifyTasks = billingId -> {
      BillingRecordInfo recordInfo = billingRecordInfoRepository.findBillForUpdate(billingId);
      if (recordInfo == null) {
        log.error("wx billing notify billing id not match in db, id: {}", billingId);
        return;
      }
      String curStatus = recordInfo.getBillingstatus();
      if (!BillingStatus.SUCCESS.name().equals(curStatus)) {
        if (BillingStatus.PENDING.name().equals(curStatus)) {
          recordInfo.setBillingstatus(BillingStatus.SUCCESS.name());
        } else {
          recordInfo.setBillingstatus(BillingStatus.FAILED.name());
          recordInfo.setErrorinfo(
              String.format("订单支付失败,当前:%s,通知:%s,请联系服务人员", curStatus, "SUCCESS"));
        }
        String time = Common.findTimeByMillSecondTimestamp(System.currentTimeMillis());
        recordInfo.setCompletetime(time);
        recordInfo.setUpdatetime(time);
        billingRecordInfoRepository.update(recordInfo);
        billingStatusSyncTasks.cancelTask(billingId);
      }

    };
    return wxPayServiceMgr.handWxNotify(request, notifyTasks);
  }

  public ReturnDataInfo<BillingStatus> checkInternalBillingStatus(String billingId) {
    if (!Common.isStringInValid(billingId)) {
      try {
        String status = billingRecordInfoRepository.findStatusById(billingId);
        if (Common.isStringInValid(status)) {
          return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
        }
        Optional<BillingStatus> statusOptional = BillingStatus.findStatusByName(status);
        return statusOptional.map(ReturnDataInfo::successData)
            .orElseGet(() -> ReturnDataInfo.failDataByType(ErrorType.DATA_ABNORMAL_ERROR,
                "数据库中订单状态无效：" + status));
      } catch (Exception e) {
        log.error("check billing status failed due to db error:{}", e.getMessage());
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  public ReturnDataInfo<BillingRecordDTO> findBillById(String billingId) {
    if (!Common.isStringInValid(billingId)) {
      try {
        BillingRecordInfo recordInfo = billingRecordInfoRepository.findBillById(billingId);
        if (recordInfo != null) {
          return ReturnDataInfo.successData(toDTO(recordInfo));
        }
      } catch (Exception e) {
        log.error("find bill by id failed due to db error:{}", e.getMessage());
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  public ReturnDataInfo<List<BillingRecordDTO>> findBillByIds(List<String> ids) {
    if (!Common.isCollectionInValid(ids)) {
      try {
        List<BillingRecordInfo> recordInfos = billingRecordInfoRepository.finBillsByIds(ids);
        if (recordInfos != null && !recordInfos.isEmpty()) {
          return ReturnDataInfo.successData(
              recordInfos.stream().map(this::toDTO).collect(Collectors.toList()));
        }
      } catch (Exception e) {
        log.error("find bills by ids failed due to db error:{}", e.getMessage());
        return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
      }
    }
    return ReturnDataInfo.failDataByType(ErrorType.INPUT_ERROR);
  }

  public ReturnDataInfo<List<BillingRecordDTO>> findBillByStatus(BillingStatus billingStatus) {
    try {
      List<BillingRecordInfo> recordInfos = billingRecordInfoRepository.findBillingsByStatus(
          billingStatus.name());
      if (recordInfos != null && !recordInfos.isEmpty()) {
        return ReturnDataInfo.successData(
            recordInfos.stream().map(this::toDTO).collect(Collectors.toList()));
      }
      return ReturnDataInfo.successData(new ArrayList<>());
    } catch (Exception e) {
      log.error("find bills by status failed due to db error:{}", e.getMessage());
      return ReturnDataInfo.failDataByType(ErrorType.DB_ERROR);
    }
  }

  private WxPayDes genCommonPxPayDes(String type, String billingId, int amount, String expireTime) {
    WxPayDes payDes = new WxPayDes();
    payDes.setDes(type);
    payDes.setOrderId(billingId);
    payDes.setAmountCent(amount);
    payDes.setExpireTime(expireTime);
    return payDes;
  }

  private ReturnDataInfo<WxPayResponse> processPendingPayRequest(BillingRecordInfo recordInfo,
      String billingType, String billingId, String time) {
    String expireTime = recordInfo.getExpiretime();
    if (!Common.isStringInValid(expireTime)) {
      boolean isHost = recordInfo.getRootindex() == HOST_INDEX;
      if (expireTime.compareTo(WxPayProcessor.findWxPayValidTime(0)) > 0) {
        String payInfo = recordInfo.getPayinfo();
        if (!Common.isStringInValid(payInfo)) {
          try {
            WxPayRequest wxPayRequestP = GsonFactory.fromJson(payInfo, WxPayRequest.class);
            WxPayDes payDes = genCommonPxPayDes(billingType, billingId, recordInfo.getAmountcent(),
                expireTime);
            return wxPayServiceMgr.payWxByPayType(wxPayRequestP.getPayType(), payDes, wxPayRequestP,
                isHost);
          } catch (JsonSyntaxException e) {
            log.error("wx pay id: {} info param illegal:{}", billingId, payInfo);
            return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR, "支付信息非法");
          }
        }
        log.error("wx pay id: {} info param miss in db:{}", billingId, payInfo);
        return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR, "缺少支付信息");
      } else {
        String errorMsg;
        if (wxPayServiceMgr.closeBill(billingId, isHost)) {
          recordInfo.setBillingstatus(BillingStatus.CANCELED.name());
          errorMsg = WxBillingStatusTask.NORMAL_CANCEL;
        } else {
          recordInfo.setBillingstatus(BillingStatus.FAILED.name());
          errorMsg = WxBillingStatusTask.ABNORMAL_CANCEL;
        }
        recordInfo.setErrorinfo(errorMsg);
        recordInfo.setCompletetime(time);
        recordInfo.setUpdatetime(time);
        billingRecordInfoRepository.update(recordInfo);
        billingStatusSyncTasks.cancelTask(billingId);
        return ReturnDataInfo.failDataByType(ErrorType.TIME_OUT, errorMsg);
      }
    }
    log.error("wx pay id: {} expire time miss in db", billingId);
    return ReturnDataInfo.failDataByType(ErrorType.SYSTEM_ERROR, "缺少支付超时信息");
  }

  private ReturnDataInfo<WxPayResponse> processCreatedPayRequest(WxPayRequest wxPayRequest,
      BillingRecordInfo recordInfo, String billingType, String billingId, String time) {
    WxPayType payType = wxPayRequest.getPayType();
    int rootIndex = wxPayRequest.getRootIndex();
    boolean isHost = rootIndex == HOST_INDEX;
    String expireTime = WxPayProcessor.findWxPayValidTime(EXPIRE_TIME);
    WxPayDes payDes = genCommonPxPayDes(billingType, billingId, recordInfo.getAmountcent(),
        expireTime);
    ReturnDataInfo<WxPayResponse> response = wxPayServiceMgr.payWxByPayType(payType, payDes,
        wxPayRequest, isHost);
    if (response.isSuccess()) {
      recordInfo.setPaymethod(WX_PAY);
      recordInfo.setRootindex(rootIndex);
      recordInfo.setBillingstatus(BillingStatus.PENDING.name());
      recordInfo.setUpdatetime(time);
      recordInfo.setExpiretime(expireTime);
      recordInfo.setPayinfo(GsonFactory.toJson(wxPayRequest));
      billingRecordInfoRepository.update(recordInfo);
      billingStatusSyncTasks.addTask(billingId,
          new WxBillingStatusTask(billingId, wxPayServiceMgr, isHost, billingRecordInfoRepository));
    }
    return response;
  }

  private BillingRecordDTO toDTO(BillingRecordInfo recordInfo) {
    BillingRecordDTO recordDTO = new BillingRecordDTO();
    recordDTO.setBillingId(recordInfo.getBillingid())
        .setAmountCent(recordInfo.getAmountcent())
        .setBillingStatus(recordInfo.getBillingstatus())
        .setPayMethod(recordInfo.getPaymethod())
        .setBillingType(recordInfo.getBillingtype())
        .setCreateUserId(recordInfo.getCreateuserid())
        .setCreateUserName(recordInfo.getCreateusername())
        .setCreateTime(recordInfo.getCreatetime())
        .setUpdateTime(recordInfo.getUpdatetime())
        .setCompleteTime(recordInfo.getCompletetime())
        .setErrorInfo(recordInfo.getErrorinfo())
        .setRootIndex(recordInfo.getRootindex())
        .setExpireTime(recordInfo.getExpiretime())
        .setPayInfo(recordInfo.getPayinfo());
    return recordDTO;
  }
}
