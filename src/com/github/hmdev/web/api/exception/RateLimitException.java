package com.github.hmdev.web.api.exception;

/**
 * レート制限超過 (HTTP 503等)
 */
public class RateLimitException extends ApiException {
	private static final long serialVersionUID = 1L;

	public RateLimitException(String message) {
		super(message);
	}

	public RateLimitException(String message, Throwable cause) {
		super(message, cause);
	}
}
