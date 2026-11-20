package com.saathchalo.admin;

import com.saathchalo.audit.AuditEntryView;
import com.saathchalo.features.Audience;
import com.saathchalo.rides.RideDtos.RideStats;
import com.saathchalo.user.Gender;
import com.saathchalo.user.MemberDirectory.AreaOption;
import com.saathchalo.user.MemberDirectory.MemberDetails;
import com.saathchalo.user.MemberDirectory.MemberStats;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class AdminDtos {

	private AdminDtos() {
	}

	public record AdminMe(UUID userId, String displayName, String avatarUrl, String role, String roleName,
			List<String> permissions) {
	}

	public record PermissionInfo(String code, String label) {
	}

	public record RoleResponse(String code, String name, String description, List<PermissionInfo> permissions) {
	}

	public record Dashboard(MemberStats members, RideStats rides, long openSosAlerts, long openReports,
			long overdueReports, long ticketsWaiting, long idChecksWaiting, long staffCount, long featuresOn, long featuresTotal,
			boolean maintenance, boolean signupsOpen, List<AuditEntryView> recentActivity) {
	}

	public record CancelRideRequest(
			@NotBlank(message = "Please give a reason. The driver and passengers will see it.")
			@Size(min = 3, max = 300, message = "Use 3 to 300 characters") String reason) {
	}

	public record MemberRow(UUID id, String displayName, String maskedPhone, String avatarUrl, Gender gender,
			String city, Instant memberSince, boolean betaTester, boolean suspended, List<String> restrictedAreas,
			String staffRole, String staffRoleName) {
	}

	public record MemberDetailResponse(MemberDetails member, String staffRole, String staffRoleName,
			List<AuditEntryView> activity, List<AreaOption> areas) {
	}

	public record PhoneResponse(String phone) {
	}

	public record RestrictRequest(
			@NotBlank(message = "Please choose what to restrict") String area,
			@NotBlank(message = "Please give a reason. The member will see it.")
			@Size(min = 3, max = 500, message = "Use 3 to 500 characters") String reason,
			LocalDate until) {
	}

	public record BetaTesterRequest(boolean betaTester) {
	}

	public record FeatureRow(String key, String parent, String area, String label, String description, int phase,
			boolean built, boolean critical, boolean global, boolean enabled, Audience audience, List<String> cities,
			Instant updatedAt, String updatedByName) {
	}

	public record FeatureUpdateRequest(boolean enabled, Audience audience, List<String> cities) {
	}

	public record StaffRow(UUID userId, String displayName, String maskedPhone, String avatarUrl, String role,
			String roleName, boolean totpEnabled, Instant addedAt, Instant lastActiveAt, boolean you) {
	}

	public record AddStaffRequest(@NotBlank(message = "Please enter their mobile number") String phone,
			@NotBlank(message = "Please choose a role") String role) {
	}

	public record ChangeRoleRequest(@NotBlank(message = "Please choose a role") String role) {
	}
}
