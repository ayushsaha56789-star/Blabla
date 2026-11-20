package com.saathchalo.safety;

import com.saathchalo.audit.Actor;
import com.saathchalo.audit.AuditService;
import com.saathchalo.auth.SmsSender;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Masking;
import com.saathchalo.config.AppProperties;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.rides.RideLookup.RideParticipants;
import com.saathchalo.safety.EmergencyContactService.Recipient;
import com.saathchalo.safety.SafetyDtos.LocationRequest;
import com.saathchalo.safety.SafetyDtos.SosRequest;
import com.saathchalo.safety.SafetyDtos.SosRow;
import com.saathchalo.safety.SafetyDtos.SosView;
import com.saathchalo.safety.internal.SosAlert;
import com.saathchalo.safety.internal.SosAlertRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The SOS button. Pressing it alerts SaathChalo support (the console's Safety page) and texts the member's emergency
 * contacts with a link to follow the trip. Calling 112 happens on the member's phone. An emergency is never blocked:
 * pressing again during the same trip updates the same alert instead of failing.
 */
@Service
public class SosService {

	private static final Logger log = LoggerFactory.getLogger(SosService.class);

	private static final Duration SAME_ALERT = Duration.ofMinutes(30);

	private final SosAlertRepository alerts;

	private final EmergencyContactService contacts;

	private final TripShareService shares;

	private final RideLookup rides;

	private final MemberProfiles members;

	private final PlaceDirectory places;

	private final SmsSender sms;

	private final AuditService audit;

	private final String publicUrl;

	private final Clock clock;

	public SosService(SosAlertRepository alerts, EmergencyContactService contacts, TripShareService shares,
			RideLookup rides, MemberProfiles members, PlaceDirectory places, SmsSender sms, AuditService audit,
			AppProperties props, Clock clock) {
		this.alerts = alerts;
		this.contacts = contacts;
		this.shares = shares;
		this.rides = rides;
		this.members = members;
		this.places = places;
		this.sms = sms;
		this.audit = audit;
		this.publicUrl = props.publicUrl().replaceAll("/+$", "");
		this.clock = clock;
	}

	@Transactional
	public SosView raise(UUID me, SosRequest req) {
		Instant now = clock.instant();
		UUID rideId = null;
		RideBrief ride = null;
		if (req.rideId() != null) {
			RideParticipants trip = rides.participants(req.rideId()).orElse(null);
			if (trip != null && (trip.isDriver(me) || trip.isConfirmedPassenger(me))) {
				rideId = trip.ride().id();
				ride = trip.ride();
			}
		}
		if (rideId != null) {
			SosAlert existing = alerts.findFirstByUserIdAndRideIdAndStatusNotOrderByCreatedAtDesc(me, rideId, SosAlert.RESOLVED)
				.filter(a -> a.getCreatedAt().isAfter(now.minus(SAME_ALERT)))
				.orElse(null);
			if (existing != null) {
				if (req.lat() != null && req.lng() != null) {
					existing.updateLocation(req.lat(), req.lng(), req.accuracy());
				}
				return new SosView(existing.getId(), existing.getContactsNotified(), shares.ensureLink(me, rideId),
						existing.getCreatedAt());
			}
		}
		Double lat = req.lat() != null && req.lng() != null ? req.lat() : null;
		Double lng = lat == null ? null : req.lng();
		Integer accuracy = lat == null ? null : req.accuracy();
		if (lat == null && rideId != null) {
			// The phone couldn't send a position with the alert: use the trip link's latest one if it is recent.
			double[] recent = shares.recentLocation(me, rideId, Duration.ofMinutes(10)).orElse(null);
			if (recent != null) {
				lat = recent[0];
				lng = recent[1];
				accuracy = recent[2] < 0 ? null : (int) recent[2];
			}
		}
		SosAlert alert = alerts.save(new SosAlert(me, rideId, lat, lng, accuracy, now));
		String link = rideId == null ? null : shares.ensureLink(me, rideId);
		String name = members.findTraveller(me).map(t -> t.displayName()).orElse("A SaathChalo member");
		String message = "SOS from " + name + " on SaathChalo: they pressed the emergency button"
				+ (ride == null ? "" : " during a trip from " + ride.fromName() + " to " + ride.toName())
				+ (link == null ? "" : ". Follow the trip: " + publicUrl + link)
				+ ". Please call them now, or call 112.";
		int sent = 0;
		for (Recipient r : contacts.recipients(me)) {
			try {
				sms.send(r.phone(), message);
				sent++;
			}
			catch (RuntimeException e) {
				log.error("Could not text an emergency contact for SOS alert {}", alert.getId(), e);
			}
		}
		alert.contactsNotified(sent);
		log.warn("SOS alert {} raised by member {} (ride {}); {} contacts texted", alert.getId(), me, rideId, sent);
		return new SosView(alert.getId(), sent, link, now);
	}

	@Transactional
	public void updateLocation(UUID me, UUID alertId, LocationRequest loc) {
		SosAlert alert = alerts.findById(alertId).filter(a -> a.getUserId().equals(me) && !SosAlert.RESOLVED.equals(a.getStatus()))
			.orElseThrow(() -> ApiException.notFound("alert_not_found", "This alert is closed."));
		alert.updateLocation(loc.lat(), loc.lng(), loc.accuracy());
	}

	// ----- Staff console ------------------------------------------------------------------------------------------

	/** Open alerts (newest first), or the 50 most recently resolved. */
	@Transactional(readOnly = true)
	public List<SosRow> alerts(boolean open) {
		List<SosAlert> list = open ? alerts.findByStatusNotOrderByCreatedAtDesc(SosAlert.RESOLVED)
				: alerts.findByStatusOrderByResolvedAtDesc(SosAlert.RESOLVED, PageRequest.of(0, 50));
		return rows(list);
	}

	@Transactional(readOnly = true)
	public long openCount() {
		return alerts.countByStatusNot(SosAlert.RESOLVED);
	}

	@Transactional
	public SosRow acknowledge(UUID alertId, Actor actor) {
		SosAlert a = find(alertId);
		a.acknowledge(actor.id(), clock.instant());
		audit.record(actor, "sos.acknowledged", "sos", alertId.toString(), "Took charge of an SOS alert", Map.of());
		return rows(List.of(a)).getFirst();
	}

	@Transactional
	public SosRow resolve(UUID alertId, String notes, Actor actor) {
		SosAlert a = find(alertId);
		if (SosAlert.RESOLVED.equals(a.getStatus())) {
			throw ApiException.conflict("already_resolved", "This alert is already resolved.");
		}
		String clean = notes == null || notes.isBlank() ? null : notes.strip();
		a.resolve(actor.id(), clean, clock.instant());
		audit.record(actor, "sos.resolved", "sos", alertId.toString(), "Resolved an SOS alert",
				clean == null ? Map.of() : Map.of("notes", clean));
		return rows(List.of(a)).getFirst();
	}

	private SosAlert find(UUID alertId) {
		return alerts.findById(alertId).orElseThrow(() -> ApiException.notFound("alert_not_found", "We couldn't find that alert."));
	}

	private List<SosRow> rows(List<SosAlert> list) {
		Set<UUID> people = new HashSet<>();
		list.forEach(a -> {
			people.add(a.getUserId());
			if (a.getAcknowledgedBy() != null) {
				people.add(a.getAcknowledgedBy());
			}
			if (a.getResolvedBy() != null) {
				people.add(a.getResolvedBy());
			}
		});
		Map<UUID, MemberCard> cards = members.cards(people);
		Map<UUID, RideBrief> briefs = rides.briefs(list.stream().map(SosAlert::getRideId).filter(id -> id != null).toList());
		List<SosRow> rows = new ArrayList<>();
		for (SosAlert a : list) {
			String near = a.getLat() == null ? null : places.nearest(a.getLat(), a.getLng()).map(Place::label).orElse(null);
			rows.add(new SosRow(a.getId(), a.getStatus(), cards.getOrDefault(a.getUserId(), MemberProfiles.deleted()),
					members.phone(a.getUserId()).map(Masking::phone).orElse(null), briefs.get(a.getRideId()), a.getLat(),
					a.getLng(), a.getAccuracy(), near, a.getContactsNotified(), a.getCreatedAt(), a.getAcknowledgedAt(),
					name(cards, a.getAcknowledgedBy()), a.getResolvedAt(), name(cards, a.getResolvedBy()), a.getNotes()));
		}
		return rows;
	}

	private static String name(Map<UUID, MemberCard> cards, UUID id) {
		return id == null ? null : cards.getOrDefault(id, MemberProfiles.deleted()).displayName();
	}
}
