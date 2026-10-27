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

/**
 * なろうのジャンル (API の genre)。0 は総合。大ジャンル (API の biggenre) は b＋番号で頼む (internal #62)。
 * その他の大ジャンル (99) にノンジャンル (9801) は入らない (ノンジャンルは大ジャンル 98)
 */
const RANKING_GENRES = [
	['恋愛', 'b1', [[101, '異世界'], [102, '現実世界']]],
	['ファンタジー', 'b2', [[201, 'ハイファンタジー'], [202, 'ローファンタジー']]],
	['文芸', 'b3', [[301, '純文学'], [302, 'ヒューマンドラマ'], [303, '歴史'], [304, '推理'], [305, 'ホラー'], [306, 'アクション'], [307, 'コメディー']]],
	['SF', 'b4', [[401, 'VRゲーム'], [402, '宇宙'], [403, '空想科学'], [404, 'パニック']]],
	['その他', 'b99', [[9901, '童話'], [9902, '詩'], [9903, 'エッセイ'], [9904, 'リプレイ'], [9999, 'その他'], [9801, 'ノンジャンル']]],
];

/** カクヨムのジャンル (ランキングのページの URL の名前)。0 は総合 */
const KAKUYOMU_GENRES = [
	['fantasy', '異世界ファンタジー'], ['action', '現代ファンタジー'], ['sf', 'SF'], ['love_story', '恋愛'],
	['romance', 'ラブコメ'], ['drama', '現代ドラマ'], ['horror', 'ホラー'], ['mystery', 'ミステリー'],
	['nonfiction', 'エッセイ・ノンフィクション'], ['history', '歴史・時代・伝奇'], ['criticism', '創作論・評論'],
	['others', '詩・童話・その他'],
];

const RANKING_PERIOD_NAMES = {
	d: '日間', w: '週間', m: '月間', q: '四半期', y: '年間', all: '累計',
	// なろうだけ (internal #62)。ポイントの順ではない
	fav: 'ブックマーク数', new: '新着更新', len: '文字数が多い',
};

/** サイトごとの選べるもの */
const RANKING_SITES = {
	narou: {
		periods: ['d', 'w', 'm', 'q', 'y', 'all', 'fav', 'new', 'len'],
		// なろうは連載だけかを rankingType で選ぶ (この印は出さない)
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

/** 並び → なろうの、並びの理由になる項目 (サーバが of に入れる名前) と、その出し方 */
const RANKING_POINTS = {
	d: 'daily_point', w: 'weekly_point', m: 'monthly_point',
	q: 'quarter_point', y: 'yearly_point', all: 'global_point',
	fav: 'fav_novel_cnt', new: 'general_lastup', len: 'length',
};

/** ポイントの順の並び (サイトのランキングのページと順番が少し違う、と断る並び) */
const RANKING_POINT_PERIODS = ['d', 'w', 'm', 'q', 'y', 'all'];

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

function bindRankingEvents()
{
	fillRankingChoices();
	el.rankingSite.addEventListener('change', () => {
		fillRankingChoices();
		loadRanking();
	});
	el.libraryRankingToggle.addEventListener('click', () => toggleRanking());
	for (const control of [el.rankingPeriod, el.rankingGenre, el.rankingSerial, el.rankingType, el.rankingLength,
		el.rankingStop, el.rankingNotR15, el.rankingNotTensei, el.rankingNotTenni]) {
		control.addEventListener('change', () => loadRanking());
	}
	// 単語は打つたびには頼まない。Enter (search・change) か、欄を離れたとき (change)。× で消したとき (search) も
	let rankingWordSent = el.rankingWord.value.trim();
	for (const type of ['change', 'search']) {
		el.rankingWord.addEventListener(type, () => {
			const word = el.rankingWord.value.trim();
			if (word === rankingWordSent) return;
			rankingWordSent = word;
			loadRanking();
		});
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
		downloadFromRanking(button);
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
		for (const [group, big, items] of RANKING_GENRES) {
			const optgroup = document.createElement('optgroup');
			optgroup.label = group;
			optgroup.appendChild(rankingOption(big, group + 'のすべて' + (big === 'b99' ? ' (ノンジャンルを除く)' : '')));
			for (const [code, name] of items) optgroup.appendChild(rankingOption(String(code), name));
			genre.appendChild(optgroup);
		}
	}
	genre.value = '0';
	el.rankingSerialLabel.textContent = config.serial;
	// 連載だけかは、なろうでは作品の種類で選ぶ。絞り込みの行はなろうだけ
	el.rankingSerialBox.hidden = (site === 'narou');
	el.rankingNarouFilters.hidden = (site !== 'narou');
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
	// 出していないときの文字と説明は viewer.html のものを使う (2 か所に書くと食い違う)
	const toggle = el.libraryRankingToggle;
	if (toggle.dataset.idleText === undefined) {
		toggle.dataset.idleText = toggle.textContent;
		toggle.dataset.idleTitle = toggle.title;
	}
	toggle.textContent = show ? '← 本棚に戻る' : toggle.dataset.idleText;
	toggle.title = show ? '本の一覧に戻る (Esc)' : toggle.dataset.idleTitle;
	// 読み上げで矢印の記号を読ませない
	if (show) toggle.setAttribute('aria-label', '本棚に戻る');
	else toggle.removeAttribute('aria-label');
	// 本の一覧にだけ効く絞り込み・並び順・棚は隠す (残すと本棚にいるように見える。viewer.css)
	el.libraryView.classList.toggle('ranking-open', show);
	// 開くたびに頼む。同じ問い合わせはサーバが 10 分覚えているので、なろうへは行かない。
	// 前に開いたときの一覧を出したままにすると、日をまたいでも古い順位が残る (PR #127 の codex)
	if (show) loadRanking();
	// ランキングを出している間に来た失敗は、しまったときに理由の出る ＋ の欄を開いて見せる (PR #171 の Opus)
	else if (libraryFailureUnseen) openDownloadPanel();
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
	let query = 'site=' + encodeURIComponent(site)
		+ '&period=' + encodeURIComponent(period)
		+ '&genre=' + encodeURIComponent(genre);
	if (site === 'narou') {
		// 空白は全角もサーバがそろえる (同じ単語を 1 つの問い合わせに数える)
		query += '&type=' + encodeURIComponent(el.rankingType.value)
			+ '&length=' + encodeURIComponent(el.rankingLength.value)
			+ '&word=' + encodeURIComponent(el.rankingWord.value.trim())
			+ '&stop=' + (el.rankingStop.checked ? '1' : '0')
			+ '&notr15=' + (el.rankingNotR15.checked ? '1' : '0')
			+ '&nottensei=' + (el.rankingNotTensei.checked ? '1' : '0')
			+ '&nottenni=' + (el.rankingNotTenni.checked ? '1' : '0');
	} else {
		query += '&serial=' + (serial ? '1' : '0');
	}
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
	el.rankingStatus.textContent = rankingWorks.length ? rankingNote(site, period, works.allcount) : '作品がありません';
	renderRanking();
}

/**
 * 一覧の上の断り書き。なろうは、絞り込んで当たった数が出した数より多いときに、その数も出す。
 * ポイントの順でない並び (ブックマーク数など) は、サイトのランキングと比べる断りが要らない
 */
function rankingNote(site, period, allcount)
{
	if (site !== 'narou') return RANKING_SITES[site].note;
	const note = RANKING_POINT_PERIODS.includes(period)
		? RANKING_SITES.narou.note
		: 'なろうの API で、' + RANKING_PERIOD_NAMES[period] + 'の順に並べています';
	const shown = rankingWorks.length;
	if (typeof allcount === 'number' && allcount > shown) {
		return '当てはまる ' + allcount.toLocaleString('ja-JP') + ' 作のうち、先頭の ' + shown + ' 作です。' + note;
	}
	return note;
}

/**
 * なろうの API の行をそろえる。先頭は {"allcount": n}。作品の行は ncode を持つ。
 * period は頼んだときの並び (点数の項目を選ぶ。今の選択で読むと、別の並びの答えで点数が消える)。
 * 返す配列の allcount に、絞り込みに当てはまった作品の数を持たせる (数で返ったときだけ)
 */
function narouWorks(rows, period)
{
	if (!Array.isArray(rows)) throw new Error('なろうの API が一覧を返しませんでした');
	const point = RANKING_POINTS[period];
	const head = rows.find(row => row && typeof row.allcount === 'number');
	const works = rows.filter(row => row && typeof row.ncode === 'string').map(row => {
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
			points: narouReason(period, row[point]),
			summary: firstLine(story),
			story,
		};
	});
	if (head) works.allcount = head.allcount;
	return works;
}

/** 並びの理由の値。ポイント (… pt)・ブックマーク数・文字数・最終掲載日 */
function narouReason(period, value)
{
	if (period === 'fav') return rankingPoints(value, '', 'ブックマーク ');
	if (period === 'len') return rankingPoints(value, ' 字');
	if (period === 'new') {
		// 「2026-10-27 07:00:00」。分まで出す
		const text = rankingText(value);
		return /^\d{4}-\d{2}-\d{2} \d{2}:\d{2}/.test(text) ? '更新 ' + text.slice(0, 16) : '';
	}
	return rankingPoints(value, ' pt');
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
	// 期間を変えて描き直しても、順番待ちと落としている最中の札はそのまま
	const queued = queueStateOfKey(work.key);
	if (queued) paintRankingBusy(action, queued);
	else paintRankingButton(action, owned.has(work.key), queueFailureOfKey(work.key));
	item.appendChild(action);
	return item;
}

/**
 * 落とせる札。failure は並びの最後の試みが失敗した 1 件 (internal #52): 札に失敗を出し、押せばもう一度並べる。
 * 理由は札の title に出す (＋ の欄はランキングを出している間は開かない。開くと一覧がずれる)
 */
function paintRankingButton(button, owned, failure)
{
	// 進み具合が分からなくなった作品はアプリが落とし続けているかもしれない。もう一度は勧めず、押せなくする
	// (押すと同じ作品をもう一度頼む。PR #171 の Opus)。本棚に出れば「本棚にあります」になる (queueFailureOfKey)
	const unknown = !owned && !!failure && failure.state === 'unknown';
	const failed = !owned && !!failure && !unknown;
	button.disabled = owned || unknown;
	button.textContent = owned ? '本棚にあります' : (unknown ? '確かめられません' : (failed ? 'もう一度落とす' : '落とす'));
	button.dataset.state = owned ? 'owned' : (unknown ? 'unknown' : (failed ? 'failed' : ''));
	button.title = unknown ? '落とし続けているかもしれません。⟳ 一覧を更新で確かめてください (本棚に出なければ、このページを開き直すと、もう一度落とせます)'
		: (failed ? '落とせませんでした' + (failure.message ? ': ' + failure.message : '') : '');
}

/** 並びにいる札 (internal #50)。取り消しは ＋ の欄から */
function paintRankingBusy(button, queued)
{
	button.disabled = true;
	button.textContent = queued === 'waiting' ? '順番待ち' : '落としています…';
	button.dataset.state = 'busy';
	// 前の失敗の理由を残さない
	button.title = '';
}

/** 本棚を読み込み直すたびと、落とす並びが動くたびに、札の印を付け直す (開いたあらすじはそのまま) */
function remarkRanking()
{
	// 本棚を読み込めなかった (state.library が null) ときは、本棚の印を消さない。消すと本棚にある作品をまた
	// 落とせる。並びから出た札だけは押せる形に戻す (落とせたかは本棚を読み直すまで分からない)
	const owned = state.library ? shelfKeys() : null;
	for (const item of el.rankingList.children) {
		const button = item.querySelector('button.ranking-get');
		if (!button) continue;
		const queued = queueStateOfKey(item.dataset.key);
		if (queued) paintRankingBusy(button, queued);
		else if (owned) paintRankingButton(button, owned.has(item.dataset.key), queueFailureOfKey(item.dataset.key));
		else if (button.dataset.state === 'busy' || button.dataset.state === 'failed' || button.dataset.state === 'unknown') {
			paintRankingButton(button, false, queueFailureOfKey(item.dataset.key));
		}
	}
}

/** 札の「落とす」。ほかの作品を落としている最中でも断らず、順番に並べる (internal #50) */
function downloadFromRanking(button)
{
	const item = button.closest('.ranking-card');
	const work = rankingWorks.find(w => w.key === item.dataset.key);
	if (!work) return;
	// ＋ の欄は開かない: 開くと札がずれて、続けて押すと別の札や空いた所に当たる (win2 の実機)。
	// 並んだことは札が「順番待ち」で示す。場所を聞くときと失敗したときは、viewer-library.js が欄を開く
	enqueueDownload(work.url, work.title, work.key);
}

function rankingGenreName(code)
{
	for (const [, , items] of RANKING_GENRES) {
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
