package com.rasoiroute.api;

/** What the API answers: an HTTP status code and a body (a Map or List that Json.write can turn into text). */
public record ApiResponse(int status, Object body) {
}
