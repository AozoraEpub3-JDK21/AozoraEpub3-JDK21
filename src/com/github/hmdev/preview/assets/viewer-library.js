/*
 * 本棚 (Phase 2 C3)
 *
 * 設計メモ (docs/epub-preview-plan.md):
 * - api/library は要求のたびに全冊 stat する。ポーリングせず、開いた時と
 *   ⟳ を押した時にだけ取りに行く。
 * - 表紙は no-cache + ETag なので、再変換した本は開き直すだけで新しい絵になる。
 *   URL にキャッシュ避けのパラメータを足さないこと (304 が効かなくなる)。
 * - 本を開くときは iframe の data-path を消す。別の本でも 1 番目のセクションの
 *   パスは同じことが多く (text/xhtml0001.xhtml など)、消さないと
 *   gotoSection が「同じセクション」と判断して前の本を映したままになる。
 */
'use strict';

/** 一度に描くカードの枚数。棚は 2000 冊を想定するので分けて描く */
const LIBRARY_PAGE_SIZE = 200;

/** 書名・著者の並べ替えに使う照合器 (日本語) */
const libraryCollator = new Intl.Collator('ja');

/** 絞り込みを反映するまでの待ち。1 打鍵ごとに全冊を並べ替えるのを避ける (ミリ秒) */
const LIBRARY_FILTER_DELAY = 150;

/** いま描き終えているカードの枚数。「さらに表示」で増える */
let libraryShown = 0;

/**
 * 絞り込みと並べ替えを適用した後の本の配列。
 * 「さらに表示」で描き足すたびに並べ替え直さないよう持っておく。
 */
let libraryVisible = [];

/** 絞り込みの待ち合わせタイマー */
let libraryFilterTimer = 0;

/** 本を切り替えている最中か。連打で 2 冊が同時に読み込まれるのを防ぐ */
let libraryOpening = false;

/**
 * 「続きを取る」の状態。鍵は本の ID。本棚を描き直しても状態が消えないよう、カードの外に持つ
 * (internal #11。サーバの api/book/{id}/update と api/jobs/{id})
 */
const libraryUpdates = new Map();

/**
 * 「続きを取る」で上書きされたのに、読み込み直せなかった開いている本の ID。開いている本のカードは普段は
 * 本棚を閉じるだけだが、ここにある本は読み込み直す
 */
const libraryStale = new Set();

/** 仕事の状態を続けて取れなかったら諦める回数 (一時的な失敗では「失敗」にしない) */
const LIBRARY_UPDATE_MISSES = 5;

/** 本棚の読み込みの通し番号。遅れて返った古い一覧で新しい一覧を上書きしない */
let libraryLoadSeq = 0;

/** 仕事の状態を問い合わせる間隔 (ミリ秒) */
const LIBRARY_UPDATE_POLL = 2000;

/** 仕事の状態の表示 */
const LIBRARY_UPDATE_LABELS = {
	queued: '順番待ち…',
	running: '続きを取っています…',
	done: '更新しました',
	noUpdate: '更新はありません',
	failed: '更新できませんでした',
	gone: '掲載元に作品がありません',
	shrunk: '話数が減ったので止めました',
};

function bindLibraryEvents()
{
	el.libraryToggle.addEventListener('click', () => toggleLibrary());
	el.libraryClose.addEventListener('click', () => closeLibrary());

	bindDownloadEvents();

	el.libraryReload.addEventListener('click', () => {
		loadLibrary().catch(err => showLibraryStatus('本棚を読み込めませんでした: ' + err.message));
	});

	el.libraryFilter.addEventListener('input', () => {
		// 打鍵ごとに 2000 冊を並べ替えると入力が引っ掛かる
		clearTimeout(libraryFilterTimer);
		libraryFilterTimer = setTimeout(() => renderLibrary(), LIBRARY_FILTER_DELAY);
	});
	el.librarySort.addEventListener('change', () => renderLibrary());

	el.libraryShelfSelect.addEventListener('change', () => {
		state.libraryShelf = Number(el.libraryShelfSelect.value);
		renderLibrary();
	});

	el.libraryGrid.addEventListener('click', event => {
		const card = event.target.closest('button.book-card');
		if (!card) return;
		openLibraryBook(card.dataset.bookId)
			.catch(err => showLibraryStatus('本を開けませんでした: ' + err.message));
	});

	// 本棚のキー操作は自分で持つ。本文側の onKeyDown は本棚表示中は何もしない
	document.addEventListener('keydown', onLibraryKeyDown);
}

function onLibraryKeyDown(event)
{
	if (event.ctrlKey || event.altKey || event.metaKey) return;
	if (event.key === 'Escape' && state.libraryOpen) {
		// 検索欄に文字が残っているうちは、まず絞り込みを解く (type="search" の作法)。
		// ブラウザ任せにすると入力欄だけ空になって一覧が戻らない実装があるので自分で消す
		if (event.target === el.libraryFilter && el.libraryFilter.value !== '') {
			el.libraryFilter.value = '';
			clearTimeout(libraryFilterTimer);
			renderLibrary();
			event.preventDefault();
			return;
		}
		closeLibrary();
		event.preventDefault();
		return;
	}
	const tag = (event.target && event.target.tagName) ? event.target.tagName.toLowerCase() : '';
	if (tag === 'input' || tag === 'select' || tag === 'textarea') return;
	if (event.key === 'l' || event.key === 'L') {
		toggleLibrary();
		event.preventDefault();
	}
}

/** 棚を読み込んでいるときだけ本棚ボタンを出す */
function updateLibraryAvailability()
{
	el.libraryToggle.hidden = !state.libraryShelfCount;
	el.libraryToggle.title = (state.libraryCount === null)
		? '本棚 (l)' : '本棚 (l) — ' + state.libraryCount + ' 冊';
	el.libraryFolderName.textContent = libraryPlaceLabel();
}

/** 棚の場所の表示。1 つならフォルダ名、複数なら個数だけ (絶対パスは持っていない) */
function libraryPlaceLabel()
{
	if (state.libraryFolder) return state.libraryFolder;
	return state.libraryShelfCount ? state.libraryShelfCount + ' 個のフォルダ' : '';
}

/** 棚の選択肢を作る。棚が 1 つだけなら選ばせない (選択肢が「すべて」と 1 個で無意味) */
function buildShelfSelect()
{
	const shelves = (state.library && state.library.shelves) ? state.library.shelves : [];
	el.libraryShelfSelect.hidden = (shelves.length < 2);
	if (shelves.length < 2) {
		state.libraryShelf = -1;
		return;
	}
	// 棚を読み込み直したら選択が範囲外になりうる
	if (state.libraryShelf >= shelves.length) state.libraryShelf = -1;
	el.libraryShelfSelect.textContent = '';
	const all = document.createElement('option');
	all.value = '-1';
	all.textContent = 'すべての棚 (' + state.library.count + ' 冊)';
	el.libraryShelfSelect.appendChild(all);
	shelves.forEach((shelf, index) => {
		const option = document.createElement('option');
		option.value = String(index);
		option.textContent = shelf.name + ' (' + shelf.count + ' 冊)';
		el.libraryShelfSelect.appendChild(option);
	});
	el.libraryShelfSelect.value = String(state.libraryShelf);
}

function toggleLibrary(force)
{
	const show = (force === undefined) ? !state.libraryOpen : force;
	if (show) {
		openLibrary().catch(err => showLibraryStatus('本棚を読み込めませんでした: ' + err.message));
	} else {
		closeLibrary();
	}
}

async function openLibrary()
{
	// 棚が 2 つ以上あると libraryFolder は null になる。棚の有無は数で見ること
	if (!state.libraryShelfCount) return;
	state.libraryOpen = true;
	el.libraryView.hidden = false;
	// 本文は隠す。重ねて表示すると裏の iframe が本文を読み込み続ける
	el.mainBody.hidden = true;
	el.libraryToggle.setAttribute('aria-pressed', 'true');
	el.settingsPopover.hidden = true;
	// 本が開かれていないときは戻る先が無いので閉じるボタンを出さない
	el.libraryClose.hidden = !state.bookId;
	// 開くたびに取り直す。変換し直した本の書名・表紙・更新日時を古いまま出さないため
	await loadLibrary();
	el.libraryFilter.focus();
}

function closeLibrary()
{
	// 棚しか読み込んでいない起動では閉じる先が無い
	if (!state.bookId) return;
	state.libraryOpen = false;
	el.libraryView.hidden = true;
	el.mainBody.hidden = false;
	el.libraryToggle.setAttribute('aria-pressed', 'false');
}

/**
 * 本棚を読み込む。keepView なら、描いた枚数・スクロール位置・フォーカスを保つ
 * (「続きを取る」が終わるたびに先頭へ戻らないように)
 */
async function loadLibrary(keepView)
{
	const seq = ++libraryLoadSeq;
	if (!keepView) showLibraryStatus('読み込み中…');
	let library;
	try {
		library = await getJson('api/library');
	} catch (e) {
		if (seq !== libraryLoadSeq) return;
		// 古い一覧を残さない。残すと、消された本のカードを押せてしまう
		state.library = null;
		clearLibraryGrid();
		showLibraryStatus('本棚を読み込めませんでした: ' + e.message);
		return;
	}
	if (seq !== libraryLoadSeq) return;
	state.library = library;
	checkDownloadAvailable();
	state.libraryFolder = library.folderName || null;
	state.libraryShelfCount = library.shelves ? library.shelves.length : state.libraryShelfCount;
	state.libraryCount = library.count;
	updateLibraryAvailability();
	buildShelfSelect();
	if (keepView) renderLibraryKeepingView();
	else renderLibrary();
}

/** 描き直しても、描いた枚数・スクロール位置・フォーカスを保つ */
function renderLibraryKeepingView()
{
	const shown = libraryShown;
	const top = el.libraryGrid.scrollTop;
	const active = document.activeElement;
	const slot = (active && active.closest) ? active.closest('.book-slot') : null;
	const focusId = slot ? slot.dataset.bookId : null;
	const focusUpdate = !!(active && active.classList && active.classList.contains('book-update'));

	//開いている名前の欄は、描き直しても残す (打ちかけの名前を消さない。PR のゲート2)
	const renaming = el.libraryGrid.querySelector('.book-rename-form');
	const renameState = renaming ? {id: renaming.closest('.book-slot').dataset.bookId, value: renaming.querySelector('input').value,
		focused: renaming.contains(document.activeElement)} : null;

	libraryVisible = visibleLibraryBooks();
	libraryShown = 0;
	el.libraryGrid.textContent = '';
	appendLibraryCards(Math.max(shown, LIBRARY_PAGE_SIZE));
	el.libraryGrid.scrollTop = top;
	if (renameState) {
		const slot = el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(renameState.id) + '"]');
		const book = libraryVisible.find(b => b.id === renameState.id);
		if (slot && book) {
			openRenameForm(slot, book, renameState.focused);
			slot.querySelector('.book-rename-form input').value = renameState.value;
		}
	}
	if (focusId) {
		const again = el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(focusId) + '"]');
		const target = again ? again.querySelector(focusUpdate ? '.book-update' : '.book-card') : null;
		if (target) target.focus({preventScroll: true});
	}
}

function clearLibraryGrid()
{
	libraryVisible = [];
	libraryShown = 0;
	el.libraryGrid.textContent = '';
}

/** 棚の選択・絞り込み・並べ替えを適用した本の配列を返す */
function visibleLibraryBooks()
{
	let books = (state.library && state.library.books) ? state.library.books.slice() : [];
	if (state.libraryShelf >= 0) books = books.filter(book => book.shelf === state.libraryShelf);
	const keyword = el.libraryFilter.value.trim().toLowerCase();
	const filtered = keyword ? books.filter(book => libraryHaystack(book).includes(keyword)) : books;

	const order = el.librarySort.value;
	filtered.sort((a, b) => {
		switch (order) {
		case 'modified-asc':
			return (a.modified || 0) - (b.modified || 0);
		case 'title':
			return libraryCollator.compare(a.title || '', b.title || '');
		case 'creator':
			// 著者が同じ本は書名で並べる (著者なしは末尾へ)
			if ((a.creator || '') !== (b.creator || '')) {
				if (!a.creator) return 1;
				if (!b.creator) return -1;
				return libraryCollator.compare(a.creator, b.creator);
			}
			return libraryCollator.compare(a.title || '', b.title || '');
		default:
			return (b.modified || 0) - (a.modified || 0);
		}
	});
	return filtered;
}

/** いま見ている範囲 (棚を選んでいればその棚、選んでいなければ全体) の冊数 */
function shelfScopeCount()
{
	if (!state.library) return 0;
	if (state.libraryShelf < 0) return state.library.count;
	const shelf = (state.library.shelves || [])[state.libraryShelf];
	return shelf ? shelf.count : 0;
}

function libraryHaystack(book)
{
	return [book.title, book.creator, book.fileName, book.subFolder]
		.filter(Boolean).join('\n').toLowerCase();
}

/** 絞り込みと並べ替えをやり直して描き直す。並べ替えはここでしか行わない */
function renderLibrary()
{
	libraryVisible = visibleLibraryBooks();
	libraryShown = 0;
	el.libraryGrid.textContent = '';
	appendLibraryCards();
}

/** 続きのカードを描き足す (既定は 1 ページぶん)。全部描き切るまで「さらに表示」を出す */
function appendLibraryCards(count)
{
	const books = libraryVisible;
	const upto = Math.min(books.length, libraryShown + (count || LIBRARY_PAGE_SIZE));
	const fragment = document.createDocumentFragment();
	for (let i = libraryShown; i < upto; i++) fragment.appendChild(libraryCard(books[i]));
	el.libraryGrid.appendChild(fragment);
	libraryShown = upto;

	// 分母は「いま見ている棚」の冊数。棚を選んでいるときに棚全体の冊数と混ぜない
	const total = shelfScopeCount();
	if (books.length === 0) {
		showLibraryStatus(total === 0 ? 'この棚に EPUB がありません' : '絞り込みに一致する本がありません');
		return;
	}
	// 何冊を隠しているかを必ず出す。黙って打ち切ると「全部出ている」と読めてしまう。
	// 絞り込み中は「一致した冊数」と「棚の冊数」を混ぜない
	const filtered = (books.length !== total);
	const remaining = books.length - libraryShown;
	let message;
	if (remaining > 0) {
		message = books.length + ' 冊中 ' + libraryShown + ' 冊を表示';
		if (filtered) message += ' (棚全体は ' + total + ' 冊)';
	} else {
		message = filtered ? books.length + ' 冊 / 全 ' + total + ' 冊' : '全 ' + total + ' 冊';
	}
	showLibraryStatus(message, remaining > 0 ? remaining : 0);
}

/**
 * 状態表示。残り冊数を渡すと「さらに表示」ボタンを添える。
 * @param {string} message 状態の文言
 * @param {number} [remaining] まだ描いていない冊数
 */
function showLibraryStatus(message, remaining)
{
	el.libraryStatus.textContent = message;
	if (!remaining) return;
	const more = document.createElement('button');
	more.type = 'button';
	more.className = 'more';
	more.textContent = 'さらに表示 (残り ' + remaining + ' 冊)';
	more.addEventListener('click', () => appendLibraryCards());
	el.libraryStatus.append(' ', more);
}

/**
 * 本 1 冊の枠。カードはボタンなので、中にボタンを入れられない。Web から取った本 (掲載元のある本) には、
 * 枠の上に「続きを取る」のボタンを重ね、カードの下に進み具合を出す
 */
function libraryCard(book)
{
	const card = libraryBookButton(book);
	const slot = document.createElement('div');
	slot.className = 'book-slot';
	slot.dataset.bookId = book.id;
	slot.appendChild(card);
	// 進み具合の行は、掲載元の無い本にも空で置く (同じ列のカードの高さを揃える)
	const status = document.createElement('div');
	status.className = 'book-update-status';
	if (book.source) {
		const update = document.createElement('button');
		update.type = 'button';
		update.className = 'book-update';
		update.textContent = '⟳';
		update.title = '続きを取る (' + book.source + ')';
		update.setAttribute('aria-label', '続きを取る: ' + (book.title || book.fileName));
		update.addEventListener('click', event => {
			event.stopPropagation();
			startLibraryUpdate(book).catch(err => setLibraryUpdate(book.id, {state: 'failed', message: err.message}));
		});
		status.setAttribute('aria-live', 'polite');
		//話数が減って止めたときだけ出す。掲載先で話がまとめられただけ、などのとき利用者が選んで取り直す
		const fewer = document.createElement('button');
		fewer.type = 'button';
		fewer.className = 'book-update-anyway';
		fewer.textContent = '減ったまま更新';
		fewer.title = '話数が減ったまま、掲載元の今の内容で本を作り直す';
		fewer.hidden = true;
		fewer.addEventListener('click', event => {
			event.stopPropagation();
			startLibraryUpdate(book, true).catch(err => setLibraryUpdate(book.id, {state: 'failed', message: err.message}));
		});
		//名前を変える (Web から取った本だけ。台帳も一緒に書き換えるので、続きを取るのはそのまま効く)
		const rename = document.createElement('button');
		rename.type = 'button';
		rename.className = 'book-rename';
		rename.textContent = '✎';
		rename.title = '名前を変える';
		rename.setAttribute('aria-label', '名前を変える: ' + (book.title || book.fileName));
		rename.addEventListener('click', event => {
			event.stopPropagation();
			openRenameForm(slot, book);
		});
		slot.append(update, rename, status, fewer);
		paintLibraryUpdate(slot, libraryUpdates.get(book.id));
	} else {
		slot.appendChild(status);
	}
	return slot;
}

function libraryBookButton(book)
{
	const card = document.createElement('button');
	card.type = 'button';
	card.className = 'book-card';
	card.dataset.bookId = book.id;
	if (book.id === state.bookId) {
		card.classList.add('current');
		card.setAttribute('aria-current', 'true');
	}

	const cover = document.createElement('div');
	cover.className = 'cover';
	if (book.hasCover) {
		const image = document.createElement('img');
		image.loading = 'lazy';
		image.decoding = 'async';
		image.alt = '';
		// 版の印に更新日時を付ける。no-cache + ETag で再検証しているが、同じ URL の絵はページの中で
		// 使い回され、「続きを取る」で表紙が変わっても前の絵のままになる。毎回変わる値は付けないこと (304 が効かなくなる)
		image.src = 'api/library/cover/' + encodeURIComponent(book.id) + '?v=' + encodeURIComponent(book.modified || 0);
		// 壊れた画像・未対応形式では 404 が返る。1 冊ぶん絵が出ないだけで棚は使える
		image.addEventListener('error', () => {
			image.remove();
			cover.appendChild(coverPlaceholder(book));
		});
		cover.appendChild(image);
	} else {
		cover.appendChild(coverPlaceholder(book));
	}

	const title = document.createElement('div');
	title.className = 'book-title';
	title.textContent = book.title || book.fileName;

	const creator = document.createElement('div');
	creator.className = 'book-sub';
	creator.textContent = book.creator || '';

	const meta = document.createElement('div');
	meta.className = 'book-meta';
	meta.textContent = formatDateTime(book.modified) + ' · ' + formatBytes(book.size);

	card.append(cover, title, creator);
	// 棚の下にフォルダを切っている場合、同じ書名の本を見分けられるようにする
	if (book.subFolder) {
		const where = document.createElement('div');
		where.className = 'book-sub';
		where.textContent = '📁 ' + book.subFolder;
		card.appendChild(where);
	}
	card.appendChild(meta);
	// 一覧に出していない情報 (置き場所・ファイル名) は tooltip で補う
	card.title = [book.title || book.fileName, book.creator,
		(book.subFolder ? book.subFolder + '/' : '') + book.fileName].filter(Boolean).join('\n');
	return card;
}

/**
 * 「続きを取る」を頼み、終わるまで状態を問い合わせる
 * @param {boolean} [allowFewer] 話数が減っていても取り直す (利用者が「減ったまま更新」を押した)
 */
async function startLibraryUpdate(book, allowFewer)
{
	const current = libraryUpdates.get(book.id);
	if (current && (current.state === 'queued' || current.state === 'running')) return;
	setLibraryUpdate(book.id, {state: 'queued', message: ''});
	const response = await fetch('api/book/' + encodeURIComponent(book.id) + '/update' + (allowFewer ? '?allowFewer=1' : ''),
		{method: 'POST', cache: 'no-store'});
	let body = null;
	try { body = await response.json(); } catch (e) { /* 本文の無い応答 */ }
	if (!response.ok || !body || !body.job) {
		throw new Error((body && body.error) ? body.error : 'HTTP ' + response.status);
	}
	setLibraryUpdate(book.id, {state: body.state, message: body.message || ''});
	let misses = 0;
	for (;;) {
		await new Promise(resolve => setTimeout(resolve, LIBRARY_UPDATE_POLL));
		let job;
		try {
			job = await getJson('api/jobs/' + encodeURIComponent(body.job));
			misses = 0;
		} catch (e) {
			// 一時的な失敗で「失敗」にしない。サーバでは更新が続いている
			if (++misses < LIBRARY_UPDATE_MISSES) continue;
			setLibraryUpdate(book.id, {state: 'failed',
				message: '進み具合を確かめられません (更新は続いているかもしれません。⟳ 一覧を更新で確かめてください): ' + e.message});
			return;
		}
		if (job.state === 'done' && book.id === state.bookId) {
			// 開いている本は、サーバが次の読み込みで新しい版を展開する。目次を古いままにしない
			job.reloaded = await reloadOpenBook(book.id);
		}
		setLibraryUpdate(book.id, {state: job.state, message: job.message || '', reloaded: job.reloaded});
		if (job.state !== 'queued' && job.state !== 'running') {
			//上書きした本の題・表紙を出し直す
			if (job.state === 'done') await loadLibrary(true);
			return;
		}
	}
}

/**
 * 開いている本を新しい版で読み込み直す。読んでいたセクションが新しい版にもあれば、そこに留まる。
 * 読み込み直せなければ、カードを押したときに読み込み直す
 * @return {boolean} 読み込み直したか
 */
async function reloadOpenBook(bookId)
{
	// 本を開いている最中なら、開き終わるのを待つ (開いた本が古い版を読んだ直後に更新が終わることがある。PR の codex)
	for (let i = 0; libraryOpening && i < 300; i++) await new Promise(resolve => setTimeout(resolve, 100));
	if (state.bookId !== bookId) return false;
	if (libraryOpening) {
		libraryStale.add(bookId);
		return false;
	}
	libraryOpening = true;
	try {
		state.inspection = null;
		await loadBook(true);
		if (!el.inspectPanel.hidden) renderInspector();
		libraryStale.delete(bookId);
		return true;
	} catch (e) {
		libraryStale.add(bookId);
		return false;
	} finally {
		libraryOpening = false;
	}
}

/** 状態を覚えて、見えているカードに出す */
function setLibraryUpdate(bookId, update)
{
	libraryUpdates.set(bookId, update);
	const slot = el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(bookId) + '"]');
	if (slot) paintLibraryUpdate(slot, update);
}

function paintLibraryUpdate(slot, update)
{
	const status = slot.querySelector('.book-update-status');
	const button = slot.querySelector('.book-update');
	if (!status || !button) return;
	const busy = update && (update.state === 'queued' || update.state === 'running');
	// disabled にするとフォーカスが外れる (キーボードで押した人が先頭へ戻される)。連打は startLibraryUpdate が断る
	button.setAttribute('aria-disabled', busy ? 'true' : 'false');
	slot.classList.toggle('updating', !!busy);
	if (!update) {
		status.textContent = '';
		return;
	}
	const fewer = slot.querySelector('.book-update-anyway');
	if (fewer) fewer.hidden = update.state !== 'shrunk';
	let text = LIBRARY_UPDATE_LABELS[update.state] || update.state;
	//守りで止めたときは、サーバの文 (N → M 話・HTTP の状態) をそのまま出す。どちらも本は書き換えていない
	if ((update.state === 'gone' || update.state === 'shrunk') && update.message) text = update.message;
	else if (update.state === 'failed' && update.message) text += ': ' + update.message;
	if (update.state === 'done' && update.reloaded) text += ' (開いている本も新しい版にしました)';
	else if (update.state === 'done' && libraryStale.has(slot.dataset.bookId)) text += ' (押すと新しい版を開きます)';
	status.textContent = text;
	// 2 行に収まらないときのため、全文を title にも入れる
	status.title = text + (update.message && !text.includes(update.message) ? '\n' + update.message : '');
	status.dataset.state = update.state;
}

/** 表紙が無い本の代わりに置く箱。書名の 1 文字目を出す */
function coverPlaceholder(book)
{
	const box = document.createElement('div');
	box.className = 'cover-none';
	const label = book.title || book.fileName || '';
	box.textContent = label ? Array.from(label)[0] : '本';
	return box;
}

/**
 * 本棚から本を開く。
 *
 * <p>ページを読み込み直さずに差し替える。読み込み直すと pagehide で
 * {@code api/bye} が飛び、サーバが「ビューアーが閉じた」と判断しうるため
 * (猶予はあるが、わざわざ危ない方を通らない)。
 * 前の本の状態が残らないよう、書籍に紐づく state を明示的に戻すこと。</p>
 *
 * <p><b>同時に 2 冊を読み込ませない。</b>state は 1 冊ぶんしか無いため、
 * 読み込み中に別のカードを押されると 2 つの読み込みが同じ state を書き合い、
 * 遅い方が後に終わると {@code state.bookId} と表示中の本が食い違う。</p>
 */
async function openLibraryBook(bookId)
{
	if (!bookId || libraryOpening) return;
	if (bookId === state.bookId) {
		// 更新の後に読み込み直せなかった本は、読んでいたセクションを保って読み込み直す (PR #119 の codex)
		if (libraryStale.has(bookId)) {
			if (!await reloadOpenBook(bookId)) throw new Error('新しい版を読み込めませんでした');
		}
		closeLibrary();
		return;
	}
	libraryOpening = true;
	try {
		await switchBook(bookId);
		libraryStale.delete(bookId);
	} finally {
		libraryOpening = false;
	}
}

async function switchBook(bookId)
{
	// 開けなかったときに戻せるよう控えておく (消された本を選ぶと 404 になる)
	const previous = {id: state.bookId, book: state.book, inspection: state.inspection,
		spineIndex: state.spineIndex, path: el.frame.getAttribute('data-path')};

	state.bookId = bookId;
	state.book = null;
	// 前の本の EPUB 情報を出し続けないよう捨てる (次に開いたとき取り直す)
	state.inspection = null;
	state.spineIndex = 0;
	state.pendingFragment = null;
	state.pendingAtEnd = false;
	// 別の本でも 1 番目のセクションのパスは同じことが多い。消さないと iframe が更新されない
	el.frame.removeAttribute('data-path');

	try {
		await loadBook();
	} catch (e) {
		state.bookId = previous.id;
		state.book = previous.book;
		state.inspection = previous.inspection;
		state.spineIndex = previous.spineIndex;
		if (previous.path !== null) el.frame.setAttribute('data-path', previous.path);
		throw e;
	}
	// 閉じるのは読み込めてから。棚しか開いていない起動では bookId が入って初めて閉じられる
	closeLibrary();
	if (!el.inspectPanel.hidden) renderInspector();

	// 再読み込みしても同じ本が開くようにする。履歴は増やさない。
	// パスは今の URL のまま使う (相対 URL の解決が変わると api/ 配下に届かなくなる)
	try {
		history.replaceState(null, '', location.pathname + '?book=' + encodeURIComponent(bookId));
	} catch (e) {
		/* 意図的: URL を書き換えられなくても表示中の本には影響しない */
	}
}

/*
 * URL から落とす (internal #11 の案 A)
 *
 * - 落とした本は Web 本棚 (アプリの設定に持つフォルダ) に置く。まだ決まっていなければ、最初に落とすときに聞く
 *   (本棚の画面を開いただけでは聞かない。手元の本を読むだけの人に割り込まない)
 * - 「別の場所…」はアプリ (Java) がフォルダ選択を出す。Windows では選択画面がブラウザの上に出ても、
 *   クリックするまで入力はブラウザに行くので、そう知らせる。パスを打ち込む欄も置く
 */

/** 落とす仕事の ID (問い合わせ中なら)。同時に 1 つだけ */
let libraryDownloadJob = null;
/** Web 本棚の場所を聞いている間、決めたら続けて落とす URL */
let libraryPendingUrl = null;

const LIBRARY_DOWNLOAD_LABELS = {
	queued: '順番待ち…',
	running: '取っています… (話数が多いと時間がかかります)',
	done: '落としました',
	failed: '落とせませんでした',
};

function bindDownloadEvents()
{
	el.libraryDownloadToggle.addEventListener('click', () => {
		const open = el.libraryDownload.hidden;
		el.libraryDownload.hidden = !open;
		el.libraryDownloadToggle.setAttribute('aria-expanded', String(open));
		if (open) el.libraryDownloadUrl.focus();
	});
	el.libraryDownloadForm.addEventListener('submit', event => {
		event.preventDefault();
		startDownload(el.libraryDownloadUrl.value.trim())
			.catch(err => showDownloadStatus('落とせませんでした: ' + err.message, true));
	});
	el.libraryShelfUse.addEventListener('click', () => {
		useWebShelf(el.libraryShelfPath.value).catch(err => { el.libraryShelfNote.textContent = err.message; });
	});
	el.libraryShelfPick.addEventListener('click', () => {
		pickWebShelf().catch(err => { el.libraryShelfNote.textContent = err.message; });
	});
}

/** 落とせるかを確かめたか (本棚を読み直すたびに問い合わせない) */
let libraryDownloadChecked = false;

/** アプリから開いた本棚 (Web 本棚と変換を使える) だけ、落とすボタンを出す */
async function checkDownloadAvailable()
{
	if (libraryDownloadChecked) return;
	libraryDownloadChecked = true;
	try {
		const response = await fetch('api/webshelf', {cache: 'no-store'});
		const info = response.ok ? await response.json() : null;
		el.libraryDownloadToggle.hidden = !(info && info.canDownload);
	} catch (e) {
		el.libraryDownloadToggle.hidden = true;
		libraryDownloadChecked = false;
	}
}

function showDownloadStatus(text, failed)
{
	el.libraryDownloadStatus.textContent = text;
	el.libraryDownloadStatus.dataset.state = failed ? 'failed' : '';
}

/** 本文を 1 つ POST して、JSON (読めなければ null) と状態を返す */
async function postText(path, body)
{
	const response = await fetch(path, {method: 'POST', cache: 'no-store',
		headers: {'Content-Type': 'text/plain; charset=utf-8'}, body: body});
	let json = null;
	try { json = await response.json(); } catch (e) { /* 本文の無い応答 */ }
	return {response, json};
}

async function startDownload(url)
{
	if (!url || libraryDownloadJob) return;
	showDownloadStatus('頼んでいます…');
	const {response, json} = await postText('api/download', url);
	if (response.status === 409 && json && json.needShelf) {
		//Web 本棚がまだ無い。場所を決めたら、この URL を続けて落とす
		libraryPendingUrl = url;
		showDownloadStatus('');
		await askWebShelf();
		return;
	}
	if (!response.ok || !json || !json.job) throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
	libraryDownloadJob = json.job;
	el.libraryDownloadGo.disabled = true;
	try {
		await pollDownload(json.job);
	} finally {
		libraryDownloadJob = null;
		el.libraryDownloadGo.disabled = false;
	}
}

async function pollDownload(jobId)
{
	let misses = 0;
	for (;;) {
		let job;
		try {
			job = await getJson('api/jobs/' + encodeURIComponent(jobId));
			misses = 0;
		} catch (e) {
			// 一時的な失敗で「失敗」にしない。アプリでは落とし続けている
			if (++misses < LIBRARY_UPDATE_MISSES) {
				await new Promise(resolve => setTimeout(resolve, LIBRARY_UPDATE_POLL));
				continue;
			}
			showDownloadStatus('進み具合を確かめられません (落とし続けているかもしれません。⟳ 一覧を更新で確かめてください): ' + e.message, true);
			return;
		}
		const label = LIBRARY_DOWNLOAD_LABELS[job.state] || job.state;
		const failed = job.state === 'failed';
		showDownloadStatus(failed && job.message ? label + ': ' + job.message : label, failed);
		if (job.state === 'done') {
			el.libraryDownloadUrl.value = '';
			try {
				await loadLibrary(true);
			} catch (e) {
				// 落とせている。本棚の読み直しだけが失敗した (PR のゲート2)
				showDownloadStatus(label + ' (本棚に出せませんでした。⟳ 一覧を更新を押してください): ' + e.message, true);
			}
			return;
		}
		if (job.state !== 'queued' && job.state !== 'running') return;
		await new Promise(resolve => setTimeout(resolve, LIBRARY_UPDATE_POLL));
	}
}

/** Web 本棚の場所を聞く。提案の場所を入れておく */
async function askWebShelf()
{
	const info = await getJson('api/webshelf');
	el.libraryShelfPath.value = info.location || info.suggestion || '';
	el.libraryShelfPick.hidden = !info.canPick;
	el.libraryShelfNote.textContent = '';
	el.libraryShelfAsk.hidden = false;
	el.libraryShelfUse.focus();
}

async function useWebShelf(path)
{
	if (!path.trim()) {
		el.libraryShelfNote.textContent = 'フォルダのパスを入れてください';
		return;
	}
	const {response, json} = await postText('api/webshelf', path);
	if (!response.ok) throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
	el.libraryShelfAsk.hidden = true;
	showDownloadStatus('Web 本棚: ' + json.location);
	// Web 本棚が棚に加わったので、本棚を読み直す
	await loadLibrary(true);
	const url = libraryPendingUrl;
	libraryPendingUrl = null;
	if (url) await startDownload(url);
}

/** アプリにフォルダ選択を出してもらう。選んだ場所は入力欄に入れるだけ (決めるのは「この場所にする」) */
async function pickWebShelf()
{
	el.libraryShelfPick.disabled = true;
	el.libraryShelfNote.textContent = 'アプリがフォルダ選択を開きました (ブラウザの外の窓を見てください。出てこなければ、パスを入力してください)';
	try {
		const {response, json} = await postText('api/webshelf/pick', '');
		if (response.status === 501) {
			el.libraryShelfPick.hidden = true;
			el.libraryShelfNote.textContent = 'フォルダ選択を出せません。パスを入力してください';
			return;
		}
		if (!response.ok) throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
		if (json.path) {
			el.libraryShelfPath.value = json.path;
			el.libraryShelfNote.textContent = 'この場所でよければ「この場所にする」を押してください';
			el.libraryShelfUse.focus();
		} else {
			el.libraryShelfNote.textContent = '';
		}
	} finally {
		el.libraryShelfPick.disabled = false;
	}
}

/*
 * 名前を変える (internal #11 の案 A)
 * カードの下に入力欄を出す。拡張子は変えない (アプリが元の拡張子を付ける)
 */

/** 本の名前から拡張子を除いた部分 (.fxl.kepub.epub・.kepub.epub はまとめて 1 つ。アプリの ShelfNames.extensionOf と同じ) */
function baseNameOf(fileName)
{
	const lower = fileName.toLowerCase();
	for (const ext of ['.fxl.kepub.epub', '.kepub.epub']) {
		if (lower.endsWith(ext) && lower.length > ext.length) return fileName.slice(0, -ext.length);
	}
	const dot = fileName.lastIndexOf('.');
	return dot > 0 ? fileName.slice(0, dot) : fileName;
}

/** 名前を変える仕事を待つ。最後の状態を返す */
async function waitRenameJob(jobId)
{
	let misses = 0;
	for (;;) {
		await new Promise(resolve => setTimeout(resolve, 500));
		try {
			const job = await getJson('api/jobs/' + encodeURIComponent(jobId));
			misses = 0;
			if (job.state !== 'queued' && job.state !== 'running') return job;
		} catch (e) {
			if (++misses >= LIBRARY_UPDATE_MISSES) throw e;
		}
	}
}

function openRenameForm(slot, book, focus = true)
{
	if (slot.querySelector('.book-rename-form')) return;
	const form = document.createElement('form');
	form.className = 'book-rename-form';
	const input = document.createElement('input');
	input.type = 'text';
	input.value = baseNameOf(book.fileName);
	input.setAttribute('aria-label', '新しい名前');
	input.spellcheck = false;
	const save = document.createElement('button');
	save.type = 'submit';
	save.textContent = '変える';
	const cancel = document.createElement('button');
	cancel.type = 'button';
	cancel.textContent = '取消';
	const note = document.createElement('div');
	note.className = 'note';
	note.setAttribute('aria-live', 'polite');
	form.append(input, save, cancel, note);
	// カードのキー操作 (Esc で本棚を閉じる) に食われないよう、ここで止める
	form.addEventListener('keydown', event => {
		event.stopPropagation();
		if (event.key === 'Escape') form.remove();
	});
	cancel.addEventListener('click', () => form.remove());
	form.addEventListener('submit', async event => {
		event.preventDefault();
		save.disabled = true;
		note.textContent = '変えています…';
		try {
			const {response, json} = await postText('api/book/' + encodeURIComponent(book.id) + '/rename', input.value);
			if (!response.ok || !json || !json.job) throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
			// ほかの本を更新している間は待つ (名前を変えるのも同じ列)
			const job = await waitRenameJob(json.job);
			if (job.state !== 'done') throw new Error(job.message || '名前を変えられませんでした');
			form.remove();
			await loadLibrary(true);
			// 名前を変えたことを、その本のカードに出す (カードの書名は本の題なので、名前の変化は見えない。win2 の確認)
			const renamed = (state.library && state.library.books || []).find(b => b.subFolder === book.subFolder && b.shelf === book.shelf
				&& baseNameOf(b.fileName) === input.value);
			const renamedSlot = renamed && el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(renamed.id) + '"]');
			const line = renamedSlot && renamedSlot.querySelector('.book-update-status');
			if (line) line.textContent = '名前を変えました: ' + renamed.fileName;
		} catch (err) {
			note.textContent = err.message;
			save.disabled = false;
		}
	});
	slot.appendChild(form);
	if (focus) {
		input.focus();
		input.select();
	}
}

