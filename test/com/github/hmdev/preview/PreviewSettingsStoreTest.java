package com.github.hmdev.preview;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Path;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/**
 * 表示設定の永続化。
 * プレビューサーバは毎回ランダムポートで起動するため localStorage では設定が保たない。
 * サーバ側のファイルが正となる。
 */
public class PreviewSettingsStoreTest
{
	@Rule
	public TemporaryFolder temp = new TemporaryFolder();

	private PreviewSettingsStore store()
	{
		Path file = temp.getRoot().toPath().resolve("nested").resolve("preview-settings.json");
		return new PreviewSettingsStore(file);
	}

	@Test
	public void missingFileReturnsEmptyObject()
	{
		assertEquals("{}", store().load());
	}

	@Test
	public void roundTripsSettings()
	{
		PreviewSettingsStore store = store();
		// 親ディレクトリが無くても保存できること
		assertTrue(store.save("{\"theme\":\"dark\",\"marginBlock\":2.5}"));
		assertEquals("{\"theme\":\"dark\",\"marginBlock\":2.5}", store.load());
	}

	@Test
	public void rejectsNonObjectPayload()
	{
		PreviewSettingsStore store = store();
		assertFalse(store.save("[1,2,3]"));
		assertFalse(store.save("theme=dark"));
		assertFalse(store.save(null));
		assertEquals("{}", store.load());
	}

	@Test
	public void rejectsOversizedPayload()
	{
		PreviewSettingsStore store = store();
		StringBuilder buf = new StringBuilder("{\"x\":\"");
		buf.append("a".repeat(PreviewSettingsStore.MAX_BYTES));
		buf.append("\"}");
		assertFalse(store.save(buf.toString()));
	}
}
