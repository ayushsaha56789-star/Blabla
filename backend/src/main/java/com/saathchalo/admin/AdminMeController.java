package com.saathchalo.admin;

import com.saathchalo.access.AccessService;
import com.saathchalo.access.Permission;
import com.saathchalo.admin.AdminDtos.AdminMe;
import com.saathchalo.admin.AdminDtos.PermissionInfo;
import com.saathchalo.admin.AdminDtos.RoleResponse;
import com.saathchalo.user.UserService;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminMeController {

	private final AdminContext context;

	private final AccessService access;

	private final UserService users;

	public AdminMeController(AdminContext context, AccessService access, UserService users) {
		this.context = context;
		this.access = access;
		this.users = users;
	}

	/** Who is using the console and what they may do (the console shows only what they're allowed). */
	@GetMapping("/me")
	public AdminMe me() {
		AdminContext.Staff staff = context.current();
		return new AdminMe(staff.userId(), staff.displayName(), users.me(staff.userId()).avatarUrl(),
				staff.membership().roleCode(), staff.membership().roleName(),
				staff.membership().permissions().stream().sorted().toList());
	}

	@GetMapping("/roles")
	public List<RoleResponse> roles() {
		return access.roles().stream()
			.map(r -> new RoleResponse(r.code(), r.name(), r.description(), r.permissions().stream()
				.map(p -> new PermissionInfo(p, Permission.ofCode(p).map(Permission::label).orElse(p)))
				.toList()))
			.toList();
	}
}
