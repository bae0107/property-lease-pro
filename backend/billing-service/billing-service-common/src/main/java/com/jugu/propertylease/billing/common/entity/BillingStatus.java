package com.jugu.propertylease.billing.common.entity;

import java.util.Optional;
import lombok.Getter;

@Getter
public enum BillingStatus {
  CREATED,
  PENDING,
  SUCCESS,
  CANCELED,
  FAILED;

  public static Optional<BillingStatus> findStatusByName(String name) {
    for (BillingStatus status : BillingStatus.values()) {
      if (name.equals(status.name())) {
        return Optional.of(status);
      }
    }
    return Optional.empty();
  }
}
