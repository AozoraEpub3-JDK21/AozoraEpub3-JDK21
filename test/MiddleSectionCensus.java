import java.io.File;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.image.ImageInfoReader;
import com.github.hmdev.info.BookInfo;
import com.github.hmdev.io.ArchiveTextExtractor;
import com.github.hmdev.pipeline.ConversionSettings;
import com.github.hmdev.writer.Epub3Writer;

/**
 * 計器（internal #8）: 左右中央の節が、横書きの親の中の縦書きのブロック（中央に出る）と
 * 従来の表組み（右に寄ることがある）のどちらで組まれるかを、本ごと・節ごとに数える。
 *
 * 判定は変換器そのもの（AozoraEpub3Converter.getBookInfo の事前走査）を使い、理由は変換器が
 * 表組みに倒したその場所で控えたもの（BookInfo.middleSections）を読む。判定を書き直さない。
 *
 * 使い方: ./gradlew middleCensus --args="&lt;出力 TSV&gt; &lt;入力のフォルダかファイル&gt;..."（空白を含むパスは引用符で囲む）
 *   入力は txt / zip（zip の中の 1 つ目のテキスト）。txt は UTF-8 として読めなければ MS932、zip は MS932（青空文庫）で読む。
 *   ini はリポジトリの AozoraEpub3.ini（配布の既定）。
 * 出力: 1 行 1 節の TSV（file, line, lines, layout, reasons）と、最後に理由ごとの件数を標準出力へ。
 */
public class MiddleSectionCensus {

	public static void main(String[] args) throws Exception {
		if (args.length < 2) {
			System.err.println("usage: MiddleSectionCensus <out.tsv> <dir-or-file>...");
			System.exit(2);
		}
		File root = new File(".").getCanonicalFile();
		String jarPath = root.getPath() + File.separator;
		Properties props = new Properties();
		try (var in = Files.newInputStream(new File(root, "AozoraEpub3.ini").toPath())) { props.load(in); }
		Epub3Writer writer = new Epub3Writer(jarPath + "template" + File.separator);
		AozoraEpub3Converter converter = new AozoraEpub3Converter(writer, jarPath);
		ConversionSettings.fromProps(props).applyTo(converter);

		List<File> files = new ArrayList<>();
		for (int i = 1; i < args.length; i++) collect(new File(args[i]), files);
		files.sort(null);

		int books = 0, booksWithMiddle = 0, booksWithTable = 0, failed = 0, sections = 0, tables = 0;
		Map<String, Integer> anyReason = new TreeMap<>();
		Map<String, Integer> firstReason = new TreeMap<>();
		try (PrintStream out = new PrintStream(new File(args[0]), StandardCharsets.UTF_8)) {
			out.println("file\tline\tlines\tlayout\treasons");
			for (File f : files) {
				String name = f.getName();
				String ext = name.substring(name.lastIndexOf('.') + 1).toLowerCase();
				//zip は青空文庫の MS932。txt は UTF-8 として読めなければ MS932（CLI の既定）
				String enc = "zip".equals(ext) ? "MS932" : (isUtf8(f) ? "UTF-8" : "MS932");
				books++;
				BookInfo info = null;
				String cause = "";
				try {
					info = AozoraEpub3.getBookInfo(f, ext, 0, new ImageInfoReader("txt".equals(ext), f),
						converter, enc, BookInfo.TitleType.TITLE_AUTHOR, false);
				} catch (Throwable t) {
					info = null;
					cause = t.toString();
				} finally {
					if (!"txt".equals(ext)) ArchiveTextExtractor.clearCache(f);
				}
				if (info == null) {
					//getBookInfo は失敗を LogAppender に書いて null を返す。例外ならその理由を残す
					failed++;
					out.println(f.getPath() + "\t-\t-\tfailed\t" + (cause.isEmpty() ? "getBookInfo returned null (see log)" : cause));
					continue;
				}
				if (!info.getMiddleSections().isEmpty()) booksWithMiddle++;
				boolean anyTable = false;
				for (BookInfo.MiddleSection m : info.getMiddleSections()) {
					sections++;
					boolean table = info.isLongMiddleLine(m.line);
					if (table) { tables++; anyTable = true; }
					out.println(f.getPath() + "\t" + (m.line + 1) + "\t" + m.lines + "\t" + (table ? "table" : "centered") + "\t" + String.join(",", m.reasons));
					if (table) {
						for (String r : m.reasons) anyReason.merge(r, 1, Integer::sum);
						if (!m.reasons.isEmpty()) firstReason.merge(m.reasons.iterator().next(), 1, Integer::sum);
						else firstReason.merge("(none)", 1, Integer::sum);
					}
				}
				if (anyTable) booksWithTable++;
			}
		}
		System.out.println("books=" + books + " failed=" + failed + " booksWithMiddle=" + booksWithMiddle + " booksWithTable=" + booksWithTable);
		System.out.println("sections=" + sections + " table=" + tables + " centered=" + (sections - tables));
		System.out.println("table sections by any reason: " + anyReason);
		System.out.println("table sections by first reason: " + firstReason);
	}

	private static boolean isUtf8(File f) {
		try {
			StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(Files.readAllBytes(f.toPath())));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private static void collect(File f, List<File> files) {
		if (f.isDirectory()) {
			File[] children = f.listFiles();
			if (children != null) for (File c : children) collect(c, files);
		} else {
			String n = f.getName().toLowerCase();
			if (n.endsWith(".txt") || n.endsWith(".zip")) files.add(f);
		}
	}
}
