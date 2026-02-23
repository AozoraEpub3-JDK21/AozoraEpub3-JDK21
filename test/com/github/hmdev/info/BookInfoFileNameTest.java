package com.github.hmdev.info;

import static org.junit.Assert.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

/**
 * BookInfo.getFileTitleCreator() のユニットテスト
 *
 * セキュリティ修正（ReDoS対策）の動作確認と既存機能のリグレッションテスト。
 * - Alert #8-11 (java/polynomial-redos) possessive quantifier 適用後の検証
 */
public class BookInfoFileNameTest {

    /** 全テストに1秒タイムアウトを設定（ReDoSの検出用） */
    @Rule
    public Timeout globalTimeout = new Timeout(1, TimeUnit.SECONDS);

    // ── 基本動作テスト ──────────────────────────────────────────────

    @Test
    public void testSimpleTitle() {
        String[] r = BookInfo.getFileTitleCreator("タイトル.txt");
        assertEquals("タイトル", r[0]);
        assertNull(r[1]);
    }

    @Test
    public void testDoubleExtension() {
        String[] r = BookInfo.getFileTitleCreator("タイトル.tar.gz");
        assertEquals("タイトル", r[0]);
        assertNull(r[1]);
    }

    @Test
    public void testEmptyFileName() {
        String[] r = BookInfo.getFileTitleCreator(".txt");
        // 拡張子のみ → titleはnullまたは空
        assertTrue(r[0] == null || r[0].isEmpty());
    }

    // ── [著者名] タイトル 形式 ──────────────────────────────────────

    @Test
    public void testSquareBracketsFormat() {
        String[] r = BookInfo.getFileTitleCreator("[著者名] タイトル.txt");
        assertEquals("タイトル", r[0]);
        assertEquals("著者名", r[1]);
    }

    @Test
    public void testZenSquareBracketsFormat() {
        String[] r = BookInfo.getFileTitleCreator("［著者名］ タイトル.txt");
        assertEquals("タイトル", r[0]);
        assertEquals("著者名", r[1]);
    }

    @Test
    public void testSquareBracketsNoSpace() {
        String[] r = BookInfo.getFileTitleCreator("[著者名]タイトル.txt");
        assertEquals("タイトル", r[0]);
        assertEquals("著者名", r[1]);
    }

    // ── タイトル(著者名) 形式 ────────────────────────────────────────

    @Test
    public void testTitleWithAuthorInParens() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(著者名).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testTitleWithSpaceBeforeParens() {
        String[] r = BookInfo.getFileTitleCreator("タイトル (著者名).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testTitleWithZenParens() {
        String[] r = BookInfo.getFileTitleCreator("タイトル（著者名）.txt");
        assertEquals("タイトル", r[0]);
    }

    // ── メタデータ除去テスト（Alert #10, #11 修正対象） ────────────────

    @Test
    public void testAozoraMetaStrip() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(青空文庫).txt");
        assertEquals("タイトル", r[0]);
        assertNull(r[1]);
    }

    @Test
    public void testProofreadStrip() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(校正版).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testLightStrip() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(軽量版).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testRubyStrip() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(ルビ付き).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testRevStrip() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(Rev1).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testRevLowerStrip() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(rev2).txt");
        assertEquals("タイトル", r[0]);
    }

    @Test
    public void testMultipleKeywordInBrackets() {
        String[] r = BookInfo.getFileTitleCreator("タイトル(校正済みルビ付き).txt");
        assertEquals("タイトル", r[0]);
    }

    // ── ReDoS 安全性テスト（1秒タイムアウトで検出）───────────────────
    // Alert #11: \\(青空[^)]*+\\) — 未終端の括弧が大量にある場合

    @Test
    public void testReDoSLine791_UnclosedAozoraBracket() {
        // 未閉じの "(青空xxxx..." が大量にある入力
        String fileName = "(青空" + "x".repeat(10000) + ".txt";
        BookInfo.getFileTitleCreator(fileName); // タイムアウトしなければOK
    }

    // Alert #10: \\([^)]*+(?:keyword)[^)]*+\\) — キーワードの前後に大量の文字

    @Test
    public void testReDoSLine792_UnclosedKeywordBracket() {
        // 未閉じの "(xxxx校正xxxx..." が大量にある入力
        String fileName = "(" + "x".repeat(5000) + "校正" + "x".repeat(5000) + ".txt";
        BookInfo.getFileTitleCreator(fileName);
    }

    // Alert #9: [\\[|［](.+?)[\\]|］][ |　]*+(.*+)[ |　]*$ — 大量スペース

    @Test
    public void testReDoSLine794_LargeSpacesAfterBracket() {
        // "[a]" の後に大量のスペース
        String fileName = "[a]" + " ".repeat(10000) + ".txt";
        BookInfo.getFileTitleCreator(fileName);
    }

    // Alert #8: ^([^(（]*+)[ 　]*(\\(|（) — 括弧なしの大量スペース

    @Test
    public void testReDoSLine799_LargeSpacesNoParens() {
        // 括弧なしで大量スペースが入力される場合
        String fileName = "title" + " ".repeat(10000) + ".txt";
        BookInfo.getFileTitleCreator(fileName);
    }

    @Test
    public void testReDoSLine799_SpacesBeforeParens() {
        // 大量スペースの後に括弧
        String fileName = "title" + " ".repeat(10000) + "(author).txt";
        BookInfo.getFileTitleCreator(fileName);
    }
}
