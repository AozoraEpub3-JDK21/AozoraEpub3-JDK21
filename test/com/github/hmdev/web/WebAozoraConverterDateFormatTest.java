package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.File;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;

import org.junit.Test;

/**
 * WebAozoraConverter.dateFormat が「変換日時」表示の SimpleDateFormat 互換挙動を維持していることを
 * 検証するテスト (ステージ 0B-3)。
 *
 * <p>byte-identical 比較側 (.NET ポート JavaComparisonTests) は NormalizeEpubText で
 * タイムスタンプを正規化するため、format 文字列の崩れを検出できない。本テストは
 * 固定 Instant に対する formatter の出力を未正規化のまま厳密にアサートする。</p>
 *
 * <p>WebAozoraConverter.dateFormat は instance final フィールドであり、TZ はインスタンス生成時に
 * 捕捉される (元コード SimpleDateFormat と同等)。本テストは新規 WebAozoraConverter インスタンスを
 * 作って dateFormat を取り出し、その捕捉済 zone で legacy SimpleDateFormat を作って等価比較する。</p>
 */
public class WebAozoraConverterDateFormatTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-30T12:00:00Z");

    /**
     * dateFormat が legacy SimpleDateFormat と等価な出力を返すことを確認。
     * formatter が捕捉済の zone を取り出し、同じ TZ を SimpleDateFormat にも設定して比較するため、
     * テスト実行時のシステム TZ に依存しない。
     */
    @Test
    public void formatterEquivalentToLegacySimpleDateFormat() throws Exception {
        File emptyConfigDir = Files.createTempDirectory("aozora-webconv-test").toFile();
        emptyConfigDir.deleteOnExit();

        // fqdn が configPath 配下に無くても constructor は早期 return し、
        // final dateFormat フィールドは初期化済になる
        WebAozoraConverter converter = new WebAozoraConverter("test.example.invalid", emptyConfigDir);

        DateTimeFormatter dateFormat = converter.dateFormat;
        ZoneId capturedZone = dateFormat.getZone();
        assertNotNull("dateFormat は zone を捕捉済であること", capturedZone);

        SimpleDateFormat legacy = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
        legacy.setTimeZone(TimeZone.getTimeZone(capturedZone));
        String expected = legacy.format(Date.from(FIXED_INSTANT));

        String actual = dateFormat.format(FIXED_INSTANT);

        assertEquals(expected, actual);
    }

    /**
     * パターン文字列 "yyyy/MM/dd HH:mm:ss" がローカル時刻を出力することを Asia/Tokyo 固定で確認。
     */
    @Test
    public void patternProducesLocalTime() {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                .withZone(ZoneId.of("Asia/Tokyo"));

        String formatted = formatter.format(FIXED_INSTANT);

        // 2026-04-30T12:00:00Z は JST で 2026/04/30 21:00:00
        assertEquals("2026/04/30 21:00:00", formatted);
    }

    /**
     * Codex [P2] 反映 / 意図的非互換 (Option B): 非グレゴリオロケール (th_TH 仏暦) で
     * dateFormat は **ISO/Gregorian 年を出力する**ことをロックダウン。詳細は
     * Epub3WriterDateFormatTest#normalizesNonGregorianLocaleToIsoYear と
     * docs/stage-0b3-plan.md §4.2 を参照。
     */
    @Test
    public void normalizesNonGregorianLocaleToIsoYear() {
        Locale origFormat = Locale.getDefault(Locale.Category.FORMAT);
        try {
            Locale.setDefault(Locale.Category.FORMAT, Locale.of("th", "TH"));

            DateTimeFormatter dtfWithLocaleRoot = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                    .withLocale(Locale.ROOT)
                    .withZone(ZoneId.of("Asia/Tokyo"));
            DateTimeFormatter dtfDefaultLocale = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss")
                    .withZone(ZoneId.of("Asia/Tokyo"));
            SimpleDateFormat sdf = new SimpleDateFormat("yyyy/MM/dd HH:mm:ss");
            sdf.setTimeZone(TimeZone.getTimeZone("Asia/Tokyo"));

            assertEquals("2026/04/30 21:00:00", dtfWithLocaleRoot.format(FIXED_INSTANT));
            assertEquals("2026/04/30 21:00:00", dtfDefaultLocale.format(FIXED_INSTANT));
            assertEquals("2569/04/30 21:00:00", sdf.format(Date.from(FIXED_INSTANT)));
        } finally {
            Locale.setDefault(Locale.Category.FORMAT, origFormat);
        }
    }
}
