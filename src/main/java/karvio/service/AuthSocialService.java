package karvio.service;

import com.auth0.jwk.Jwk;
import com.auth0.jwk.JwkProvider;
import com.auth0.jwk.UrlJwkProvider;
import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.interfaces.DecodedJWT;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import karvio.dto.CustomUserDetails;
import karvio.dto.TokenRequest;
import karvio.dto.TokenResponse;
import karvio.entity.Role;
import karvio.entity.User;
import karvio.enums.AuthProvider;
import karvio.enums.RoleName;
import karvio.repository.RoleRepository;
import karvio.repository.UserRepository;
import karvio.security.JwtUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

@Service
@RequiredArgsConstructor
public class AuthSocialService {

    @Value("${webClient.id}")
    private String googleClientId;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final CustomUserDetailsService customUserDetailsService;
    private final JwtUtil jwtUtil;
    private final RoleRepository roleRepository;
    private final JwkProvider jwksProvider = new UrlJwkProvider("https://appleid.apple.com/auth");

    @Transactional
    public TokenResponse loginWithGoogle(TokenRequest request) throws GeneralSecurityException, IOException {

        GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(new NetHttpTransport(), new GsonFactory())
                .setAudience(Collections.singletonList(googleClientId))
                .build();

        GoogleIdToken idToken = verifier.verify(request.token());

        if (idToken != null) {
            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();

            Optional<User> userByEmail = userRepository.findByEmail(email);
            if (userByEmail.isEmpty()) {
                insertUser(email, AuthProvider.GOOGLE, null);
            }

            CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(email);

            Authentication authentication = new UsernamePasswordAuthenticationToken(
                    userDetails, null, userDetails.getAuthorities());

            String newAccessToken = jwtUtil.generateAccessToken(authentication);
            String newRefreshToken = jwtUtil.generateRefreshToken(authentication);

            return new TokenResponse(newAccessToken, newRefreshToken);
        } else {
            throw new RuntimeException("Invalid Google Token");
        }
    }

    @Transactional
    public TokenResponse loginWithApple(TokenRequest request) throws Exception {

        DecodedJWT decodedJWT = JWT.decode(request.token());
        String kid = decodedJWT.getKeyId();

        Jwk jwk = jwksProvider.get(kid);
        PublicKey publicKey = jwk.getPublicKey();

        Algorithm algorithm = Algorithm.RSA256((RSAPublicKey) publicKey, null);

        JWTVerifier verifier = JWT.require(algorithm)
                .withIssuer("https://appleid.apple.com")
                .withAudience("com.anonymous.karvioapp")
                .build();

        String emailForToken;
        DecodedJWT verifiedJWT = verifier.verify(request.token());
        String appleUserId = verifiedJWT.getClaim("sub").asString();
        Optional<User> userById = userRepository.findByAppleUserId(appleUserId);
        String email = verifiedJWT.getClaim("email").asString();
        Optional<User> userByEmail = userRepository.findByEmail(email);

        if (userById.isEmpty()) {
            if (userByEmail.isEmpty()) {
                User newUser = insertUser(email, AuthProvider.APPLE, appleUserId);
                emailForToken = newUser.getEmail();
            } else {
                User existingUser = userByEmail.get();
                existingUser.setAppleUserId(appleUserId);
                userRepository.save(existingUser);
                emailForToken = existingUser.getEmail();
            }
        } else {
            emailForToken = userById.get().getEmail();
        }
        return generateTokens(emailForToken);
    }

    private User insertUser(String email, AuthProvider provider, String appleUserId) {
        User newUser = new User();
        newUser.setEmail(email);
        newUser.setAppleUserId(appleUserId);

        Role role = roleRepository.findByName(RoleName.ROLE_USER)
                .orElseThrow(() -> new RuntimeException("Error: Role is not found."));

        String randomPassword = UUID.randomUUID().toString();
        newUser.setPassword(passwordEncoder.encode(randomPassword));
        newUser.setProvider(provider);
        Set<Role> roles = new HashSet<>();
        roles.add(role);
        newUser.setRoles(roles);

        userRepository.save(newUser);

        return newUser;
    }

    private TokenResponse generateTokens(String email) {
        CustomUserDetails userDetails = (CustomUserDetails) customUserDetailsService.loadUserByUsername(email);

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                userDetails, null, userDetails.getAuthorities());

        String newAccessToken = jwtUtil.generateAccessToken(authentication);
        String newRefreshToken = jwtUtil.generateRefreshToken(authentication);

        return new TokenResponse(newAccessToken, newRefreshToken);
    }
}
