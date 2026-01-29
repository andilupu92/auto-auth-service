package auto.trace.dto;

public record TokenResponse(
        String accessToken,
        String refreshToken
) {}