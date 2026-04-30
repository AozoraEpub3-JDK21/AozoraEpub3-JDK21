package com.github.hmdev.writer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

/**
 * Epub3Writer.MODIFIED_FORMATTER が dcterms:modified に対する
 * 「システム TZ ローカル時刻 + literal 'Z' 接尾辞」という SimpleDateFormat 互換挙動を
 * 維持していることを検証するテスト (ステージ 0B-3)。
 *
 * <p>byte-identical 比較側 (.NET ポート JavaComparisonTests) は NormalizeEpubText で
 * dcterms:modified を正規化するため、format 文字列の崩れを検出できない。本テストは
 * 固定 Instant に対する formatter の出力を未正規化のまま厳密にアサートする。</p>
 */
public class Epub3WriterDateFormatTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-30T12:00:00Z");

    /**
     * MODIFIED_FORMATTER が legacy SimpleDateFormat と等価な出力を返すことを確認。
     * formatter が捕捉済の zone を取り出し、同じ TZ を SimpleDateFormat にも設定して比較するため、
     * テストクラスのロード順序やシステム TZ に依存しない。
     */
    @Test
    public void formatterEquivalentToLegacySimpleDateFormat() {
        ZoneId capturedZone = Epub3Writer.MODIFIED_FORMATTER.getZone();
        assertNotNull("MODIFIED_FORMATTER は zone を捕捉済であること", capturedZone);

        SimpleDateFormat legacy = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
        legacy.setTimeZone(TimeZone.getTimeZone(capturedZone));
        String expected = legacy.format(Date.from(FIXED_INSTANT));

        String actual = Epub3Writer.MODIFIED_FORMATTER.format(FIXED_INSTANT);

        assertEquals(expected, actual);
    }

    /**
     * パターン文字列 "yyyy-MM-dd'T'HH:mm:ss'Z'" が
     * 「ローカル時刻 + literal 'Z'」形式を出力することを Asia/Tokyo 固定で確認。
     * これは MODIFIED_FORMATTER の class load 時 TZ に依存しない (zone を明示指定するため)。
     */
    @Test
    public void patternProducesLocalTimeWithLiteralZ() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                .withZone(ZoneId.of("Asia/Tokyo"));

        String formatted = formatter.format(FIXED_INSTANT);

        // 2026-04-30T12:00:00Z は JST で 2026-04-30T21:00:00、literal 'Z' 接尾辞付き
        assertEquals("2026-04-30T21:00:00Z", formatted);
    }

    /**
     * Codex [P2] 反映 / 意図的非互換 (Option B): 非グレゴリオロケール (th_TH 仏暦) で MODIFIED_FORMATTER
     * は **ISO/Gregorian 年を出力する**ことをロックダウン。SDF は仏暦年 (2569) を出力するが、本 PR は
     * EPUB 3.3 dcterms:modified の ISO 8601 仕様準拠を優先し ISO/Gregorian 年 (2026) を意図的に出力する。
     *
     * <p>本テストでは `withLocale(Locale.ROOT)` を明示した formatter (production code と同じ recipe) と、
     * `withLocale` を付けないデフォルト挙動の formatter を両方アサートし、Locale.ROOT 明示の有無に関係なく
     * DTF が常に ISO/Gregorian 年を出力することも確認する。</p>
     *
     * <p>詳細は docs/stage-0b3-plan.md §4.2 を参照。</p>
     */
    @Test
    public void normalizesNonGregorianLocaleToIsoYear() {
        Locale origFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.of("th", "TH"));

            DateTimeFormatter dtfWithLocaleRoot = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withLocale(Locale.ROOT)
                    .withZone(ZoneId.of("Asia/Tokyo"));
            DateTimeFormatter dtfDefaultLocale = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")
                    .withZone(ZoneId.of("Asia/Tokyo"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));

            // production code と同じ Locale.ROOT 明示版: ISO 年
            assertEquals("2026-04-30T21:00:00Z", dtfWithLocaleRoot.format(FIXED_INSTANT));
            // Locale 未指定 (デフォルト) でも ISO 年で同一 (Locale.ROOT 明示は意図表示目的のみ)
            assertEquals("2026-04-30T21:00:00Z", dtfDefaultLocale.format(FIXED_INSTANT));
            // SDF: th_TH では BuddhistCalendar が選ばれ仏暦年 (BE = CE + 543) を出力する
            // これは元の AozoraEpub3 の挙動だが、本 PR では ISO 化される (意図的非互換)
            assertEquals("2569-04-30T21:00:00Z", sdf.format(Date.from(FIXED_INSTANT)));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, origFormat);
        }
    }
}
