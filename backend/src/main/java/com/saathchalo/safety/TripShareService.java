package com.saathchalo.safety;

import com.saathchalo.common.ApiException;
import com.saathchalo.common.Crypto;
import com.saathchalo.common.FieldEncryptor;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.geo.Place;
import com.saathchalo.geo.PlaceDirectory;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.RideParticipants;
import com.saathchalo.rides.RideLookup.RideSnapshot;
import com.saathchalo.rides.RideStatus;
import com.saathchalo.safety.SafetyDtos.CarInfo;
import com.saathchalo.safety.SafetyDtos.LiveLocation;
import com.saathchalo.safety.SafetyDtos.LocationRequest;
import com.saathchalo.safety.SafetyDtos.ShareView;
import com.saathchalo.safety.SafetyDtos.SharedTrip;
import com.saathchalo.safety.internal.SosAlert;
import com.saathchalo.safety.internal.SosAlertRepository;
import com.saathchalo.safety.internal.TripShare;
import com.saathchalo.safety.internal.TripShareRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.vehicles.VehicleInfo;
import com.saathchalo.vehicles.VehicleService;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * "Share my trip": a secret link family can open, without an account, to follow one trip — route, times, driver, car
 * and (if the member turns it on) their latest location. Links stop working 24 hours after arrival, and only the
 * latest location point is ever kept.
 */
@Service
public class TripShareService {

	static final Duration VALID_AFTER_ARRIVAL = Duration.ofHours(24);

	private final TripShareRepository shares;

	private final SosAlertRepository alerts;

	private final RideLookup rides;

	private final MemberProfiles members;

	private final VehicleService vehicles;

	private final PlaceDirectory places;

	private final FieldEncryptor encryptor;

	private final RateLimiter limiter;

	private final Clock clock;

	public TripShareService(TripShareRepository shares, SosAlertRepository alerts, RideLookup rides,
			MemberProfiles members, VehicleService vehicles, PlaceDirectory places, FieldEncryptor encryptor,
			RateLimiter limiter, Clock clock) {
		this.shares = shares;
		this.alerts = alerts;
		this.rides = rides;
		this.members = members;
		this.vehicles = vehicles;
		this.places = places;
		this.encryptor = encryptor;
		this.limiter = limiter;
		this.clock = clock;
	}

	/** The member's link for a ride, created on first use. */
	@Transactional
	public ShareView share(UUID me, UUID rideId) {
		RideParticipants trip = onTrip(me, rideId);
		Instant now = clock.instant();
		if (trip.ride().status() == RideStatus.CANCELLED || !now.isBefore(trip.ride().arrivalAt().plus(Duration.ofHours(6)))) {
			throw ApiException.conflict("trip_over", "This trip is over, so there's nothing to share.");
		}
		TripShare share = active(me, rideId, now).orElseGet(() -> {
			String token = Crypto.randomUrlSafeToken(24);
			return shares.save(new TripShare(Crypto.sha256Hex(token), encryptor.encrypt(token), rideId, me, now,
					trip.ride().arrivalAt().plus(VALID_AFTER_ARRIVAL)));
		});
		return view(share);
	}

	@Transactional(readOnly = true)
	public Optional<ShareView> current(UUID me, UUID rideId) {
		return active(me, rideId, clock.instant()).map(this::view);
	}

	@Transactional
	public void stop(UUID me, UUID rideId) {
		shares.findByRideIdAndUserIdAndRevokedAtIsNull(rideId, me).ifPresent(s -> s.revoke(clock.instant()));
	}

	/** The member's phone sends its position every half a minute or so while live sharing is on. */
	@Transactional
	public ShareView updateLocation(UUID me, UUID rideId, LocationRequest loc) {
		Instant now = clock.instant();
		TripShare share = active(me, rideId, now)
			.orElseThrow(() -> ApiException.conflict("not_sharing", "Turn on trip sharing first."));
		if (!limiter.tryAcquire("share-location:" + me, 12, Duration.ofMinutes(1))) {
			throw ApiException.tooManyRequests("slow_down", "Location updates are coming too fast.");
		}
		share.updateLocation(loc.lat(), loc.lng(), loc.accuracy(), now);
		// Support sees a moving location on an open SOS alert too.
		alerts.findFirstByUserIdAndRideIdAndStatusNotOrderByCreatedAtDesc(me, rideId, SosAlert.RESOLVED)
			.ifPresent(a -> a.updateLocation(loc.lat(), loc.lng(), loc.accuracy()));
		return view(share);
	}

	/** What a family member sees when they open the link. Public: the secret link is the key. */
	@Transactional(readOnly = true)
	public SharedTrip open(String token) {
		Instant now = clock.instant();
		TripShare share = (token == null || token.length() > 100 ? Optional.<TripShare>empty()
				: shares.findByTokenHash(Crypto.sha256Hex(token)))
			.filter(s -> s.isActive(now))
			.orElseThrow(() -> ApiException.notFound("trip_link_expired",
					"This trip link has expired or was turned off by the traveller."));
		RideSnapshot snap = rides.snapshot(share.getRideId())
			.orElseThrow(() -> ApiException.notFound("trip_link_expired", "This trip is no longer available."));
		VehicleInfo car = vehicles.infos(List.of(snap.vehicleId())).get(snap.vehicleId());
		CarInfo carInfo = car == null ? null
				: new CarInfo(car.make(), car.model(), car.color(), vehicles.plate(car.id()).orElse(null));
		LiveLocation live = null;
		if (share.getLiveAt() != null && share.getLiveLat() != null) {
			String near = places.nearest(share.getLiveLat(), share.getLiveLng()).map(Place::label).orElse(null);
			live = new LiveLocation(share.getLiveLat(), share.getLiveLng(), share.getLiveAccuracy(), share.getLiveAt(), near);
		}
		Instant sosAt = alerts.findFirstByUserIdAndRideIdAndStatusNotOrderByCreatedAtDesc(share.getUserId(),
				share.getRideId(), SosAlert.RESOLVED).map(SosAlert::getCreatedAt).orElse(null);
		String firstName = members.findTraveller(share.getUserId()).map(t -> t.displayName()).orElse("A SaathChalo member");
		return new SharedTrip(firstName, snap.ride().driverId().equals(share.getUserId()), snap.ride(), snap.stops(),
				members.card(snap.ride().driverId()), carInfo, live, sosAt, share.getExpiresAt());
	}

	/**
	 * The link path for a member's share on a ride, creating one if needed. Used by SOS, which must never fail, so
	 * this returns null instead of an error (for example when the trip is already over).
	 */
	@Transactional
	String ensureLink(UUID me, UUID rideId) {
		try {
			return share(me, rideId).path();
		}
		catch (ApiException e) {
			return null;
		}
	}

	/** The member's latest shared position on this ride, if it is recent ({@code [lat, lng, accuracy]}). */
	@Transactional(readOnly = true)
	Optional<double[]> recentLocation(UUID me, UUID rideId, Duration maxAge) {
		Instant now = clock.instant();
		return active(me, rideId, now)
			.filter(s -> s.getLiveAt() != null && s.getLiveLat() != null && s.getLiveAt().isAfter(now.minus(maxAge)))
			.map(s -> new double[] { s.getLiveLat(), s.getLiveLng(), s.getLiveAccuracy() == null ? -1 : s.getLiveAccuracy() });
	}

	private Optional<TripShare> active(UUID me, UUID rideId, Instant now) {
		return shares.findByRideIdAndUserIdAndRevokedAtIsNull(rideId, me).filter(s -> s.isActive(now));
	}

	/** Only the driver and passengers with a confirmed seat can share a trip. */
	RideParticipants onTrip(UUID me, UUID rideId) {
		RideParticipants trip = rides.participants(rideId)
			.orElseThrow(() -> ApiException.notFound("ride_not_found", "We couldn't find that ride."));
		if (!trip.isDriver(me) && !trip.isConfirmedPassenger(me)) {
			throw ApiException.forbidden("not_on_trip", "Only the driver and passengers with a confirmed seat can do this.");
		}
		return trip;
	}

	private ShareView view(TripShare s) {
		return new ShareView("/trip/" + encryptor.decrypt(s.getTokenEnc()), s.getExpiresAt(), s.getLiveAt());
	}
}
