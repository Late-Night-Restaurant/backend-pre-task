package com.backend.oauthlogin.service;

import com.backend.oauthlogin.dto.TokenDto;
import com.backend.oauthlogin.dto.oauth.kakao.KakaoAccountDto;
import com.backend.oauthlogin.dto.oauth.kakao.KakaoTokenDto;
import com.backend.oauthlogin.entity.RefreshToken;
import com.backend.oauthlogin.entity.User;
import com.backend.oauthlogin.jwt.TokenProvider;
import com.backend.oauthlogin.repository.RefreshTokenRepository;
import com.backend.oauthlogin.repository.UserRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;


import static com.backend.oauthlogin.entity.LoginType.KAKAO;
import static com.backend.oauthlogin.entity.Role.ROLE_USER;

@Service
@Slf4j
@RequiredArgsConstructor
public class OauthService {

    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final UserRepository userRepository;
    private final RefreshTokenRepository tokenRepository;
    private final AuthService authService;
    private final TokenProvider tokenProvider;

    // 환경변수 가져오기
    @Value("${spring.jpa.security.oauth2.client.registration.kakao.client-id}")
    String KAKAO_CLIENT_ID;

    @Value("${spring.jpa.security.oauth2.client.registration.kakao.redirect-uri}")
    String KAKAO_REDIRECT_URI;

    @Value("${spring.jpa.security.oauth2.client.registration.kakao.client-secret}")
    String KAKAO_CLIENT_SECRET;

    // 인가코드로 Kakao Access Token 을 요청하는 메소드
    public KakaoTokenDto getKakaoAccessToken(String code) {

        // Access Token 발급 요청
        RestTemplate rt = new RestTemplate();  // 통신용 템플릿 for HTTP 통신 단순화 (스프링에서 지원하는 REST 서비스 호출방식)
        rt.setRequestFactory(new HttpComponentsClientHttpRequestFactory());

        /* Kakao 공식 문서에 따라 헤더,바디 값 구성 */

        HttpHeaders headers = new HttpHeaders();
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();
        params.add("grant_type", "authorization_code");
        params.add("client_id", KAKAO_CLIENT_ID);
        params.add("redirect_uri", KAKAO_REDIRECT_URI);
        params.add("code", code);  // 인가 코드 요청 시 받은 인가 코드 값 from 프론트엔드
//        params.add("client_secret", KAKAO_CLIENT_SECRET);   // 우선 생략

        // Kakao 에 Access Token 요청
        HttpEntity<MultiValueMap<String, String>> kakaoTokenRequest = new HttpEntity<>(params, headers);
        log.info("KakaoTokenRequest: {}", kakaoTokenRequest);

        // Kakao 로부터 Access Token 수신
        ResponseEntity<String> accessTokenResponse = rt.exchange(
                "https://kauth.kakao.com/oauth/token",   // 토큰 발급 요청 주소에 POST 방식으로 HTTP Body 에 데이터를 담아서 전달
                HttpMethod.POST,
                kakaoTokenRequest,
                String.class
        );

        // JSON Parsing (KakaoTokenDto)
        ObjectMapper objectMapper = new ObjectMapper();
        KakaoTokenDto kakaoTokenDto = null;
        try {
            kakaoTokenDto = objectMapper.readValue(accessTokenResponse.getBody(), KakaoTokenDto.class);
        } catch (JsonProcessingException e) {
            log.debug("Kakao Access Token(KakaoTokenDto) JSON Parsing 에 실패했습니다.");
        }

        return kakaoTokenDto;
    }

    // Kakao Access Token 으로 카카오 서버에 정보 요청하는 메소드
    public KakaoAccountDto getKakaoInfo(String kakaoAccessToken) {

        RestTemplate rt = new RestTemplate();

        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + kakaoAccessToken);
        headers.add("Content-type", "application/x-www-form-urlencoded;charset=utf-8");

        HttpEntity<MultiValueMap<String, String>> kakaoInfoRequest = new HttpEntity<>(headers);

        // POST 방식으로 API 서버에 요청을 보내고 Response 를 받아온다.
        ResponseEntity<String> kakaoInfoResponse = rt.exchange(
                "https://kapi.kakao.com/v2/user/me",
                HttpMethod.POST,
                kakaoInfoRequest,
                String.class
        );

        log.info("카카오 서버에서 정상적으로 데이터를 수신했습니다. {}", kakaoInfoResponse.getBody());

        // Json Parsing (KakaoAccountDto)
        ObjectMapper objectMapper = new ObjectMapper();
        KakaoAccountDto kakaoAccountDto = null;
        try {
            log.info("kakaoInfoResponse.getBody() : {}", kakaoInfoResponse.getBody());
            kakaoAccountDto = objectMapper.readValue(kakaoInfoResponse.getBody(), KakaoAccountDto.class);
            log.info("KakaoInfo(KakaoAccountDto) JSON Parsing 에 성공했습니다.");
        } catch (JsonProcessingException e) {
            log.debug("KakaoInfo(KakaoAccountDto) JSON Parsing 에 실패했습니다.");
        } catch (Exception e) {
            e.printStackTrace();
        }

        return kakaoAccountDto;
    }

    public TokenDto saveKakaoUser(String token) {

        KakaoAccountDto kakaoAccount = getKakaoInfo(token);
        log.info("OauthService - saveKakaoUser() kakaoInfo 받아오기 성공 : {}", kakaoAccount);

        User user = null;
//        User user = userRepository.findByEmail(kakaoAccount.getKakao_account().getEmail()).orElseThrow(null);
        log.info("userRepository 에 중복된 이메일이 없으므로 자체 유저 객체로 생성");
        if (user == null) {
            user = User.builder()
                    .email(kakaoAccount.getKakao_account().getEmail())
                    .pw("change your password!")
                    .loginType(KAKAO)
                    .role(ROLE_USER)
                    .activated(true)
                    .build();
            userRepository.save(user);
        }
        log.info("UserRepository에 카카오 유저저장(JWT 토큰 발급 이전) : {}", user);
        /*LoginDto loginDto = LoginDto.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .build();
        return authService.authenticate(loginDto);*/
        return tokenProvider.createToken(user.getEmail());
    }

    public HttpHeaders setTokenHeaders(TokenDto tokenDto) {
        HttpHeaders headers = new HttpHeaders();
        ResponseCookie cookie = ResponseCookie.from("RefreshToken", tokenDto.getRefreshToken())
                .path("/")
                .maxAge(60*60*24*7)   // Cookie 유효기간 7일로 지정
                .secure(true)
                .sameSite("None")
                .httpOnly(true)
                .build();

        headers.add("Set-cookie", cookie.toString());
        headers.add("Authorization", tokenDto.getAccessToken());

        return headers;
    }

    public void saveRefreshToken(User user, TokenDto tokenDto) {
        RefreshToken refreshToken = RefreshToken.builder()
                .key(user.getUsername())
                .value(tokenDto.getRefreshToken())
                .build();

        tokenRepository.save(refreshToken);
        log.info("토큰 저장이 완료되었습니다 : 계정 아이디 - {}, refresh token - {}", user.getUserId(), tokenDto.getRefreshToken());
    }

    //== 채니님 코드 ==//
    /*public String createToken(User user) {

        String jwtToken = JWT.create()
                .withSubject(user.getEmail())
                .withExpiresAt(new Date(System.currentTimeMillis()+ JwtProperties.EXPIRATION_TIME))

                .withClaim("id", user.getId())
                .withClaim("email", user.getEmail())

                .sign(Algorithm.HMAC512(JwtProperties.SECRET));
        System.out.println("createToken 메소드 : 자체 토큰 발급 완료");
        return jwtToken;
    }
*/
}
