package com.saathchalo.rentals;

import com.saathchalo.config.AppConfig;
import com.saathchalo.notifications.Notice;
import com.saathchalo.notifications.Notifier;
import com.saathchalo.rentals.internal.RentalBooking;
import com.saathchalo.user.MemberProfiles;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Notices about car rentals: new requests, answers and cancellations. */
@Component
class RentalNotices {

	private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("EEE d MMM, h:mm a", Locale.ENGLISH);

	private final Notifier notifier;

	private final MemberProfiles members;

	RentalNotices(Notifier notifier, MemberProfiles members) {
		this.notifier = notifier;
		this.members = members;
	}

	/** To the owner: someone booked the car straight away, or asked to rent it. */
	void requested(RentalBooking b) {
		String who = name(b.getRenterId());
		notifier.send(b.getOwnerId(), RentalBooking.REQUESTED.equals(b.getStatus())
				? Notice.trips("car", who + " wants to rent your car", period(b) + ". Please accept or decline soon.", link(b))
				: Notice.trips("checkCircle", who + " booked your car", period(b), link(b)));
	}

	/** To the renter: the owner's answer. */
	void answered(RentalBooking b, boolean accepted) {
		String owner = name(b.getOwnerId());
		notifier.send(b.getRenterId(), accepted
				? Notice.trips("checkCircle", owner + " accepted your rental",
						period(b) + (b.isPayOnline() ? ". Please pay now to confirm it." : "."), link(b))
				: Notice.trips("close", owner + " can't rent the car then", period(b) + ". Try another car.", "/rentals"));
	}

	/** To the other side (or both, when our team cancelled). */
	void cancelled(RentalBooking b, String by) {
		switch (by) {
			case "RENTER" -> notifier.send(b.getOwnerId(),
					Notice.trips("close", name(b.getRenterId()) + " cancelled their rental", period(b), link(b)));
			case "OWNER" -> notifier.send(b.getRenterId(), Notice.trips("close", name(b.getOwnerId()) + " cancelled your rental",
					period(b) + ". Any online payment is refunded.", link(b)));
			default -> {
				Notice notice = Notice.trips("close", "Our team cancelled a rental", period(b) + ". Everything paid is refunded.", link(b));
				notifier.send(b.getRenterId(), notice);
				notifier.send(b.getOwnerId(), notice);
			}
		}
	}

	private String name(UUID id) {
		return members.card(id).displayName();
	}

	private static String period(RentalBooking b) {
		return WHEN.format(b.getStartsAt().atZone(AppConfig.ZONE)) + " to " + WHEN.format(b.getEndsAt().atZone(AppConfig.ZONE));
	}

	private static String link(RentalBooking b) {
		return "/rentals/bookings/" + b.getId();
	}
}
