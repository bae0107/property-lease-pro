package com.jugu.propertylease.billing.app.util;

import com.jugu.propertylease.common.utils.Common;
import java.util.concurrent.ThreadLocalRandom;

public class BillingIdTools {

  private static final String DIGITS = "0123456789";

  private static final int SHIFT = 8;

  public static String generateBillingId(String userId, int count) {
    String randomStr = String.format("%03d", ThreadLocalRandom.current().nextInt(0, 1000));
    StringBuilder builder = new StringBuilder(randomStr);
    builder.append(padLeftZeros(caesarEncrypt(userId)));
    builder.append("-");
    String date = Common.findDatSysTime().replaceAll("-", "").substring(2);
    builder.append(padLeftZeros(caesarEncrypt(date)));
    builder.append(padLeftZeros(caesarEncrypt(getLastFive(String.valueOf(count)))));
    return builder.toString();
  }

  /**
   * 凯撒密码加密
   *
   * @param str 原始字符串
   * @return 加密后的字符串
   */
  private static String caesarEncrypt(String str) {
    StringBuilder result = new StringBuilder();
    for (char c : str.toCharArray()) {
      if (c >= '0' && c <= '9') {
        int index = (c - '0' + BillingIdTools.SHIFT) % 10;
        result.append(DIGITS.charAt(index));
      } else if (c >= 'A' && c <= 'Z') {
        int index = (c - 'A' + BillingIdTools.SHIFT) % 10;
        result.append(DIGITS.charAt(index));
      } else if (c >= 'a' && c <= 'z') {
        int index = (c - 'a' + BillingIdTools.SHIFT) % 10;
        result.append(DIGITS.charAt(index));
      } else {
        int index = (c + BillingIdTools.SHIFT) % 10;
        result.append(DIGITS.charAt(index));
      }
    }

    return result.toString();
  }

  private static String padLeftZeros(String str) {
    return String.format("%" + 5 + "s", str).replace(' ', '0');
  }

  public static String getLastFive(String str) {
    if (str.length() <= 5) {
      return str;
    }
    return str.substring(str.length() - 5);
  }
}
