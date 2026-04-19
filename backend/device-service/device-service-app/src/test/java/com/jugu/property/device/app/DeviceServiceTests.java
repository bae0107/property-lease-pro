package com.jugu.property.device.app;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.jugu.propertylease.common.info.RequestDataInfo;
import com.jugu.propertylease.common.info.ReturnDataInfo;
import com.jugu.propertylease.common.info.ReturnInfo;
import com.jugu.propertylease.common.tools.Sender;
import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.common.utils.Encodes;
import com.jugu.propertylease.common.utils.GsonFactory;
import com.jugu.propertylease.device.app.DeviceServiceAppApplication;
import com.jugu.propertylease.device.app.callback.CallBackTaskMgrE;
import com.jugu.propertylease.device.app.callback.CallbackTaskAllocator;
import com.jugu.propertylease.device.app.callback.CallbackUtil;
import com.jugu.propertylease.device.app.entity.response.electricity.EMeterSwitchResponse;
import com.jugu.propertylease.device.app.entity.response.water.WMeterCheckResponse;
import com.jugu.propertylease.device.app.jooq.tables.daos.ElectronicMeterInfoDao;
import com.jugu.propertylease.device.app.jooq.tables.pojos.ElectronicMeterInfo;
import com.jugu.propertylease.device.app.locker.LockRequestOp;
import com.jugu.propertylease.device.app.locker.LockService;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicMeterService;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicRequestOp;
import com.jugu.propertylease.device.app.meter.electricity.ElectronicSwitchProcessor;
import com.jugu.propertylease.device.app.meter.water.WaterMeterService;
import com.jugu.propertylease.device.app.meter.water.WaterRequestOp;
import com.jugu.propertylease.device.app.repository.ElectronicMeterRepository;
import com.jugu.propertylease.device.app.repository.ThirdPartyServiceRecordMgr;
import com.jugu.propertylease.device.app.repository.WaterMeterRepository;
import com.jugu.propertylease.device.app.schedule.water.WMeterScheduleTaskMgr;
import com.jugu.propertylease.device.app.thirdparty.hans.HansAccount;
import com.jugu.propertylease.device.app.thirdparty.heyi.HeYiAccessTokenSglt;
import com.jugu.propertylease.device.app.thirdparty.heyi.HeYiUrlE;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.HeYiAccount;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.HeYiAccountDTO;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiMeterCheckResponse;
import com.jugu.propertylease.device.app.thirdparty.heyi.entity.response.HeYiResponse;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingAccessTokenSglt;
import com.jugu.propertylease.device.app.thirdparty.yunding.YunDingUrlE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.YunDingAccount;
import com.jugu.propertylease.device.common.entity.dto.EMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.LockInfoDTO;
import com.jugu.propertylease.device.common.entity.dto.MeterSettlementDTO;
import com.jugu.propertylease.device.common.entity.dto.ServiceRecordDTO;
import com.jugu.propertylease.device.common.entity.dto.WMeterInfoDTO;
import com.jugu.propertylease.device.common.entity.request.AddLockPwdRequest;
import com.jugu.propertylease.device.common.entity.request.LockPwdOpRequest;
import com.jugu.propertylease.device.common.entity.request.MeterBatchNotifyRequest;
import com.jugu.propertylease.device.common.entity.request.MeterRequest;
import com.jugu.propertylease.device.common.entity.response.DeviceResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterCheckResponse;
import com.jugu.propertylease.device.common.entity.response.electricity.EMeterPeriodResponse;
import com.jugu.propertylease.device.common.entity.response.lock.AddLockPwdResponse;
import com.jugu.propertylease.device.common.entity.response.lock.LockPwdsSummary;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.testcontainers.shaded.com.google.common.reflect.TypeToken;

@SpringBootTest(classes = DeviceServiceAppApplication.class)
public class DeviceServiceTests {

  @Autowired
  private CallbackTaskAllocator callbackTaskAllocator;

  @Autowired
  private YunDingAccount yunDingAccount;

  @Autowired
  private HeYiAccount heYiAccount;

  @Autowired
  private HansAccount hansAccount;

  @Autowired
  private ElectronicMeterService electronicMeterService;

  @Autowired
  private ElectronicMeterInfoDao electronicMeterInfoDao;

  @Autowired
  private ElectronicMeterRepository electronicMeterRepository;

  @Autowired
  private WaterMeterRepository waterMeterRepository;

  @Autowired
  private WaterMeterService waterMeterService;

  @Autowired
  private ThirdPartyServiceRecordMgr thirdPartyServiceRecordMgr;

  @Autowired
  private WMeterScheduleTaskMgr wMeterScheduleTaskMgr;

  @Autowired
  private LockService lockService;
  @Autowired
  private ElectronicSwitchProcessor switchProcessor;

  @Test
  void yunDingAccountLoadTest() {
    System.out.println(new Gson().toJson(yunDingAccount.toDTO()));
  }

  @Test
  void yunDingAccountUrlTest() {
    System.out.println(yunDingAccount.getDomain() + YunDingUrlE.ACCESS_TOKEN.getUrl());
  }

  @Test
  void yunDingTokenTest() {
    Optional<String> token = YunDingAccessTokenSglt.getToken(yunDingAccount);
    System.out.println(token.get());
  }

  @Test
  void yunDingReadEMeterTest() {
    EMeterCheckResponse response = ElectronicRequestOp.YUN_DING.readMeterRequest(
        "19f20773cb9208c8be377a42f0f0d23a");
    System.out.println(response);
    System.out.println(response.getErrorInfo());
    System.out.println(response.isSuccess());
    System.out.println(response.getDeviceId());
  }

  @Test
  void yunDingEMeterEnableStateTest() {
//        EMeterCheckResponse response = ElectronicRequestOp.YUN_DING.adjustSwitch("19f20773cb9208c8be377a42f0f0d23a");
//        System.out.println(response);
//        System.out.println(response.getErrorInfo());
//        System.out.println(response.isSuccess());
//        System.out.println(response.getDeviceId());
  }

  @Test
  void yunDingReadEMeterServiceTest() {
    ReturnDataInfo<EMeterCheckResponse> responseReturnDataInfo = electronicMeterService.readAndSyncElectronicMeter(
        new RequestDataInfo<>("a", "b", 1L));
    EMeterCheckResponse response = responseReturnDataInfo.getResponseData();
    System.out.println(response);
    if (Objects.nonNull(response)) {
      System.out.println(response.getErrorInfo());
      System.out.println(response.isSuccess());
      System.out.println(response.getDeviceId());
    }
  }

  @Test
  void yunDingFindUsagePeriodTest() {
    EMeterPeriodResponse response = ElectronicRequestOp.YUN_DING.findUsagePeriod(
        "19f20773cb9208c8be377a42f0f0d23a", 1763959362L, 1764045762L);
    System.out.println(response);
    System.out.println(response.getErrorInfo());
    System.out.println(response.isSuccess());
    System.out.println(response.getDeviceId());
  }

  @Test
  void yunDingCloseMeterTest() {
    EMeterSwitchResponse response = ElectronicRequestOp.YUN_DING.switchMeterRequest(
        "19f20773cb9208c8be377a42f0f0d23a", EMeterSwitchResponse.SwitchTypeE.OPEN);
    System.out.println(response);
    System.out.println(response.getErrorInfo());
    System.out.println(response.isSuccess());
    System.out.println(response.getDeviceId());
  }

  @Test
  void addNewEMeterTest() {
    EMeterInfoDTO infoDTO = new EMeterInfoDTO();
    infoDTO.setInstallType(2);
    infoDTO.setMeterNo("200927100457");
    infoDTO.setBoundRoomId("111");
    infoDTO.setDeviceModelId("MODEL ID");
    infoDTO.setDeviceId("19f20773cb9208c8be377a42f0f0d23a");
    infoDTO.setProviderOp(1);
    RequestDataInfo<EMeterInfoDTO> eleMeterRequest = new RequestDataInfo<>("shibowenid",
        "shibowenname", infoDTO);
    ReturnInfo returnInfo = electronicMeterService.addNewElectronicMeter(eleMeterRequest);
    System.out.println(returnInfo.getErrorInfo());
    System.out.println(returnInfo.isSuccess());
  }

  @Test
  void EMeterDaoTest() {
    ElectronicMeterInfo meterInfo = electronicMeterInfoDao.findById(2L);
    System.out.println(meterInfo);
  }

  @Test
  void callbackSignTest() {
    Map<String, String> params = new HashMap<>();
    params.put("event", "lockerOpenAlarm");
    params.put("uuid", "20b20d7e56e92ff9d4126deb457c458e");
    params.put("home_id", "12372");
    params.put("room_id", "40167");
    Detail detail = new Detail();
    detail.setSource_name("H78165");
    detail.setEventid(1);
    detail.setSource(2);
    detail.setSourceid(1002);
    detail.setNotify(1);
    detail.setAudio_played(0);
    detail.setMonkey(null);
//        System.out.println(detail);
//        System.out.println("1111");
    Gson gson = new GsonBuilder().serializeNulls().create();
    params.put("detail", gson.toJson(detail));
    params.put("time", "1536565133661");
    params.put("monkey1", null);
    try {
      System.out.println(CallbackUtil.generateSign(params, "http://111.com/callback"));
//            System.out.println(CallbackUtil.verifySign("params", "http://111.com/callback", "9ad595056bdee684f24144cfe5f4ce26"));
    } catch (Exception e) {
      System.out.println("error");
    }

  }

  @Test
  void adjustSwitchTest() {
    MeterRequest meterRequest = new MeterRequest();
    meterRequest.setId(4L);
//        meterRequest.setProviderOp(ProviderOpE.YUN_DING.getIndex());
    meterRequest.setServiceType(EMeterSwitchResponse.SwitchTypeE.OPEN.getSwitchType());
    RequestDataInfo<MeterRequest> eleMeterRequest = new RequestDataInfo<>("shibowenid",
        "shibowenname", meterRequest);
    ReturnDataInfo<Integer> result = electronicMeterService.adjustESwitch(eleMeterRequest);
    System.out.println(result.isSuccess());
    System.out.println(result.getErrorInfo());
    System.out.println(result.getResponseData());
  }

  @Test
  void yunDingSwitchECallbackTest() {
    String paramsStr = "{\n" +
        "\"service\":\"Elemeter_Control_Service\",\n" +
        "\"serviceid\":\"1345179116\",\n" +
        "\"uuid\":\"19f20773cb9208c8be377a42f0f0d23a\",\n" +
        "\"result\":{\n" +
        "\"id\":\"19f20773cb9208c8be377a42f0f0d23a\",\n" +
        "\"ErrNo\":1000,\n" +
        "\"operation_type\":7\n" +
        "},\n" +
        "\"sign\":\"1536565133661\"\n" +
        "}";
    Map<String, String> paramsMap = CallbackUtil.convertToParamMap(paramsStr);
    System.out.println(paramsMap);
    CallBackTaskMgrE.YUN_DING.process(paramsMap, callbackTaskAllocator);
  }

  @Test
  void creatMockEMeterData() {
    ElectronicMeterInfo meterInfo = electronicMeterInfoDao.findById(4L);
    System.out.println(meterInfo);
    List<ElectronicMeterInfo> infos = new ArrayList<>();
    for (int i = 0; i < 20; i++) {
      ElectronicMeterInfo meterInfo1 = new ElectronicMeterInfo();
      BeanUtils.copyProperties(meterInfo, meterInfo1);
      meterInfo1.setDeviceid(i + "mock");
      meterInfo1.setId(null);
      infos.add(meterInfo1);
    }
    electronicMeterInfoDao.insert(infos);
  }

  @Test
  void settleEMeterBatchTest() {
    Set<Long> ids = new HashSet<>();
    ids.add(7L);
    ids.add(8L);
    ids.add(9L);
    ids.add(10L);
    ids.add(11L);
    ids.add(12L);
    ids.add(13L);
    ids.add(14L);
    ids.add(15L);
    ids.add(16L);
    ids.add(17L);
    ids.add(18L);
    ids.add(19L);
    ids.add(20L);
    ids.add(21L);
    ids.add(22L);
    ids.add(23L);
    ids.add(24L);
    ids.add(25L);
    ids.add(26L);
    // 无效id
    ids.add(1000L);
    RequestDataInfo<Set<Long>> settleBatchRequest = new RequestDataInfo<>("testid", "testName",
        ids);
    electronicMeterService.settleElectronicMeterBatch(settleBatchRequest);
  }

  @Test
  void settleNotifyEMeterBatchTest() {
    Set<Long> ids = new HashSet<>();
    ids.add(15L);
    ids.add(16L);
    ids.add(17L);
    List<ElectronicMeterInfo> infos = electronicMeterRepository.findEMeterInfoByIds(ids);
    Set<MeterSettlementDTO> dtos = new HashSet<>();
    for (ElectronicMeterInfo info : infos) {
      MeterSettlementDTO settlementDTO = new MeterSettlementDTO();
      settlementDTO.setConsumeAmount(info.getConsumeamount())
          .setConsumeRecordTime(info.getConsumerecordtime()).setId(info.getId())
          .setPeriodConsumeStartTime(info.getPeriodconsumestarttime())
          .setPeriodConsumeAmount(info.getPeriodconsumeamount());
      dtos.add(settlementDTO);
    }
    MeterSettlementDTO settlementDTOError = new MeterSettlementDTO();
    settlementDTOError.setId(9999L);
    dtos.add(settlementDTOError);
    MeterBatchNotifyRequest requestDataInfo = new MeterBatchNotifyRequest("notifyid", "notifyname",
        dtos);
    ReturnDataInfo<Map<Boolean, Map<Long, ReturnInfo>>> res = electronicMeterService.settleSuccessNotifyBatch(
        requestDataInfo);
    System.out.println(res);
  }

  @Test
  void yunDingWaterMeterReadTest() {
    WMeterCheckResponse checkResponse = WaterRequestOp.YUN_DING.findWMeterInfoRequest(
        "d5452fe9dbd57a23155671837d188e05");
    System.out.println(checkResponse.getErrorInfo());
    System.out.println(checkResponse);
  }

  @Test
  void yunDingWaterMeterRecordTest() {
    DeviceResponse deviceResponse = WaterRequestOp.YUN_DING.sendWaterRecordRequest(
        "d5452fe9dbd57a23155671837d188e05");
    System.out.println(System.currentTimeMillis());
    System.out.println(deviceResponse);
  }

  @Test
  void timeConvertTest() {
    String t = java.time.Instant.parse("2026-01-17T04:50:10.545Z")
        .atZone(java.time.ZoneId.of("UTC"))
        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    System.out.println(t);
  }

  @Test
  void insertWMeterMockDataTest() {
    for (int i = 0; i < 50; i++) {
      WMeterInfoDTO dto = new WMeterInfoDTO();
      dto.setInstallType(1);
      dto.setConsumeRecordTime("2020-01-27 18:33:12");
      dto.setProviderOp(1);
      dto.setDeviceId(i + "a");
      waterMeterRepository.addNewWaterMeter(dto);
    }

    for (int i = 0; i < 50; i++) {
      WMeterInfoDTO dto = new WMeterInfoDTO();
      dto.setInstallType(1);
      dto.setConsumeRecordTime("2020-01-27 18:33:12");
      dto.setProviderOp(2);
      dto.setDeviceId(i + "b");
      waterMeterRepository.addNewWaterMeter(dto);
    }
  }

  @Test
  void wMeterDailyRecordTest() throws Exception {
    waterMeterService.processWMetersDailyRecordTask("test");
  }

  @Test
  void thirdTempWithIdTest() {
    long id = thirdPartyServiceRecordMgr.recordNewServiceWithKey("test", "t",
        ThirdPartyServiceRecordMgr.DeviceType.WATER, -1, "", 0, "tesest");
    System.out.println(id);
  }

  @Test
  void waterRecordDailyScheduleTest() {
    wMeterScheduleTaskMgr.recordWMetersDailyTask();
  }

  @Test
  void waterRecordDailySyncTest() {
    wMeterScheduleTaskMgr.wMeterDailyRecordResultsSync();
  }

  @Test
  void findLockInfoTest() {
    LockRequestOp.YUN_DING.findLockInfo("9b625804dbf687322169578954e8f01b");
  }

  @Test
  void addLockTest() {
    LockInfoDTO lockInfoDTO = new LockInfoDTO();
    lockInfoDTO.setDeviceId("9b625804dbf687322169578954e8f01b");
    lockInfoDTO.setBoundRoomId("2501");
    lockInfoDTO.setProviderOp(1);
    RequestDataInfo<LockInfoDTO> requestDataInfo = new RequestDataInfo<>("111", "sjho",
        lockInfoDTO);
    System.out.println(lockService.addNewLock(requestDataInfo));
  }

  @Test
  void addLockPwdTest() {
    AddLockPwdRequest request = new AddLockPwdRequest();
    request.setDeviceId("9b625804dbf687322169578954e8f01b");
    request.setBegin(1770701511L);
    request.setEnd(1775799129L);
    request.setProviderOp(1);
    request.setPhone("13774209239");
    request.setPassword("021011");
    request.setName("测试密码");
    AddLockPwdResponse response = lockService.addLockPwd(request).getResponseData();
    System.out.println(response);
    System.out.println(response.getServiceId());
//        System.out.println(response.getErrorInfo());
    System.out.println(response.getServiceKey());
  }

  @Test
  void LockPwdInfoTest() {
    LockPwdsSummary summary = LockRequestOp.YUN_DING.checkLockPwdInfos(
        "9b625804dbf687322169578954e8f01b");
    System.out.println(summary);
    System.out.println(summary.getErrorInfo());
  }

  @Test
  void frozenPwdTest() {
    LockPwdOpRequest requestOp = new LockPwdOpRequest();
    requestOp.setId(2);
    requestOp.setPwdId("1014");

    ReturnDataInfo<ServiceRecordDTO> res = lockService.delLockPwd(requestOp);
    System.out.println(res.getResponseData());
    System.out.println(res.getErrorInfo());
  }

  @Test
  void heYiAccountTest() {
    HeYiAccountDTO heYiAccountDTO = heYiAccount.toDTO();
    System.out.println(heYiAccountDTO);
  }

  @Test
  void heYiTokenTest() {
    System.out.println(HeYiAccessTokenSglt.getToken(heYiAccount));
//        System.out.println(heYiAccount.getTokenViaHttpURLConnection());
  }

  @Test
  void heYiProductsInfosTest() {
    Sender<Object> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("Authorization",
            "Bearer " + "4d951b35-8c90-42bf-af29-e80644aaa42b");
      }
    });
    String url = heYiAccount.getDomain() + HeYiUrlE.CHECK_METER.getUrl();
    Optional<Object> res = sender.sendGetRequest(String.format(url, "350500159137"), Object.class);
    System.out.println(res.get());
  }

  @Test
  void heYiReadMeterBatchTest() {
    Sender<Object> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("Authorization",
            "Bearer " + "4d951b35-8c90-42bf-af29-e80644aaa42b");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      }
    });
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("meterNoArray", "510300257257");

    String url = heYiAccount.getDomain() + HeYiUrlE.READ_BATCH_METERS.getUrl();
    Optional<Object> res = sender.sendPostRequest(url, Encodes.toFormUrlEncoder(map), Object.class);
    System.out.println(res.get());
  }

  @Test
  void heYiReadMeterTest() {
    Sender<HeYiResponse<HeYiMeterCheckResponse>> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("Authorization",
            "Bearer " + "4d951b35-8c90-42bf-af29-e80644aaa42b");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      }
    });
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("meterNo", "510300257257");

    String url = heYiAccount.getDomain() + HeYiUrlE.READ_METER.getUrl();
    Type type = new TypeToken<HeYiResponse<HeYiMeterCheckResponse>>() {
    }.getType();
    Optional<HeYiResponse<HeYiMeterCheckResponse>> res = sender.sendPostRequest(url,
        Encodes.toFormUrlEncoder(map), type);
    HeYiResponse<HeYiMeterCheckResponse> out = res.get();
    System.out.println(out);
    System.out.println(out.getObject());

  }

  @Test
  void heYiReadMeterOpTest() {
    EMeterCheckResponse response = ElectronicRequestOp.HE_YI.readMeterRequest("510300257257");
    System.out.println(response);
    System.out.println(response.getErrorInfo());
  }

  @Test
  void timeTest() {
    System.out.println(Common.findTimeByMillSecondTimestamp(1774246425000L));
    System.out.println(Common.findTimeSecondByISO("2026-03-23T06:13:45.000+0000"));
  }

  @Test
  void heYiEFullStatusMeterOpTest() {
    EMeterCheckResponse response = ElectronicRequestOp.HE_YI.checkMeterFullState("510300257257");
    System.out.println(response);
    System.out.println(response.getErrorInfo());
  }

  @Test
  void heYiWFullStatusMeterOpTest() {
    WMeterCheckResponse response = WaterRequestOp.HE_YI.findWMeterInfoRequest("350500159137");
    System.out.println(response);
    System.out.println(response.getErrorInfo());
  }

  @Test
  void heYiSwitchEMeterTest() {
    MeterRequest meterRequest = new MeterRequest();
    meterRequest.setId(32);
    meterRequest.setServiceType(2);
    RequestDataInfo<MeterRequest> adjustRequest = new RequestDataInfo<>("id", "name", meterRequest);
    ReturnDataInfo<Integer> response = electronicMeterService.adjustESwitch(adjustRequest);
    System.out.println(response);
    System.out.println(response.getErrorInfo());
  }

  @Test
  void heYiSwitchEMeterSenderTest() {
    Sender<Object> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
        conn.setRequestProperty("Authorization",
            "Bearer " + "4d951b35-8c90-42bf-af29-e80644aaa42b");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
      }
    });
    MultiValueMap<String, String> map = new LinkedMultiValueMap<>();
    map.add("meterNo", "510300257257");
    map.add("action", "close");

    String url = heYiAccount.getDomain() + HeYiUrlE.ELE_SWITCH.getUrl();
    Optional<Object> res = sender.sendPostRequest(url, Encodes.toFormUrlEncoder(map), Object.class);
    System.out.println(res.get());
  }

  @Test
  void sassLockLoginTest() {
    System.out.println(hansAccount);
    System.out.println(GsonFactory.toJson(hansAccount.toDTO()));
    Sender<Object> sender = new Sender<>(new Sender.ConSetter() {
      @Override
      public void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
        Sender.ConSetter.super.setConnection(conn, method);
      }
    });

    String url = heYiAccount.getDomain() + "auth/login";
    Optional<Object> res = sender.sendPostRequest(url, GsonFactory.toJson(hansAccount.toDTO()),
        Object.class);
    System.out.println(sender.getException());
    System.out.println(res.get());
  }

  @Setter
  @Getter
  @ToString
  @NoArgsConstructor
  public static class Detail {

    String source_name;

    int eventid;

    int source;

    int sourceid;

    int notify;

    int audio_played;

    String monkey;
  }

  @Setter
  @Getter
  @ToString
  @NoArgsConstructor
  public static class CallbackSample {

    String event;

    String uuid;

    String home_id;

    String room_id;

    Object detail;

    long time;

    String monkey1;
  }

}
