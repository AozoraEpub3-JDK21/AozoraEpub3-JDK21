'use strict';

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

