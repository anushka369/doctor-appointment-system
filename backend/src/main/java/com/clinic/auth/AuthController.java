package com.clinic.auth;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    public record OtpRequest(String role, String email) {
    }

    public record VerifyRequest(String challengeId, String otp) {
    }

    public record LoginResponse(String token, Session.UserView user) {
    }

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/request-otp")
    public AuthService.OtpChallenge requestOtp(@RequestBody OtpRequest body) {
        return auth.requestOtp(body.role(), body.email());
    }

    @PostMapping("/verify")
    public LoginResponse verify(@RequestBody VerifyRequest body) {
        Session s = auth.verify(body.challengeId(), body.otp());
        return new LoginResponse(s.token(), s.view());
    }

    @GetMapping("/me")
    public Session.UserView me(@RequestAttribute(AuthInterceptor.ATTR) Session session) {
        return session.view();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(@RequestAttribute(AuthInterceptor.ATTR) Session session) {
        auth.logout(session.token());
    }

    @GetMapping("/demo-accounts")
    public Map<String, List<Map<String, String>>> demoAccounts() {
        return auth.demoAccounts();
    }
}
