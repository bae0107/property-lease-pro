package com.jugu.propertylease.common.utils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.util.MultiValueMap;

public class Encodes {

  public static String toFormUrlEncoder(MultiValueMap<String, String> map) {
    StringBuilder formBody = new StringBuilder();
    for (Map.Entry<String, List<String>> entry : map.entrySet()) {
      String key = entry.getKey();
      for (Object value : entry.getValue()) {
        if (formBody.length() > 0) {
          formBody.append("&");
        }
        formBody.append(URLEncoder.encode(key, StandardCharsets.UTF_8));
        formBody.append("=");
        formBody.append(
            URLEncoder.encode(value != null ? value.toString() : "", StandardCharsets.UTF_8));
      }
    }
    return formBody.toString();
  }
}
