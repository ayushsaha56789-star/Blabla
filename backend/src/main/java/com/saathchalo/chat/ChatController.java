package com.saathchalo.chat;

import com.saathchalo.chat.ChatDtos.ConversationView;
import com.saathchalo.chat.ChatDtos.InboxItem;
import com.saathchalo.chat.ChatDtos.MessageView;
import com.saathchalo.chat.ChatDtos.OpenRequest;
import com.saathchalo.chat.ChatDtos.SendRequest;
import com.saathchalo.chat.ChatDtos.UnreadCount;
import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chats")
@RequiresFeature("chat")
public class ChatController {

	private final ChatService chats;

	public ChatController(ChatService chats) {
		this.chats = chats;
	}

	@PostMapping
	public ConversationView open(@Valid @RequestBody OpenRequest request) {
		return chats.open(CurrentUser.require(), request);
	}

	@GetMapping
	public List<InboxItem> inbox() {
		return chats.inbox(CurrentUser.require());
	}

	@GetMapping("/unread")
	public UnreadCount unread() {
		return new UnreadCount(chats.unreadConversations(CurrentUser.require()));
	}

	/** {@code after}: only messages newer than this (the app polls with the time of the last message it has). */
	@GetMapping("/{id}")
	public ConversationView conversation(@PathVariable UUID id, @RequestParam(required = false) Instant after) {
		return chats.conversation(CurrentUser.require(), id, after);
	}

	@PostMapping("/{id}/messages")
	@ResponseStatus(HttpStatus.CREATED)
	public MessageView send(@PathVariable UUID id, @Valid @RequestBody SendRequest request) {
		return chats.send(CurrentUser.require(), id, request.body());
	}
}
