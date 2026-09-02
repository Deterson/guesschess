package com.guesschess.infrastructure.account.web;

record CompleteRegistrationHttpRequest(String pendingToken, String login) {
}
