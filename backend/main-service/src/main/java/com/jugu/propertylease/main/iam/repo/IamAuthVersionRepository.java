package com.jugu.propertylease.main.iam.repo;

import java.time.OffsetDateTime;
import java.util.Optional;

public interface IamAuthVersionRepository {

  int bumpAuthVersion(Long userId, OffsetDateTime now);

  Optional<Integer> findCurrentAuthVersion(Long userId);
}
