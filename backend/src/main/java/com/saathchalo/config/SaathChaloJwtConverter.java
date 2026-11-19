package com.saathchalo.config;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Turns a verified JWT into authorities. Member tokens get {@code ROLE_USER}. Staff console tokens (claim {@code sid})
 * are checked against their live session on every request and get {@code STAFF_SESSION} plus {@code PERM_<permission>},
 * so revoking a session or changing a role takes effect immediately.
 */
class SaathChaloJwtConverter implements Converter<Jwt, AbstractAuthenticationToken> {

	static final String STAFF_SESSION = "STAFF_SESSION";

	private final StaffSessionPort staffSessions;

	SaathChaloJwtConverter(StaffSessionPort staffSessions) {
		this.staffSessions = staffSessions;
	}

	@Override
	public AbstractAuthenticationToken convert(Jwt jwt) {
		List<GrantedAuthority> authorities = new ArrayList<>();
		authorities.add(new SimpleGrantedAuthority("ROLE_USER"));
		String sid = jwt.getClaimAsString("sid");
		if (sid != null) {
			UUID userId = UUID.fromString(jwt.getSubject());
			for (String permission : staffSessions.validate(UUID.fromString(sid), userId)) {
				authorities.add(new SimpleGrantedAuthority("PERM_" + permission));
			}
			authorities.add(new SimpleGrantedAuthority(STAFF_SESSION));
		}
		return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
	}
}
