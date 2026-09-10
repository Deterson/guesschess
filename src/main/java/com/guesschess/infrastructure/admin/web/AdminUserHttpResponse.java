package com.guesschess.infrastructure.admin.web;

record AdminUserHttpResponse(String id, String login, String displayName, String email, String createdAt) {
}
