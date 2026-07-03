package karvio.controller;

import karvio.dto.TokenRequest;
import karvio.dto.TokenResponse;
import karvio.service.AuthSocialService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.security.GeneralSecurityException;

@RestController
public class AuthSocialController {

    private final AuthSocialService authSocialService;

    AuthSocialController(AuthSocialService authSocialService) {
        this.authSocialService = authSocialService;
    }

    @PostMapping("/google")
    public ResponseEntity<TokenResponse> loginWithGoogle(@RequestBody TokenRequest request) throws GeneralSecurityException, IOException {
        return new ResponseEntity<>(authSocialService.loginWithGoogle(request), HttpStatus.ACCEPTED);
    }

    @PostMapping("/apple")
    public ResponseEntity<TokenResponse> loginWithApple(@RequestBody TokenRequest request) throws Exception {
        return new ResponseEntity<>(authSocialService.loginWithApple(request), HttpStatus.ACCEPTED);
    }
}
