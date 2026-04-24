package com.jugu.propertylease.main.iam.repo.model;

import java.time.OffsetDateTime;

public record UserSoftDeleteCommand(
    Long userId,
    Long operatorUserId,
    String reason,
    String tombstoneUserName,
    String tombstoneMobile,
    String tombstoneEmail,
    String oldUserName,
    String oldMobile,
    String oldEmail,
    OffsetDateTime now
) {
}
