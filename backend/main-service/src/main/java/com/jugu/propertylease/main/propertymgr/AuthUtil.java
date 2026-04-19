package com.jugu.propertylease.main.propertymgr;

import com.jugu.propertylease.security.context.CurrentUser;

public class AuthUtil {

  public static String getUserName() {
    return "shibowen";
  }

  public static String getUserId() {
    Long userIdL = CurrentUser.getCurrentUserId();
    return "shibowenid";
  }
}
