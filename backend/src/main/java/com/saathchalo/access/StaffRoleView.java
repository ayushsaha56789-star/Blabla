package com.saathchalo.access;

import java.util.List;

public record StaffRoleView(String code, String name, String description, int rank, List<String> permissions) {

	public static final String SUPER_ADMIN = "SUPER_ADMIN";
}
