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

const state = {
	bookId: null,
	book: null,
	fonts: null,
	spineIndex: 0,
	inspection: null,
	settings: Object.assign({}, DEFAULT_SETTINGS)
};

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

// ------------------------------------------------------------------
// 設定
// ------------------------------------------------------------------

function loadSettings()
{
	try {
		const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) || '{}');
		Object.assign(state.settings, saved);
	} catch (e) {
		/* 保存値が壊れていても既定値で続行する */
	}
	syncSettingsInputs();
	el.tocPanel.hidden = !state.settings.tocOpen;
	el.tocToggle.setAttribute('aria-pressed', String(state.settings.tocOpen));
}

/**
 * サーバに保存された設定を読み込む。
 * プレビューサーバは毎回ランダムポートで起動し、localStorage は
 * スキーム + ホスト + ポートで分離されるため、localStorage だけでは設定が毎回失われる。
 * サーバ側のファイルを正とし、localStorage は起動直後の表示用キャッシュとして扱う。
 */
async function loadRemoteSettings()
{
	try {
		const remote = await getJson('api/settings');
		if (remote && typeof remote === 'object') {
			Object.assign(state.settings, remote);
			syncSettingsInputs();
			el.tocPanel.hidden = !state.settings.tocOpen;
			el.tocToggle.setAttribute('aria-pressed', String(state.settings.tocOpen));
			applyTheme();
		}
	} catch (e) {
		/* サーバに設定が無い / 読めない場合は localStorage と既定値で続行する */
	} finally {
		// 成否に関わらず、以降の変更は保存してよい
		remoteSettingsLoaded = true;
	}
}

function saveSettings()
{
	try {
		localStorage.setItem(STORAGE_KEY, JSON.stringify(state.settings));
	} catch (e) {
		/* プライベートモード等で保存できなくても動作は継続する */
	}
	scheduleRemoteSave();
}

let remoteSaveTimer = null;
/**
 * サーバ側の設定を読み終えるまで保存しない。
 * 起動直後の applyTheme() が保存を予約するため、これが無いと
 * GET が遅れた場合に既定値で保存ファイルを上書きしてしまう
 * (ポートが毎回変わるので localStorage は常に空 = 既定値)。
 */
let remoteSettingsLoaded = false;

/** スライダ操作のたびに書き込まないよう、少し待ってからサーバへ保存する */
function scheduleRemoteSave()
{
	if (!remoteSettingsLoaded) return;
	if (remoteSaveTimer !== null) clearTimeout(remoteSaveTimer);
	remoteSaveTimer = setTimeout(() => {
		remoteSaveTimer = null;
		fetch('api/settings', {
			method: 'POST',
			headers: {'Content-Type': 'application/json'},
			body: JSON.stringify(state.settings)
		}).catch(() => {
			/* 保存に失敗してもプレビューの利用は続けられる */
		});
	}, 400);
}

/** スライダ・セレクトの表示値を state に合わせる */
function syncSettingsInputs()
{
	el.fontScale.value = state.settings.fontScale;
	el.lineHeight.value = state.settings.lineHeight;
	el.marginBlock.value = state.settings.marginBlock;
	el.marginInline.value = state.settings.marginInline;
	el.themeSelect.value = state.settings.theme;
	updateSettingsOutputs();
}

function updateSettingsOutputs()
{
	el.fontScaleOut.textContent = state.settings.fontScale + '%';
	el.lineHeightOut.textContent = Number(state.settings.lineHeight).toFixed(2);
	el.marginBlockOut.textContent = Number(state.settings.marginBlock) + 'em';
	el.marginInlineOut.textContent = Number(state.settings.marginInline) + 'em';
}

/**
 * フォント未設定なら、環境にある推奨フォントを既定として適用する。
 * 空文字 (EPUB の指定のまま) をユーザーが選んでいる場合は上書きしない。
 */
function applyDefaultFontIfUnset()
{
	dropUninstalledFonts();
	if (state.settings.fontFamily !== null && state.settings.fontFamily !== undefined) return;
	state.settings.fontFamily = (state.fonts && state.fonts.defaultBody) ? state.fonts.defaultBody : '';
}

/**
 * 設定に入っているフォントがこの環境に無ければ捨てる。
 * 残したままだと、選択欄は「EPUB の指定のまま」を表示しているのに
 * 存在しないファミリを !important で当て続ける、という食い違いが起きる
 * (設定を別マシンへ持ち込んだ場合やフォントを削除した場合)。
 */
function dropUninstalledFonts()
{
	const all = (state.fonts && state.fonts.all) ? state.fonts.all : [];
	if (all.length === 0) return;
	if (state.settings.fontFamily && !all.includes(state.settings.fontFamily)) {
		state.settings.fontFamily = null;
	}
	if (state.settings.gothicFamily && !all.includes(state.settings.gothicFamily)) {
		state.settings.gothicFamily = '';
	}
}

function buildFontSelects()
{
	const fonts = state.fonts || {mincho: [], gothic: [], other: [], all: []};

	fillFontSelect(el.fontSelect, 'EPUB の指定のまま', [
		['推奨 明朝', fonts.mincho],
		['推奨 ゴシック', fonts.gothic],
		['その他', fonts.other],
		['インストール済み', fonts.all]
	], state.settings.fontFamily);

	fillFontSelect(el.gothicSelect, 'EPUB の指定のまま', [
		['推奨 ゴシック', fonts.gothic],
		['推奨 明朝', fonts.mincho],
		['その他', fonts.other],
		['インストール済み', fonts.all]
	], state.settings.gothicFamily);
}

function fillFontSelect(select, emptyLabel, groups, selected)
{
	select.textContent = '';
	const none = document.createElement('option');
	none.value = '';
	none.textContent = emptyLabel;
	select.appendChild(none);

	const seen = new Set();
	for (const [label, families] of groups) {
		if (!families || families.length === 0) continue;
		const group = document.createElement('optgroup');
		group.label = label;
		let added = 0;
		for (const family of families) {
			if (seen.has(family)) continue;
			seen.add(family);
			const option = document.createElement('option');
			option.value = family;
			option.textContent = family;
			group.appendChild(option);
			added++;
		}
		if (added > 0) select.appendChild(group);
	}
	select.value = selected || '';
	if (select.value !== (selected || '')) select.value = '';
}

// ------------------------------------------------------------------
// セクション / 目次
// ------------------------------------------------------------------

function buildSectionSelect()
{
	el.sectionSelect.textContent = '';
	const labels = sectionLabels();
	state.book.spine.forEach((item, index) => {
		const option = document.createElement('option');
		option.value = String(index);
		option.textContent = (index + 1) + '. ' + (labels[index] || fileNameOf(item.path));
		el.sectionSelect.appendChild(option);
	});
}

/** 目次のラベルを spine インデックスへ割り当てる (最初に見つかったものを採用) */
function sectionLabels()
{
	const labels = {};
	walkToc(state.book.toc, entry => {
		if (entry.spineIndex >= 0 && labels[entry.spineIndex] === undefined) {
			labels[entry.spineIndex] = entry.label;
		}
	});
	return labels;
}

function walkToc(entries, callback)
{
	for (const entry of entries || []) {
		callback(entry);
		walkToc(entry.children, callback);
	}
}

function buildToc()
{
	el.tocTree.textContent = '';
	const root = renderTocList(state.book.toc);
	if (root) el.tocTree.appendChild(root);
	highlightToc();
}

function renderTocList(entries)
{
	if (!entries || entries.length === 0) return null;
	const list = document.createElement('ul');
	for (const entry of entries) {
		const item = document.createElement('li');
		const anchor = document.createElement('a');
		anchor.textContent = entry.label;
		if (entry.spineIndex >= 0) {
			anchor.href = '#';
			anchor.dataset.spineIndex = String(entry.spineIndex);
			if (entry.fragment) anchor.dataset.fragment = entry.fragment;
			anchor.addEventListener('click', event => {
				event.preventDefault();
				gotoSection(entry.spineIndex, entry.fragment);
			});
		} else {
			anchor.className = 'disabled';
		}
		item.appendChild(anchor);
		const children = renderTocList(entry.children);
		if (children) item.appendChild(children);
		list.appendChild(item);
	}
	return list;
}

function highlightToc()
{
	for (const anchor of el.tocTree.querySelectorAll('a')) {
		anchor.classList.toggle('current', anchor.dataset.spineIndex === String(state.spineIndex));
	}
}

/**
 * セクションを表示する。
 * @param {number} index spine インデックス
 * @param {?string} fragment 遷移先の id (本プロジェクトの目次は #chapterId 付き)
 */
function gotoSection(index, fragment, atEnd)
{
	if (!state.book || state.book.spine.length === 0) return;
	const clamped = Math.max(0, Math.min(index, state.book.spine.length - 1));
	const path = state.book.spine[clamped].path;
	const url = 'book/' + encodeURIComponent(state.bookId) + '/' + encodePath(path);

	state.spineIndex = clamped;
	state.pendingFragment = fragment || null;
	// 読み戻しで前のセクションへ移るときは、その末尾に着地させないと後ろ向きに読み進められない
	state.pendingAtEnd = !!atEnd;
	el.sectionSelect.value = String(clamped);
	highlightToc();

	const currentSrc = el.frame.getAttribute('data-path');
	if (currentSrc === path) {
		// 同じセクション内の移動はリロードせずスクロールだけ行う
		applyPendingFragment();
	} else {
		el.frame.setAttribute('data-path', path);
		el.frame.src = url;
	}
	updateHint();
}

function stepSection(delta, atEnd)
{
	gotoSection(state.spineIndex + delta, null, atEnd);
}

// ------------------------------------------------------------------
// iframe への介入
// ------------------------------------------------------------------

function onFrameLoad()
{
	const doc = frameDoc();
	if (!doc) return;
	syncStateFromFrame(doc);
	injectStyle(doc);
	attachFrameInput(doc);
	applyPendingFragment();
	updateHint();
	if (!el.inspectPanel.hidden) renderInspector();
}

function frameDoc()
{
	try {
		return el.frame.contentDocument;
	} catch (e) {
		return null;
	}
}

/**
 * iframe が実際に表示しているパスから状態を作り直す。
 *
 * 目次ページ (nav.xhtml) も spine に含まれるため、その中のリンクを踏むと
 * iframe だけが遷移する。追随しないとセクション選択・目次ハイライト・
 * ページ送りが実際の表示とずれ、同じページを選び直しても再読込されなくなる。
 */
function syncStateFromFrame(doc)
{
	if (!state.book) return;
	let pathname = '';
	let hash = '';
	let path = '';
	try {
		pathname = doc.location ? doc.location.pathname : '';
		hash = doc.location ? doc.location.hash : '';
		const marker = '/book/' + encodeURIComponent(state.bookId) + '/';
		const at = pathname.indexOf(marker);
		if (at < 0) return;
		// 壊れたエスケープでも onFrameLoad の後続処理 (CSS 注入・キー操作) を止めない
		path = pathname.substring(at + marker.length)
			.split('/').map(decodeURIComponent).join('/');
	} catch (e) {
		return;
	}
	if (!path) return;

	// iframe 内のリンク (目次ページの xxx.xhtml#chapterId など) で遷移した場合、
	// ブラウザは既にアンカーへスクロールしている。
	// ここで拾っておかないと applyPendingFragment が先頭へ戻してしまう。
	// ビューアー発の遷移では hash が空なので副作用は無い
	if (hash && hash.length > 1 && !state.pendingFragment) {
		try {
			state.pendingFragment = decodeURIComponent(hash.substring(1));
		} catch (e) {
			state.pendingFragment = hash.substring(1);
		}
	}

	el.frame.setAttribute('data-path', path);
	const index = state.book.spine.findIndex(item => item.path === path);
	if (index < 0 || index === state.spineIndex) return;

	state.spineIndex = index;
	el.sectionSelect.value = String(index);
	highlightToc();
}

function applyPendingFragment()
{
	const doc = frameDoc();
	if (!doc) return;
	const fragment = state.pendingFragment;
	const atEnd = state.pendingAtEnd;
	state.pendingFragment = null;
	state.pendingAtEnd = false;
	if (!fragment) {
		if (atEnd) scrollToEnd(doc);
		else scrollToStart(doc);
		return;
	}
	const target = doc.getElementById(fragment);
	if (target) {
		target.scrollIntoView({block: 'start', inline: 'start'});
	} else {
		scrollToStart(doc);
	}
}

function scrollToStart(doc)
{
	const scroller = doc.scrollingElement || doc.documentElement;
	scroller.scrollTo(0, 0);
}

/**
 * 読み方向の末尾へ移動する。
 * vertical-rl では scrollLeft の符号が処理系により異なるため、
 * 絶対位置を計算せず十分大きな相対値を渡してブラウザにクランプさせる。
 */
function scrollToEnd(doc)
{
	const scroller = doc.scrollingElement || doc.documentElement;
	const reading = readingAxis(doc);
	const far = 1e7 * reading.sign;
	if (reading.axis === 'x') scroller.scrollBy({left: far, top: 0, behavior: 'auto'});
	else scroller.scrollBy({left: 0, top: far, behavior: 'auto'});
}

function isVertical(doc)
{
	return readingAxis(doc).axis === 'x';
}

/**
 * 読み進む方向を返す。
 * 縦書き (vertical-rl) は右から左へ、vertical-lr は左から右へ、横書きは上から下へ流れる。
 * scrollLeft の符号は writing-mode により意味が変わるので、
 * 「どちらの軸を、どちら向きに動かすと読み進むか」だけをここで決める。
 */
function readingAxis(doc)
{
	let mode = '';
	try {
		mode = String(getComputedStyle(doc.documentElement).writingMode || '');
	} catch (e) {
		mode = '';
	}
	if (mode.startsWith('vertical') || mode === 'tb-rl' || mode === 'tb-lr') {
		// vertical-rl / tb-rl は左向きが読み進む方向
		const leftward = !(mode === 'vertical-lr' || mode === 'tb-lr');
		return {axis: 'x', sign: leftward ? -1 : 1};
	}
	return {axis: 'y', sign: 1};
}

function injectStyle(doc)
{
	const head = doc.head || doc.documentElement;
	if (!head) return;
	let style = doc.getElementById('__aozora_preview_style');
	if (!style) {
		// XHTML (application/xhtml+xml) でも確実に XHTML 名前空間の style になるよう明示する。
		// createElement でも仕様上は同じ結果になるが、意図を残しておく
		style = doc.createElementNS('http://www.w3.org/1999/xhtml', 'style');
		style.id = '__aozora_preview_style';
		head.appendChild(style);
	} else {
		// 常に末尾に置き、EPUB 側の CSS より後に評価させる
		head.appendChild(style);
	}
	style.textContent = buildInjectedCss();
}

function buildInjectedCss()
{
	const settings = state.settings;
	const rules = [];

	if (settings.fontFamily) {
		// html, body にだけ当てる。span や div にまで !important を当てると
		// .dakutenXXXX (合成濁点フォント) を潰してしまう
		rules.push('html, body { font-family: ' + quoteFamily(settings.fontFamily) + ', serif !important; }');
	}
	if (settings.gothicFamily) {
		// vertical_font.css の .gtc/.b/.introduction 等は !important 付きなので明示的に上書きする
		rules.push('.gtc, .b, .custom_parameter_block, .introduction, .postscript {'
			+ ' font-family: ' + quoteFamily(settings.gothicFamily) + ', sans-serif !important; }');
	}
	// EPUB 側が scroll-behavior: smooth を指定していると、scrollBy 直後に位置を読む
	// ページ送りの端検出が常に「動かなかった」と誤判定してしまう
	rules.push('html { scroll-behavior: auto !important; }');
	rules.push('html { font-size: ' + settings.fontScale + '% !important; }');
	rules.push('body { line-height: ' + settings.lineHeight + ' !important; }');

	// 本文の余白。EPUB 側は html { margin:0; padding:0 } で出力されるため画面端まで詰まる。
	// 縦書きでは上下余白が行長を、左右余白が読み始め/読み終わりの余白を決める。
	rules.push('html {'
		+ ' box-sizing: border-box !important;'
		+ ' padding: ' + settings.marginBlock + 'em ' + settings.marginInline + 'em !important; }');

	if (isDark()) {
		rules.push('html, body { background-color: #191817 !important; color: #ddd8d0 !important; }');
		rules.push('a { color: #c9a87c !important; }');
		rules.push('.introduction, .postscript { color: #bdb7ae !important; border-color: #55504a !important; }');
		rules.push('.custom_parameter_block { border-color: #55504a !important; box-shadow: none !important; }');
		rules.push('hr { border-color: #55504a !important; }');
	}
	return rules.join('\n');
}

function quoteFamily(family)
{
	return '"' + String(family).replace(/"/g, '') + '"';
}

// ------------------------------------------------------------------
// ページ送り
// ------------------------------------------------------------------

/**
 * 1 画面ぶん読み進める / 戻る。
 * 縦書きでは読み進む方向が左向きになる。
 * scrollLeft の符号は writing-mode により意味が変わるため自前で計算せず、
 * scrollBy の前後で位置が動いたかどうかで端を判定する。
 */
function pageStep(forward)
{
	const doc = frameDoc();
	if (!doc) return;
	const scroller = doc.scrollingElement || doc.documentElement;
	const reading = readingAxis(doc);
	const horizontalAxis = reading.axis === 'x';

	const before = horizontalAxis ? scroller.scrollLeft : scroller.scrollTop;
	const viewport = horizontalAxis ? scroller.clientWidth : scroller.clientHeight;
	const step = Math.max(1, viewport * 0.94) * reading.sign * (forward ? 1 : -1);
	if (horizontalAxis) scroller.scrollBy({left: step, top: 0, behavior: 'auto'});
	else scroller.scrollBy({left: 0, top: step, behavior: 'auto'});
	const after = horizontalAxis ? scroller.scrollLeft : scroller.scrollTop;

	if (Math.abs(after - before) < 1) {
		// 端に達していたので隣のセクションへ移る。
		// 戻るときは前セクションの末尾に着地させる。
		// 端のページ送りボタンは本の読み込み前や init 失敗後も押せるので、
		// state.book が無い場合を必ず確認する
		if (!state.book) return;
		const next = state.spineIndex + (forward ? 1 : -1);
		if (next >= 0 && next < state.book.spine.length) stepSection(forward ? 1 : -1, !forward);
	}
	updateHint();
}

function updateHint()
{
	if (!state.book) return;
	const total = state.book.spine.length;
	const vertical = isVertical(frameDoc() || document);
	el.pageHint.textContent = (state.spineIndex + 1) + ' / ' + total
		+ ' · ' + (vertical ? '縦書き' : '横書き');
}

// ------------------------------------------------------------------
// イベント
// ------------------------------------------------------------------

function bindEvents()
{
	el.frame.addEventListener('load', onFrameLoad);

	el.tocToggle.addEventListener('click', () => {
		state.settings.tocOpen = el.tocPanel.hidden;
		el.tocPanel.hidden = !state.settings.tocOpen;
		el.tocToggle.setAttribute('aria-pressed', String(state.settings.tocOpen));
		saveSettings();
	});

	// 端のページ送りボタン。書字方向に応じて進む向きが変わる
	el.pageLeft.addEventListener('click', () => pageStep(isLeftForward()));
	el.pageRight.addEventListener('click', () => pageStep(!isLeftForward()));

	el.prevSection.addEventListener('click', () => stepSection(-1));
	el.nextSection.addEventListener('click', () => stepSection(1));
	el.sectionSelect.addEventListener('change', () => gotoSection(Number(el.sectionSelect.value), null));

	el.settingsToggle.addEventListener('click', () => {
		el.settingsPopover.hidden = !el.settingsPopover.hidden;
	});

	el.themeToggle.addEventListener('click', () => {
		const order = ['system', 'light', 'dark'];
		const next = order[(order.indexOf(state.settings.theme) + 1) % order.length];
		state.settings.theme = next;
		el.themeSelect.value = next;
		applyTheme();
		saveSettings();
	});

	el.themeSelect.addEventListener('change', () => {
		state.settings.theme = el.themeSelect.value;
		applyTheme();
		saveSettings();
	});

	el.fontSelect.addEventListener('change', () => {
		state.settings.fontFamily = el.fontSelect.value;
		reapplyStyle();
	});

	el.gothicSelect.addEventListener('change', () => {
		state.settings.gothicFamily = el.gothicSelect.value;
		reapplyStyle();
	});

	el.fontScale.addEventListener('input', () => {
		state.settings.fontScale = Number(el.fontScale.value);
		updateSettingsOutputs();
		reapplyStyle();
	});

	el.lineHeight.addEventListener('input', () => {
		state.settings.lineHeight = Number(el.lineHeight.value);
		updateSettingsOutputs();
		reapplyStyle();
	});

	el.marginBlock.addEventListener('input', () => {
		state.settings.marginBlock = Number(el.marginBlock.value);
		updateSettingsOutputs();
		reapplyStyle();
	});

	el.marginInline.addEventListener('input', () => {
		state.settings.marginInline = Number(el.marginInline.value);
		updateSettingsOutputs();
		reapplyStyle();
	});

	el.settingsReset.addEventListener('click', () => {
		const tocOpen = state.settings.tocOpen;
		state.settings = Object.assign({}, DEFAULT_SETTINGS, {tocOpen: tocOpen});
		applyDefaultFontIfUnset();
		el.fontSelect.value = state.settings.fontFamily;
		el.gothicSelect.value = '';
		syncSettingsInputs();
		applyTheme();
		reapplyStyle();
	});

	el.inspectToggle.addEventListener('click', () => toggleInspector());
	el.inspectClose.addEventListener('click', () => toggleInspector(false));

	el.inspectTabs.addEventListener('click', event => {
		const button = event.target.closest('button[data-tab]');
		if (!button) return;
		selectInspectorTab(button.dataset.tab);
	});

	document.addEventListener('keydown', onKeyDown);

	document.addEventListener('click', event => {
		if (el.settingsPopover.hidden) return;
		if (el.settingsPopover.contains(event.target) || el.settingsToggle.contains(event.target)) return;
		el.settingsPopover.hidden = true;
	});

	window.matchMedia('(prefers-color-scheme: dark)').addEventListener('change', () => {
		if (state.settings.theme === 'system') reapplyStyle();
	});
}

function attachFrameInput(doc)
{
	// iframe にフォーカスがあるときもキー操作を効かせる
	doc.addEventListener('keydown', onKeyDown);
	// マウスでもページを送れるようにする
	doc.addEventListener('click', onFrameClick);
	doc.addEventListener('wheel', onFrameWheel, {passive: false});
}

/**
 * 「次へ」が画面左向きか。
 *
 * これは<b>本単位</b>の綴じ方向 (OPF の page-progression-direction) で決める。
 * ページごとの writing-mode で決めると、横書きの扉・目次と縦書きの本文とで
 * 同じ側を押したときの意味が反転し、2 ページ間を行ったり来たりしてしまう。
 * (ページ内をどちら向きにスクロールするかは readingAxis がページ単位で判断する)
 */
function isLeftForward()
{
	if (state.book && state.book.pageProgressionDirection) {
		return state.book.pageProgressionDirection === 'rtl';
	}
	// 綴じ方向が書かれていない本は、今表示しているページの書字方向で代用する
	const reading = readingAxis(frameDoc() || document);
	return reading.axis === 'x' && reading.sign < 0;
}

/**
 * 本文の左右端をクリックしてページを送る。
 * 中央はリンク操作と文字選択のために空けておく。
 */
function onFrameClick(event)
{
	// 修飾キー付きのクリックは別操作 (新規タブで開く等) なので触らない
	if (event.button !== 0 || event.ctrlKey || event.shiftKey || event.altKey || event.metaKey) return;
	const doc = frameDoc();
	if (!doc || !doc.documentElement) return;

	// リンクを踏んだときはページ送りしない
	if (event.target && event.target.closest && event.target.closest('a')) return;
	// 文字を選択している最中も邪魔しない
	const selection = doc.getSelection ? doc.getSelection() : null;
	if (selection && String(selection).length > 0) return;

	const width = doc.documentElement.clientWidth || 1;
	const zone = width * 0.3;
	if (event.clientX > zone && event.clientX < width - zone) return;

	const leftForward = isLeftForward();
	pageStep(event.clientX <= zone ? leftForward : !leftForward);
	event.preventDefault();
}

/** ホイールが最後にページを送った時刻。1 ノッチで飛びすぎないよう間引く */
let lastWheelPageAt = 0;

function onFrameWheel(event)
{
	// Ctrl+ホイールはブラウザのズームなので触らない
	if (event.ctrlKey) return;

	const useHorizontal = Math.abs(event.deltaX) > Math.abs(event.deltaY);
	const delta = useHorizontal ? event.deltaX : event.deltaY;
	if (delta === 0) return;

	// 自前でページ単位に送るので、既定のスクロールは止める
	event.preventDefault();

	const now = (window.performance && performance.now) ? performance.now() : Date.now();
	if (now - lastWheelPageAt < 180) return;
	lastWheelPageAt = now;

	let forward;
	if (useHorizontal) {
		// 横スワイプは「動かした向きに読み進む」。
		// vertical-rl は左が読み進む方向なので、右向き (deltaX > 0) は戻るになる
		const reading = readingAxis(frameDoc() || document);
		const sign = (reading.axis === 'x') ? reading.sign : 1;
		forward = (delta * sign) > 0;
	} else {
		// 縦ホイールは書字方向によらず「下に回すと次へ」で揃える
		forward = delta > 0;
	}
	pageStep(forward);
}

function onKeyDown(event)
{
	if (event.ctrlKey || event.altKey || event.metaKey) return;
	const tag = (event.target && event.target.tagName) ? event.target.tagName.toLowerCase() : '';
	if (tag === 'input' || tag === 'select' || tag === 'textarea') return;

	// 左右キーの意味も本単位の綴じ方向で決める (ページごとに反転させない)
	const leftIsForward = isLeftForward();
	switch (event.key) {
	case 'ArrowLeft':
		pageStep(leftIsForward);
		break;
	case 'ArrowRight':
		pageStep(!leftIsForward);
		break;
	case 'ArrowDown':
	case 'PageDown':
	case ' ':
		pageStep(true);
		break;
	case 'ArrowUp':
	case 'PageUp':
		pageStep(false);
		break;
	case '[':
		stepSection(-1);
		break;
	case ']':
		stepSection(1);
		break;
	case 't':
		el.tocToggle.click();
		break;
	case 'i':
		toggleInspector();
		break;
	default:
		return;
	}
	event.preventDefault();
}

function applyTheme()
{
	document.body.setAttribute('data-theme', state.settings.theme);
	reapplyStyle();
}

function isDark()
{
	if (state.settings.theme === 'dark') return true;
	if (state.settings.theme === 'light') return false;
	return window.matchMedia('(prefers-color-scheme: dark)').matches;
}

function reapplyStyle()
{
	const doc = frameDoc();
	if (doc) injectStyle(doc);
	saveSettings();
	if (!el.inspectPanel.hidden) renderEffectivePane();
}

// ------------------------------------------------------------------
// インスペクタ
// ------------------------------------------------------------------

function toggleInspector(force)
{
	const show = (force === undefined) ? el.inspectPanel.hidden : force;
	el.inspectPanel.hidden = !show;
	el.inspectToggle.setAttribute('aria-pressed', String(show));
	if (show) renderInspector();
}

async function renderInspector()
{
	if (!state.inspection) {
		try {
			state.inspection = await getJson('api/book/' + encodeURIComponent(state.bookId) + '/inspect');
		} catch (e) {
			pane('summary').textContent = 'EPUB 情報の取得に失敗しました: ' + e.message;
			return;
		}
	}
	renderSummaryPane();
	renderEffectivePane();
	renderFontsPane();
	renderCssPane();
}

function pane(name)
{
	return el.inspectPanel.querySelector('[data-pane="' + name + '"]');
}

function selectInspectorTab(name)
{
	for (const button of el.inspectTabs.querySelectorAll('button[data-tab]')) {
		button.classList.toggle('active', button.dataset.tab === name);
	}
	for (const section of el.inspectPanel.querySelectorAll('[data-pane]')) {
		section.hidden = section.dataset.pane !== name;
	}
}

function renderSummaryPane()
{
	const info = state.inspection;
	const target = pane('summary');
	target.textContent = '';

	target.appendChild(kvTable('書誌', [
		['書名', info.bibliography.title],
		['著者', info.bibliography.creator],
		['出版者', info.bibliography.publisher],
		['言語', info.bibliography.language],
		['識別子', info.bibliography.identifier],
		['更新日時', info.bibliography.modified]
	]));

	const structure = info.structure;
	target.appendChild(kvTable('構成', [
		['package version', structure.packageVersion],
		['綴じ方向 (page-progression-direction)', structure.pageProgressionDirection || '(未指定)'],
		['レイアウト (rendition:layout)', structure.renditionLayout || '(未指定)'],
		['primary-writing-mode', structure.primaryWritingMode || '(未指定)'],
		['OPF', structure.opfPath],
		['セクション数 (spine)', String(structure.spineCount)],
		['manifest 項目数', String(structure.manifestCount)],
		['EPUB ファイルサイズ', formatBytes(structure.fileSize)],
		['展開後サイズ', formatBytes(structure.extractedSize)]
	]));

	const note = document.createElement('p');
	note.className = 'hint-text';
	note.textContent = 'EPUB 3.x では package version が "3.0" であることが正しい値です (3.3 ではありません)。';
	target.appendChild(note);

	const table = document.createElement('table');
	table.className = 'kv';
	const caption = document.createElement('caption');
	caption.className = 'section-title';
	caption.style.captionSide = 'top';
	caption.textContent = '内訳';
	table.appendChild(caption);
	const labels = {xhtml: 'XHTML', css: 'CSS', image: '画像', font: 'フォント', ncx: 'NCX', other: 'その他'};
	for (const row of info.breakdown) {
		const tr = document.createElement('tr');
		const th = document.createElement('th');
		th.textContent = labels[row.category] || row.category;
		const tdCount = document.createElement('td');
		tdCount.className = 'num';
		tdCount.textContent = row.count + ' 件';
		const tdSize = document.createElement('td');
		tdSize.className = 'num';
		tdSize.textContent = formatBytes(row.size);
		tr.append(th, tdCount, tdSize);
		table.appendChild(tr);
	}
	target.appendChild(table);
}

function renderEffectivePane()
{
	const target = pane('effective');
	if (!target) return;
	target.textContent = '';
	const doc = frameDoc();
	if (!doc || !doc.documentElement) {
		target.textContent = '本文が読み込まれていません。';
		return;
	}
	const htmlStyle = getComputedStyle(doc.documentElement);
	const bodyStyle = doc.body ? getComputedStyle(doc.body) : htmlStyle;
	const writingMode = htmlStyle.writingMode;
	const vertical = String(writingMode || '').startsWith('vertical');

	const heading = document.createElement('p');
	heading.className = 'section-title';
	heading.textContent = '実効スタイル (ブラウザが実際に適用した値)';
	target.appendChild(heading);

	target.appendChild(kvTable(null, [
		['writing-mode', writingMode],
		['direction', htmlStyle.direction],
		['本文 font-family (指定リスト)', bodyStyle.fontFamily],
		['本文 font-size', bodyStyle.fontSize],
		['本文 line-height', bodyStyle.lineHeight],
		['ルビ (rt) font-size', rubyFontSize(doc)]
	]));

	// OPF の宣言値との突き合わせ
	const declared = state.inspection ? (state.inspection.structure.pageProgressionDirection || '') : '';
	const consistent = !declared || (vertical ? declared === 'rtl' : declared === 'ltr');
	const badge = document.createElement('span');
	badge.className = 'badge ' + (consistent ? 'ok' : 'warn');
	badge.textContent = consistent ? '整合' : '不一致';
	const check = document.createElement('p');
	check.className = 'hint-text';
	check.append('実効の書字方向は ' + (vertical ? '縦書き' : '横書き')
		+ '、OPF の page-progression-direction は ' + (declared || '(未指定)') + ' — ');
	check.appendChild(badge);
	target.appendChild(check);

	// 実効フォントの推定
	const estimate = estimateActiveFont(bodyStyle.fontFamily);
	const fontHeading = document.createElement('p');
	fontHeading.className = 'section-title';
	fontHeading.textContent = '実際に使われているフォント (推定)';
	target.appendChild(fontHeading);

	const list = document.createElement('table');
	list.className = 'kv';
	for (const item of estimate) {
		const tr = document.createElement('tr');
		const th = document.createElement('th');
		th.textContent = item.family;
		const td = document.createElement('td');
		td.textContent = item.available ? '利用可能' : '未インストール（無視される）';
		tr.append(th, td);
		list.appendChild(tr);
	}
	target.appendChild(list);

	const caveat = document.createElement('p');
	caveat.className = 'hint-text';
	caveat.textContent = 'フォントの実利用判定はブラウザ API では取得できないため、'
		+ '文字幅の比較による推定です。'
		+ 'リストの先頭にある解決可能なフォントが実際に使われます。'
		+ 'どのフォントになるかは閲覧環境のインストール状況次第で、'
		+ 'Kindle / Kobo / Apple Books の実機既定フォントとは一致しません。';
	target.appendChild(caveat);
}

/** font-family リストの各ファミリが実在するかを文字幅比較で推定する */
function estimateActiveFont(fontFamilyList)
{
	const families = String(fontFamilyList || '')
		.split(',')
		.map(name => name.trim().replace(/^["']|["']$/g, ''))
		.filter(name => name.length > 0);

	const canvas = document.createElement('canvas');
	const ctx = canvas.getContext('2d');
	const sample = '国語あアaA019';
	const generics = ['monospace', 'serif', 'sans-serif'];
	const baseline = {};
	for (const generic of generics) {
		ctx.font = '72px ' + generic;
		baseline[generic] = ctx.measureText(sample).width;
	}

	return families.map(family => {
		if (generics.includes(family) || family === 'cursive' || family === 'fantasy') {
			return {family: family, available: true};
		}
		let available = false;
		for (const generic of generics) {
			ctx.font = '72px "' + family.replace(/"/g, '') + '", ' + generic;
			if (Math.abs(ctx.measureText(sample).width - baseline[generic]) > 0.5) {
				available = true;
				break;
			}
		}
		return {family: family, available: available};
	});
}

function rubyFontSize(doc)
{
	const rt = doc.querySelector('rt');
	return rt ? getComputedStyle(rt).fontSize : '(ルビなし)';
}

function renderFontsPane()
{
	const target = pane('fonts');
	target.textContent = '';
	const fonts = state.inspection.fonts || [];

	const heading = document.createElement('p');
	heading.className = 'section-title';
	heading.textContent = 'EPUB に埋め込まれたフォント (' + fonts.length + ' 件)';
	target.appendChild(heading);

	if (fonts.length === 0) {
		const empty = document.createElement('p');
		empty.className = 'hint-text';
		empty.textContent = '埋め込みフォントはありません。'
			+ 'dakutenType=2 で変換した場合はここに濁点合成フォントが並びます。';
		target.appendChild(empty);
		return;
	}

	const table = document.createElement('table');
	table.className = 'kv';
	for (const font of fonts) {
		const tr = document.createElement('tr');
		const th = document.createElement('th');
		th.textContent = font.path;
		const td = document.createElement('td');
		td.className = 'num';
		td.textContent = formatBytes(font.size);
		tr.append(th, td);
		table.appendChild(tr);
	}
	target.appendChild(table);

	const note = document.createElement('p');
	note.className = 'hint-text';
	note.textContent = '本文フォントを変更しても、埋め込みフォントが直接指定された文字'
		+ '(濁点合成グリフなど) はそのままです。その部分だけ字形が浮いて見えることがあります。';
	target.appendChild(note);
}

async function renderCssPane()
{
	const target = pane('css');
	target.textContent = '';
	const files = state.inspection.cssFiles || [];

	const heading = document.createElement('p');
	heading.className = 'section-title';
	heading.textContent = 'EPUB に含まれる CSS (宣言値・' + files.length + ' 件)';
	target.appendChild(heading);

	const note = document.createElement('p');
	note.className = 'hint-text';
	note.textContent = 'writing-mode / font-family の行を強調しています。'
		+ 'ここに書かれている内容と、ブラウザが実際に適用した値 (実効スタイル タブ) は一致しないことがあります。';
	target.appendChild(note);

	for (const path of files) {
		const details = document.createElement('details');
		details.className = 'css-file';
		const summary = document.createElement('summary');
		summary.textContent = path;
		details.appendChild(summary);

		const pre = document.createElement('pre');
		pre.className = 'code';
		pre.textContent = '読み込み中…';
		details.appendChild(pre);

		details.addEventListener('toggle', async () => {
			if (!details.open || details.dataset.loaded === '1') return;
			details.dataset.loaded = '1';
			try {
				const text = await getText('book/' + encodeURIComponent(state.bookId) + '/' + encodePath(path));
				renderCssText(pre, text);
			} catch (e) {
				pre.textContent = '読み込みに失敗しました: ' + e.message;
			}
		});
		target.appendChild(details);
	}
}

function renderCssText(pre, text)
{
	pre.textContent = '';
	for (const line of text.split(/\r?\n/)) {
		if (/writing-mode|font-family|@font-face/.test(line)) {
			const mark = document.createElement('mark');
			mark.textContent = line;
			pre.appendChild(mark);
		} else {
			pre.appendChild(document.createTextNode(line));
		}
		pre.appendChild(document.createTextNode('\n'));
	}
}

// ------------------------------------------------------------------
// ユーティリティ
// ------------------------------------------------------------------

function kvTable(title, rows)
{
	const table = document.createElement('table');
	table.className = 'kv';
	if (title) {
		const caption = document.createElement('caption');
		caption.className = 'section-title';
		caption.style.captionSide = 'top';
		caption.textContent = title;
		table.appendChild(caption);
	}
	for (const [key, value] of rows) {
		const tr = document.createElement('tr');
		const th = document.createElement('th');
		th.textContent = key;
		const td = document.createElement('td');
		td.textContent = (value === null || value === undefined || value === '') ? '—' : value;
		tr.append(th, td);
		table.appendChild(tr);
	}
	return table;
}

function formatBytes(bytes)
{
	const value = Number(bytes) || 0;
	if (value < 1024) return value + ' B';
	if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
	return (value / 1024 / 1024).toFixed(2) + ' MB';
}

function fileNameOf(path)
{
	const slash = String(path).lastIndexOf('/');
	return (slash < 0) ? path : path.substring(slash + 1);
}

/** パスの各セグメントを URL エンコードする (スラッシュは残す) */
function encodePath(path)
{
	return String(path).split('/').map(encodeURIComponent).join('/');
}

async function getJson(path)
{
	const response = await fetch(path, {cache: 'no-store'});
	if (!response.ok) throw new Error('HTTP ' + response.status);
	return response.json();
}

async function getText(path)
{
	const response = await fetch(path, {cache: 'no-store'});
	if (!response.ok) throw new Error('HTTP ' + response.status);
	return response.text();
}

function showFatal(error)
{
	el.bookTitle.textContent = 'エラー';
	el.bookCreator.textContent = String(error && error.message ? error.message : error);
}
