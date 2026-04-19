package com.jugu.propertylease.common.utils;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import java.io.Reader;
import java.lang.reflect.Type;

public class GsonFactory {

  private static volatile Gson gson;

  private GsonFactory() {
    // 私有构造函数，防止实例化
  }

  /**
   * 获取默认配置的Gson实例
   */
  public static Gson getGson() {
    if (gson == null) {
      synchronized (GsonFactory.class) {
        if (gson == null) {
          gson = createGson();
        }
      }
    }
    return gson;
  }

  /**
   * 创建Gson实例
   */
  private static Gson createGson() {
    return new GsonBuilder()
        .setDateFormat("yyyy-MM-dd HH:mm:ss")  // 日期格式
        .disableHtmlEscaping()                 // 禁用HTML转义
        .create();
  }

  /**
   * 对象转JSON字符串
   */
  public static String toJson(Object obj) {
    return getGson().toJson(obj);
  }

  /**
   * JSON字符串转对象
   */
  public static <T> T fromJson(String json, Class<T> clazz) {
    return getGson().fromJson(json, clazz);
  }

  public static <T> T fromJson(Reader json, Class<T> classOfT)
      throws JsonSyntaxException, JsonIOException {
    return getGson().fromJson(json, classOfT);
  }

  public static <T> T fromJson(Reader json, Type type) throws JsonSyntaxException, JsonIOException {
    return getGson().fromJson(json, type);
  }

  /**
   * JSON字符串转对象（支持泛型）
   */
  public static <T> T fromJson(String json, Type type) {
    return getGson().fromJson(json, type);
  }
}
