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
	checked: '新着を確かめました',
	checkFailed: '確かめられませんでした',
};

/** 作り直す・全話を取り直す仕事 (internal #54) で、文を変える状態 */
const LIBRARY_MODE_LABELS = {
	regenerate: {
		running: '作り直しています…',
		done: '作り直しました',
		failed: '作り直せませんでした',
	},
	refetch: {
		running: '全話を取り直しています…',
		done: '全話を取り直しました',
		failed: '取り直せませんでした',
	},
	trash: {
		running: '本棚から消しています…',
		failed: '消せませんでした',
	},
};

function bindLibraryEvents()
{
	el.libraryToggle.addEventListener('click', () => toggleLibrary());
	el.libraryClose.addEventListener('click', () => closeLibrary());

	bindDownloadEvents();
	bindRankingEvents();
	el.libraryCheckAll.addEventListener('click', () => {
		checkAllBooks().catch(err => showLibraryStatus('新着を確かめられませんでした: ' + err.message));
	});

	el.libraryReload.addEventListener('click', () => {
		loadLibrary().catch(err => showLibraryStatus('本棚を読み込めませんでした: ' + err.message));
	});

	bindUpdateNoticeEvents();

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

	// 右クリック・Shift+F10・メニューキーで、その本の操作を出す (internal #54)
	el.libraryGrid.addEventListener('contextmenu', event => {
		const slot = event.target.closest('.book-slot');
		if (!slot || event.target.closest('.book-rename-form')) return;
		const book = libraryBookById(slot.dataset.bookId);
		if (!book) return;
		event.preventDefault();
		openBookMenu(book, event);
	});
	// Shift+F10・メニューキーは自分で受ける (mac の Chrome は contextmenu を起こさなかった)。
	// あとから contextmenu を起こすブラウザでも、そのときフォーカスはメニューの中 (本棚の外) なので二重に開かない
	// (ブラウザのメニューはメニュー側で止める)
	el.libraryGrid.addEventListener('keydown', event => {
		if (!(event.key === 'ContextMenu' || (event.key === 'F10' && event.shiftKey))) return;
		// ⟳ ✎ にフォーカスがあるときも開く (右クリックと同じく枠で見る)。名前の欄の中はブラウザに任せる
		const slot = event.target.closest('.book-slot');
		if (!slot || event.target.closest('.book-rename-form')) return;
		const book = libraryBookById(slot.dataset.bookId);
		if (!book) return;
		event.preventDefault();
		openBookMenu(book, null);
	});

	// 本棚のキー操作は自分で持つ。本文側の onKeyDown は本棚表示中は何もしない
	document.addEventListener('keydown', onLibraryKeyDown);
}

/**
 * 日本語入力の変換中のキーか。Safari (WebKit) は、変換を確定・取り消す Enter や Esc を、
 * compositionend の後に isComposing=false・keyCode 229 で送ってくるので、両方を見る (PR #188 のゲート2)
 */
function isImeKey(event)
{
	return event.isComposing || event.keyCode === 229;
}

function onLibraryKeyDown(event)
{
	if (event.ctrlKey || event.altKey || event.metaKey) return;
	if (event.key === 'Escape' && state.libraryOpen) {
		// 変換中の Esc は変換の取り消し。欄を消したり、ランキングや本棚を閉じたりしない (PR #188 のゲート2)
		if (isImeKey(event)) return;
		// 検索欄に文字が残っているうちは、まず絞り込みを解く (type="search" の作法)。
		// ブラウザ任せにすると入力欄だけ空になって一覧が戻らない実装があるので自分で消す
		if (event.target === el.libraryFilter && el.libraryFilter.value !== '') {
			el.libraryFilter.value = '';
			clearTimeout(libraryFilterTimer);
			renderLibrary();
			event.preventDefault();
			return;
		}
		// ランキングの単語の欄も同じ。消して探し直し、ランキングはしまわない (internal #62)
		if (clearRankingWordIfAny(event)) {
			event.preventDefault();
			return;
		}
		// 手前に出しているランキングを先にしまう
		if (closeRankingIfOpen()) {
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
	// 新しい版は本棚を開くたびに問い合わせる (1 日 1 回の制限はサーバが持つ。タブを開いたままでも次の日に確かめる。PR のゲート2)
	libraryUpdateChecked = false;
	// 開くたびに取り直す。変換し直した本の書名・表紙・更新日時を古いまま出さないため
	await loadLibrary();
	// ランキングを出したまま本棚を閉じていたら、開き直したときに頼み直す (古い順位を出したままにしない)
	if (rankingOpen()) loadRanking();
	// ランキングを出している間は絞り込みを隠している (viewer.css)。隠した欄にフォーカスを置かない
	(rankingOpen() ? el.libraryRankingToggle : el.libraryFilter).focus();
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
	checkUpdateNotice();
	updateCheckAllButton();
	state.libraryFolder = library.folderName || null;
	state.libraryShelfCount = library.shelves ? library.shelves.length : state.libraryShelfCount;
	state.libraryCount = library.count;
	updateLibraryAvailability();
	buildShelfSelect();
	if (keepView) renderLibraryKeepingView();
	else renderLibrary();
	// ランキングの「本棚にあります」も付け直す。Web 本棚の場所を聞いてから落とした本は
	// ランキングの札を通らずに落ちる (viewer-ranking.js)
	remarkRanking();
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

	//開いている名前の欄は、描き直しても残す (打ちかけの名前を消さない。PR のゲート2)。
	// 作り直さず、その欄 (要素) をそのまま新しいカードへ移す: 打ちかけの値・カーソルと選択・送信中の状態
	// (「変えています…」と押せない「変える」)・失敗を書く先は、どれも欄の要素とその処理が持っている。
	// 作り直すと、送信中の欄が押せる形で戻って 2 度送れ、失敗は外れた古い欄に書かれていた (internal #46、PR #170 の Opus)
	const renaming = el.libraryGrid.querySelector('.book-rename-form');
	const renamingInput = renaming ? renaming.querySelector('input') : null;
	const renameState = renaming ? {id: renaming.closest('.book-slot').dataset.bookId,
		// 欄の中でフォーカスを持っていた要素 (入力欄か、ボタン)
		focused: renaming.contains(document.activeElement) ? document.activeElement : null,
		selection: [renamingInput.selectionStart, renamingInput.selectionEnd, renamingInput.selectionDirection]} : null;

	libraryVisible = visibleLibraryBooks();
	libraryShown = 0;
	el.libraryGrid.textContent = '';
	//「続きを取る」を押した本（状態を持つ本）と、触っていた本（フォーカス・名前の欄）が、並べ替えで描いた範囲の外へ動いたら、そこまで描く
	//（「更新が古い順」で更新した本は末尾へ動き、描かないとカードも進み具合も消える。マウスで押すとフォーカスが移らない
	//ブラウザがあるので、フォーカスだけには頼らない。internal #24）
	let count = Math.max(shown, LIBRARY_PAGE_SIZE);
	for (const id of [...libraryUpdates.keys(), focusId, renameState ? renameState.id : null]) {
		const index = id ? libraryVisible.findIndex(b => b.id === id) : -1;
		if (index >= count) count = index + 1;
	}
	appendLibraryCards(count);
	el.libraryGrid.scrollTop = top;
	if (renameState) {
		const slot = el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(renameState.id) + '"]');
		if (slot) {
			slot.appendChild(renaming);
			// 移すとフォーカスは外れる。持っていた要素へ戻す。入力欄ならカーソルと選択 (向きも) を戻す
			if (renameState.focused) {
				renameState.focused.focus({preventScroll: true});
				if (renameState.focused === renamingInput && renameState.selection[0] !== null) {
					renamingInput.setSelectionRange(...renameState.selection);
				}
			}
		}
	}
	// 名前の欄 (入力欄かボタン) にフォーカスがあったなら、そこに置いたまま。カードへ移すと、打った字がどこにも
	// 入らない (本の描き直しは落とし終えるたびに来る。internal #46)
	if (focusId && !(renameState && renameState.focused)) {
		const again = el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(focusId) + '"]');
		const target = again ? again.querySelector(focusUpdate ? '.book-update' : '.book-card') : null;
		if (target) target.focus({preventScroll: true});
	}
	closeBookMenuIfCardGone();
}

function clearLibraryGrid()
{
	libraryVisible = [];
	libraryShown = 0;
	el.libraryGrid.textContent = '';
	closeBookMenuIfCardGone();
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
	closeBookMenuIfCardGone();
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
	status.setAttribute('aria-live', 'polite');
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
		//話数が減って止めたときだけ出す。掲載先で話がまとめられただけ、などのとき利用者が選んで取り直す
		const fewer = document.createElement('button');
		fewer.type = 'button';
		fewer.className = 'book-update-anyway';
		fewer.textContent = '減ったまま更新';
		fewer.title = '話数が減ったまま、掲載元の今の内容で本を作り直す';
		fewer.hidden = true;
		fewer.addEventListener('click', event => {
			event.stopPropagation();
			// 全話を取り直して止めたときは、全話を取り直す仕事のまま続ける
			const last = libraryUpdates.get(book.id);
			const mode = last && last.mode === 'refetch' ? 'refetch' : undefined;
			startLibraryUpdate(book, true, mode).catch(err => setLibraryUpdate(book.id, {state: 'failed', message: err.message, mode: mode}));
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
 * @param {string} [mode] regenerate: 取りに行かず、キャッシュから今の設定で作り直す。refetch: 全話を取り直す (internal #54)
 */
async function startLibraryUpdate(book, allowFewer, mode)
{
	const current = libraryUpdates.get(book.id);
	if (current && (current.state === 'queued' || current.state === 'running')) return;
	const kind = mode ? {mode: mode} : {};
	setLibraryUpdate(book.id, {state: 'queued', message: '', ...kind});
	const path = mode === 'regenerate' ? '/regenerate'
		: (mode === 'refetch' ? '/refetch' : '/update') + (allowFewer ? '?allowFewer=1' : '');
	const response = await fetch('api/book/' + encodeURIComponent(book.id) + path, {method: 'POST', cache: 'no-store'});
	let body = null;
	try { body = await response.json(); } catch (e) { /* 本文の無い応答 */ }
	if (!response.ok || !body || !body.job) {
		throw new Error((body && body.error) ? body.error : 'HTTP ' + response.status);
	}
	setLibraryUpdate(book.id, {state: body.state, message: body.message || '', ...kind});
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
			setLibraryUpdate(book.id, {state: 'failed', ...kind,
				message: '進み具合を確かめられません (更新は続いているかもしれません。⟳ 一覧を更新で確かめてください): ' + e.message});
			return;
		}
		if (job.state === 'done' && book.id === state.bookId) {
			// 開いている本は、サーバが次の読み込みで新しい版を展開する。目次を古いままにしない
			job.reloaded = await reloadOpenBook(book.id);
		}
		setLibraryUpdate(book.id, {state: job.state, message: job.message || '', reloaded: job.reloaded, ...kind});
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
		state.inspectionSeq++;
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
	if (bookMenu && bookMenu.bookId === bookId) refreshBookMenu();
	const slot = el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(bookId) + '"]');
	if (slot) paintLibraryUpdate(slot, update);
}

function paintLibraryUpdate(slot, update)
{
	const status = slot.querySelector('.book-update-status');
	// ⟳ の無い本 (掲載元の無い本) にも、本棚から消す途中・断った理由は出す
	const button = slot.querySelector('.book-update');
	if (!status) return;
	const busy = update && (update.state === 'queued' || update.state === 'running');
	// disabled にするとフォーカスが外れる (キーボードで押した人が先頭へ戻される)。連打は startLibraryUpdate が断る
	if (button) button.setAttribute('aria-disabled', busy ? 'true' : 'false');
	slot.classList.toggle('updating', !!busy);
	if (!update) {
		status.textContent = '';
		return;
	}
	const fewer = slot.querySelector('.book-update-anyway');
	// 作り直しで止めたときは出さない (押すと掲載元から取り直す。作り直しは取りに行かない操作)
	if (fewer) fewer.hidden = update.state !== 'shrunk' || update.mode === 'regenerate';
	let text = (update.mode && LIBRARY_MODE_LABELS[update.mode][update.state]) || LIBRARY_UPDATE_LABELS[update.state] || update.state;
	//守りで止めたとき・新着を確かめたときは、サーバの文 (N → M 話・HTTP の状態・n 話新着) をそのまま出す
	if ((update.state === 'gone' || update.state === 'shrunk' || update.state === 'checked') && update.message) text = update.message;
	else if ((update.state === 'failed' || update.state === 'checkFailed') && update.message) text += ': ' + update.message;
	// 全話を取り直して、取り直せなかった話があったとき (n 話は前のキャッシュで作った)
	// 但し書きがもう括弧で始まるとき (（1 つ前の版は残せませんでした）) は、括弧を重ねない
	else if (update.state === 'done' && update.mode === 'refetch' && update.message) text += /^[（(]/.test(update.message) ? update.message : ' (' + update.message + ')';
	if (update.state === 'done' && update.reloaded) text += ' (開いている本も新しい版にしました)';
	else if (update.state === 'done' && libraryStale.has(slot.dataset.bookId)) text += ' (押すと新しい版を開きます)';
	status.textContent = text;
	// 2 行に収まらないときのため、全文を title にも入れる
	status.title = text + (update.message && !text.includes(update.message) ? '\n' + update.message : '');
	status.dataset.state = update.state === 'checked' && (update.newEpisodes > 0 || update.revisedEpisodes > 0) ? 'checked-new' : update.state;
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
		inspectionSeq: state.inspectionSeq, spineIndex: state.spineIndex, path: el.frame.getAttribute('data-path')};

	state.bookId = bookId;
	state.book = null;
	// 前の本の EPUB 情報を出し続けないよう捨てる (次に開いたとき取り直す)
	state.inspection = null;
	state.inspectionSeq++;
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
		// 前の本の EPUB 情報の要求が取得中なら、その返りを受け取る（番号を進めたままだと捨てて空のままになる。PR #157 の検証）
		state.inspectionSeq = previous.inspectionSeq;
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

/** Web 本棚の場所を聞いている間、決めたら続けて落とす URL */
let libraryPendingUrl = null;

/*
 * 落とす作品の順番待ち (internal #50)
 *
 * 2 つ目の「落とす」や ＋ を断らずに、ここに並べて 1 つずつ落とす。サイトへの負荷は今までと同じ 1 本ずつ。
 * 並びはこのページが持つ (サーバへ頼むのは順番が来てから)。だから、まだ始まっていない作品は取り消せるが、
 * ページを閉じると消える。落とし始めた作品はアプリが最後まで落とす。
 * ⟳ の更新と「新着を確かめる」は、アプリの同じ 1 本の列 (updateExecutor) に押した順に並ぶ。
 *
 * 1 件: {id, url, label, key, state, message}。state は waiting / running / done / failed / unknown / cancelled。
 * key はランキングの札の印 (URL を貼ったときは null)
 */
const libraryQueue = [];
let libraryQueueSeq = 0;
/** runQueue が回っているか。同時に 2 本回さない */
let libraryQueueRunning = false;
/** 一覧に残す終わった作品の数 (古いものから消す) */
const LIBRARY_QUEUE_KEEP_FINISHED = 10;

const LIBRARY_QUEUE_LABELS = {
	waiting: '順番待ち',
	running: '落としています…',
	done: '落としました',
	failed: '落とせませんでした',
	unknown: '確かめられません (⟳ 一覧を更新で確かめてください)',
	cancelled: '取り消しました',
};

function queueActive(item)
{
	return item.state === 'waiting' || item.state === 'running';
}

/** ランキングの札の印 (key) が並びのどこにいるか: 'waiting' / 'running' / null */
function queueStateOfKey(key)
{
	const item = libraryQueue.find(i => i.key === key && queueActive(i));
	return item ? item.state : null;
}

/**
 * その作品 (key) の最後の試みが失敗か、進み具合が分からなくなったなら、その 1 件。並び直したら null。
 * ランキングの札に失敗を出すため (internal #52)
 */
function queueFailureOfKey(key)
{
	let last = null;
	for (const item of libraryQueue) if (item.key === key) last = item;
	// 「確かめられません」は、その作品が本棚に出るまで外さない (出れば札は「本棚にあります」)。読み直しで外すと、
	// アプリがまだ落としている作品をもう一度頼めた。サーバの重複の判定は URL の文字そのままで、作品では見ない
	// (PR #171 の codex)
	return last && (last.state === 'failed' || last.state === 'unknown') ? last : null;
}

/** ランキングを出している間に失敗が来て、まだ ＋ の欄で見ていない (internal #52、PR #171 の Opus) */
let libraryFailureUnseen = false;

/** 見ていない失敗の印を ＋ に付ける / 外す */
function markFailureUnseen(unseen)
{
	libraryFailureUnseen = unseen;
	el.libraryDownloadToggle.dataset.state = unseen ? 'failed' : '';
}

/**
 * 並びに足す。同じ作品がもう並んでいるか落としている最中なら足さない。
 * 貼った URL も作品の印 (workKeyOfUrl) で見る: ランキングの札に「順番待ち」が付き、書き方の違う同じ作品が
 * 二重に並ばない (PR #169 の Opus レビュー)
 */
function enqueueDownload(url, label, key)
{
	if (!url) return false;
	key = key || workKeyOfUrl(url);
	const same = i => i.url === url || (key && i.key === key);
	// 進み具合の行ではすぐ上書きされて読めない (win2 の実機、0.5 秒で消えた)。別の行に出し、次に足すまで残す
	const refuse = text => {
		el.libraryQueueRefusal.textContent = text;
		el.libraryQueueRefusal.hidden = false;
		return false;
	};
	if (libraryQueue.some(i => queueActive(i) && same(i))) return refuse('その作品はもう順番に入っています');
	// 進み具合が分からなくなった作品は、本棚に出るまで足さない。アプリがまだ落としているかもしれず、サーバの
	// 重複の判定は URL の文字そのままなので、書き方の違う URL だと同じ作品を 2 度落とす。ランキングの札だけで
	// なく ＋ からも同じ (PR #171 の codex)
	const onShelf = key && state.library ? shelfKeys().has(key) : false;
	if (!onShelf && libraryQueue.some(i => i.state === 'unknown' && same(i))) {
		return refuse('その作品はまだ落としているかもしれません。⟳ 一覧を更新で確かめてください (本棚に出なければ、このページを開き直すと落とせます)');
	}
	el.libraryQueueRefusal.hidden = true;
	libraryQueue.push({id: ++libraryQueueSeq, url: url, label: label || url, key: key || null, state: 'waiting', message: ''});
	renderQueue();
	runQueue();
	return true;
}

/** ＋ の欄を開く。ランキングの札から押したときは開かない (札がずれて、続けて押すと外れる。win2 の実機) */
function openDownloadPanel()
{
	el.libraryDownload.hidden = false;
	el.libraryDownloadToggle.setAttribute('aria-expanded', 'true');
	markFailureUnseen(false);
}

function cancelQueued(id)
{
	const item = libraryQueue.find(i => i.id === id);
	// 落とし始めた作品はアプリが落としている。ここで止めたことにはしない
	if (!item || item.state !== 'waiting') return;
	// Web 本棚の場所を聞いている作品なら、聞くのもやめる (決めても落とさない)
	if (libraryPendingUrl === item.url) {
		libraryPendingUrl = null;
		el.libraryShelfAsk.hidden = true;
	}
	item.state = 'cancelled';
	renderQueue();
	runQueue();
}

/** 待っている作品を 1 つずつ落とす。失敗しても次へ進む。Web 本棚の場所を聞いている間は止まる */
async function runQueue()
{
	if (libraryQueueRunning) return;
	libraryQueueRunning = true;
	try {
		for (;;) {
			// 場所を決めると useWebShelf が呼び直す
			if (libraryPendingUrl) return;
			const item = libraryQueue.find(i => i.state === 'waiting');
			if (!item) return;
			item.state = 'running';
			renderQueue();
			let outcome;
			try {
				outcome = await startDownload(item.url);
			} catch (err) {
				// 場所を聞く途中で失敗したなら、聞いていないことにする (残すと列が止まったままになる)
				if (libraryPendingUrl === item.url) libraryPendingUrl = null;
				outcome = {state: 'failed', message: err.message};
				showDownloadStatus('落とせませんでした: ' + err.message, true);
			}
			if (outcome.state === 'needShelf') {
				// 場所を決めたら、この作品から続ける
				item.state = 'waiting';
				renderQueue();
				return;
			}
			item.state = outcome.state;
			item.message = outcome.message || '';
			// 失敗は理由の出る ＋ の欄を開いて見せる。ただしランキングを出している間は開かない: 開くと一覧が欄の
			// 高さだけ下がり、そのとき押そうとしていた札が外れる (internal #52、win2 の実機)。そこでは札が失敗を出す
			// 札が見えないこともある (期間を変えた、貼った URL)。そのときのために ＋ に印を付け、ランキングを
			// しまったら欄を開く (toggleRanking)
			if (outcome.state === 'failed' || outcome.state === 'unknown') {
				if (el.libraryRanking.hidden) openDownloadPanel();
				else markFailureUnseen(true);
			}
			renderQueue();
		}
	} finally {
		libraryQueueRunning = false;
		renderQueue();
	}
}

/** ＋ の欄に並びを出す。ランキングの札も塗り直す */
function renderQueue()
{
	// 終わった作品は新しいものから LIBRARY_QUEUE_KEEP_FINISHED 件だけ残す
	// 進み具合が分からなくなった作品は消さない: 本棚に出るまで、2 度落とさないための印になっている (PR #171 の codex)
	const finished = libraryQueue.filter(i => !queueActive(i) && i.state !== 'unknown');
	for (const old of finished.slice(0, Math.max(0, finished.length - LIBRARY_QUEUE_KEEP_FINISHED))) {
		libraryQueue.splice(libraryQueue.indexOf(old), 1);
	}
	const list = el.libraryQueueList;
	list.replaceChildren();
	for (const item of libraryQueue) {
		const row = document.createElement('li');
		row.dataset.state = item.state;
		const name = document.createElement('span');
		name.className = 'name';
		name.textContent = item.label;
		name.title = item.url;
		row.appendChild(name);
		const status = document.createElement('span');
		status.className = 'state';
		status.textContent = LIBRARY_QUEUE_LABELS[item.state] + (item.message ? ': ' + item.message : '');
		row.appendChild(status);
		if (item.state === 'waiting') {
			const cancel = document.createElement('button');
			cancel.type = 'button';
			cancel.textContent = '取り消す';
			cancel.addEventListener('click', () => cancelQueued(item.id));
			row.appendChild(cancel);
		}
		list.appendChild(row);
	}
	list.hidden = libraryQueue.length === 0;
	el.libraryQueueNote.hidden = !libraryQueue.some(i => i.state === 'waiting');
	if (typeof remarkRanking === 'function') remarkRanking();
}

const LIBRARY_DOWNLOAD_LABELS = {
	// アプリの列で、⟳ の更新や新着の確認の後ろにいる (並びの一覧では「落としています…」)
	queued: '始まるのを待っています…',
	running: '取っています… (話数が多いと時間がかかります)',
	done: '落としました',
	failed: '落とせませんでした',
};

function bindDownloadEvents()
{
	el.libraryDownloadToggle.addEventListener('click', () => {
		markFailureUnseen(false);
		const open = el.libraryDownload.hidden;
		el.libraryDownload.hidden = !open;
		el.libraryDownloadToggle.setAttribute('aria-expanded', String(open));
		if (open) el.libraryDownloadUrl.focus();
	});
	el.libraryDownloadForm.addEventListener('submit', event => {
		event.preventDefault();
		const url = el.libraryDownloadUrl.value.trim();
		// 並べたら欄は空ける。続けて次の URL を貼れる。断ったときは残す (何を断ったか分かるように)
		if (enqueueDownload(url)) el.libraryDownloadUrl.value = '';
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
		el.libraryRankingToggle.hidden = !(info && info.canDownload);
		libraryCanCheck = !!(info && info.canDownload);
		updateCheckAllButton();
	} catch (e) {
		el.libraryDownloadToggle.hidden = true;
		el.libraryRankingToggle.hidden = true;
		libraryDownloadChecked = false;
	}
}

function showDownloadStatus(text, failed)
{
	el.libraryDownloadStatus.textContent = text;
	el.libraryDownloadStatus.dataset.state = failed ? 'failed' : '';
}

/** 新しい版を確かめたか (本棚を読み直すたびに問い合わせない。internal #48) */
let libraryUpdateChecked = false;
/** 帯に出している版 (閉じるときに送る) */
let libraryUpdateShown = null;

/**
 * 本棚を開いたときに、新しい版の知らせを問い合わせる (internal #48)。サーバが 1 日 1 回までに抑えるので、
 * ここでは本棚を開いて最初の 1 回だけ問い合わせる。失敗しても本棚は使える
 */
async function checkUpdateNotice()
{
	if (libraryUpdateChecked) return;
	libraryUpdateChecked = true;
	try {
		const response = await fetch('api/update', {cache: 'no-store'});
		showUpdateNotice(response.ok ? await response.json() : null);
	} catch (e) {
		libraryUpdateChecked = false;
	}
}

/** 表示設定の欄を開いたとき、確かめるかの印だけを読む (外に通信しない) */
async function peekUpdateNotice()
{
	try {
		const response = await fetch('api/update?peek', {cache: 'no-store'});
		if (response.ok) showUpdateNotice(await response.json());
	} catch (e) { /* 読めなければ印は出さないまま */ }
}

/** 知らせを帯と表示設定の欄に出す。アプリから開いた本棚でなければ (available でなければ) どちらも出さない */
function showUpdateNotice(info)
{
	const available = !!(info && info.available);
	el.updateCheckField.hidden = !available;
	el.updateCheckEnabled.checked = !!(info && info.enabled);
	libraryUpdateShown = available && info.show ? info.latest : null;
	el.libraryUpdateBand.hidden = !libraryUpdateShown;
	if (libraryUpdateShown) {
		// タグは v1.8.0-jdk21、今の版は 1.7.3-jdk21。頭の v をそろえて出す (PR のゲート2)
		const plain = v => String(v).replace(/^v(?=\d)/, '');
		el.libraryUpdateText.textContent = '新しい版 ' + plain(info.latest) + ' があります（使っているのは ' + plain(info.current) + '）';
		el.libraryUpdateLink.href = info.url;
	}
}

function bindUpdateNoticeEvents()
{
	el.libraryUpdateClose.addEventListener('click', async () => {
		if (!libraryUpdateShown) return;
		el.libraryUpdateBand.hidden = true;
		const {response, json} = await postText('api/update/dismiss', libraryUpdateShown).catch(() => ({}));
		if (response && response.ok && json) {
			showUpdateNotice(json);
		} else {
			// 閉じたことを書けなかったら、帯を戻す (次に開いたときにまた出るので、閉じたように見せない。PR の codex)
			el.libraryUpdateBand.hidden = false;
			el.libraryUpdateClose.title = '閉じたことを保存できませんでした';
		}
	});
	el.updateCheckEnabled.addEventListener('change', async () => {
		const wanted = el.updateCheckEnabled.checked;
		const {response, json} = await postText('api/update/enabled', String(wanted)).catch(() => ({}));
		if (response && response.ok && json) {
			showUpdateNotice(json);
		} else {
			// 書けなかった (記録の場所に書けないなど) なら、印を元に戻す。切り替わったように見せない (PR の codex)
			el.updateCheckEnabled.checked = !wanted;
			el.updateCheckField.title = '切り替えを保存できませんでした';
		}
	});
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

/**
 * 1 つ落とす。順番待ちの列 (runQueue) からだけ呼ぶ。
 * 返り値の state: done / failed / unknown (進み具合が分からなくなった) / needShelf (Web 本棚の場所を聞いた)
 */
async function startDownload(url)
{
	showDownloadStatus('頼んでいます…');
	const {response, json} = await postText('api/download', url);
	if (response.status === 409 && json && json.needShelf) {
		//Web 本棚がまだ無い。場所を決めたら、この URL から続けて落とす
		libraryPendingUrl = url;
		showDownloadStatus('');
		await askWebShelf();
		return {state: 'needShelf'};
	}
	if (!response.ok || !json || !json.job) throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
	return pollDownload(json.job);
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
			return {state: 'unknown', message: e.message};
		}
		const label = LIBRARY_DOWNLOAD_LABELS[job.state] || job.state;
		const failed = job.state === 'failed';
		showDownloadStatus(failed && job.message ? label + ': ' + job.message : label, failed);
		if (job.state === 'done') {
			try {
				await loadLibrary(true);
			} catch (e) {
				// 落とせている。本棚の読み直しだけが失敗した (PR のゲート2)
				showDownloadStatus(label + ' (本棚に出せませんでした。⟳ 一覧を更新を押してください): ' + e.message, true);
			}
			return {state: 'done'};
		}
		if (failed) return {state: 'failed', message: job.message || ''};
		if (job.state !== 'queued' && job.state !== 'running') return {state: 'unknown', message: job.state};
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
	// ランキングの札から押したときは欄が閉じている。場所を聞くのはここなので開く
	openDownloadPanel();
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
	// 場所は決まった。聞いている印は、本棚の読み直しより先に外す: 読み直しが失敗しても列が止まらないように
	// (PR #169 の Opus レビュー)
	libraryPendingUrl = null;
	try {
		// Web 本棚が棚に加わったので、本棚を読み直す
		await loadLibrary(true);
	} finally {
		// 待っていた作品 (場所を聞いた作品から) を続けて落とす
		runQueue();
	}
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

/*
 * 本の右クリックのメニュー (internal #54)
 * カードのボタン (⟳ ✎) はそのまま残し、同じ操作とボタンの無い操作をここに並べる。
 * メニューは body に 1 つだけ置く (カードの中に置くと、本棚の描き直しで消える)
 */

/** 開いているメニュー。閉じているときは null */
let bookMenu = null;

function libraryBookById(bookId)
{
	return ((state.library && state.library.books) || []).find(b => b.id === bookId) || null;
}

/** いま描かれている、その本の枠。描き直しの後でも引けるよう、押したときに探す */
function librarySlotOf(bookId)
{
	return el.libraryGrid.querySelector('.book-slot[data-book-id="' + CSS.escape(bookId) + '"]');
}

/** 掲載元のページとして開いてよい URL か (台帳の値をそのまま開かない) */
function isWebSource(source)
{
	try {
		const url = new URL(source);
		return url.protocol === 'https:' || url.protocol === 'http:';
	} catch (e) {
		return false;
	}
}

/** その本の更新・確かめる仕事が、順番待ちか動いている最中か */
function libraryBookBusy(bookId)
{
	const update = libraryUpdates.get(bookId);
	return !!(update && (update.state === 'queued' || update.state === 'running'));
}

/**
 * メニューの項目。disabled は開いたときと押したときの両方で見る (開いている間に仕事が終わる・始まる)。
 * run には押したときの本を渡す (開いている間に本棚が読み直されることがある)。
 * keepFocus の項目は自分でフォーカスを動かすので、カードへ戻さない
 */
function bookMenuItems(book)
{
	const items = [];
	if (book.source) {
		items.push({label: '続きを取る', disabled: () => libraryBookBusy(book.id), run: current => {
			startLibraryUpdate(current).catch(err => setLibraryUpdate(current.id, {state: 'failed', message: err.message}));
		}});
		// 掲載元へは取りに行かず、キャッシュから今の設定で作り直す (設定を変えたあとに)。アプリから開いた本棚だけ
		if (libraryCanCheck) {
			items.push({label: '作り直す (今の設定で)', disabled: () => libraryBookBusy(book.id), run: current => {
				startLibraryUpdate(current, false, 'regenerate')
					.catch(err => setLibraryUpdate(current.id, {state: 'failed', message: err.message, mode: 'regenerate'}));
			}});
			// キャッシュにある話も掲載元から取り直す。話の数だけサイトに頼むので、もう一度押して決める
			items.push({label: '全話を取り直す…', confirm: 'もう一度押すと、全話を掲載元から取り直します',
				disabled: () => libraryBookBusy(book.id), run: current => {
					startLibraryUpdate(current, false, 'refetch')
						.catch(err => setLibraryUpdate(current.id, {state: 'failed', message: err.message, mode: 'refetch'}));
				}});
		}
		if (libraryCanCheck) {
			// 結果の文 (n 話新着・新着なし) はサーバが作り、カードの下に出る
			items.push({label: 'この本の新着を確かめる', disabled: () => libraryBookBusy(book.id) || libraryChecking,
				run: current => checkSingleBook(current)});
		}
		items.push({label: '名前を変える…', keepFocus: true, run: current => {
			const slot = librarySlotOf(current.id);
			if (slot) openRenameForm(slot, current);
			else showLibraryStatus('この本のカードが一覧に出ていません (絞り込みを解いてください)');
		}});
		items.push(null);
	}
	items.push({label: '保存フォルダを開く', run: current => {
		revealBook(current.id).catch(err => showLibraryStatus('フォルダを開けませんでした: ' + err.message));
	}});
	// Send to Kindle のページはファイルを受け取れないので、本を選んだフォルダも開き、そこから落としてもらう (internal #55)
	const sendToKindle = state.library && state.library.sendToKindle;
	if (state.settings.sendToKindle && sendToKindle) {
		items.push({label: 'Kindle に送る', run: current => {
			window.open(sendToKindle, '_blank', 'noopener');
			revealBook(current.id).catch(err => showLibraryStatus('フォルダを開けませんでした: ' + err.message));
		}});
	}
	if (book.source && isWebSource(book.source)) {
		items.push({label: '作品ページを開く', run: current => {
			window.open(current.source, '_blank', 'noopener');
		}});
	}
	// 本を OS のゴミ箱へ移す (ゴミ箱から戻せる)。キャッシュと台帳は残る
	items.push(null);
	items.push({label: '本棚から消す…', confirm: 'もう一度押すと、この本をゴミ箱へ移します',
		disabled: () => libraryBookBusy(book.id), run: current => {
			trashBook(current).catch(err => setLibraryUpdate(current.id, {state: 'failed', message: err.message, mode: 'trash'}));
		}});
	return items;
}

/** 本をゴミ箱へ移し、本棚を読み直す (internal #54) */
async function trashBook(book)
{
	// 消している間は、その本の ⟳・確かめる・もう一度消すを押せなくする (Windows はドライブを確かめるので数秒かかる)
	setLibraryUpdate(book.id, {state: 'running', message: '', mode: 'trash'});
	const response = await fetch('api/book/' + encodeURIComponent(book.id) + '/trash', {method: 'POST', cache: 'no-store'});
	if (!response.ok) {
		let body = null;
		try { body = await response.json(); } catch (e) { /* 本文の無い応答 */ }
		throw new Error((body && body.error) ? body.error : 'HTTP ' + response.status);
	}
	libraryUpdates.delete(book.id);
	await loadLibrary(true);
	// 冊数と「さらに表示」は残し、その前に書く
	el.libraryStatus.prepend('「' + (book.title || book.fileName) + '」をゴミ箱へ移しました。');
}

/**
 * メニューを開く。マウスなら押した所に、キーボード (Shift+F10・メニューキー) ならカードの左上に出す
 */
function openBookMenu(book, event)
{
	closeBookMenu(false);
	const slot = librarySlotOf(book.id);
	const menu = document.createElement('div');
	menu.className = 'book-menu';
	menu.setAttribute('role', 'menu');
	menu.setAttribute('aria-label', (book.title || book.fileName) + ' の操作');
	const entries = [];
	// 確かめの文を読み上げる場所 (押した項目の文を変えるだけでは、読み上げないことが多い)。
	// menu の子に置けるのは項目と区切りだけなので、メニューの外に置き、閉じるときに一緒に外す
	const live = document.createElement('div');
	live.className = 'book-menu-live';
	live.setAttribute('role', 'status');
	document.body.appendChild(live);
	for (const item of bookMenuItems(book)) {
		if (!item) {
			const line = document.createElement('div');
			line.className = 'book-menu-separator';
			line.setAttribute('role', 'separator');
			menu.appendChild(line);
			continue;
		}
		const button = document.createElement('button');
		button.type = 'button';
		button.setAttribute('role', 'menuitem');
		button.textContent = item.label;
		entries.push({button: button, item: item});
		if (item.confirm) {
			// Enter の押しっぱなしは、キーの繰り返しのたびにクリックを起こす。確かめの要る項目では繰り返しを捨て、
			// 2 回目はキーを離して押し直したときだけにする (経過時間だけでは、500 ms を過ぎた押しっぱなしが通る。internal #59)
			button.addEventListener('keydown', event => {
				if (event.repeat && (event.key === 'Enter' || event.key === ' ')) event.preventDefault();
			});
			// メニューのほかの項目へ移ったら、確かめは最初から (メニューの外へ出たら、メニューごと閉じる。
			// Safari はボタンを押してもフォーカスを移さず、行き先の無い blur になるので、それでは戻さない)
			button.addEventListener('blur', event => {
				if (!button.dataset.armed || !event.relatedTarget || !menu.contains(event.relatedTarget)) return;
				delete button.dataset.armed;
				button.textContent = item.label;
				live.textContent = '';
			});
		}
		button.addEventListener('click', event => {
			if (item.disabled && item.disabled()) {
				button.setAttribute('aria-disabled', 'true');
				return;
			}
			// 確かめの要る項目は、1 回目は文を変えるだけ (ブラウザの確認の窓は使わない)
			// ダブルクリックの 2 回目 (detail が 2)・素早い 2 回では始めない。2 回目は間を空けて押す (押しっぱなしは上の keydown が捨てる)
			if (item.confirm && (!button.dataset.armed || event.detail > 1 || Date.now() - Number(button.dataset.armed) < 500)) {
				if (!button.dataset.armed) button.dataset.armed = String(Date.now());
				button.textContent = item.confirm;
				live.textContent = item.confirm;
				// 文が長くなって画面の右へはみ出したら、内側へ寄せ直す。右端に寄っていると幅が縮んで測れるので、
				// 左へ置いてから本来の幅を測る (描く前なので、動いて見えない)
				const left = parseFloat(menu.style.left) || 0;
				menu.style.left = '4px';
				const width = menu.getBoundingClientRect().width;
				menu.style.left = Math.max(4, Math.min(left, window.innerWidth - width - 4)) + 'px';
				return;
			}
			// 開いている間に本棚が読み直され、本が消えた・名前が変わった (id が変わる) ときは何もしない
			const current = libraryBookById(book.id);
			closeBookMenu(!item.keepFocus && !!current);
			if (!current) {
				showLibraryStatus('この本は本棚から無くなりました (一覧が読み直されました)');
				return;
			}
			item.run(current);
		});
		menu.appendChild(button);
	}
	bookMenu = {menu: menu, live: live, bookId: book.id, entries: entries};
	refreshBookMenu();
	menu.addEventListener('keydown', onBookMenuKeyDown);
	// メニューの上ではブラウザのメニューを出さない (Windows のメニューキーは keyup で contextmenu を起こし、
	// そのときフォーカスはこのメニューにある)
	menu.addEventListener('contextmenu', event => event.preventDefault());
	document.body.appendChild(menu);

	// キーボードで開いたときは座標が 0 か、カードの外になることがある。そのときはカードの左上に出す
	// (mac の control+クリックは button が 0 なので、ボタンでは見分けない)
	let x = event ? event.clientX : 0;
	let y = event ? event.clientY : 0;
	if (slot) {
		const rect = slot.getBoundingClientRect();
		if (x < rect.left || x > rect.right || y < rect.top || y > rect.bottom) {
			x = rect.left + 8;
			y = rect.top + 8;
		}
	}
	// 画面の端ではみ出さないよう、内側へ寄せる
	const size = menu.getBoundingClientRect();
	x = Math.max(4, Math.min(x, window.innerWidth - size.width - 4));
	y = Math.max(4, Math.min(y, window.innerHeight - size.height - 4));
	menu.style.left = x + 'px';
	menu.style.top = y + 'px';

	const first = menu.querySelector('[role="menuitem"]:not([aria-disabled="true"])');
	if (first) first.focus();
	// 開いた右クリックそのものの mousedown で閉じないよう、次の周から外の操作を見る
	setTimeout(() => {
		if (!bookMenu || bookMenu.menu !== menu) return;
		document.addEventListener('pointerdown', onBookMenuOutside, true);
		window.addEventListener('blur', onBookMenuDismiss);
		window.addEventListener('resize', onBookMenuDismiss);
		el.libraryGrid.addEventListener('scroll', onBookMenuDismiss);
	}, 0);
}

/**
 * 押せない項目の印を、いまの状態に合わせ直す。開いている間に、その本の仕事・新着の確かめが始まる・終わる
 * (印が古いままだと、押せるのに淡いまま・押せないと読み上げられる)
 */
function refreshBookMenu()
{
	if (!bookMenu) return;
	for (const {button, item} of bookMenu.entries) {
		if (item.disabled && item.disabled()) button.setAttribute('aria-disabled', 'true');
		else button.removeAttribute('aria-disabled');
	}
}

/**
 * @param {boolean} returnFocus カードへフォーカスを戻すか (Esc で閉じたとき)
 */
function closeBookMenu(returnFocus)
{
	if (!bookMenu) return;
	const {menu, live, bookId} = bookMenu;
	bookMenu = null;
	live.remove();
	document.removeEventListener('pointerdown', onBookMenuOutside, true);
	window.removeEventListener('blur', onBookMenuDismiss);
	window.removeEventListener('resize', onBookMenuDismiss);
	el.libraryGrid.removeEventListener('scroll', onBookMenuDismiss);
	menu.remove();
	if (returnFocus) {
		const slot = librarySlotOf(bookId);
		const card = slot && slot.querySelector('.book-card');
		if (card) card.focus({preventScroll: true});
	}
}

/**
 * 描き直しでその本のカードが一覧から外れたら (絞り込み・並べ替え・裏の仕事の後の読み直し)、メニューも閉じる。
 * 残すと、カードの無い本のメニューが浮いたままになり、「名前を変える…」が黙って何もしない
 */
function closeBookMenuIfCardGone()
{
	if (bookMenu && !librarySlotOf(bookMenu.bookId)) closeBookMenu(false);
}

function onBookMenuOutside(event)
{
	if (bookMenu && !bookMenu.menu.contains(event.target)) closeBookMenu(false);
}

function onBookMenuDismiss()
{
	closeBookMenu(false);
}

function onBookMenuKeyDown(event)
{
	// 本棚のキー操作 (Esc で本棚を閉じる・L) に渡さない
	event.stopPropagation();
	const items = [...event.currentTarget.querySelectorAll('[role="menuitem"]')];
	const index = items.indexOf(document.activeElement);
	let next = -1;
	if (event.key === 'Escape') {
		event.preventDefault();
		closeBookMenu(true);
		return;
	} else if (event.key === 'Tab') {
		event.preventDefault();
		closeBookMenu(true);
		return;
	} else if (event.key === 'ArrowDown') {
		next = (index + 1) % items.length;
	} else if (event.key === 'ArrowUp') {
		next = (index - 1 + items.length) % items.length;
	} else if (event.key === 'Home') {
		next = 0;
	} else if (event.key === 'End') {
		next = items.length - 1;
	} else {
		return;
	}
	event.preventDefault();
	if (items[next]) items[next].focus();
}

/*
 * 新着を確かめる (internal #11)
 * Web から取った本の目次だけを読み、新しい話と改稿された話を数えてカードに出す。話は取らない (取るのは ⟳)。
 * 1 冊ずつ順に頼む (アプリも 1 冊ずつ、間隔を空けて確かめる)
 */

/** アプリが確かめられるか (Web 本棚と同じく、アプリから開いた本棚だけ) */
let libraryCanCheck = false;
/** 確かめている最中か */
let libraryChecking = false;
/** メニューから 1 冊だけ確かめている本の数。その間は見出しの「新着を確かめる」を押せない (数えた結果から漏れるので) */
let librarySingleChecks = 0;

function updateCheckAllButton()
{
	const books = (state.library && state.library.books) || [];
	el.libraryCheckAll.hidden = !libraryCanCheck || !books.some(b => b.source);
	el.libraryCheckAll.disabled = libraryChecking || librarySingleChecks > 0;
	// 「この本の新着を確かめる」は、全体を確かめている間は押せない
	refreshBookMenu();
}

/** メニューから 1 冊だけ確かめる */
async function checkSingleBook(book)
{
	librarySingleChecks++;
	updateCheckAllButton();
	try {
		await checkOneBook(book);
	} finally {
		librarySingleChecks--;
		updateCheckAllButton();
	}
}

async function checkAllBooks()
{
	if (libraryChecking || librarySingleChecks > 0) return;
	const books = ((state.library && state.library.books) || []).filter(b => b.source);
	if (!books.length) return;
	libraryChecking = true;
	updateCheckAllButton();
	let found = 0;
	let failed = 0;
	try {
		for (let i = 0; i < books.length; i++) {
			const book = books[i];
			// 確かめている間に本棚から消した本は飛ばす (確かめられなかった本に数えない)
			if (!libraryBookById(book.id)) continue;
			el.libraryCheckAll.textContent = '確かめています… (' + (i + 1) + '/' + books.length + ')';
			const result = await checkOneBook(book);
			if (result === 'found') found++;
			else if (result === 'failed') failed++;
		}
		// 確かめられなかった本があるときは「新着は無い」と言い切らない (PR の codex)
		let summary = found ? '新着のある本: ' + found + ' 冊 (⟳ で取れます)' : (failed ? '' : '新着のある本はありません');
		if (failed) summary += (summary ? '・' : '') + '確かめられなかった本: ' + failed + ' 冊';
		showLibraryStatus(summary);
	} finally {
		libraryChecking = false;
		el.libraryCheckAll.textContent = '新着を確かめる';
		updateCheckAllButton();
	}
}

/**
 * 1 冊を確かめて、カードに出す
 * @return {Promise<string>} found (新着あり)・none (新着なし)・failed・skipped (更新中なので飛ばした)
 */
async function checkOneBook(book)
{
	// ⟳ で更新している本は飛ばす (その結果を上書きしない。PR のゲート2)
	const current = libraryUpdates.get(book.id);
	if (current && (current.state === 'queued' || current.state === 'running')) return 'skipped';
	setLibraryUpdate(book.id, {state: 'queued', message: ''});
	try {
		const job = await checkBook(book);
		// 確かめるのに失敗したのを「更新できませんでした」と言わない
		const state = job.state === 'failed' ? 'checkFailed' : job.state;
		setLibraryUpdate(book.id, {state: state, message: job.message || '',
			newEpisodes: job.newEpisodes, revisedEpisodes: job.revisedEpisodes});
		if (job.state !== 'checked') return 'failed';
		return (job.newEpisodes > 0 || job.revisedEpisodes > 0) ? 'found' : 'none';
	} catch (err) {
		setLibraryUpdate(book.id, {state: 'checkFailed', message: err.message});
		return 'failed';
	}
}

/** 1 冊を確かめる仕事を頼み、終わるまで待つ。最後の状態を返す */
async function checkBook(book)
{
	const {response, json} = await postText('api/book/' + encodeURIComponent(book.id) + '/check', '');
	if (!response.ok || !json || !json.job) throw new Error((json && json.error) ? json.error : 'HTTP ' + response.status);
	let misses = 0;
	for (;;) {
		await new Promise(resolve => setTimeout(resolve, 700));
		try {
			const job = await getJson('api/jobs/' + encodeURIComponent(json.job));
			misses = 0;
			if (job.state !== 'queued' && job.state !== 'running') return job;
		} catch (e) {
			if (++misses >= LIBRARY_UPDATE_MISSES) throw e;
		}
	}
}

