package com.saathchalo.chat;

import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.user.MemberProfiles.MemberCard;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class ChatDtos {

	private ChatDtos() {
	}

	/** Opens (or finds) the chat about a ride. The driver names the passenger; anyone else chats as themselves. */
	public record OpenRequest(@NotNull(message = "Which ride?") UUID rideId, UUID passengerId) {
	}

	public record SendRequest(
			@NotBlank(message = "Type a message first")
			@Size(max = 1000, message = "Keep messages under 1,000 characters") String body) {
	}

	/** {@code hidden}: removed by staff after a report (the text is not sent). */
	public record MessageView(UUID id, boolean mine, String body, Instant createdAt, boolean hidden) {
	}

	/**
	 * One chat. {@code otherIsDriver}: whether the other person drives. {@code passengerBooking}: the passenger's
	 * booking on the ride (null if they haven't booked). {@code seenAt}: when the other person last read the chat.
	 */
	public record ConversationView(UUID id, RideBrief ride, MemberCard other, boolean otherIsDriver,
			String passengerBooking, boolean canSend, String cannotSendReason, Instant seenAt,
			List<MessageView> messages) {
	}

	public record InboxItem(UUID id, RideBrief ride, MemberCard other, boolean otherIsDriver, String lastMessage,
			boolean lastFromMe, Instant lastMessageAt, int unread) {
	}

	public record UnreadCount(long conversations) {
	}
}
