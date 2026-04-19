package com.jugu.propertylease.device.app.callback;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.reflect.TypeToken;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;
import lombok.extern.log4j.Log4j2;

@Log4j2
public class CallbackUtil {

  private static final Gson gson = new GsonBuilder().serializeNulls().create();

  public static String generateSign(Map<String, String> params, String callbackUrl)
      throws RuntimeException, NoSuchAlgorithmException {
    // 1. 移除sign参数（如果存在）
    params.remove("sign");

    // 2. 第一层参数按ASCII码排序（字典序）
    TreeMap<String, String> sortedParams = new TreeMap<>();
    for (Map.Entry<String, String> entry : params.entrySet()) {
      // 规则2：值为空填空
      String val = entry.getValue();
      if (val != null) {
        if (!val.trim().isEmpty()) {
          sortedParams.put(entry.getKey(), val);
        }
      } else {
        sortedParams.put(entry.getKey(), "");
      }
    }

    // 3. 拼接成 key1=value1&key2=value2... 格式
    StringBuilder stringA = new StringBuilder();
    for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
      if (stringA.length() > 0) {
        stringA.append("&");
      }
      stringA.append(entry.getKey())
          .append("=")
          .append(entry.getValue());
    }

    // 4. 前面拼接回调URL得到待签名字符串
    String stringSignTemp = callbackUrl + stringA;
    log.info(stringSignTemp);
    // 5. 进行MD5运算
    return md5(stringSignTemp);
  }

  public static boolean verifySign(Map<String, String> params, String callbackUrl)
      throws RuntimeException, NoSuchAlgorithmException {
    String receivedSign = params.getOrDefault("sign", "");
    if (receivedSign.isEmpty()) {
      return false;
    }
    // 生成签名
    String calculatedSign = generateSign(params, callbackUrl);
    log.info(calculatedSign);

    // 比较签名（忽略大小写）
    return calculatedSign.equalsIgnoreCase(receivedSign);
  }

  public static Map<String, String> convertToParamMap(String paramsStr) {
    // 1. 先解析为Map<String, JsonElement>
    Type type = new TypeToken<Map<String, JsonElement>>() {
    }.getType();
    Map<String, JsonElement> rawMap = gson.fromJson(paramsStr, type);

    Map<String, String> result = new HashMap<>();

    for (Map.Entry<String, JsonElement> entry : rawMap.entrySet()) {
      String key = entry.getKey();
      JsonElement value = entry.getValue();

      if (value.isJsonNull()) {
        result.put(key, null);
      } else if (value.isJsonObject() || value.isJsonArray()) {
        // 对象或数组转换为JSON字符串
        result.put(key, value.toString());
      } else if (value.isJsonPrimitive()) {
        // 基本类型
        result.put(key, value.getAsString());
      }
    }
    log.info(result);
    return result;
  }

  /**
   * MD5加密方法
   */
  private static String md5(String input) throws RuntimeException, NoSuchAlgorithmException {
    java.security.MessageDigest md = java.security.MessageDigest.getInstance("MD5");
    byte[] messageDigest = md.digest(input.getBytes(StandardCharsets.UTF_8));

    StringBuilder hexString = new StringBuilder();
    for (byte b : messageDigest) {
      String hex = Integer.toHexString(0xff & b);
      if (hex.length() == 1) {
        hexString.append('0');
      }
      hexString.append(hex);
    }
    return hexString.toString().toUpperCase();
  }
}
