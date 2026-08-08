'use strict';

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

