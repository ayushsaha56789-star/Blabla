package com.saathchalo.chat;

import com.saathchalo.chat.ChatDtos.ConversationView;
import com.saathchalo.chat.ChatDtos.InboxItem;
import com.saathchalo.chat.ChatDtos.MessageView;
import com.saathchalo.chat.ChatDtos.OpenRequest;
import com.saathchalo.chat.internal.ChatMessage;
import com.saathchalo.chat.internal.ChatMessageRepository;
import com.saathchalo.chat.internal.Conversation;
import com.saathchalo.chat.internal.ConversationRepository;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.RateLimiter;
import com.saathchalo.rides.RideLookup;
import com.saathchalo.rides.RideLookup.RideBrief;
import com.saathchalo.rides.RideLookup.RideParticipants;
import com.saathchalo.rides.RideLookup.Seat;
import com.saathchalo.rides.RideStatus;
import com.saathchalo.user.BlockService;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import com.saathchalo.user.MemberProfiles.Traveller;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Chat between a ride's driver and one passenger. Anyone who could book a ride can ask the driver a question first;
 * the driver can message anyone who booked. Phone numbers stay private until a seat is confirmed, so chat is the
 * safe way to sort out pickup points and luggage. Staff can't read chats, only messages someone reports.
 */
@Service
public class ChatService {

	static final int MAX_NEW_CHATS_PER_DAY = 20;

	/** A chat stays open for a week after the trip, for lost-and-found and thank-yous. */
	static final Duration OPEN_AFTER_ARRIVAL = Duration.ofDays(7);

	private static final String UNREAD = """
			(SELECT count(*) FROM chat_messages m
			 WHERE m.conversation_id = c.id AND m.sender_id <> ? AND m.hidden_at IS NULL
			   AND m.created_at > COALESCE(CASE WHEN c.driver_id = ? THEN c.driver_read_at ELSE c.passenger_read_at END,
			                               '-infinity'::timestamptz))""";

	private final ConversationRepository conversations;

	private final ChatMessageRepository messages;

	private final RideLookup rides;

	private final MemberProfiles members;

	private final RateLimiter limiter;

	private final BlockService blocks;

	private final JdbcTemplate jdbc;

	private final Clock clock;

	public ChatService(ConversationRepository conversations, ChatMessageRepository messages, RideLookup rides,
			MemberProfiles members, RateLimiter limiter, BlockService blocks, JdbcTemplate jdbc, Clock clock) {
		this.conversations = conversations;
		this.messages = messages;
		this.rides = rides;
		this.members = members;
		this.limiter = limiter;
		this.blocks = blocks;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/** Finds or starts the chat about a ride. */
	@Transactional
	public ConversationView open(UUID me, OpenRequest req) {
		RideParticipants ride = rides.participants(req.rideId()).orElseThrow(ChatService::rideNotFound);
		UUID passengerId;
		if (ride.isDriver(me)) {
			if (req.passengerId() == null || !ride.hasBooking(req.passengerId())) {
				throw ApiException.badRequest("not_a_passenger", "You can message people who have booked this ride.");
			}
			passengerId = req.passengerId();
		}
		else {
			passengerId = me;
		}
		UUID other = ride.isDriver(me) ? passengerId : ride.ride().driverId();
		if (blocks.blockedEitherWay(me, other)) {
			throw ApiException.forbidden("blocked", "You can't message this member.");
		}
		Conversation conversation = conversations.findByRideIdAndPassengerId(ride.ride().id(), passengerId)
			.orElseGet(() -> {
				if (!ride.isDriver(me)) {
					checkCanAsk(me, ride);
				}
				return conversations.save(new Conversation(ride.ride().id(), ride.ride().driverId(), passengerId,
						clock.instant()));
			});
		return view(conversation, me, null, ride);
	}

	/** One chat with its latest messages (or only those after {@code after}); marks it read. */
	@Transactional
	public ConversationView conversation(UUID me, UUID conversationId, Instant after) {
		Conversation c = conversations.findById(conversationId).filter(x -> x.isParticipant(me))
			.orElseThrow(ChatService::chatNotFound);
		RideParticipants ride = rides.participants(c.getRideId()).orElseThrow(ChatService::chatNotFound);
		return view(c, me, after, ride);
	}

	@Transactional
	public MessageView send(UUID me, UUID conversationId, String raw) {
		Conversation c = conversations.lockById(conversationId).filter(x -> x.isParticipant(me))
			.orElseThrow(ChatService::chatNotFound);
		RideParticipants ride = rides.participants(c.getRideId()).orElseThrow(ChatService::chatNotFound);
		String closed = cannotSendReason(ride.ride(), me, c.otherThan(me));
		if (closed != null) {
			throw ApiException.conflict("chat_closed", closed);
		}
		String body = raw == null ? "" : raw.strip().replaceAll("\\n{3,}", "\n\n");
		if (body.isEmpty()) {
			throw ApiException.badRequest("empty_message", "Type a message first.");
		}
		if (!limiter.tryAcquire("chat-send:" + me, 60, Duration.ofHours(1))) {
			throw ApiException.tooManyRequests("slow_down", "You've sent a lot of messages. Please wait a little.");
		}
		Instant now = clock.instant();
		ChatMessage m = messages.save(new ChatMessage(c.getId(), me, body, now));
		c.messageSent(now);
		c.markRead(me, now);
		return toView(m, me);
	}

	@Transactional(readOnly = true)
	public List<InboxItem> inbox(UUID me) {
		record Row(UUID id, UUID rideId, UUID driverId, UUID passengerId, Instant lastAt, int unread, String lastBody,
				UUID lastSender, boolean lastHidden) {
		}
		List<Row> rows = jdbc.query("""
				SELECT c.id, c.ride_id, c.driver_id, c.passenger_id, c.last_message_at, %s AS unread,
				       lm.body, lm.sender_id, lm.hidden_at
				FROM conversations c
				LEFT JOIN LATERAL (SELECT m.body, m.sender_id, m.hidden_at FROM chat_messages m
				                   WHERE m.conversation_id = c.id ORDER BY m.created_at DESC LIMIT 1) lm ON TRUE
				WHERE (c.driver_id = ? OR c.passenger_id = ?) AND c.last_message_at IS NOT NULL
				ORDER BY c.last_message_at DESC
				LIMIT 100
				""".formatted(UNREAD), (rs, n) -> new Row(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
				rs.getObject(3, UUID.class), rs.getObject(4, UUID.class), rs.getTimestamp(5).toInstant(), rs.getInt(6),
				rs.getString(7), rs.getObject(8, UUID.class), rs.getTimestamp(9) != null), me, me, me, me);
		Map<UUID, RideBrief> briefs = rides.briefs(rows.stream().map(Row::rideId).toList());
		Map<UUID, MemberCard> cards = members.cards(rows.stream().map(r -> r.driverId().equals(me) ? r.passengerId() : r.driverId()).toList());
		List<InboxItem> items = new ArrayList<>();
		for (Row r : rows) {
			RideBrief ride = briefs.get(r.rideId());
			if (ride == null) {
				continue;
			}
			UUID other = r.driverId().equals(me) ? r.passengerId() : r.driverId();
			items.add(new InboxItem(r.id(), ride, cards.getOrDefault(other, MemberProfiles.deleted()), !r.driverId().equals(me),
					r.lastHidden() ? "Message removed by SaathChalo" : preview(r.lastBody()), me.equals(r.lastSender()),
					r.lastAt(), r.unread()));
		}
		return items;
	}

	/** How many chats have messages this member hasn't read (for the badge). */
	@Transactional(readOnly = true)
	public long unreadConversations(UUID me) {
		Long n = jdbc.queryForObject("SELECT count(*) FROM conversations c WHERE (c.driver_id = ? OR c.passenger_id = ?) AND "
				+ UNREAD + " > 0", Long.class, me, me, me, me);
		return n == null ? 0 : n;
	}

	private ConversationView view(Conversation c, UUID me, Instant after, RideParticipants ride) {
		List<ChatMessage> list;
		if (after == null) {
			list = new ArrayList<>(messages.findTop200ByConversationIdOrderByCreatedAtDesc(c.getId()));
			Collections.reverse(list);
		}
		else {
			list = messages.findByConversationIdAndCreatedAtAfterOrderByCreatedAtAsc(c.getId(), after);
		}
		c.markRead(me, clock.instant());
		Seat seat = ride.passengers().get(c.getPassengerId());
		String reason = cannotSendReason(ride.ride(), me, c.otherThan(me));
		return new ConversationView(c.getId(), ride.ride(), members.card(c.otherThan(me)), !c.isDriver(me),
				seat == null ? null : seat.status().name(), reason == null, reason, c.readByOtherAt(me),
				list.stream().map(m -> toView(m, me)).toList());
	}

	private void checkCanAsk(UUID me, RideParticipants ride) {
		Instant now = clock.instant();
		if (ride.ride().status() != RideStatus.SCHEDULED || !ride.ride().arrivalAt().isAfter(now)) {
			throw ApiException.conflict("ride_not_available", "This ride is no longer open for questions.");
		}
		if (ride.ride().ladiesOnly() && !members.findTraveller(me).map(Traveller::isWoman).orElse(false)) {
			throw ApiException.forbidden("ladies_only", "This ride is for women only.");
		}
		if (conversations.countByPassengerIdAndCreatedAtAfter(me, now.minus(Duration.ofDays(1))) >= MAX_NEW_CHATS_PER_DAY) {
			throw ApiException.tooManyRequests("slow_down",
					"You've started a lot of chats today. Please continue the ones you have, or try tomorrow.");
		}
	}

	private String cannotSendReason(RideBrief ride, UUID me, UUID other) {
		if (clock.instant().isAfter(ride.arrivalAt().plus(OPEN_AFTER_ARRIVAL))) {
			return "This chat closed a week after the trip.";
		}
		if (blocks.blockedEitherWay(me, other)) {
			return "You can't message each other any more.";
		}
		return null;
	}

	/** A message someone received and reports (only the other person's messages can be reported). */
	public record ReportedMessage(UUID messageId, UUID senderId, String body, UUID rideId) {
	}

	@Transactional(readOnly = true)
	public java.util.Optional<ReportedMessage> messageForReport(UUID reporterId, UUID messageId) {
		return messages.findById(messageId)
			.filter(m -> !m.getSenderId().equals(reporterId))
			.flatMap(m -> conversations.findById(m.getConversationId())
				.filter(c -> c.isParticipant(reporterId))
				.map(c -> new ReportedMessage(m.getId(), m.getSenderId(), m.getBody(), c.getRideId())));
	}

	/** Staff remove a reported message; both people then see "Message removed by SaathChalo". */
	@Transactional
	public boolean hideMessage(UUID messageId) {
		return messages.findById(messageId).map(m -> {
			m.hide(clock.instant());
			return true;
		}).orElse(false);
	}

	private static MessageView toView(ChatMessage m, UUID me) {
		boolean hidden = m.getHiddenAt() != null;
		return new MessageView(m.getId(), m.getSenderId().equals(me), hidden ? null : m.getBody(), m.getCreatedAt(), hidden);
	}

	private static String preview(String body) {
		if (body == null) {
			return "";
		}
		String oneLine = body.replaceAll("\\s+", " ").strip();
		return oneLine.length() > 90 ? oneLine.substring(0, 89) + "…" : oneLine;
	}

	private static ApiException rideNotFound() {
		return ApiException.notFound("ride_not_found", "We couldn't find that ride.");
	}

	private static ApiException chatNotFound() {
		return ApiException.notFound("chat_not_found", "We couldn't find that chat.");
	}
}
