package com.saathchalo.privacy;

import com.saathchalo.access.AccessService;
import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.PersonalData;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.privacy.PrivacyDtos.LeaveReason;
import com.saathchalo.rides.LeavingMembers;
import com.saathchalo.rides.LeavingMembers.Commitments;
import com.saathchalo.user.UserService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The privacy centre: a copy of everything we hold about a member, and deleting their account. */
@Service
public class PrivacyService {

	/** How long a member has to change their mind. Logging in again cancels the deletion. */
	static final Duration CHANGE_OF_MIND = Duration.ofDays(30);

	static final int MAX_EXPORTS_PER_DAY = 5;

	private final List<PersonalData> sections;

	private final UserService users;

	private final AccessService access;

	private final LeavingMembers rides;

	private final AuditService audit;

	private final RateLimiter limiter;

	private final Clock clock;

	public PrivacyService(List<PersonalData> sections, UserService users, AccessService access, LeavingMembers rides,
			AuditService audit, RateLimiter limiter, Clock clock) {
		this.sections = sections.stream().sorted(Comparator.comparingInt(PersonalData::order)).toList();
		this.users = users;
		this.access = access;
		this.rides = rides;
		this.audit = audit;
		this.limiter = limiter;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public Map<String, Object> export(UUID me) {
		if (!limiter.tryAcquire("privacy-export:" + me, MAX_EXPORTS_PER_DAY, Duration.ofDays(1))) {
			throw ApiException.tooManyRequests("slow_down", "You've downloaded your data several times today. Please try again tomorrow.");
		}
		Map<String, Object> out = new LinkedHashMap<>();
		Map<String, Object> about = new LinkedHashMap<>();
		about.put("service", "SaathChalo");
		about.put("createdAt", clock.instant().toString());
		about.put("note", "Everything SaathChalo holds about you. Reports other members made about you are left out, "
				+ "to protect the people who made them. Times are in UTC.");
		out.put("about", about);
		sections.forEach(s -> out.put(s.section(), s.export(me)));
		return out;
	}

	/**
	 * Switches the account off straight away and erases it after {@link #CHANGE_OF_MIND}. Refused while other people
	 * are counting on the member for a trip. Upcoming rides nobody is confirmed on are cancelled.
	 */
	@Transactional
	public Instant requestDeletion(UUID me, LeaveReason reason) {
		if (access.membership(me).isPresent()) {
			throw ApiException.conflict("staff_account",
					"Staff accounts can't be deleted here. Ask the owner to remove your staff access first.");
		}
		Commitments trips = rides.commitments(me);
		if (trips.any()) {
			throw ApiException.conflict("upcoming_trips", tripsMessage(trips))
				.with("ridesWithPassengers", trips.ridesWithPassengers())
				.with("confirmedSeats", trips.confirmedSeats());
		}
		rides.release(me);
		Instant due = clock.instant().plus(CHANGE_OF_MIND);
		users.scheduleDeletion(me, due);
		audit.record(new Actor(me, "Member (self-service)"), "member.deletion_requested", "member", me.toString(),
				"Asked to delete their account", reason == null ? null : Map.of("reason", reason.name()));
		return due;
	}

	private static String tripsMessage(Commitments trips) {
		StringBuilder b = new StringBuilder("Other people are counting on you: ");
		if (trips.ridesWithPassengers() > 0) {
			b.append(trips.ridesWithPassengers() == 1 ? "1 ride you're driving has" : trips.ridesWithPassengers() + " rides you're driving have")
				.append(" confirmed passengers");
		}
		if (trips.confirmedSeats() > 0) {
			b.append(trips.ridesWithPassengers() > 0 ? ", and " : "")
				.append(trips.confirmedSeats() == 1 ? "you have 1 confirmed seat" : "you have " + trips.confirmedSeats() + " confirmed seats");
		}
		return b.append(". Cancel those trips first (or wait until they're over), then delete your account.").toString();
	}
}
