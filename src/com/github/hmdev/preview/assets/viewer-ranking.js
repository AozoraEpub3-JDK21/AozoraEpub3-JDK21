/*
 * 本棚のランキング (internal #11)
 *
 * - サーバの api/ranking は、なろう小説 API の応答をそのまま返す。並べる・本棚にあるかを見るのはここ
 * - 順番は今のポイントで並べたもの。サイトのランキングのページ (集計した時点の写し) と少し違う
 * - 「落とす」は URL を貼って落とすのと同じ道 (viewer-library.js の startDownload)。同時に 1 つだけ
 * - API の文字列は textContent でしか入れない (あらすじや題に < が入る)
 */
'use strict';

/** なろうのジャンル (API の genre)。0 は総合 */
const RANKING_GENRES = [
	['恋愛', [[101, '異世界'], [102, '現実世界']]],
	['ファンタジー', [[201, 'ハイファンタジー'], [202, 'ローファンタジー']]],
	['文芸', [[301, '純文学'], [302, 'ヒューマンドラマ'], [303, '歴史'], [304, '推理'], [305, 'ホラー'], [306, 'アクション'], [307, 'コメディー']]],
	['SF', [[401, 'VRゲーム'], [402, '宇宙'], [403, '空想科学'], [404, 'パニック']]],
	['その他', [[9901, '童話'], [9902, '詩'], [9903, 'エッセイ'], [9904, 'リプレイ'], [9999, 'その他'], [9801, 'ノンジャンル']]],
];

/** 期間 → 点数の項目 (サーバが of に入れる名前) */
const RANKING_POINTS = {
	d: 'daily_point', w: 'weekly_point', m: 'monthly_point',
	q: 'quarter_point', y: 'yearly_point', all: 'global_point',
};

/** ランキングの読み込みの通し番号。遅れて返った古い一覧で新しい一覧を上書きしない */
let rankingLoadSeq = 0;

/** いま出している作品 (API の行)。本棚の印を付け直すときに使う */
let rankingWorks = [];

function bindRankingEvents()
{
	const genre = el.rankingGenre;
	for (const [group, items] of RANKING_GENRES) {
		const optgroup = document.createElement('optgroup');
		optgroup.label = group;
		for (const [code, name] of items) {
			const option = document.createElement('option');
			option.value = String(code);
			option.textContent = name;
			optgroup.appendChild(option);
		}
		genre.appendChild(optgroup);
	}
	el.libraryRankingToggle.addEventListener('click', () => toggleRanking());
	for (const control of [el.rankingPeriod, el.rankingGenre, el.rankingSerial]) {
		control.addEventListener('change', () => loadRanking());
	}
	el.rankingList.addEventListener('click', event => {
		const button = event.target.closest('button.ranking-get');
		if (!button) return;
		downloadFromRanking(button).catch(err => showDownloadStatus('落とせませんでした: ' + err.message, true));
	});
}

function rankingOpen()
{
	return !el.libraryRanking.hidden;
}

/** ランキングを出す・しまう。出している間は本の一覧を隠す */
function toggleRanking(force)
{
	const show = (force === undefined) ? !rankingOpen() : force;
	el.libraryRanking.hidden = !show;
	el.libraryStatus.hidden = show;
	el.libraryGrid.hidden = show;
	el.libraryRankingToggle.setAttribute('aria-pressed', String(show));
	if (show && !rankingWorks.length) loadRanking();
}

/** Esc で手前のランキングを先にしまう。しまったら true */
function closeRankingIfOpen()
{
	if (!rankingOpen()) return false;
	toggleRanking(false);
	el.libraryRankingToggle.focus();
	return true;
}

async function loadRanking()
{
	const seq = ++rankingLoadSeq;
	const query = 'period=' + encodeURIComponent(el.rankingPeriod.value)
		+ '&genre=' + encodeURIComponent(el.rankingGenre.value)
		+ '&serial=' + (el.rankingSerial.checked ? '1' : '0');
	el.rankingStatus.textContent = '読み込み中…';
	el.rankingStatus.dataset.state = '';
	let rows;
	try {
		const response = await fetch('api/ranking?' + query, {cache: 'no-store'});
		let json = null;
		try { json = await response.json(); } catch (e) { /* 本文の無い応答 */ }
		if (!response.ok || !Array.isArray(json)) {
			throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
		}
		rows = json;
	} catch (e) {
		if (seq !== rankingLoadSeq) return;
		rankingWorks = [];
		el.rankingList.replaceChildren();
		el.rankingStatus.textContent = 'ランキングを読み込めませんでした: ' + e.message;
		el.rankingStatus.dataset.state = 'failed';
		return;
	}
	if (seq !== rankingLoadSeq) return;
	// 先頭は {"allcount": n}。作品の行は ncode を持つ
	rankingWorks = rows.filter(row => row && typeof row.ncode === 'string');
	el.rankingStatus.textContent = rankingWorks.length
		? 'なろうの今のポイントで並べています (サイトのランキングのページと順番が少し違うことがあります)'
		: '作品がありません';
	renderRanking();
}

/** 本棚にある作品のコード (dc:source の URL から取る。小文字) */
function shelfNcodes()
{
	const codes = new Set();
	for (const book of (state.library && state.library.books) || []) {
		const code = ncodeOfUrl(book.source);
		if (code) codes.add(code);
	}
	return codes;
}

/** なろうの作品ページの URL から作品コード (小文字) を取る。なろうでなければ null */
function ncodeOfUrl(url)
{
	if (!url) return null;
	const match = /^https?:\/\/ncode\.syosetu\.com\/(n[0-9a-z]+)(?:\/|$)/i.exec(url);
	return match ? match[1].toLowerCase() : null;
}

function rankingWorkUrl(work)
{
	return 'https://ncode.syosetu.com/' + work.ncode.toLowerCase() + '/';
}

function renderRanking()
{
	const owned = shelfNcodes();
	const point = RANKING_POINTS[el.rankingPeriod.value];
	const items = rankingWorks.map((work, index) => rankingCard(work, index + 1, point, owned));
	el.rankingList.replaceChildren(...items);
}

function rankingCard(work, rank, point, owned)
{
	const item = document.createElement('li');
	item.className = 'ranking-card';
	item.dataset.ncode = work.ncode.toLowerCase();

	const number = document.createElement('div');
	number.className = 'rank';
	number.textContent = String(rank);
	item.appendChild(number);

	const body = document.createElement('div');
	body.className = 'body';
	const title = document.createElement('a');
	title.className = 'title';
	title.href = rankingWorkUrl(work);
	title.target = '_blank';
	title.rel = 'noopener noreferrer';
	title.title = '作品のページを開く';
	title.textContent = work.title || work.ncode;
	body.appendChild(title);

	const meta = document.createElement('div');
	meta.className = 'meta';
	meta.textContent = [work.writer, rankingGenreName(work.genre), rankingKind(work), rankingPoints(work[point])]
		.filter(Boolean).join(' ・ ');
	body.appendChild(meta);

	if (work.story) {
		const story = document.createElement('details');
		story.className = 'story';
		const summary = document.createElement('summary');
		summary.textContent = firstLine(work.story);
		story.appendChild(summary);
		const full = document.createElement('div');
		full.textContent = work.story;
		story.appendChild(full);
		body.appendChild(story);
	}
	item.appendChild(body);

	const action = document.createElement('button');
	action.type = 'button';
	action.className = 'ranking-get';
	paintRankingButton(action, owned.has(item.dataset.ncode));
	item.appendChild(action);
	return item;
}

function paintRankingButton(button, owned)
{
	button.disabled = owned;
	button.textContent = owned ? '本棚にあります' : '落とす';
	button.dataset.state = owned ? 'owned' : '';
}

/** 本棚を読み込み直すたびに、本棚の印だけ付け直す (開いたあらすじはそのまま) */
function remarkRanking()
{
	const owned = shelfNcodes();
	for (const item of el.rankingList.children) {
		const button = item.querySelector('button.ranking-get');
		if (button && button.dataset.state !== 'busy') paintRankingButton(button, owned.has(item.dataset.ncode));
	}
}

async function downloadFromRanking(button)
{
	if (libraryDownloadJob) {
		showDownloadStatus('ほかの作品を落としています。終わってから押してください');
		el.libraryDownload.hidden = false;
		return;
	}
	const item = button.closest('.ranking-card');
	const work = rankingWorks.find(w => w.ncode.toLowerCase() === item.dataset.ncode);
	if (!work) return;
	// 進み具合と Web 本棚の場所を聞く欄は、URL を貼って落とすのと同じ所に出る
	el.libraryDownload.hidden = false;
	el.libraryDownloadToggle.setAttribute('aria-expanded', 'true');
	el.libraryDownloadUrl.value = rankingWorkUrl(work);
	button.disabled = true;
	button.dataset.state = 'busy';
	button.textContent = '落としています…';
	try {
		await startDownload(rankingWorkUrl(work));
	} finally {
		// 落とせたときの印は、本棚の読み直し (loadLibrary) が付ける。ここは押せる形に戻すだけ
		button.dataset.state = '';
		remarkRanking();
	}
}

function rankingGenreName(code)
{
	for (const [, items] of RANKING_GENRES) {
		for (const [value, name] of items) if (value === code) return name;
	}
	return '';
}

/** noveltype 2＝短編。連載は end 1＝連載中、0＝完結済み */
function rankingKind(work)
{
	if (work.noveltype === 2) return '短編';
	const episodes = (typeof work.general_all_no === 'number') ? work.general_all_no + ' 話' : '';
	const state = work.end === 1 ? '連載中' : '完結';
	return episodes ? state + ' ' + episodes : state;
}

function rankingPoints(value)
{
	return (typeof value === 'number') ? value.toLocaleString('ja-JP') + ' pt' : '';
}

/** あらすじの 1 行目 (畳んだときに出す) */
function firstLine(text)
{
	// 行頭の字下げ (全角の空白) は畳んだ 1 行では邪魔になる。trim は U+3000 も落とす
	const line = (text.split(/\r?\n/).find(l => l.trim()) || '').trim();
	return line.length > 80 ? line.slice(0, 80) + '…' : line;
}
