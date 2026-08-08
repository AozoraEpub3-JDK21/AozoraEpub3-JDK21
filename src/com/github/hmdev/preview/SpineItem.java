package com.github.hmdev.preview;

/**
 * spine (読み順) の 1 項目。
 *
 * @param idref manifest の id
 * @param path EPUB ルートからの相対パス
 * @param mediaType media-type
 */
public record SpineItem(String idref, String path, String mediaType) {}
