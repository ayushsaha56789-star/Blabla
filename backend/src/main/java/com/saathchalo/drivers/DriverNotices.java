package com.saathchalo.drivers;

import com.saathchalo.config.AppConfig;
import com.saathchalo.drivers.internal.DriverTrip;
import com.saathchalo.notifications.Notice;
import com.saathchalo.notifications.Notifier;
import com.saathchalo.user.MemberProfiles;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Notices about hired-driver trips: new requests, answers and cancellations. */
@Component
class DriverNotices {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

	private final Notifier notifier;

	private final MemberProfiles members;

	DriverNotices(Notifier notifier, MemberProfiles members) {
		this.notifier = notifier;
		this.members = members;
	}

	/** To the driver: a member wants to hire them. */
	void requested(DriverTrip t) {
		notifier.send(t.getDriverId(), Notice.trips("steering", name(t.getCustomerId()) + " wants to hire you",
				trip(t) + ". Please accept or decline soon.", link(t)));
	}

	/** To the member: the driver's answer. */
	void answered(DriverTrip t, boolean accepted) {
		String driver = name(t.getDriverId());
		notifier.send(t.getCustomerId(), accepted
				? Notice.trips("checkCircle", driver + " accepted your trip",
						trip(t) + (t.isPayOnline() ? ". Please pay now to confirm it." : "."), link(t))
				: Notice.trips("close", driver + " can't drive for you then", trip(t) + ". Try another driver.", "/drivers"));
	}

	/** To the other side (or both, when our team cancelled). */
	void cancelled(DriverTrip t, String by, String reason) {
		String why = reason == null || reason.isBlank() ? "" : ". Reason: " + reason.strip();
		switch (by) {
			case "DRIVER" -> notifier.send(t.getCustomerId(),
					Notice.trips("close", name(t.getDriverId()) + " cancelled your trip", trip(t) + why + ". Any online payment is refunded.", link(t)));
			case "CUSTOMER" -> notifier.send(t.getDriverId(),
					Notice.trips("close", name(t.getCustomerId()) + " cancelled the trip", trip(t) + why, link(t)));
			default -> {
				Notice notice = Notice.trips("close", "Our team cancelled a hired-driver trip", trip(t) + why, link(t));
				notifier.send(t.getCustomerId(), notice);
				notifier.send(t.getDriverId(), notice);
			}
		}
	}

	private String name(UUID id) {
		return members.card(id).displayName();
	}

	private static String trip(DriverTrip t) {
		String where = t.getDestinationName() == null ? t.getPlaceName() : t.getPlaceName() + " → " + t.getDestinationName();
		return where + ", " + WHEN.format(t.getStartsAt().atZone(AppConfig.ZONE));
	}

	private static String link(DriverTrip t) {
		return "/drivers/trips/" + t.getId();
	}
}
