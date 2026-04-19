//package com.jugu.propertylease.common.exception;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import org.junit.jupiter.api.Test;
//
//import static org.assertj.core.api.Assertions.assertThat;
//
//class ErrorResponseTest {
//
//    private final ObjectMapper mapper = new ObjectMapper();
//
//    @Test
//    void constructor_setsAllFields() {
//        ErrorResponse e = new ErrorResponse("USER_NOT_FOUND", "用户不存在", "trace123");
//        assertThat(e.getCode()).isEqualTo("USER_NOT_FOUND");
//        assertThat(e.getMessage()).isEqualTo("用户不存在");
//        assertThat(e.getTraceId()).isEqualTo("trace123");
//    }
//
//    @Test
//    void blankTraceId_isNormalisedToNull() {
//        assertThat(new ErrorResponse("C", "M", "").getTraceId()).isNull();
//        assertThat(new ErrorResponse("C", "M", "  ").getTraceId()).isNull();
//        assertThat(new ErrorResponse("C", "M", null).getTraceId()).isNull();
//    }
//
//    @Test
//    void serialisation_omitsNullTraceId() throws Exception {
//        String json = mapper.writeValueAsString(new ErrorResponse("CODE", "msg", null));
//        assertThat(json).contains("\"code\":\"CODE\"");
//        assertThat(json).contains("\"message\":\"msg\"");
//        assertThat(json).doesNotContain("traceId");
//    }
//
//    @Test
//    void serialisation_includesTraceIdWhenPresent() throws Exception {
//        String json = mapper.writeValueAsString(new ErrorResponse("CODE", "msg", "t1"));
//        assertThat(json).contains("\"traceId\":\"t1\"");
//    }
//
//    @Test
//    void deserialisation_fromJson_works() throws Exception {
//        String json = "{\"code\":\"FOO\",\"message\":\"bar\",\"traceId\":\"xyz\"}";
//        ErrorResponse e = mapper.readValue(json, ErrorResponse.class);
//        assertThat(e.getCode()).isEqualTo("FOO");
//        assertThat(e.getMessage()).isEqualTo("bar");
//        assertThat(e.getTraceId()).isEqualTo("xyz");
//    }
//}
