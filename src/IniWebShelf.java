import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import com.github.hmdev.preview.PreviewLibraryPrefs;
import com.github.hmdev.preview.WebShelf;
import com.github.hmdev.preview.WebShelfPrefs;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CLI で開いた本棚の Web 本棚（internal #11 の案 A）。場所は ini（GUI と同じもの）に書き、棚の一覧にも足す
 * （GUI で開いたときにも棚として出るように）。GUI を開いたまま CLI で決めると、GUI が次に ini を書くとき
 * （終わるとき・窓が手前から外れたとき）に消える
 */
class IniWebShelf implements WebShelf
{
	static final Logger logger = LoggerFactory.getLogger(IniWebShelf.class);

	private final File iniFile;

	IniWebShelf(File iniFile)
	{
		this.iniFile = iniFile;
	}

	@Override
	public Path location()
	{
		return WebShelfPrefs.load(read());
	}

	@Override
	public synchronized void setLocation(Path dir) throws IOException
	{
		//書く前に読めなければ書かない（読めなかった設定を空のまま書き戻すと、ほかの設定が消える。PR の手元の codex）
		//コメントと行の順を残すため、読んだ中身のまま持って、変わったキーの行だけ書き換える（internal #30）
		byte[] original = this.iniFile.isFile() ? Files.readAllBytes(this.iniFile.toPath()) : null;
		Properties before = new Properties();
		if (original != null) {
			try (InputStream in = new ByteArrayInputStream(original)) {
				before.load(in);
			} catch (IllegalArgumentException e) {
				throw new IOException("設定ファイルを読めないため、Web 本棚を保存しませんでした: " + this.iniFile, e);
			}
		}
		Properties props = new Properties();
		props.putAll(before);
		WebShelfPrefs.store(props, dir);
		//同じ棚は store が畳み、上限を超えた分は落とす
		List<String> folders = new ArrayList<>(PreviewLibraryPrefs.load(props));
		folders.add(dir.toString());
		PreviewLibraryPrefs.store(props, folders);
		byte[] content;
		if (original == null) {
			java.io.ByteArrayOutputStream all = new java.io.ByteArrayOutputStream();
			props.store(all, "AozoraEpub3 Parameters");
			content = all.toByteArray();
		} else {
			content = IniLines.update(original, before, props);
			//同じ場所を決め直しただけなら書かない（更新時刻も権限も変えない）
			if (java.util.Arrays.equals(content, original)) return;
		}
		write(content);
	}

	@Override
	public boolean canPick()
	{
		return FolderPicker.available();
	}

	@Override
	public Path pickFolder(Path initial) throws Exception
	{
		return FolderPicker.pick(initial, "Web 本棚の場所");
	}

	private Properties read()
	{
		Properties props = new Properties();
		if (!this.iniFile.isFile()) return props;
		try (InputStream in = Files.newInputStream(this.iniFile.toPath())) {
			props.load(in);
		} catch (IOException | IllegalArgumentException e) {
			logger.warn("設定ファイルを読めませんでした: {}", this.iniFile, e);
		}
		return props;
	}

	/**
	 * 一時ファイルに書いてから置き換える（途中で止まっても ini が壊れない）。
	 * 元の ini の権限は一時ファイルへ写す（createTempFile は 0600 で作るので、そのまま置き換えると
	 * ほかの利用者が読めなくなる。PR のゲート2）
	 */
	private void write(byte[] content) throws IOException
	{
		//ini がリンクなら、リンクの先を書き換える（リンクを普通のファイルに置き換えない。PR の codex）
		Path target = this.iniFile.toPath();
		if (Files.exists(target)) target = target.toRealPath();
		Path dir = target.toAbsolutePath().getParent();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, target.getFileName().toString(), ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(tmp)) {
				out.write(content);
			}
			if (Files.exists(target)) {
				try {
					Files.setPosixFilePermissions(tmp, Files.getPosixFilePermissions(target));
				} catch (UnsupportedOperationException e) {
					//Windows（POSIX の権限が無い）。置き換えた先はフォルダの既定の ACL を受け継ぐ
				}
			}
			Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
