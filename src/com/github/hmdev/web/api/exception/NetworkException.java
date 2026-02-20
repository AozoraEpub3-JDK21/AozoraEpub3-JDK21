package com.github.hmdev.web.api.exception;

/**
 * ネットワーク通信エラー
 */
public class NetworkException extends ApiException {
	private static final long serialVersionUID = 1L;

	public NetworkException(String message) {
		super(message);
	}

	public NetworkException(String message, Throwable cause) {
		super(message, cause);
	}
}
