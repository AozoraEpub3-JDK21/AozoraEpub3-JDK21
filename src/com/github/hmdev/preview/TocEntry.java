package com.github.hmdev.preview;

import java.util.List;

/**
 * 目次の 1 項目。階層構造を持つ。
 *
 * <p>本プロジェクトが生成する目次は {@code text00001.xhtml#chapter1} のように
 * フラグメント付きのリンクを持つため、遷移先セクションとフラグメントを分けて保持する。
 * ({@code template/OPS/toc.ncx.vm} / {@code template/OPS/xhtml/xhtml_nav.vm} 参照)</p>
 *
 * @param label 表示ラベル
 * @param path EPUB ルートからの相対パス (フラグメントを除く)
 * @param fragment 遷移先の id (無ければ null)
 * @param spineIndex 対応する spine のインデックス。spine に無い場合は -1
 * @param children 子項目 (無ければ空リスト)
 */
public record TocEntry(String label, String path, String fragment, int spineIndex, List<TocEntry> children) {}
