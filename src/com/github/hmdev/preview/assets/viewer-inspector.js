'use strict';

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

