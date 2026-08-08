package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ビューアーの表示設定 (テーマ・フォント・余白・行間) を永続化する。
 *
 * <p>ブラウザの localStorage はスキーム + ホスト + <b>ポート</b> で分離される。
 * プレビューサーバは毎回ランダムポートで起動するため、localStorage だけに保存すると
 * 設定は起動のたびに失われる。そのためサーバ側のファイルを正とする。</p>
 *
 * <p>保存先はユーザーのホーム配下に置く。アプリの配置場所が読み取り専用でも書けるようにするため。</p>
 */
public class PreviewSettingsStore
{
	private static final Logger logger = LoggerFactory.getLogger(PreviewSettingsStore.class);

	/** 設定 JSON の上限。表示設定にこれ以上の大きさは必要ない */
	static final int MAX_BYTES = 64 * 1024;

	private final Path file;

	public PreviewSettingsStore()
	{
		this(Path.of(System.getProperty("user.home", "."), ".aozoraepub3", "preview-settings.json"));
	}

	PreviewSettingsStore(Path file)
	{
		this.file = file;
	}

	Path getFile() { return this.file; }

	/** 保存済みの設定 JSON を返す。無ければ空オブジェクト */
	public String load()
	{
		try {
			if (!Files.isRegularFile(this.file)) return "{}";
			if (Files.size(this.file) > MAX_BYTES) return "{}";
			String json = Files.readString(this.file, StandardCharsets.UTF_8).trim();
			return isJsonObject(json) ? json : "{}";
		} catch (IOException e) {
			/* 意図的: 読めなければ既定値で動かす */
			logger.debug("プレビュー設定の読み込みに失敗しました: {}", this.file, e);
			return "{}";
		}
	}

	/**
	 * 設定 JSON を保存する。
	 *
	 * @return 保存できたら true
	 */
	public boolean save(String json)
	{
		if (json == null) return false;
		String trimmed = json.trim();
		if (!isJsonObject(trimmed)) return false;
		if (trimmed.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) return false;
		try {
			Path parent = this.file.getParent();
			if (parent != null) Files.createDirectories(parent);
			Files.writeString(this.file, trimmed, StandardCharsets.UTF_8);
			return true;
		} catch (IOException e) {
			/* 意図的: 保存できなくてもプレビューの利用は続けられる */
			logger.debug("プレビュー設定の保存に失敗しました: {}", this.file, e);
			return false;
		}
	}

	/** JSON オブジェクトの形をしているか (パーサを持たないための最小限の検査) */
	static boolean isJsonObject(String json)
	{
		return json != null && json.length() >= 2 && json.startsWith("{") && json.endsWith("}");
	}
}
