import java.awt.Desktop;
import java.awt.EventQueue;
import java.awt.FileDialog;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.io.File;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFileChooser;
import javax.swing.JFrame;

/**
 * 本棚の画面（ブラウザ）から頼まれて、アプリの側でフォルダ選択を出す（Web 本棚の場所。internal #11 の案 A）。
 *
 * <p>2026-10-10 の spike: mac は {@link FileDialog}（フォルダを選ぶ形）がブラウザの前に出て、そのまま操作できた。
 * Windows は Java から前面（入力先）を取れない。{@link JFileChooser} を常に前の持ち主の窓で出すと、ブラウザの上に
 * 見えるが、選択画面をクリックするまで入力はブラウザに行く。画面の側で「選択画面を開きました」と知らせる。
 * FileDialog は Windows ではブラウザの後ろに出て、フォルダだけを選ぶ形にもできない</p>
 */
final class FolderPicker
{
	private FolderPicker() {}

	static boolean available()
	{
		return !GraphicsEnvironment.isHeadless();
	}

	/** 選んだフォルダ。選ばなければ null。画面が無ければ {@link UnsupportedOperationException} */
	static Path pick(Path initial, String title) throws Exception
	{
		if (!available()) throw new UnsupportedOperationException();
		boolean mac = System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("mac");
		AtomicReference<Path> picked = new AtomicReference<>();
		EventQueue.invokeAndWait(() -> {
			if (mac) picked.set(pickWithFileDialog(initial, title));
			else picked.set(pickWithChooser(initial, title));
		});
		return picked.get();
	}

	private static Path pickWithFileDialog(Path initial, String title)
	{
		String key = "apple.awt.fileDialogForDirectories";
		String before = System.getProperty(key);
		System.setProperty(key, "true");
		Frame owner = new Frame();
		try {
			owner.setUndecorated(true);
			owner.setAlwaysOnTop(true);
			owner.setLocationRelativeTo(null);
			owner.setVisible(true);
			owner.toFront();
			if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.APP_REQUEST_FOREGROUND)) {
				Desktop.getDesktop().requestForeground(true);
			}
			FileDialog dialog = new FileDialog(owner, title, FileDialog.LOAD);
			if (initial != null) dialog.setDirectory(initial.toString());
			dialog.setVisible(true);
			if (dialog.getFile() == null) return null;
			return new File(dialog.getDirectory(), dialog.getFile()).toPath();
		} finally {
			owner.dispose();
			//GUI のほかのファイル選択に影響しないよう戻す
			if (before == null) System.clearProperty(key);
			else System.setProperty(key, before);
		}
	}

	private static Path pickWithChooser(Path initial, String title)
	{
		JFrame owner = new JFrame();
		try {
			owner.setUndecorated(true);
			owner.setAlwaysOnTop(true);
			owner.setLocationRelativeTo(null);
			owner.setVisible(true);
			owner.toFront();
			JFileChooser chooser = new JFileChooser(initial != null ? initial.toFile() : null);
			chooser.setDialogTitle(title);
			chooser.setFileSelectionMode(JFileChooser.DIRECTORIES_ONLY);
			if (chooser.showOpenDialog(owner) != JFileChooser.APPROVE_OPTION || chooser.getSelectedFile() == null) return null;
			return chooser.getSelectedFile().toPath();
		} finally {
			owner.dispose();
		}
	}
}
