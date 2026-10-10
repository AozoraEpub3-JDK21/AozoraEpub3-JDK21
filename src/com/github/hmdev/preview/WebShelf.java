package com.github.hmdev.preview;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Web 本棚（internal #11 の案 A）。本棚の画面から新しく落とした本を置くフォルダ。
 *
 * <p>場所は設定（ini）に持つ。GUI は終わるときに自分の設定で ini を書くので、サーバが ini を直接書くと上書きされる。
 * そこで、本棚を開く側（GUI・CLI）が読み書きを渡す（{@link BookUpdater} と同じ形）。</p>
 */
public interface WebShelf
{
	/** 決まっている場所。まだなら null */
	Path location();

	/**
	 * 場所を決めて設定に保存する（開く側の棚の一覧にも入れる）。フォルダはできていて、書き込めることを確かめてから呼ばれる。
	 * 起動中の本棚に棚を足すのはサーバがする
	 */
	void setLocation(Path dir) throws IOException;

	/**
	 * アプリの側でフォルダ選択を出す（ブラウザからはフォルダを選べない）。選ばなかったら null。
	 * 画面の無い環境では {@link UnsupportedOperationException}
	 * @param initial 最初に開くフォルダ（無ければ null）
	 */
	default Path pickFolder(Path initial) throws Exception
	{
		throw new UnsupportedOperationException();
	}

	/** フォルダ選択を出せるか */
	default boolean canPick()
	{
		return false;
	}
}
