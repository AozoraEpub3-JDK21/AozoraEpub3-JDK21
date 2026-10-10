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
 * （GUI で開いたときにも棚として出るように）。GUI を開いたまま CLI で決めると、GUI が終わるときに ini を書いて消える
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
	public void setLocation(Path dir) throws IOException
	{
		Properties props = read();
		WebShelfPrefs.store(props, dir);
		//同じ棚は store が畳み、上限を超えた分は落とす
		List<String> folders = new ArrayList<>(PreviewLibraryPrefs.load(props));
		folders.add(dir.toString());
		PreviewLibraryPrefs.store(props, folders);
		write(props);
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

	/** 一時ファイルに書いてから置き換える（途中で止まっても ini が壊れない） */
	private void write(Properties props) throws IOException
	{
		Path dir = this.iniFile.getAbsoluteFile().getParentFile().toPath();
		Files.createDirectories(dir);
		Path tmp = Files.createTempFile(dir, this.iniFile.getName(), ".tmp");
		try {
			try (OutputStream out = Files.newOutputStream(tmp)) {
				props.store(out, "AozoraEpub3 Parameters");
			}
			Files.move(tmp, this.iniFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		} finally {
			Files.deleteIfExists(tmp);
		}
	}
}
