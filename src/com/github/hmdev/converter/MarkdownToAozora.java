package com.github.hmdev.converter;

import java.util.List;
import java.util.Map;

import org.commonmark.Extension;
import org.commonmark.ext.front.matter.YamlFrontMatterBlock;
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
	static final char JOINER = '\u2060';

	private static final Parser PARSER_WITH_FRONT_MATTER = Parser.builder()
		.extensions(List.<Extension>of(TablesExtension.create(), YamlFrontMatterExtension.create())).build();
	private static final Parser PARSER = Parser.builder().extensions(List.<Extension>of(TablesExtension.create())).build();
	/** 「キー: 値」の行（front matter の中身） */
	private static final java.util.regex.Pattern FRONT_MATTER_KEY = java.util.regex.Pattern.compile("[A-Za-z0-9_-]+[ \\t]*:.*");

	/**
	 * front matter として読む形か: 1 行目が ---、閉じの ---（か ...）があり、間は「キー: 値」・字下げした行（リストの項目など）・空行だけ。
	 * 正規表現 1 本で書くと、行の選択肢が重なって入力によっては極端に遅くなる（CodeQL）ので、1 行ずつ見る
	 */
	static boolean looksLikeFrontMatter(String md) {
		String[] lines = md.split("\r?\n", -1);
		if (lines.length < 2 || !lines[0].strip().equals("---") || !lines[0].startsWith("---")) return false;
		for (int i = 1; i < lines.length; i++) {
			String line = lines[i];
			String t = line.strip();
			if (t.equals("---") || t.equals("...")) return line.startsWith(t);
			if (t.isEmpty()) continue;
			if (Character.isWhitespace(line.charAt(0))) continue;
			if (!FRONT_MATTER_KEY.matcher(line).matches()) return false;
		}
		return false;
	}

	private final StringBuilder out = new StringBuilder();
	/** 注記を書かずに文字だけ取る（題名） */
	private boolean plain = false;
	/** 題名の後に本文を書いたか（最初の大見出しの前に改ページを入れるかどうか） */
	private boolean bodyWritten = false;

	private MarkdownToAozora() {}

	/**
	 * @param markdown Markdown の本文（BOM は呼び手が外す）
	 * @param defaultTitle front matter にも先頭の # にも題名が無いときの題名（ファイル名など）
	 * @return 青空文庫形式のテキスト（改行は \n）
	 */
	public static String convert(String markdown, String defaultTitle) {
		if (markdown == null) markdown = "";
		//先頭の --- が区切り線のこともある。front matter の形のときだけ front matter として読む
		//（拡張に任せると、次の --- まで、閉じが無ければ全部を front matter として捨てる）
		Node doc = (looksLikeFrontMatter(markdown) ? PARSER_WITH_FRONT_MATTER : PARSER).parse(markdown);
		YamlFrontMatterVisitor fm = new YamlFrontMatterVisitor();
		doc.accept(fm);
		Map<String, List<String>> data = fm.getData();

		MarkdownToAozora w = new MarkdownToAozora();
		Node first = firstContent(doc);
		//題名は注記を含めない（表題ページと dc:title に注記の字が出る）。front matter とファイル名はここで逃がし、
		//見出しから取るときは文字の節点が逃がし済みなので、太字などの注記を書かずに文字だけ取る
		//題名は空にしない（1 行目が空だと、変換器が著者や本文の行を表題・著者に取る）
		String title = first(data, "title");
		if (title != null) title = oneLine(title, true);
		if ((title == null || title.isEmpty()) && first instanceof Heading && ((Heading) first).getLevel() == 1) {
			w.plain = true;
			title = oneLine(w.inline(first), false);
			w.plain = false;
			first.unlink();
		}
		if (title == null || title.isEmpty()) title = oneLine(defaultTitle == null ? "" : defaultTitle, true);
		if (title.isEmpty()) title = "無題";
		String author = first(data, "author");
		author = author == null ? "" : oneLine(author, true);
		//変換器は ― や【 で始まる著者の行を副題などとみなして捨てる
		if (author.startsWith("―") || author.startsWith("【")) author = JOINER + author;

		w.out.append(title).append('\n');
		w.out.append(author).append('\n');
		w.out.append('\n');
		w.blocks(doc, 0);
		return w.out.toString();
	}

	private static String first(Map<String, List<String>> data, String key) {
		List<String> v = data.get(key);
		if (v == null || v.isEmpty()) return null;
		String s = v.get(0);
		if (s == null) return null;
		s = literal(s).strip();
		return s.isEmpty() ? null : s;
	}

	/** front matter を飛ばした最初のブロック */
	private static Node firstContent(Node doc) {
		Node n = doc.getFirstChild();
		//捨てる HTML（先頭のコメントなど）も飛ばす。飛ばさないと続く # が題名にならない
		while (n != null && (n instanceof HtmlBlock || n instanceof YamlFrontMatterBlock)) n = n.getNext();
		return n;
	}

	// ---- ブロック ----

	private void blocks(Node parent, int indent) {
		for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) block(n, indent);
	}

	private void block(Node n, int indent) {
		if (n instanceof Heading && indent > 0) {
			//箇条書き・引用の中の見出しは、改ページも見出しの注記も入れず、太字の段落にする
			String t = inline(n);
			if (!t.isBlank()) paragraph(NOTE_BOLD_OPEN + t + NOTE_BOLD_CLOSE, indent, indent, "");
		} else if (n instanceof Heading) {
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
			if (!(n instanceof YamlFrontMatterBlock)) blocks(n, indent);
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
			out.append(lead).append(escape(literal(line.substring(i)))).append('\n');
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
						if (name.isEmpty() && value.isEmpty()) continue;
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

	/**
	 * 子のインラインを文字にする。改行は \n で残す（段落の中の強制改行）。
	 * 逃がしは節点ごとではなく、つなぎ終えた文字に 1 回かける（節点の境目で ［＃ や << が組み上がる。#215 の codex P2）。
	 * 自分で書く注記の ［＃ は {@link #NOTE} で仮に置き、逃がした後で戻す
	 */
	private String inline(Node parent) {
		return escape(rawInline(parent)).replace(String.valueOf(NOTE), "［＃");
	}

	/** 逃がす前の文字（自分の注記は {@link #NOTE} で始まる） */
	private String rawInline(Node parent) {
		StringBuilder b = new StringBuilder();
		for (Node n = parent.getFirstChild(); n != null; n = n.getNext()) inline(n, b);
		return b.toString();
	}

	private void inline(Node n, StringBuilder b) {
		if (n instanceof Text) {
			b.append(literal(((Text) n).getLiteral()));
		} else if (n instanceof Code) {
			b.append(literal(((Code) n).getLiteral()));
		} else if (n instanceof SoftLineBreak) {
			//日本語どうしは詰め、英数字どうしは空白でつなぐ。前後の字は注記を飛ばした見える字で見る
			char prev = lastVisible(b);
			char nextCh = firstVisible(n.getNext());
			if (prev < 0x80 && prev > ' ' && (nextCh == 0 || nextCh < 0x80)) b.append(' ');
		} else if (n instanceof HardLineBreak) {
			b.append('\n');
		} else if (n instanceof StrongEmphasis) {
			String inner = rawInline(n);
			if (plain) b.append(inner);
			else if (!inner.isEmpty()) {
				//強制改行は行（<p>）を分けるので、太字は行ごとに閉じて開き直す（span が <p> をまたぐと XHTML が壊れる。#215 の codex P2）
				String open = NOTE + "太字］", close = NOTE + "太字終わり］";
				b.append(open).append(inner.replace("\n", close + "\n" + open)).append(close);
			}
		} else if (n instanceof Emphasis) {
			b.append(rawInline(n));
		} else if (n instanceof Link) {
			String text = rawInline(n);
			String url = literal(((Link) n).getDestination());
			if (text.isEmpty() || text.equals(url) || ("mailto:" + text).equals(url)) b.append(text.isEmpty() ? url : text);
			else b.append(text).append('（').append(url).append('）');
		} else if (n instanceof Image) {
			String alt = rawInline(n);
			b.append("（図").append(alt.isEmpty() ? "" : "：" + alt).append('）');
		} else if (n instanceof HtmlInline) {
			//捨てる
		} else {
			for (Node c = n.getFirstChild(); c != null; c = c.getNext()) inline(c, b);
		}
	}

	// ---- 文字 ----

	/** 自分で書く注記の ［＃ の仮置き（私用領域の字。入力の同じ字は {@link #literal} で外す） */
	static final char NOTE = '\uE000';
	/** 段落の文字の中に置く太字の注記（逃がしの前。inline と同じ仮置き） */
	private static final String NOTE_BOLD_OPEN = "［＃太字］", NOTE_BOLD_CLOSE = "［＃太字終わり］";

	/** 入力の文字。仮置きの字と取り違えないよう、入力にある同じ字は外す */
	private static String literal(String s) {
		//変換器が入口で消す内部の字（CharUtils.stripInternalChars: \u0001・\uFDD0・\uFDD1）も先に外す。
		//残すと逃がしの並びを分けたまま、変換器で消えて ［＃ や << が組み上がる
		StringBuilder b = null;
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			boolean drop = c == NOTE || c == '\u0001' || c == '\uFDD0' || c == '\uFDD1';
			if (drop && b == null) b = new StringBuilder(s.length()).append(s, 0, i);
			else if (!drop && b != null) b.append(c);
		}
		return b == null ? s : b.toString();
	}

	/** 末尾の注記（NOTE…］）を飛ばした、最後の見える字。NOTE から末尾の ］ までの間に ］ がほかにあれば、それは本文の ］ */
	private static char lastVisible(CharSequence b) {
		int end = b.length();
		while (end > 0 && b.charAt(end - 1) == '］') {
			int open = -1;
			for (int i = end - 1; i >= 0; i--) if (b.charAt(i) == NOTE) { open = i; break; }
			if (open < 0) break;
			boolean ours = true;
			for (int i = open + 1; i < end - 1; i++) if (b.charAt(i) == '］') { ours = false; break; }
			if (!ours) break;
			end = open;
		}
		return end == 0 ? 0 : b.charAt(end - 1);
	}

	/** 節点から始まる最初の見える字（無ければ 0） */
	private static char firstVisible(Node n) {
		for (; n != null; n = n.getNext()) {
			if (n instanceof Text || n instanceof Code) {
				String t = n instanceof Text ? ((Text) n).getLiteral() : ((Code) n).getLiteral();
				if (!t.isEmpty()) return t.charAt(0);
			} else if (n instanceof SoftLineBreak || n instanceof HardLineBreak) {
				return 0;
			} else if (n.getFirstChild() != null) {
				char c = firstVisible(n.getFirstChild());
				if (c != 0) return c;
			}
		}
		return 0;
	}

	/** 見出し・題名・表の升は 1 行にする（強制改行は空白に） */
	private static String oneLine(String s, boolean escapeIt) {
		String t = (escapeIt ? escape(literal(s)) : s).replace('\n', ' ').strip();
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
			case '〔':
				//変換器は 〔 〕 の中が半角だけなら欧文アクセントの注記として読み、括弧を外す。半角でない字を挟んで字のまま残す
				b.append(c).append(JOINER);
				break;
			case '／':
				//変換器は ／＼・／″＼ をくの字点に変える
				b.append(c);
				if (i + 1 < s.length() && (s.charAt(i + 1) == '＼' || s.charAt(i + 1) == '″')) b.append(JOINER);
				break;
			case '-':
				//変換器は行頭の - の並び（32 個で警告、50 個でコメントの区切り）を読む。16 個ごとに崩す
				if (dashRun(b) >= 16) b.append(JOINER);
				b.append(c);
				break;
			case '<': case '>': {
				//変換器は <…> をすべてタグとして読む（<img・<a は画像・リンク、<大見出し> は見出し、間の ［＃太字］ は飲まれる）。
				//半角の < > は全角にする
				char z = c == '<' ? '＜' : '＞';
				if (b.length() > 0 && b.charAt(b.length() - 1) == z) b.append(JOINER);
				b.append(z);
				break;
			}
			case '※':
				//変換器は ※［＃…］ を外字の注記と読む。後ろに自分で書く注記（太字・見出しの閉じ）が来ても外字にしない
				b.append(c).append(JOINER);
				break;
			case '底':
				//変換器は行頭の「底本：」を奥付として読み、改ページする（字下げの中ではエラー）
				b.append(c);
				if (s.startsWith("本：", i + 1)) b.append(JOINER);
				break;
			case '＜': case '＞':
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

	/** b の末尾に続く - の数（崩した印より後ろだけ） */
	private static int dashRun(StringBuilder b) {
		int n = 0;
		for (int i = b.length() - 1; i >= 0 && b.charAt(i) == '-'; i--) n++;
		return n;
	}

	private static String zen(int n) {
		StringBuilder b = new StringBuilder();
		for (char c : Integer.toString(n).toCharArray()) b.append((char) ('０' + (c - '0')));
		return b.toString();
	}
}
