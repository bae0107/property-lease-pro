package com.jugu.propertylease.main.iam.auth;

public enum IdentityProvider {
  PASSWORD("password");

  private final String value;

  IdentityProvider(String value) {
    this.value = value;
  }

  public String value() {
    return value;
  }
}
