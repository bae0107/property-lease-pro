package com.jugu.propertylease.main.iam.delegate;

import com.jugu.propertylease.main.api.AuthApiDelegate;
import com.jugu.propertylease.main.api.model.LoginResult;
import com.jugu.propertylease.main.api.model.LogoutRequest;
import com.jugu.propertylease.main.api.model.PasswordLoginRequest;
import com.jugu.propertylease.main.api.model.RefreshResult;
import com.jugu.propertylease.main.api.model.RefreshTokenRequest;
import com.jugu.propertylease.main.iam.auth.AuthSessionService;
import com.jugu.propertylease.main.iam.auth.PasswordLoginService;
import org.springframework.stereotype.Service;

@Service
public class AuthApiDelegateImpl implements AuthApiDelegate {

  private final PasswordLoginService passwordLoginService;
  private final AuthSessionService authSessionService;

  public AuthApiDelegateImpl(PasswordLoginService passwordLoginService,
      AuthSessionService authSessionService) {
    this.passwordLoginService = passwordLoginService;
    this.authSessionService = authSessionService;
  }

  @Override
  public LoginResult passwordLogin(PasswordLoginRequest passwordLoginRequest) {
    return passwordLoginService.login(passwordLoginRequest);
  }

  @Override
  public RefreshResult refreshToken(RefreshTokenRequest refreshTokenRequest) {
    return authSessionService.refresh(refreshTokenRequest);
  }

  @Override
  public void logout(LogoutRequest logoutRequest) {
    authSessionService.logout(logoutRequest);
  }
}
