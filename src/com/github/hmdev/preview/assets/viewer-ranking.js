/*
 * 本棚のランキング (internal #11)
 *
 * - サーバの api/ranking は、なろうは小説 API の応答を、カクヨムはランキングのページの __NEXT_DATA__ を
 *   そのまま返す。読んで並べる・本棚にあるかを見るのはここ (サイトごとの行を rankingWork の形にそろえる)
 * - なろうの順番は今のポイントで並べたもの。サイトのランキングのページ (集計した時点の写し) と少し違う。
 *   カクヨムはランキングのページそのものなので、順番はサイトと同じ
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

/** カクヨムのジャンル (ランキングのページの URL の名前)。0 は総合 */
const KAKUYOMU_GENRES = [
	['fantasy', '異世界ファンタジー'], ['action', '現代ファンタジー'], ['sf', 'SF'], ['love_story', '恋愛'],
	['romance', 'ラブコメ'], ['drama', '現代ドラマ'], ['horror', 'ホラー'], ['mystery', 'ミステリー'],
	['nonfiction', 'エッセイ・ノンフィクション'], ['history', '歴史・時代・伝奇'], ['criticism', '創作論・評論'],
	['others', '詩・童話・その他'],
];

const RANKING_PERIOD_NAMES = {d: '日間', w: '週間', m: '月間', q: '四半期', y: '年間', all: '累計'};

/** サイトごとの選べるもの */
const RANKING_SITES = {
	narou: {
		periods: ['d', 'w', 'm', 'q', 'y', 'all'],
		serial: '連載だけ (短編を外す)',
		note: 'なろうの今のポイントで並べています (サイトのランキングのページと順番が少し違うことがあります)',
	},
	kakuyomu: {
		// カクヨムに四半期は無い
		periods: ['d', 'w', 'm', 'y', 'all'],
		serial: '長編だけ (短編を外す)',
		note: 'カクヨムのランキングのページから読んでいます (上位 100 作)',
	},
};

/** 期間 → なろうの点数の項目 (サーバが of に入れる名前) */
const RANKING_POINTS = {
	d: 'daily_point', w: 'weekly_point', m: 'monthly_point',
	q: 'quarter_point', y: 'yearly_point', all: 'global_point',
};

/** ランキングの読み込みの通し番号。遅れて返った古い一覧で新しい一覧を上書きしない */
let rankingLoadSeq = 0;

/**
 * いま出している作品 (サイトをそろえた形)。本棚の印を付け直すときに使う。
 * {key, url, title, writer, genre, kind, points, summary, story}。key は本棚の本の URL からも作れる印 (workKeyOfUrl)
 */
let rankingWorks = [];

/**
 * 問い合わせはタブごとに 1 本ずつ。選択を矢印キーで送ると change が続けて来るので、
 * 少し待ってから、走っている問い合わせが終わってから、最後の選択だけを頼む。
 * 1 本ごとにサーバの 4 本しかないスレッドを掴んで、なろうの API を待つため (PR #127 のゲート2)
 */
const RANKING_DELAY = 250;
let rankingTimer = 0;
let rankingInFlight = false;
let rankingWanted = false;

/** ランキングから落としている作品の印 (key)。押した瞬間から持つ (startDownload が仕事を持つのは POST の返り以降) */
let rankingBusyKey = null;

function bindRankingEvents()
{
	fillRankingChoices();
	el.rankingSite.addEventListener('change', () => {
		fillRankingChoices();
		loadRanking();
	});
	el.libraryRankingToggle.addEventListener('click', () => toggleRanking());
	for (const control of [el.rankingPeriod, el.rankingGenre, el.rankingSerial]) {
		control.addEventListener('change', () => loadRanking());
	}
	// 本棚の見出しの操作は本の一覧に効く。ランキングを出したままだと結果が見えないので、しまう
	el.libraryFilter.addEventListener('input', () => toggleRanking(false));
	for (const control of [el.librarySort, el.libraryShelfSelect]) {
		control.addEventListener('change', () => toggleRanking(false));
	}
	for (const control of [el.libraryReload, el.libraryCheckAll]) {
		control.addEventListener('click', () => toggleRanking(false));
	}
	el.rankingList.addEventListener('click', event => {
		const button = event.target.closest('button.ranking-get');
		if (!button) return;
		downloadFromRanking(button).catch(err => showDownloadStatus('落とせませんでした: ' + err.message, true));
	});
}

/** サイトに合わせて期間とジャンルの選択肢を作り直す。期間はそのサイトにあれば残す (四半期はカクヨムでは月間に) */
function fillRankingChoices()
{
	const site = RANKING_SITES[el.rankingSite.value] ? el.rankingSite.value : 'narou';
	const config = RANKING_SITES[site];
	// 最初は日間 (まだ選択肢が無い)
	const period = el.rankingPeriod.value || 'd';
	el.rankingPeriod.replaceChildren(...config.periods.map(code => rankingOption(code, RANKING_PERIOD_NAMES[code])));
	el.rankingPeriod.value = config.periods.includes(period) ? period : 'm';
	const genre = el.rankingGenre;
	genre.replaceChildren(rankingOption('0', '総合'));
	if (site === 'kakuyomu') {
		for (const [code, name] of KAKUYOMU_GENRES) genre.appendChild(rankingOption(code, name));
	} else {
		for (const [group, items] of RANKING_GENRES) {
			const optgroup = document.createElement('optgroup');
			optgroup.label = group;
			for (const [code, name] of items) optgroup.appendChild(rankingOption(String(code), name));
			genre.appendChild(optgroup);
		}
	}
	genre.value = '0';
	el.rankingSerialLabel.textContent = config.serial;
}

function rankingOption(value, label)
{
	const option = document.createElement('option');
	option.value = value;
	option.textContent = label;
	return option;
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
	// 同じボタンで戻る。押された印 (aria-pressed) だけでは、戻るにはもう一度押すと分からなかった (internal #49)
	el.libraryRankingToggle.textContent = show ? '← 本棚に戻る' : 'ランキング';
	el.libraryRankingToggle.title = show ? '本の一覧に戻る (Esc)' : '小説家になろう・カクヨムのランキングから落とす';
	// 本の一覧にだけ効く絞り込み・並び順・棚は隠す (残すと本棚にいるように見える。viewer.css)
	el.libraryView.classList.toggle('ranking-open', show);
	// 開くたびに頼む。同じ問い合わせはサーバが 10 分覚えているので、なろうへは行かない。
	// 前に開いたときの一覧を出したままにすると、日をまたいでも古い順位が残る (PR #127 の codex)
	if (show) loadRanking();
}

/** Esc で手前のランキングを先にしまう。しまったら true */
function closeRankingIfOpen()
{
	if (!rankingOpen()) return false;
	toggleRanking(false);
	el.libraryRankingToggle.focus();
	return true;
}

/** ランキングを頼む (少し待ち、1 本ずつ) */
function loadRanking()
{
	el.rankingStatus.textContent = '読み込み中…';
	el.rankingStatus.dataset.state = '';
	// 走っている問い合わせの返りは、もう今の選択の答えではない。捨てさせる (PR #127 の codex)
	rankingLoadSeq++;
	rankingWanted = true;
	clearTimeout(rankingTimer);
	rankingTimer = setTimeout(pumpRanking, RANKING_DELAY);
}

async function pumpRanking()
{
	if (rankingInFlight || !rankingWanted) return;
	rankingWanted = false;
	rankingInFlight = true;
	try {
		await fetchRanking();
	} finally {
		rankingInFlight = false;
	}
	// 待っている間に選択が変わっていたら、最後の選択で頼み直す
	if (rankingWanted) pumpRanking();
}

async function fetchRanking()
{
	// 選択が変わるたびに loadRanking が番号を進める。返ったときに番号が違えば、選択が変わっている
	const seq = rankingLoadSeq;
	const site = el.rankingSite.value;
	const period = el.rankingPeriod.value;
	const genre = el.rankingGenre.value;
	const serial = el.rankingSerial.checked;
	const query = 'site=' + encodeURIComponent(site)
		+ '&period=' + encodeURIComponent(period)
		+ '&genre=' + encodeURIComponent(genre)
		+ '&serial=' + (serial ? '1' : '0');
	let works;
	try {
		const response = await fetch('api/ranking?' + query, {cache: 'no-store'});
		let json = null;
		try { json = await response.json(); } catch (e) { /* 本文の無い応答 */ }
		if (!response.ok || !json || (json.error && !Array.isArray(json))) {
			throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
		}
		works = (site === 'kakuyomu')
			? kakuyomuWorks(json, period, genre, serial)
			: narouWorks(json, period);
	} catch (e) {
		if (seq !== rankingLoadSeq) return;
		rankingWorks = [];
		el.rankingList.replaceChildren();
		el.rankingStatus.textContent = 'ランキングを読み込めませんでした: ' + e.message;
		el.rankingStatus.dataset.state = 'failed';
		return;
	}
	if (seq !== rankingLoadSeq) return;
	rankingWorks = works;
	el.rankingStatus.textContent = rankingWorks.length ? RANKING_SITES[site].note : '作品がありません';
	renderRanking();
}

/**
 * なろうの API の行をそろえる。先頭は {"allcount": n}。作品の行は ncode を持つ。
 * period は頼んだときの期間 (点数の項目を選ぶ。今の選択で読むと、別の期間の答えで点数が消える)
 */
function narouWorks(rows, period)
{
	if (!Array.isArray(rows)) throw new Error('なろうの API が一覧を返しませんでした');
	const point = RANKING_POINTS[period];
	return rows.filter(row => row && typeof row.ncode === 'string').map(row => {
		// 題・作者・あらすじは文字列にそろえる (数字だけだと数で返ることがある。split で落ちる)
		const story = rankingText(row.story);
		const ncode = row.ncode.toLowerCase();
		return {
			key: ncode,
			url: 'https://ncode.syosetu.com/' + ncode + '/',
			title: rankingText(row.title) || ncode,
			writer: rankingText(row.writer),
			genre: rankingGenreName(row.genre),
			kind: rankingKind(row),
			points: rankingPoints(row[point], ' pt'),
			summary: firstLine(story),
			story,
		};
	});
}

/**
 * カクヨムのページの __NEXT_DATA__ をそろえる。順位の並びは ROOT_QUERY の rankedWorks(…) の nodes にあり、
 * 作品と作者は Apollo の控えの Work:… と UserAccount:… を引く (2026-10-11 のページの作り)
 */
function kakuyomuWorks(data, period, genre, longOnly)
{
	const apollo = data && data.props && data.props.pageProps && data.props.pageProps.__APOLLO_STATE__;
	const root = apollo && apollo.ROOT_QUERY;
	const key = root && Object.keys(root).find(k => kakuyomuRankedWorksFor(k, period, genre, longOnly));
	const nodes = key && root[key] && root[key].nodes;
	if (!Array.isArray(nodes)) throw new Error('カクヨムのページにランキングが見つかりません (ページの作りが変わったかもしれません)');
	const works = [];
	for (const node of nodes) {
		const work = node && apollo[node.__ref];
		if (!work || !/^[0-9]+$/.test(String(work.id))) continue;
		const author = (work.author && apollo[work.author.__ref]) || {};
		const intro = rankingText(work.introduction);
		const id = String(work.id);
		works.push({
			key: 'kakuyomu:' + id,
			url: 'https://kakuyomu.jp/works/' + id,
			title: rankingText(work.title) || id,
			writer: rankingText(work.alternateAuthorName) || rankingText(author.activityName) || rankingText(author.name),
			genre: kakuyomuGenreName(work.genre),
			kind: kakuyomuKind(work),
			// 期間の点数はページに無い。出すのは累計の★ (並びの理由ではないので「累計」と書く)
			points: rankingPoints(work.totalReviewPoint, '', '累計 ★ '),
			summary: firstLine(rankingText(work.catchphrase) || intro),
			story: intro,
		});
	}
	return works;
}

/** カクヨムの期間 (なろうと同じ印) → ページの rankedWorks の period */
const KAKUYOMU_PERIODS = {d: 'DAILY', w: 'WEEKLY', m: 'MONTHLY', y: 'YEARLY', all: 'ENTIRE'};

/**
 * ROOT_QUERY の鍵 rankedWorks({...}) が、頼んだ期間・ジャンル・長編だけかどうかと合うか。
 * ページの控えに別の一覧が混ざっても、先に見つかった方を黙って出さない (PR のゲート2)
 */
function kakuyomuRankedWorksFor(key, period, genre, longOnly)
{
	if (!key.startsWith('rankedWorks(') || !key.endsWith(')')) return false;
	let args;
	try { args = JSON.parse(key.slice('rankedWorks('.length, -1)); } catch (e) { return false; }
	const wantGenre = (genre === '0') ? null : String(genre).toUpperCase();
	return args && args.period === KAKUYOMU_PERIODS[period]
		&& (args.genre === undefined ? null : args.genre) === wantGenre
		&& args.workVariation === (longOnly ? 'LONG' : 'ALL');
}

/** 本棚にある作品の印 (dc:source の URL から取る) */
function shelfKeys()
{
	const keys = new Set();
	for (const book of (state.library && state.library.books) || []) {
		const key = workKeyOfUrl(book.source);
		if (key) keys.add(key);
	}
	return keys;
}

/** 作品ページの URL から印を取る。なろうは作品コード (小文字)、カクヨムは kakuyomu:作品の番号。どちらでもなければ null */
function workKeyOfUrl(url)
{
	if (!url) return null;
	const narou = /^https?:\/\/ncode\.syosetu\.com\/(n[0-9a-z]+)(?:\/|$)/i.exec(url);
	if (narou) return narou[1].toLowerCase();
	const kakuyomu = /^https?:\/\/kakuyomu\.jp\/works\/([0-9]+)(?:[/?#]|$)/i.exec(url);
	return kakuyomu ? 'kakuyomu:' + kakuyomu[1] : null;
}

function rankingText(value)
{
	return (value === null || value === undefined) ? '' : String(value);
}

function renderRanking()
{
	const owned = shelfKeys();
	const items = rankingWorks.map((work, index) => rankingCard(work, index + 1, owned));
	el.rankingList.replaceChildren(...items);
}

function rankingCard(work, rank, owned)
{
	const item = document.createElement('li');
	item.className = 'ranking-card';
	item.dataset.key = work.key;

	const number = document.createElement('div');
	number.className = 'rank';
	number.textContent = String(rank);
	item.appendChild(number);

	const body = document.createElement('div');
	body.className = 'body';
	const title = document.createElement('a');
	title.className = 'title';
	title.href = work.url;
	title.target = '_blank';
	title.rel = 'noopener noreferrer';
	title.title = '作品のページを開く';
	title.textContent = work.title;
	body.appendChild(title);

	const meta = document.createElement('div');
	meta.className = 'meta';
	meta.textContent = [work.writer, work.genre, work.kind, work.points].filter(Boolean).join(' ・ ');
	body.appendChild(meta);

	// カクヨムはキャッチコピーだけでイントロが空のことがある。そのときも 1 行は出す
	if (work.story || work.summary) {
		const story = document.createElement('details');
		story.className = 'story';
		const summary = document.createElement('summary');
		summary.textContent = work.summary || firstLine(work.story);
		story.appendChild(summary);
		const full = document.createElement('div');
		full.textContent = work.story || work.summary;
		story.appendChild(full);
		body.appendChild(story);
	}
	item.appendChild(body);

	const action = document.createElement('button');
	action.type = 'button';
	action.className = 'ranking-get';
	// 期間を変えて描き直しても、落としている最中の札は「落としています…」のまま
	if (work.key === rankingBusyKey) paintRankingBusy(action);
	else paintRankingButton(action, owned.has(work.key));
	item.appendChild(action);
	return item;
}

function paintRankingButton(button, owned)
{
	button.disabled = owned;
	button.textContent = owned ? '本棚にあります' : '落とす';
	button.dataset.state = owned ? 'owned' : '';
}

function paintRankingBusy(button)
{
	button.disabled = true;
	button.textContent = '落としています…';
	button.dataset.state = 'busy';
}

/** 本棚を読み込み直すたびに、本棚の印だけ付け直す (開いたあらすじはそのまま) */
function remarkRanking()
{
	// 本棚を読み込めなかった (state.library が null) ときは、印を消さない。消すと本棚にある作品をまた落とせる
	if (!state.library) return;
	const owned = shelfKeys();
	for (const item of el.rankingList.children) {
		const button = item.querySelector('button.ranking-get');
		if (button && item.dataset.key !== rankingBusyKey) paintRankingButton(button, owned.has(item.dataset.key));
	}
}

async function downloadFromRanking(button)
{
	if (libraryDownloadJob || libraryDownloadPosting || rankingBusyKey) {
		showDownloadStatus('ほかの作品を落としています。終わってから押してください');
		el.libraryDownload.hidden = false;
		el.libraryDownloadToggle.setAttribute('aria-expanded', 'true');
		return;
	}
	// Web 本棚の場所を聞いている間は、決めたら落とす URL が 1 つ待っている。上書きすると先に押した作品が消える
	if (libraryPendingUrl) {
		el.libraryDownload.hidden = false;
		el.libraryDownloadToggle.setAttribute('aria-expanded', 'true');
		el.libraryShelfNote.textContent = '先に Web 本棚の場所を決めてください (決めると、先に押した作品を落とします)';
		return;
	}
	const item = button.closest('.ranking-card');
	const work = rankingWorks.find(w => w.key === item.dataset.key);
	if (!work) return;
	// 進み具合と Web 本棚の場所を聞く欄は、URL を貼って落とすのと同じ所に出る
	el.libraryDownload.hidden = false;
	el.libraryDownloadToggle.setAttribute('aria-expanded', 'true');
	el.libraryDownloadUrl.value = work.url;
	rankingBusyKey = work.key;
	paintRankingBusy(button);
	try {
		await startDownload(work.url);
	} finally {
		// 落とせたときの印は、本棚の読み直し (loadLibrary) が付ける。ここは押せる形に戻すだけ。
		// 描き直されていれば button はもう画面に無いので、今の札を探して塗る
		rankingBusyKey = null;
		const owned = state.library ? shelfKeys() : null;
		for (const current of el.rankingList.querySelectorAll('.ranking-card')) {
			if (current.dataset.key !== work.key) continue;
			const now = current.querySelector('button.ranking-get');
			if (now) paintRankingButton(now, owned ? owned.has(work.key) : false);
		}
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
	// state という名前はビューアー全体の状態 (viewer-core.js) と重なるので使わない
	const progress = work.end === 1 ? '連載中' : '完結';
	return episodes ? progress + ' ' + episodes : progress;
}

/** 点数 (数のときだけ)。なろうは期間の点数 (… pt)、カクヨムは累計の★ (累計 ★ …) */
function rankingPoints(value, suffix, prefix)
{
	return (typeof value === 'number') ? (prefix || '') + value.toLocaleString('ja-JP') + suffix : '';
}

/** カクヨムのジャンル (Work の genre は URL の名前を大文字にしたもの。LOVE_STORY など) */
function kakuyomuGenreName(code)
{
	const slug = rankingText(code).toLowerCase();
	const found = KAKUYOMU_GENRES.find(([value]) => value === slug);
	return found ? found[1] : '';
}

/** カクヨムの連載の状態 (serialStatus) と公開している話数 */
function kakuyomuKind(work)
{
	const progress = work.serialStatus === 'COMPLETED' ? '完結' : (work.serialStatus === 'RUNNING' ? '連載中' : '');
	const episodes = (typeof work.publicEpisodeCount === 'number') ? work.publicEpisodeCount + ' 話' : '';
	return [progress, episodes].filter(Boolean).join(' ');
}

/** あらすじの 1 行目 (畳んだときに出す) */
function firstLine(text)
{
	// 行頭の字下げ (全角の空白) は畳んだ 1 行では邪魔になる。trim は U+3000 も落とす
	const line = (text.split(/\r?\n/).find(l => l.trim()) || '').trim();
	// 字数はコードポイントで数える (UTF-16 で切ると、絵文字や第 2 面の漢字が割れる)
	const chars = Array.from(line);
	return chars.length > 80 ? chars.slice(0, 80).join('') + '…' : line;
}
