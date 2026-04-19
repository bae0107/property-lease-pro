package com.jugu.propertylease.main.outer.wx;

import com.jugu.propertylease.common.utils.Common;
import com.jugu.propertylease.main.MainServiceApplication;
import com.jugu.propertylease.main.outer.ali.AliYunConfig;
import com.jugu.propertylease.main.outer.ali.AliYunMsgOp;
import com.jugu.propertylease.main.outer.ali.AliYunMsgTools;
import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;
import java.util.Enumeration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(classes = MainServiceApplication.class)
public class WxLoginTest {

  @Autowired
  WxOpenServiceTools wxOpenServiceTools;
  @Autowired
  AliYunMsgTools aliYunMsgTools;
  @Autowired
  AliYunConfig aliYunConfig;
  @Autowired
  private WxOpenConfig wxOpenConfig;

  @Test
  public void wxConfigTest() {
    System.out.println(wxOpenConfig);
    System.out.println(wxOpenServiceTools.genLoginQR(new HttpSession() {
      @Override
      public long getCreationTime() {
        return 0;
      }

      @Override
      public String getId() {
        return null;
      }

      @Override
      public long getLastAccessedTime() {
        return 0;
      }

      @Override
      public ServletContext getServletContext() {
        return null;
      }

      @Override
      public int getMaxInactiveInterval() {
        return 0;
      }

      @Override
      public void setMaxInactiveInterval(int i) {

      }

      @Override
      public Object getAttribute(String s) {
        return null;
      }

      @Override
      public Enumeration<String> getAttributeNames() {
        return null;
      }

      @Override
      public void setAttribute(String s, Object o) {

      }

      @Override
      public void removeAttribute(String s) {

      }

      @Override
      public void invalidate() {

      }

      @Override
      public boolean isNew() {
        return false;
      }
    }));
  }

  @Test
  public void aliConfigTest() {
    System.out.println(aliYunConfig);
  }

  @Test
  public void aliMsgTest() {
    System.out.println(
        aliYunMsgTools.sendDomesticMsg(AliYunMsgOp.MULTI_VALUE_EXAMPLE, "13774209239",
            "666666", Common.findTimeByMillSecondTimestamp(System.currentTimeMillis()), "d", "d"));
  }

  @Test
  public void aliMsgCodeTest() {
    System.out.println(aliYunMsgTools.sendDomesticMsg(AliYunMsgOp.VALID_CODE, "13774209239",
        "666666"));
  }
}
