package com.jugu.propertylease.common.feign;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jugu.propertylease.common.exception.BusinessException;
import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class FeignBusinessExceptionErrorDecoderTest {

  private final FeignBusinessExceptionErrorDecoder decoder =
      new FeignBusinessExceptionErrorDecoder(new ObjectMapper());

  @Test
  void response404WithErrorBody_returnsBusinessExceptionWith404() {
    String body = "{\"code\":\"USER_NOT_FOUND\",\"message\":\"用户不存在\",\"traceId\":\"t1\"}";
    Response response = buildResponse(404, body);

    Exception ex = decoder.decode("UserApi#getUser", response);

    assertThat(ex).isInstanceOf(BusinessException.class);
    BusinessException bex = (BusinessException) ex;
    assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.NOT_FOUND);
    assertThat(bex.getErrorCode()).isEqualTo("USER_NOT_FOUND");
    assertThat(bex.getMessage()).isEqualTo("用户不存在");
  }

  @Test
  void response400WithErrorBody_returnsBusinessExceptionWith400() {
    String body = "{\"code\":\"INVENTORY_INSUFFICIENT\",\"message\":\"库存不足\"}";
    Response response = buildResponse(400, body);

    Exception ex = decoder.decode("OrderApi#placeOrder", response);

    assertThat(ex).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) ex).getHttpStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
    assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("INVENTORY_INSUFFICIENT");
  }

  @Test
  void response500WithHtmlBody_returnsDownstreamErrorFallback() {
    String body = "<html><body>Internal Server Error</body></html>";
    Response response = buildResponse(500, body);

    Exception ex = decoder.decode("SomeApi#someMethod", response);

    assertThat(ex).isInstanceOf(BusinessException.class);
    BusinessException bex = (BusinessException) ex;
    assertThat(bex.getHttpStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    assertThat(bex.getErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
  }

  @Test
  void responseWithNullBody_returnsDownstreamErrorFallback() {
    Response response = Response.builder()
        .status(503)
        .request(buildRequest())
        .headers(Collections.emptyMap())
        .build(); // no body

    Exception ex = decoder.decode("SomeApi#someMethod", response);

    assertThat(ex).isInstanceOf(BusinessException.class);
    assertThat(((BusinessException) ex).getErrorCode()).isEqualTo("DOWNSTREAM_ERROR");
  }

  // ===== Helpers =====

  private Response buildResponse(int status, String body) {
    return Response.builder()
        .status(status)
        .request(buildRequest())
        .headers(Collections.emptyMap())
        .body(body, StandardCharsets.UTF_8)
        .build();
  }

  private Request buildRequest() {
    return Request.create(Request.HttpMethod.GET, "http://test/api",
        Collections.emptyMap(), null, StandardCharsets.UTF_8, null);
  }
}
