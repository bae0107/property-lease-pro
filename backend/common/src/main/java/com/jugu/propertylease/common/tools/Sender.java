package com.jugu.propertylease.common.tools;

import com.jugu.propertylease.common.utils.GsonFactory;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.Getter;

@SuppressWarnings("ClassCanBeRecord")
@Getter
public class Sender<T> {

  private final ConSetter conSetter;

  private IOException exception;

  public Sender(ConSetter conSetter) {
    this.conSetter = conSetter;
  }

  public Optional<T> sendPostRequest(String url, String data, Class<T> clz) {
    Converter<T> converter = in -> GsonFactory.fromJson(in, clz);
    return sendPostRequest(url, data, converter);
  }

  public Optional<T> sendPostRequest(String url, String data, Type type) {
    Converter<T> converter = in -> GsonFactory.fromJson(in, type);
    return sendPostRequest(url, data, converter);
  }

  public Optional<T> sendGetRequest(String url, Class<T> clz) {
    Converter<T> converter = in -> GsonFactory.fromJson(in, clz);
    return sendGetRequest(url, converter);
  }

  public Optional<T> sendGetRequest(String url, Type type) {
    Converter<T> converter = in -> GsonFactory.fromJson(in, type);
    return sendGetRequest(url, converter);
  }

  private Optional<T> sendGetRequest(String url, Converter<T> converter) {
    HttpURLConnection connection = null;
    try {
      URL realUrl = new URL(url);
      connection = (HttpURLConnection) realUrl.openConnection();
      conSetter.setConnection(connection, "GET");
      connection.connect();
      try (BufferedReader reader = new BufferedReader(
          new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
        return Optional.of(converter.convert(reader));
      }
    } catch (IOException e) {
      this.exception = e;
      return Optional.empty();
    } finally {
      if (connection != null) {
        connection.disconnect();
      }
    }
  }

  private Optional<T> sendPostRequest(String url, String input, Converter<T> converter) {
    HttpURLConnection conn = null;
    try {
      URL realUrl = new URL(url);
      conn = (HttpURLConnection) realUrl.openConnection();
      conSetter.setConnection(conn, "POST");
      conn.connect();
      try (OutputStreamWriter out = new OutputStreamWriter(conn.getOutputStream(),
          StandardCharsets.UTF_8)) {
        // 发送请求参数
        out.write(input);
        out.flush();
        try (BufferedReader in = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
          return Optional.of(converter.convert(in));
        }
      }
    } catch (IOException e) {
      this.exception = e;
      return Optional.empty();
    } finally {
      if (conn != null) {
        conn.disconnect();
      }
    }
  }

  public interface Converter<D> {

    D convert(BufferedReader in);
  }

  public interface ConSetter {

    default void setConnection(HttpURLConnection conn, String method) throws ProtocolException {
      conn.setConnectTimeout(2000);
      conn.setReadTimeout(5000);
      conn.setDoInput(true);
      conn.setDoOutput(true);
      conn.setRequestMethod(method);
      conn.setUseCaches(false);
      conn.setRequestProperty("Content-Type", "application/json");
    }
  }
}
