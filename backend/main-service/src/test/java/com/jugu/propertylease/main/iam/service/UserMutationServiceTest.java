package com.jugu.propertylease.main.iam.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.jugu.propertylease.common.exception.BusinessException;
import com.jugu.propertylease.main.api.model.PatchUserRequest;
import com.jugu.propertylease.main.api.model.SourceType;
import com.jugu.propertylease.main.api.model.UserType;
import com.jugu.propertylease.main.iam.auth.AuthVersionService;
import com.jugu.propertylease.main.iam.repo.IamUserMutationRepository;
import com.jugu.propertylease.main.iam.repo.model.UserBaseInfo;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class UserMutationServiceTest {

  @Mock
  private IamUserMutationRepository userMutationRepository;

  @Mock
  private AuthVersionService authVersionService;

  @Mock
  private UserReadService userReadService;

  @InjectMocks
  private UserMutationService userMutationService;

  @Test
  void patchUser_builtinUser_shouldReject() {
    Long userId = 100L;
    when(userMutationRepository.findActiveUserBase(userId))
        .thenReturn(Optional.of(new UserBaseInfo(UserType.STAFF.getValue(), SourceType.BUILTIN.getValue())));

    assertThatThrownBy(() -> userMutationService.patchUser(userId, new PatchUserRequest()))
        .isInstanceOf(BusinessException.class)
        .satisfies(ex -> assertThat(((BusinessException) ex).getCode())
            .isEqualTo("IAM_USER_PATCH_BUILTIN_FORBIDDEN"));

    verifyNoInteractions(authVersionService, userReadService);
  }
}
