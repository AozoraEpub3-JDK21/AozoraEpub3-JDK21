/*
 * AozoraEpub3 プレビュー ビューアー
 *
 * 設計メモ (docs/epub-preview-plan.md):
 * - 本文フォントの上書きは html, body だけに !important を当てる。
 *   span や div にまで当てると、dakutenType=2 で埋め込まれた濁点合成フォント
 *   (.dakutenXXXX に font-family が直接指定されている) を潰してしまう。
 * - vertical-rl のスクロール原点は右端にあり scrollLeft は 0 〜 負値を取る。
 *   符号を自前で計算せず scrollBy() に相対値を渡し、動いたかどうかで端を判定する。
 * - ダークモードで filter: invert() は使わない。挿絵の見え方確認が壊れるため。
 */
'use strict';

const STORAGE_KEY = 'aozoraepub3.preview.settings';

const DEFAULT_SETTINGS = {
	theme: 'system',
	/**
	 * null = 未設定。サーバが薦める既定フォント (UD デジタル教科書体など) を適用する。
	 * 空文字は「EPUB の指定のまま」をユーザーが明示的に選んだ状態で、上書きしない。
	 */
	fontFamily: null,
	gothicFamily: '',
	fontScale: 100,
	lineHeight: 1.8,
	/** 本文の上下余白 (em)。EPUB 側は余白 0 で出力されるため、既定で少し空ける */
	marginBlock: 1.5,
	/** 本文の左右余白 (em) */
	marginInline: 1.5,
	tocOpen: true
};

/**
 * ビューアー全体で共有する状態。
 *
 * <p>ファイルを分割したので、フィールドを動的に生やすと「どこで生まれたか」を
 * grep しないと追えなくなる。<b>全フィールドをここに宣言し、主な書き手を併記すること</b>。</p>
 */
const state = {
	/** 表示中の書籍 ID (viewer-core.js が URL から決める) */
	bookId: null,
	/** /api/book のレスポンス。spine を含む (viewer-core.js) */
	book: null,
	/** インストール済みフォント一覧 (viewer-settings.js) */
	fonts: null,
	/** 表示中の spine 位置 (viewer-toc.js / viewer-frame.js) */
	spineIndex: 0,
	/** /inspect のレスポンス。初回表示時に遅延取得 (viewer-inspector.js) */
	inspection: null,
	/** 表示設定。localStorage とサーバの両方に保存する (viewer-settings.js) */
	settings: Object.assign({}, DEFAULT_SETTINGS),
	/**
	 * 遷移先のフラグメント。目次クリックで積み、iframe の load 後に消費する
	 * (積む: viewer-toc.js / 消費: viewer-frame.js)
	 */
	pendingFragment: null,
	/** 遷移後に末尾へスクロールするか。前ページへ戻るときに使う (同上) */
	pendingAtEnd: false
};

/** キャッシュした DOM 要素。cacheElements() が一括で埋める */
const el = {};

// ------------------------------------------------------------------
// 起動
// ------------------------------------------------------------------

document.addEventListener('DOMContentLoaded', () => {
	cacheElements();
	loadSettings();
	bindEvents();
	applyTheme();
	startHeartbeat();
	init().catch(err => showFatal(err));
});

/**
 * サーバへ「まだ見ている」ことを伝える。
 * CLI プレビューはこれが途絶えたことでブラウザが閉じられたと判断して自ら終了する。
 * ブラウザの終了を直接検知する API は無いため、この方式を採る。
 *
 * 注意: バックグラウンドタブでは setInterval が抑制され (Chrome は hidden タブを
 * 概ね 1 分に 1 回まで間引き、条件によっては freeze する)、定期送信だけに頼ると
 * 「別のタブを見ていただけなのにサーバが終了する」ことが起こる。
 * そのため送信間隔より十分長い猶予をサーバ側に持たせたうえで、
 * 表示に戻った瞬間にも即座に送る。
 */
function startHeartbeat()
{
	// タブごとに ID を持たせる。サーバはタブ単位で生存を管理するので、
	// 2 つ開いて片方を閉じても、残ったタブが巻き添えで終了させられない
	const tab = 'tab=' + encodeURIComponent(newTabId());
	const beat = () => {
		fetch('api/heartbeat?' + tab, {method: 'POST', cache: 'no-store', keepalive: true})
			.catch(() => {
				/* サーバが既に終了している場合は何もしない */
			});
	};
	beat();
	setInterval(beat, 15000);

	// 抑制されていたタイマーの穴を埋める
	document.addEventListener('visibilitychange', () => {
		if (!document.hidden) beat();
	});
	window.addEventListener('pageshow', beat);
	window.addEventListener('focus', beat);

	// タブを閉じたことを明示的に伝える。これで待たずに終了できる。
	// bfcache への退避でも発火するが、復帰時は pageshow の beat で登録し直される
	window.addEventListener('pagehide', () => {
		try {
			navigator.sendBeacon('api/bye?' + tab, '');
		} catch (e) {
			/* sendBeacon が使えなければ猶予切れによる終了に任せる */
		}
	});
}

/** タブを識別する ID を作る。crypto が使えない環境でも動くようフォールバックする */
function newTabId()
{
	try {
		if (window.crypto && typeof window.crypto.randomUUID === 'function') {
			return window.crypto.randomUUID();
		}
	} catch (e) {
		/* フォールバックへ */
	}
	return 't' + Date.now().toString(36) + Math.floor(Math.random() * 1e9).toString(36);
}

function cacheElements()
{
	const ids = ['tocToggle', 'bookTitle', 'bookCreator', 'prevSection', 'sectionSelect', 'nextSection',
		'settingsToggle', 'themeToggle', 'inspectToggle', 'tocPanel', 'tocTree', 'frame', 'pageHint',
		'inspectPanel', 'inspectClose', 'inspectTabs', 'settingsPopover', 'fontSelect', 'gothicSelect',
		'fontScale', 'fontScaleOut', 'lineHeight', 'lineHeightOut',
		'marginBlock', 'marginBlockOut', 'marginInline', 'marginInlineOut',
		'themeSelect', 'settingsReset', 'pageLeft', 'pageRight'];
	for (const id of ids) el[id] = document.getElementById(id);
}

async function init()
{
	await loadRemoteSettings();
	const session = await getJson('api/session');
	state.fonts = session.fonts;
	applyDefaultFontIfUnset();
	buildFontSelects();
	reapplyStyle();

	const params = new URLSearchParams(location.search);
	state.bookId = params.get('book') || session.defaultBookId;
	if (!state.bookId) throw new Error('プレビュー対象の EPUB が登録されていません');

	await loadBook();
}

async function loadBook()
{
	const book = await getJson('api/book/' + encodeURIComponent(state.bookId));
	state.book = book;
	document.title = (book.title || 'EPUB') + ' — AozoraEpub3 プレビュー';
	el.bookTitle.textContent = book.title || '(無題)';
	el.bookCreator.textContent = book.creator || '';

	buildSectionSelect();
	buildToc();
	gotoSection(0, null);
}

