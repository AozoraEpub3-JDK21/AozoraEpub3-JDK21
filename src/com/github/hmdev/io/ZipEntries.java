package com.github.hmdev.io;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.apache.commons.compress.archivers.zip.ZipFile;

/**
 * zip の項目を、ファイルの中の順に 1 つずつ渡す（internal #36）。
 *
 * <p>目次（セントラルディレクトリ）から開くので、中身を読むのは開いた項目だけ。
 * 先頭から順に読む形（ZipArchiveInputStream）は、大きさを後ろに書く項目（データ記述子。Java の ZipOutputStream が作る）を
 * 飛ばすだけでも展開が要り、壊れたテキストが 1 つあると、その後ろの画像まで読めなかった。
 * 目次が壊れていて開けない zip だけは、前と同じく先頭から順に読む。</p>
 */
public final class ZipEntries
{
	private ZipEntries() {}

	/** 項目の中身を開く（呼んだときだけ展開する） */
	public interface Opener
	{
		InputStream open() throws IOException;
	}

	/** 項目を 1 つ受け取る。false を返すと、そこで止める */
	public interface Visitor
	{
		boolean visit(String name, Opener opener) throws IOException;
	}

	/**
	 * @param useUnicodeExtraFields ZipArchiveInputStream の同じ名前の設定（呼ぶ側の前の値を保つ）
	 */
	public static void forEach(Path zipPath, boolean useUnicodeExtraFields, Visitor visitor) throws IOException
	{
		ZipFile zipFile;
		try {
			zipFile = ZipFile.builder().setPath(zipPath).setCharset("MS932").setUseUnicodeExtraFields(useUnicodeExtraFields).get();
		} catch (IOException e) {
			forEachInStream(zipPath, useUnicodeExtraFields, visitor);
			return;
		}
		try (zipFile) {
			for (ZipArchiveEntry entry : Collections.list(zipFile.getEntriesInPhysicalOrder())) {
				if (!visitor.visit(entry.getName(), () -> zipFile.getInputStream(entry))) return;
			}
		}
	}

	/** 前からの読み方（先頭から順に） */
	private static void forEachInStream(Path zipPath, boolean useUnicodeExtraFields, Visitor visitor) throws IOException
	{
		try (ZipArchiveInputStream zis = new ZipArchiveInputStream(new BufferedInputStream(Files.newInputStream(zipPath), 65536), "MS932", useUnicodeExtraFields)) {
			ArchiveEntry entry;
			while ((entry = zis.getNextEntry()) != null) {
				//中身を開いた側が閉じても、次の項目へ進めるように閉じない流れで渡す
				InputStream unclosable = new java.io.FilterInputStream(zis) { @Override public void close() {} };
				if (!visitor.visit(entry.getName(), () -> unclosable)) return;
			}
		}
	}
}
