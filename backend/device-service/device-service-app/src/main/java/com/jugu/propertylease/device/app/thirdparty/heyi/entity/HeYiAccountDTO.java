package com.jugu.propertylease.device.app.thirdparty.heyi.entity;

import com.google.gson.annotations.SerializedName;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import lombok.Data;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Data
public class HeYiAccountDTO {

  @SerializedName("client_id")
  private String clientId;

  @SerializedName("client_secret")
  private String clientSecret;

  @SerializedName("grant_type")
  private String grantType;

  public String toForm() {
    MultiValueMap<String, Object> map = new LinkedMultiValueMap<>();
    map.add("grant_type", this.grantType);
    map.add("client_id", this.clientId);
    map.add("client_secret", this.clientSecret);

    StringBuilder formBody = new StringBuilder();
    for (Map.Entry<String, List<Object>> entry : map.entrySet()) {
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
