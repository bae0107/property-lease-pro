package com.jugu.propertylease.gateway.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RouteConfig 测试")
class RouteConfigTest {

  /**
   * 构造包含所有服务配置的 GatewayProperties
   */
  private GatewayProperties propertiesWithAllRoutes() {
    GatewayProperties props = new GatewayProperties();
    Map<String, GatewayProperties.RouteProperties> routes = new HashMap<>();
    int port = 8081;
    for (String name : new String[]{"main-service", "billing-service", "device-service"}) {
      GatewayProperties.RouteProperties route = new GatewayProperties.RouteProperties();
      route.setUrl("http://localhost:" + port++);
      routes.put(name, route);
    }
    props.setRoutes(routes);
    return props;
  }

  @Test
  @DisplayName("所有路由配置完整时构造成功")
  void constructor_allRoutesConfigured_shouldSucceed() {
    // 不抛出异常即为成功
    assertThat(new RouteConfig(propertiesWithAllRoutes())).isNotNull();
  }

  @Test
  @DisplayName("缺少 main-service 配置时构造器快速失败")
  void constructor_missingMainService_shouldFailFast() {
    GatewayProperties props = new GatewayProperties();
    props.setRoutes(new HashMap<>());

    assertThatThrownBy(() -> new RouteConfig(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("gateway.routes.main-service.url 未配置");
  }

  @Test
  @DisplayName("下游服务 URL 为空白时构造器快速失败")
  void constructor_blankUrl_shouldFailFast() {
    GatewayProperties props = propertiesWithAllRoutes();
    GatewayProperties.RouteProperties blank = new GatewayProperties.RouteProperties();
    blank.setUrl("   ");
    props.getRoutes().put("billing-service", blank);

    assertThatThrownBy(() -> new RouteConfig(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("gateway.routes.billing-service.url 未配置");
  }

  @Test
  @DisplayName("device-service URL 缺失时构造器快速失败")
  void constructor_missingDeviceService_shouldFailFast() {
    GatewayProperties props = propertiesWithAllRoutes();
    props.getRoutes().remove("device-service");

    assertThatThrownBy(() -> new RouteConfig(props))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("gateway.routes.device-service.url 未配置");
  }
}
