package com.github.hmdev.converter;

import java.util.List;
import java.util.Map;

import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterExtension;
import org.commonmark.ext.front.matter.YamlFrontMatterVisitor;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.Parser;

/**
 * Markdown を青空文庫形式のテキストに直す（internal #72）。
 *
 * 構文は commonmark-java（CommonMark と GFM の表）で読み、ここでは木を青空文庫の注記へ書き出すだけにする。
 * 縦中横・目次・表題ページなどは、書き出したテキストを読む今の変換器がそのまま受け持つ。
 *
 * 決めごと:
 * <ul>
 * <li>題名は front matter の title、無ければ最初の要素が # の見出しならそれ、無ければ呼び手が渡す既定（ファイル名）。
 *     著者は front matter の author（無ければ空）</li>
 * <li>#（題名以外）と ## は改ページして大見出し、### は中見出し、#### 以下は小見出し</li>
 * <li>箇条書き・引用・コードは字下げの注記で表す。注記は入れ子にせず、葉のブロックごとに字下げを計算して
 *     開いて閉じる（対にならないブロックの注記で XHTML が壊れる穴＝internal #41 を踏まない）</li>
 * <li>表は 1 行ずつ「・見出し：値／見出し：値」に崩す。リンクは「文字（URL）」。画像は「（図：代わりの文字）」。HTML は捨てる</li>
 * <li>文字の中の《》｜・［＃・&lt;&lt; &gt;&gt; ＜＜ ＞＞ は注記やルビに読まれないよう置き換える。置き換えるのは文字の節点だけ</li>
 * </ul>
 */
public final class MarkdownToAozora {

	/** 並びを崩すために挟む文字（WORD JOINER。幅が無く、改行も起こさない） */
	static final char JOINER = '⁠';

	private static final List<Extension> EXTENSIONS = List.of(TablesExtension.create(), YamlFrontMatterExtension.create());
	private static final Parser PARSER = Parser.builder().extensions(EXTENSIONS).build();

	private final StringBuilder out = new StringBuilder();
	/** 題名の後に本文を書いたか（最初の大見出しの前に改ページを入れるかどうか） */
	private boolean bodyWritten = false;

	private MarkdownToAozora() {}

	/**
	 * @param markdown Markdown の本文（BOM は呼び手が外す）
	 * @param defaultTitle front matter にも先頭の # にも題名が無いときの題名（ファイル名など）
	 * @return 青空文庫形式のテキスト（改行は \n）
	 */
	public static String convert(String markdown, String defaultTitle) {
		Node doc = PARSER.parse(markdown);
		YamlFrontMatterVisitor fm = new YamlFrontMatterVisitor();
		doc.accept(fm);
		Map<String, List<String>> data = fm.getData();

		MarkdownToAozora w = new MarkdownToAozora();
		Node first = firstContent(doc);
		String title = first(data, "title");
		if (title == null && first instanceof Heading && ((Heading) first).getLevel() == 1) {
			title = w.inline(first);
			first.unlink();
		}
		if (title == null || title.isBlank()) title = defaultTitle == null ? "" : defaultTitle;
		String author = first(data, "author");

		w.out.append(oneLine(title, true)).append('\n');
		w.out.append(author == null ? "" : oneLine(author, true)).append('\n');
		w.out.append('\n');
		w.blocks(doc, 0);
		return w.out.toString();
	}

	private static String first(Map<String, List<String>> data, String key) {
		List<String> v = data.get(key);
		if (v == null || v.isEmpty()) return null;
		String s = v.get(0);
		return s == null || s.isBlank() ? null : s.trim();
	}

	/** front matter を飛ばした最初のブロック */
	private static Node firstContent(Node doc) {
		Node n = doc.getFirstChild();
		while (n != null && n.getClass().getSimpleName().startsWith("YamlFrontMatter")) n = n.getNext();
		return n;
	}

	// ---- ブロック ----

	private void blocks(Node parent, int indent) {
		for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) block(n, indent);
	}

	private void block(Node n, int indent) {
		if (n instanceof Heading) {
			heading((Heading) n);
		} else if (n instanceof Paragraph) {
			paragraph(inline(n), indent, indent, "");
		} else if (n instanceof BulletList || n instanceof OrderedList) {
			list(n, indent);
		} else if (n instanceof BlockQuote) {
			blocks(n, indent + 2);
		} else if (n instanceof FencedCodeBlock) {
			code(((FencedCodeBlock) n).getLiteral(), indent + 2);
		} else if (n instanceof IndentedCodeBlock) {
			code(((IndentedCodeBlock) n).getLiteral(), indent + 2);
		} else if (n instanceof TableBlock) {
			table((TableBlock) n, indent);
		} else if (n instanceof ThematicBreak) {
			out.append('\n');
		} else if (n instanceof HtmlBlock) {
			//HTML は読まない（AI のレポートのコメント <!-- --> など）
		} else if (n.getFirstChild() != null) {
			//front matter など、知らない入れ物は中身だけ
			if (!n.getClass().getSimpleName().startsWith("YamlFrontMatter")) blocks(n, indent);
		}
	}

	private void heading(Heading h) {
		String text = oneLine(inline(h), false);
		if (text.isEmpty()) return;
		String tag;
		if (h.getLevel() <= 2) {
			if (bodyWritten) out.append("［＃改ページ］\n");
			tag = "大見出し";
		} else if (h.getLevel() == 3) {
			out.append('\n');
			tag = "中見出し";
		} else {
			out.append('\n');
			tag = "小見出し";
		}
		out.append("［＃").append(tag).append("］").append(text).append("［＃").append(tag).append("終わり］\n");
		bodyWritten = true;
	}

	/**
	 * 段落を書く。first は 1 行目の字下げ、wrap は折り返しの字下げ、marker は 1 行目の頭（箇条書きの「・」など）。
	 * 字下げが無い段落は全角の空白で書き出す
	 */
	private void paragraph(String text, int first, int wrap, String marker) {
		if (text.isBlank() && marker.isEmpty()) return;
		bodyWritten = true;
		String[] lines = text.split("\n", -1);
		if (first == 0 && wrap == 0) {
			for (int i = 0; i < lines.length; i++) {
				out.append(i == 0 ? "　" : "").append(lines[i]).append('\n');
			}
			return;
		}
		out.append("［＃ここから").append(zen(first)).append("字下げ");
		if (wrap != first) out.append("、折り返して").append(zen(wrap)).append("字下げ");
		out.append("］\n");
		for (int i = 0; i < lines.length; i++) {
			out.append(i == 0 ? marker : "").append(lines[i]).append('\n');
		}
		out.append("［＃ここで字下げ終わり］\n");
	}

	private void list(Node list, int indent) {
		int num = list instanceof OrderedList ? ((OrderedList) list).getMarkerStartNumber() : 0;
		for (Node item = list.getFirstChild(); item != null; item = item.getNext()) {
			if (!(item instanceof ListItem)) continue;
			String marker = list instanceof OrderedList ? (num++) + "．" : "・";
			int first = indent + 1;
			int wrap = first + (list instanceof OrderedList ? 2 : 1);
			boolean markerWritten = false;
			for (Node c = item.getFirstChild(); c != null; c = c.getNext()) {
				if (c instanceof Paragraph && !markerWritten) {
					paragraph(inline(c), first, wrap, marker);
					markerWritten = true;
				} else if (c instanceof Paragraph) {
					paragraph(inline(c), wrap, wrap, "");
				} else {
					if (!markerWritten) { paragraph("", first, wrap, marker); markerWritten = true; }
					block(c, wrap - 1);
				}
			}
			if (!markerWritten) paragraph("", first, wrap, marker);
		}
	}

	private void code(String literal, int indent) {
		bodyWritten = true;
		String body = literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal;
		out.append("［＃ここから").append(zen(indent)).append("字下げ］\n");
		for (String line : body.split("\n", -1)) {
			//行頭の空白は全角にして残す（半角の空白は行頭で詰められる）
			int i = 0;
			StringBuilder lead = new StringBuilder();
			while (i < line.length() && (line.charAt(i) == ' ' || line.charAt(i) == '\t')) {
				lead.append(line.charAt(i) == '\t' ? "　　" : "　");
				i++;
			}
			out.append(lead).append(escape(line.substring(i))).append('\n');
		}
		out.append("［＃ここで字下げ終わり］\n");
	}

	private void table(TableBlock table, int indent) {
		List<String> header = new java.util.ArrayList<>();
		for (Node part = table.getFirstChild(); part != null; part = part.getNext()) {
			if (part instanceof TableHead) {
				for (Node row = part.getFirstChild(); row != null; row = row.getNext()) {
					for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext()) header.add(oneLine(inline(cell), false));
				}
			} else if (part instanceof TableBody) {
				for (Node row = part.getFirstChild(); row != null; row = row.getNext()) {
					if (!(row instanceof TableRow)) continue;
					StringBuilder line = new StringBuilder();
					int col = 0;
					for (Node cell = row.getFirstChild(); cell != null; cell = cell.getNext(), col++) {
						if (!(cell instanceof TableCell)) continue;
						String value = oneLine(inline(cell), false);
						String name = col < header.size() ? header.get(col) : "";
						if (line.length() > 0) line.append('／');
						if (!name.isEmpty()) line.append(name).append('：');
						line.append(value);
					}
					paragraph(line.toString(), indent + 1, indent + 2, "・");
				}
			}
		}
	}

	// ---- インライン ----

	/** 子のインラインを文字にする。改行は \n で残す（段落の中の強制改行） */
	private String inline(Node parent) {
		StringBuilder b = new StringBuilder();
		for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) inline(n, b);
		return b.toString();
	}

	private void inline(Node n, StringBuilder b) {
		if (n instanceof Text) {
			b.append(escape(((Text) n).getLiteral()));
		} else if (n instanceof Code) {
			b.append(escape(((Code) n).getLiteral()));
		} else if (n instanceof SoftLineBreak) {
			//日本語どうしは詰め、英数字どうしは空白でつなぐ
			Node next = n.getNext();
			char prev = b.length() == 0 ? 0 : b.charAt(b.length() - 1);
			char nextCh = next instanceof Text && !((Text) next).getLiteral().isEmpty() ? ((Text) next).getLiteral().charAt(0) : 0;
			if (prev < 0x80 && prev > ' ' && (nextCh == 0 || nextCh < 0x80)) b.append(' ');
		} else if (n instanceof HardLineBreak) {
			b.append('\n');
		} else if (n instanceof StrongEmphasis) {
			String inner = inline(n);
			if (!inner.isEmpty()) b.append("［＃太字］").append(inner).append("［＃太字終わり］");
		} else if (n instanceof Emphasis) {
			b.append(inline(n));
		} else if (n instanceof Link) {
			String text = inline(n);
			String url = ((Link) n).getDestination();
			String escapedUrl = escape(url);
			if (text.isEmpty() || text.equals(escapedUrl) || ("mailto:" + text).equals(url)) b.append(text.isEmpty() ? escapedUrl : text);
			else b.append(text).append('（').append(escapedUrl).append('）');
		} else if (n instanceof Image) {
			String alt = inline(n);
			b.append("（図").append(alt.isEmpty() ? "" : "：" + alt).append('）');
		} else if (n instanceof HtmlInline) {
			//捨てる
		} else {
			for (Node c = n.getFirstChild(); c != null; c = c.getNext()) inline(c, b);
		}
	}

	// ---- 文字 ----

	/** 見出し・題名・表の升は 1 行にする（強制改行は空白に） */
	private static String oneLine(String s, boolean escapeIt) {
		String t = (escapeIt ? escape(s) : s).replace('\n', ' ').trim();
		return t;
	}

	/** 注記・ルビ・外字に読まれる文字を置き換える */
	static String escape(String s) {
		StringBuilder b = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			switch (c) {
			case '《': b.append('≪'); break;
			case '》': b.append('≫'); break;
			case '｜': b.append('│'); break;
			case '＃':
				if (b.length() > 0 && b.charAt(b.length() - 1) == '［') b.append(JOINER);
				b.append(c);
				break;
			case '<': case '>': case '＜': case '＞':
				//変換器は 2 つ並んだ < > を《》と読む（3 つ以上は読まない）
				if (b.length() > 0 && b.charAt(b.length() - 1) == c) b.append(JOINER);
				b.append(c);
				break;
			default:
				b.append(c);
			}
		}
		return b.toString();
	}

	private static String zen(int n) {
		StringBuilder b = new StringBuilder();
		for (char c : Integer.toString(n).toCharArray()) b.append((char) ('０' + (c - '0')));
		return b.toString();
	}
}
