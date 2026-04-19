package com.jugu.propertylease.device.app.thirdparty.yunding.error;

import com.jugu.propertylease.common.info.ErrorInfo;
import com.jugu.propertylease.device.app.enums.ErrorTypesE;
import com.jugu.propertylease.device.app.thirdparty.yunding.entity.response.YunDingError;
import java.util.Optional;
import lombok.Getter;

@Getter
public enum YunDingErrorMsgInfo {
  DEFAULT(-999, "发生未知错误，未知错误码:%d"),
  SUCCESS(0, "成功"),
  SUCCESS2(1000, "成功"),
  EXPIRED_TOKEN(102, "token 过期"),
  INVALID_TOKEN_USER(4018, "Token 失效，用户已登出"),
  REPEAT_OBJECT(11000, "无法添加重复对象"),
  PARAM_ERROR(14001, "参数错误"),
  INTERNAL_ERROR(14003, "云丁内部错误"),
  DEVICE_NOT_EXIST_ERROR(14015, "云丁设备不存在"),
  INTERNAL_ERROR2(15000, "云丁内部错误"),
  INVALID_REQUEST(15001, "云丁访问无效"),
  RECORD_NOT_EXIST(15006, "设备记录不存在"),
  METHOD_NOT_EXIST(15007, "方法不存在"),
  ROOM_HAS_SAME_DEVICE(15008, "该房间已有相同类型设备绑定"),
  HOME_HAS_GATEWAY(15009, "改房源已存在绑定的网关"),
  DEVICE_ALREADY_EXIST(15010, "设备记录已经存在"),
  DEVICE_BOUND_BY_OTHER_ACCOUNT(15011, "该设备已被其他账号绑定"),
  ROOM_INFO_INVALID(15012, "房间信息不存在"),
  AUTHOR_INVALID(15014, "权限无效"),
  API_NOT_EXIST(15051, "接口不存在"),
  AUTHOR_SECRET_NOT_EXIST(15013, "管理员密码无法进行此操作"),
  CHECK_TIME_EXPIRE(15605, "查询范围不能超过一年"),
  INVALID_TOKEN(101, "token无效");

  private final int errCode;

  private final String errMsg;

  YunDingErrorMsgInfo(int errCode, String errMsg) {
    this.errCode = errCode;
    this.errMsg = errMsg;
  }

  public static YunDingErrorMsgInfo findMsgInfo(YunDingError yunDingError) {
    int errCode = yunDingError.getErrCode();
    return findMsgInfo(errCode);
  }

  public static YunDingErrorMsgInfo findMsgInfo(int errorCode) {
    for (YunDingErrorMsgInfo msgInfo : values()) {
      if (msgInfo.getErrCode() == errorCode) {
        return msgInfo;
      }
    }
    return YunDingErrorMsgInfo.DEFAULT;
  }

  /**
   * 错误信息以及错误码整合，把云丁直接返回的错误信息索引至本地云丁错误信息管理， 再统一映射到本地服务的错误信息管理，产出最终的业务侧错误信息
   *
   * @param yunDingError 云丁原始错误信息
   * @param serviceName  第三方服务名称
   * @param requestName  第三方接口名称
   * @return empty说明请求成功，反之请求错误
   */
  public static Optional<ErrorInfo> syncErrorInfoByYunDingError(YunDingError yunDingError,
      String serviceName, String requestName) {
    YunDingErrorMsgInfo errorMsgInfo = findMsgInfo(yunDingError);
    if (errorMsgInfo == YunDingErrorMsgInfo.SUCCESS
        || errorMsgInfo == YunDingErrorMsgInfo.SUCCESS2) {
      return Optional.empty();
    }
    String msg = errorMsgInfo.getErrMsg();
    if (errorMsgInfo == YunDingErrorMsgInfo.DEFAULT) {
      msg = String.format(msg, yunDingError.getErrCode());
    }
    ErrorTypesE typesE = ErrorTypesE.THIRD_PARTY_ERROR;
    String hostErrorMsg = String.format(typesE.getErrorMsg(), serviceName, requestName,
        errorMsgInfo.getErrCode(), msg);
    return Optional.of(new ErrorInfo(typesE.getErrorCode(), hostErrorMsg));
  }
}
