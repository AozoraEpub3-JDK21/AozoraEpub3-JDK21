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
	tocOpen: true,
	/** 本の右クリックに「Kindle に送る」を出すか (internal #55)。Kindle を使わない人には要らないので既定は出さない */
	sendToKindle: false
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
	/**
	 * EPUB 情報の要求の通し番号。本を読み込み直す・切り替えるたびに進め、要求したときと違う返りは捨てる
	 * (読み込み直しでは bookId が変わらないので、bookId だけ見ると前の版の返りで新しい情報を上書きする。internal #23)
	 */
	inspectionSeq: 0,
	/** 表示設定。localStorage とサーバの両方に保存する (viewer-settings.js) */
	settings: Object.assign({}, DEFAULT_SETTINGS),
	/**
	 * 遷移先のフラグメント。目次クリックで積み、iframe の load 後に消費する
	 * (積む: viewer-toc.js / 消費: viewer-frame.js)
	 */
	pendingFragment: null,
	/** 遷移後に末尾へスクロールするか。前ページへ戻るときに使う (同上) */
	pendingAtEnd: false,
	/** 棚のフォルダ名。棚が 1 つのときだけ入る (複数なら null。viewer-core.js が api/session から取る) */
	libraryFolder: null,
	/** 棚の数。0 = 棚を読み込んでいない (viewer-core.js が api/session から取る) */
	libraryShelfCount: 0,
	/** 棚の冊数。null = 未取得 (viewer-core.js が api/session から、以後は viewer-library.js が更新) */
	libraryCount: null,
	/** 表示する棚の添字。-1 = すべての棚 (viewer-library.js) */
	libraryShelf: -1,
	/** /api/library のレスポンス。本棚を開くたびに取り直す (viewer-library.js) */
	library: null,
	/** 本棚を表示中か (viewer-library.js) */
	libraryOpen: false
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
	// 本棚は自分のイベントを自分で bind する (bindEvents を肥大させない)
	bindLibraryEvents();
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

	startInbox(tab);

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

/** 見えている間、受け箱を見に行く間隔 (サーバの INBOX_POLL_MILLIS と合わせる) */
const INBOX_POLL = 1000;

/**
 * 受け箱を見に行く (PreviewServer#serveInbox)。アプリで本や本棚を開いたとき、見えているタブがあれば
 * 新しいタブを開かずにここへ URL が届く。見えていない間は行かない (行くこと自体が「見えている」の知らせ)
 */
function startInbox(tab)
{
	const check = () => {
		if (document.hidden) return;
		const focus = document.hasFocus() ? '&focus=1' : '&focus=0';
		fetch('api/inbox?' + tab + focus, {method: 'POST', cache: 'no-store'})
			.then(response => (response.status === 200) ? response.json() : null)
			.then(json => {
				if (!json || typeof json.open !== 'string') return;
				// 同じサーバの URL だけを開く
				const target = new URL(json.open, location.href);
				if (target.origin !== location.origin) return;
				// 受け取ったと返事をしてから移る。返事が届かなければ、アプリは新しいタブを開く
				const ack = (typeof json.ack === 'string') ? json.ack : '';
				fetch('api/inbox?' + tab + '&ack=' + encodeURIComponent(ack), {method: 'POST', cache: 'no-store', keepalive: true})
					.catch(() => { /* 返事が届かなくても移る (アプリは新しいタブも開く) */ })
					.finally(() => location.assign(target.href));
			})
			.catch(() => {
				/* サーバが既に終了している場合は何もしない */
			});
	};
	setInterval(check, INBOX_POLL);
	// 手前に戻ったらすぐに見に行く。奥へ回ったら、候補から外してもらう (外さないと、来ないタブを数秒待たせる)
	document.addEventListener('visibilitychange', () => {
		if (!document.hidden) {
			check();
			return;
		}
		fetch('api/inbox?' + tab + '&hidden=1', {method: 'POST', cache: 'no-store', keepalive: true})
			.catch(() => { /* サーバが既に終了している */ });
	});
	check();
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
		'revealFolder', 'settingsToggle', 'themeToggle', 'inspectToggle', 'tocPanel', 'tocTree', 'frame', 'pageHint',
		'inspectPanel', 'inspectClose', 'inspectTabs', 'settingsPopover', 'fontSelect', 'gothicSelect',
		'fontScale', 'fontScaleOut', 'lineHeight', 'lineHeightOut',
		'marginBlock', 'marginBlockOut', 'marginInline', 'marginInlineOut',
		'themeSelect', 'settingsReset', 'pageLeft', 'pageRight',
		'mainBody', 'libraryToggle', 'libraryView', 'libraryFolderName', 'libraryShelfSelect',
		'libraryFilter', 'librarySort',
		'libraryReload', 'libraryClose', 'libraryStatus', 'libraryGrid',
		'libraryDownloadToggle', 'libraryDownload', 'libraryDownloadForm', 'libraryDownloadUrl', 'libraryDownloadGo',
		'libraryShelfAsk', 'libraryShelfPath', 'libraryShelfUse', 'libraryShelfPick', 'libraryShelfNote', 'libraryDownloadStatus',
		'libraryQueueList', 'libraryQueueNote', 'libraryQueueRefusal',
		'libraryCheckAll',
		'libraryUpdateBand', 'libraryUpdateText', 'libraryUpdateLink', 'libraryUpdateClose', 'updateCheckField', 'updateCheckEnabled', 'sendToKindleField', 'sendToKindleEnabled',
		'libraryRankingToggle', 'libraryRanking', 'rankingSite', 'rankingPeriod', 'rankingGenre', 'rankingSerial', 'rankingSerialLabel',
		'rankingSerialBox', 'rankingNarouFilters', 'rankingWord', 'rankingType', 'rankingLength', 'rankingStop',
		'rankingNotR15', 'rankingNotTensei', 'rankingNotTenni',
		'rankingStatus', 'rankingList'];
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

	state.libraryFolder = session.libraryFolder || null;
	state.libraryShelfCount = session.libraryShelfCount || 0;
	state.libraryCount = (session.libraryCount === undefined) ? null : session.libraryCount;
	updateLibraryAvailability();

	const params = new URLSearchParams(location.search);
	state.bookId = params.get('book') || session.defaultBookId;
	if (!state.bookId) {
		// 棚だけを読み込んだ起動 (--library <フォルダ>)。本棚から選んでもらう
		if (!state.libraryShelfCount) throw new Error('プレビュー対象の EPUB が登録されていません');
		setBookControlsEnabled(false);
		el.bookTitle.textContent = '本棚';
		el.bookCreator.textContent = libraryPlaceLabel();
		await openLibrary();
		return;
	}

	await loadBook();
}

/**
 * 本が開かれていない状態では、本に対する操作を押せなくする。
 * 押せてしまうと、bookId が無いまま要求が飛んで無言で失敗する。
 */
function setBookControlsEnabled(enabled)
{
	for (const button of [el.tocToggle, el.prevSection, el.nextSection, el.sectionSelect,
		el.revealFolder, el.inspectToggle]) {
		button.disabled = !enabled;
	}
	// 目次パネルは設定に従う。閉じたまま本を開いても勝手に開かない
	el.tocPanel.hidden = enabled ? !state.settings.tocOpen : true;
	el.tocToggle.setAttribute('aria-pressed', String(!el.tocPanel.hidden));
	if (!enabled) {
		el.inspectPanel.hidden = true;
		el.inspectToggle.setAttribute('aria-pressed', 'false');
	}
}

/**
 * 本を読み込む。keepPlace なら、新しい本にも今のセクションと同じパスがあればそこを開く
 * (本棚の「続きを取る」で開いている本が上書きされたとき、読んでいた所に留まる)。
 * 今のセクションは読み込みが返ってから見る (待っている間に読み進めた所を巻き戻さない。PR #119 の codex)
 */
async function loadBook(keepPlace)
{
	const book = await getJson('api/book/' + encodeURIComponent(state.bookId));
	const current = (keepPlace && state.book && state.book.spine) ? state.book.spine[state.spineIndex] : null;
	const keepPath = current ? current.path : null;
	// 本文が 1 つも無い本を黙って受け入れると、書名だけ新しくなって
	// iframe には前の本が映ったまま残る (gotoSection が何もせずに返るため)
	if (!book.spine || book.spine.length === 0) {
		throw new Error('本文が見つかりません (spine が空の EPUB です)');
	}
	setBookControlsEnabled(true);
	state.book = book;
	document.title = (book.title || 'EPUB') + ' — AozoraEpub3 プレビュー';
	el.bookTitle.textContent = book.title || '(無題)';
	el.bookCreator.textContent = book.creator || '';

	buildSectionSelect();
	buildToc();
	const kept = keepPath ? book.spine.findIndex(item => item.path === keepPath) : -1;
	// 同じパスでも中身は新しい版なので、iframe を必ず読み直させる
	if (keepPlace) el.frame.removeAttribute('data-path');
	gotoSection(kept >= 0 ? kept : 0, null);
}

/** フォルダを開くボタンの既定 title。失敗時に書き換えるので復元用に持つ */
const REVEAL_TITLE = 'EPUB のあるフォルダを開く';

/**
 * EPUB のあるフォルダを OS のファイラで開く。
 *
 * <p>ブラウザからファイラは開けないのでサーバに依頼する。
 * 開く対象はサーバが bookId から解決するため、パスは送らない。</p>
 */
async function revealBook(bookId = state.bookId)
{
	if (!bookId) return;
	const response = await fetch('api/book/' + encodeURIComponent(bookId) + '/reveal',
		{method: 'POST', cache: 'no-store'});
	if (!response.ok) throw new Error('HTTP ' + response.status);
}

