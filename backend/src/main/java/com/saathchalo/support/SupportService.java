package com.saathchalo.support;

import com.saathchalo.audit.Actor;
import com.saathchalo.common.ApiException;
import com.saathchalo.common.Masking;
import com.saathchalo.support.SupportDtos.Category;
import com.saathchalo.support.SupportDtos.MessageView;
import com.saathchalo.support.SupportDtos.NewTicket;
import com.saathchalo.support.SupportDtos.StaffTicketRow;
import com.saathchalo.support.SupportDtos.StaffTicketView;
import com.saathchalo.support.SupportDtos.TicketSummary;
import com.saathchalo.support.SupportDtos.TicketView;
import com.saathchalo.support.internal.Ticket;
import com.saathchalo.support.internal.TicketMessage;
import com.saathchalo.support.internal.TicketMessageRepository;
import com.saathchalo.support.internal.TicketRepository;
import com.saathchalo.user.MemberProfiles;
import com.saathchalo.user.MemberProfiles.MemberCard;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Help requests: members write in, staff answer in the console, and either side can close them. */
@Service
public class SupportService {

	static final int MAX_NEW_PER_DAY = 5;

	static final Duration RESPOND_WITHIN = Duration.ofHours(24);

	private final TicketRepository tickets;

	private final TicketMessageRepository messages;

	private final MemberProfiles members;

	private final Clock clock;

	public SupportService(TicketRepository tickets, TicketMessageRepository messages, MemberProfiles members, Clock clock) {
		this.tickets = tickets;
		this.messages = messages;
		this.members = members;
		this.clock = clock;
	}

	// ----- Members -----

	@Transactional
	public TicketView create(UUID me, NewTicket req) {
		Instant now = clock.instant();
		if (tickets.countByUserIdAndCreatedAtAfter(me, now.minus(Duration.ofDays(1))) >= MAX_NEW_PER_DAY) {
			throw ApiException.tooManyRequests("slow_down",
					"You've sent several requests today. Please add to an open one instead, and we'll reply soon.");
		}
		Ticket t = tickets.save(new Ticket(me, req.category().name(), req.subject().strip(), req.rideId(), now));
		messages.save(new TicketMessage(t.getId(), me, false, req.body().strip(), now));
		return view(t);
	}

	@Transactional(readOnly = true)
	public List<TicketSummary> mine(UUID me) {
		return tickets.findByUserIdOrderByUpdatedAtDesc(me).stream()
			.map(t -> new TicketSummary(t.getId(), Category.valueOf(t.getCategory()), t.getSubject(), t.getStatus(),
					t.hasNewReply(), t.getCreatedAt(), t.getUpdatedAt()))
			.toList();
	}

	/** How many of the member's requests have a reply they haven't read yet. */
	@Transactional(readOnly = true)
	public long answered(UUID me) {
		return tickets.countNewReplies(me);
	}

	/** Opening a request marks support's replies as read. */
	@Transactional
	public TicketView get(UUID me, UUID ticketId) {
		Ticket t = own(me, ticketId);
		t.readByMember(clock.instant());
		return view(t);
	}

	@Transactional
	public TicketView reply(UUID me, UUID ticketId, String body) {
		Ticket t = own(me, ticketId);
		Instant now = clock.instant();
		messages.save(new TicketMessage(t.getId(), me, false, body.strip(), now));
		t.memberReplied(now);
		return view(t);
	}

	@Transactional
	public TicketView close(UUID me, UUID ticketId) {
		Ticket t = own(me, ticketId);
		t.close(clock.instant());
		return view(t);
	}

	// ----- Staff -----

	/** {@code status}: OPEN (waiting for staff, oldest first), ANSWERED, CLOSED, or null for the latest of all. */
	@Transactional(readOnly = true)
	public List<StaffTicketRow> queue(String status) {
		List<Ticket> list = status == null ? tickets.findAllByOrderByUpdatedAtDesc(PageRequest.of(0, 100))
				: tickets.findByStatusOrderByUpdatedAtAsc(status, PageRequest.of(0, 200));
		Map<UUID, MemberCard> cards = members.cards(list.stream().map(Ticket::getUserId).toList());
		return list.stream().map(t -> row(t, cards.getOrDefault(t.getUserId(), MemberProfiles.deleted()))).toList();
	}

	@Transactional(readOnly = true)
	public long waitingForStaff() {
		return tickets.countByStatus(Ticket.OPEN);
	}

	@Transactional(readOnly = true)
	public StaffTicketView staffView(UUID ticketId) {
		Ticket t = find(ticketId);
		return new StaffTicketView(row(t, members.card(t.getUserId())), members.phone(t.getUserId()).map(Masking::phone).orElse(null),
				t.getRideId(), messageViews(t));
	}

	@Transactional
	public StaffTicketView staffReply(UUID ticketId, String body, Actor actor) {
		Ticket t = find(ticketId);
		Instant now = clock.instant();
		messages.save(new TicketMessage(t.getId(), actor.id(), true, body.strip(), now));
		t.staffReplied(actor.id(), now);
		return staffView(ticketId);
	}

	@Transactional
	public StaffTicketView staffClose(UUID ticketId) {
		find(ticketId).close(clock.instant());
		return staffView(ticketId);
	}

	private Ticket own(UUID me, UUID ticketId) {
		return tickets.findById(ticketId).filter(t -> t.getUserId().equals(me))
			.orElseThrow(() -> ApiException.notFound("ticket_not_found", "We couldn't find that request."));
	}

	private Ticket find(UUID ticketId) {
		return tickets.findById(ticketId).orElseThrow(() -> ApiException.notFound("ticket_not_found", "We couldn't find that request."));
	}

	private StaffTicketRow row(Ticket t, MemberCard member) {
		Instant respondBy = t.getCreatedAt().plus(RESPOND_WITHIN);
		boolean overdue = t.getFirstResponseAt() == null && !Ticket.CLOSED.equals(t.getStatus()) && clock.instant().isAfter(respondBy);
		return new StaffTicketRow(t.getId(), member, Category.valueOf(t.getCategory()), t.getSubject(), t.getStatus(),
				t.getCreatedAt(), t.getUpdatedAt(), t.getFirstResponseAt(), respondBy, overdue);
	}

	private TicketView view(Ticket t) {
		return new TicketView(t.getId(), Category.valueOf(t.getCategory()), t.getSubject(), t.getRideId(), t.getStatus(),
				t.getCreatedAt(), t.getUpdatedAt(), t.getFirstResponseAt(), messageViews(t));
	}

	private List<MessageView> messageViews(Ticket t) {
		List<TicketMessage> list = messages.findByTicketIdOrderByCreatedAtAsc(t.getId());
		Set<UUID> authors = new HashSet<>();
		list.forEach(m -> {
			if (m.getAuthorId() != null) authors.add(m.getAuthorId());
		});
		Map<UUID, MemberCard> cards = members.cards(authors);
		return list.stream().map(m -> {
			MemberCard who = m.getAuthorId() == null ? MemberProfiles.deleted() : cards.getOrDefault(m.getAuthorId(), MemberProfiles.deleted());
			boolean named = who.id() != null && !"SaathChalo member".equals(who.displayName());
			String staffName = named ? who.displayName().split(" ")[0] + " from SaathChalo support" : "SaathChalo support";
			return new MessageView(m.getId(), m.isFromStaff(), m.isFromStaff() ? staffName : who.displayName(), m.getBody(),
					m.getCreatedAt());
		}).toList();
	}
}
