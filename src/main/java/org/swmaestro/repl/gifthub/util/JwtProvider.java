package org.swmaestro.repl.gifthub.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.PropertySource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.swmaestro.repl.gifthub.auth.repository.RefreshTokenRepository;
import org.swmaestro.repl.gifthub.exception.BusinessException;
import org.swmaestro.repl.gifthub.exception.ErrorCode;
import org.swmaestro.repl.gifthub.security.JpaUserDetailsService;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Component
@PropertySource("classpath:application.yml")
public class JwtProvider {
	private final String secretKey;
	private final long expiration;
	private final String issuer;
	private final JpaUserDetailsService userDetailsService;
	private final RefreshTokenRepository refreshTokenRepository;

	public JwtProvider(@Value("${jwt.secret-key}") String secretKey, @Value("${jwt.expiration-time}") long expiration,
	                   @Value("${issuer}") String issuer, JpaUserDetailsService userDetailsService,
	                   RefreshTokenRepository refreshTokenRepository) {
		this.secretKey = secretKey;
		this.expiration = expiration;
		this.issuer = issuer;
		this.userDetailsService = userDetailsService;
		this.refreshTokenRepository = refreshTokenRepository;
	}

	/**
	 * AccessToken 생성 메소드
	 *
	 * @param username
	 * @return JWT 토큰
	 */
	public String generateToken(String username) {
		return io.jsonwebtoken.Jwts.builder()
				.setSubject(username)
				.setIssuer(issuer)
				.setIssuedAt(new java.util.Date(System.currentTimeMillis()))
				.setExpiration(new java.util.Date(System.currentTimeMillis() + expiration))
				.signWith(io.jsonwebtoken.SignatureAlgorithm.HS512, secretKey.getBytes())
				.compact();
	}

	/**
	 * JWT 유효성 검사 메소드
	 *
	 * @param token
	 * @return 유효성 여부
	 */
	public boolean validateToken(String token) {
		try {
			// Bearer 검증
			if (!token.substring(0, "BEARER ".length()).equalsIgnoreCase("BEARER ")) {
				return false;
			} else {
				token = token.split(" ")[1].trim();
			}
			Jws<Claims> claims = Jwts.parserBuilder().setSigningKey(secretKey.getBytes()).build().parseClaimsJws(token);
			// 만료되었을 시 false
			return !claims.getBody().getExpiration().before(new Date());
		} catch (Exception e) {
			return false;
		}
	}

	/**
	 * HTTP Header에서 JWT 토큰을 가져오는 메소드
	 *
	 * @param request
	 * @return JWT 토큰
	 */
	public String resolveToken(HttpServletRequest request) {
		return request.getHeader("Authorization");
	}

	/**
	 * JWT 토큰에서 인증 정보를 가져오는 메소드
	 *
	 * @param token
	 * @return 인증 정보
	 */
	public Authentication getAuthentication(String token) {
		UserDetails userDetails = userDetailsService.loadUserByUsername(this.getUsername(token));
		return new UsernamePasswordAuthenticationToken(userDetails, "", userDetails.getAuthorities());
	}

	/**
	 * JWT 토큰에서 username을 가져오는 메소드
	 *
	 * @param token
	 * @return username
	 */
	public String getUsername(String token) {
		return Jwts.parserBuilder()
				.setSigningKey(secretKey.getBytes())
				.build()
				.parseClaimsJws(token)
				.getBody()
				.getSubject();
	}

	/**
	 * RefreshToken 생성 메소드
	 *
	 * @param username
	 * @return username
	 */
	public String generateRefreshToken(String username) {
		return io.jsonwebtoken.Jwts.builder()
				.setSubject(username)
				.setIssuer(issuer)
				.setIssuedAt(new java.util.Date(System.currentTimeMillis()))
				.setExpiration(Date.from(Instant.now().plus(15, ChronoUnit.DAYS)))
				.signWith(io.jsonwebtoken.SignatureAlgorithm.HS512, secretKey.getBytes())
				.compact();
	}

	/**
	 * RefreshToken으로 AccessToken을 재발급하는 메소드(단 DB에 저장 되어 있던 RefreshToken과 username이 일치해야 함)
	 *
	 * @param refreshToken
	 * @return accessToken
	 */
	public String reissueAccessToken(String refreshToken) {
		String username = getUsername(refreshToken);
		String storedRefreshToken = refreshTokenRepository.findByUsername(username).get().getToken();

		if (!refreshToken.equals(storedRefreshToken)) {
			throw new BusinessException("RefreshToken이 유효하지 않습니다.", ErrorCode.INVALID_AUTHENTICATION);
		}
		return generateToken(username);
	}

	/**
	 * 토큰 발급 시간을 가져오는 메소드
	 *
	 * @param token
	 * @return 발급 시간
	 */
	public LocalDateTime getIssuedAt(String token) {
		return Jwts.parserBuilder()
				.setSigningKey(secretKey.getBytes())
				.build()
				.parseClaimsJws(token)
				.getBody()
				.getIssuedAt()
				.toInstant()
				.atZone(java.time.ZoneId.systemDefault())
				.toLocalDateTime();

	}
}
