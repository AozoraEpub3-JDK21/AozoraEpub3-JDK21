'use strict';

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

