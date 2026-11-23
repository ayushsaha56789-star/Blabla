package com.saathchalo.notifications;

import com.saathchalo.common.CurrentUser;
import com.saathchalo.features.RequiresFeature;
import com.saathchalo.notifications.NotificationDtos.Inbox;
import com.saathchalo.notifications.NotificationDtos.MarkRead;
import com.saathchalo.notifications.NotificationDtos.Settings;
import com.saathchalo.notifications.NotificationDtos.SettingsRequest;
import com.saathchalo.notifications.NotificationDtos.Unread;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** The member's notification inbox. */
@RestController
@RequestMapping("/api/notifications")
@RequiresFeature("notifications")
class NotificationController {

	private final NotificationService notifications;

	NotificationController(NotificationService notifications) {
		this.notifications = notifications;
	}

	@GetMapping
	Inbox inbox(@RequestParam(required = false) Long before) {
		return notifications.inbox(CurrentUser.require(), before);
	}

	@GetMapping("/unread")
	Unread unread() {
		return new Unread(notifications.unread(CurrentUser.require()));
	}

	@PostMapping("/read")
	ResponseEntity<Void> markRead(@Valid @RequestBody MarkRead request) {
		notifications.markRead(CurrentUser.require(), request.ids(), Boolean.TRUE.equals(request.all()));
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/settings")
	Settings settings() {
		return notifications.settings(CurrentUser.require());
	}

	@PutMapping("/settings")
	Settings saveSettings(@Valid @RequestBody SettingsRequest request) {
		return notifications.saveSettings(CurrentUser.require(), request.muted());
	}
}
