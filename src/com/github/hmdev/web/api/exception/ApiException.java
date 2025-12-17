package com.github.hmdev.web.api.exception;

/**
 * API呼び出し時の基底例外クラス
 */
public class ApiException extends Exception {
	private static final long serialVersionUID = 1L;
	
	public ApiException(String message) {
		super(message);
	}
	
	public ApiException(String message, Throwable cause) {
		super(message, cause);
	}
}
