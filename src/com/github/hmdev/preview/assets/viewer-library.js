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

/** いま描き終えているカードの枚数。「さらに表示」で増える */
let libraryShown = 0;

function bindLibraryEvents()
{
	el.libraryToggle.addEventListener('click', () => toggleLibrary());
	el.libraryClose.addEventListener('click', () => closeLibrary());

	el.libraryReload.addEventListener('click', () => {
		loadLibrary().catch(err => showLibraryStatus('本棚を読み込めませんでした: ' + err.message));
	});

	el.libraryFilter.addEventListener('input', () => renderLibrary());
	el.librarySort.addEventListener('change', () => renderLibrary());

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
	el.libraryToggle.hidden = !state.libraryFolder;
	el.libraryFolderName.textContent = state.libraryFolder || '';
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
	if (!state.libraryFolder) return;
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

async function loadLibrary()
{
	showLibraryStatus('読み込み中…');
	state.library = await getJson('api/library');
	state.libraryFolder = state.library.folderName || state.libraryFolder;
	updateLibraryAvailability();
	renderLibrary();
}

/** 絞り込みと並べ替えを適用した本の配列を返す */
function visibleLibraryBooks()
{
	const books = (state.library && state.library.books) ? state.library.books.slice() : [];
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

function libraryHaystack(book)
{
	return [book.title, book.creator, book.fileName, book.subFolder]
		.filter(Boolean).join('\n').toLowerCase();
}

function renderLibrary()
{
	libraryShown = 0;
	el.libraryGrid.textContent = '';
	appendLibraryCards();
}

/** 続きのカードを描き足す。全部描き切るまで「さらに表示」を出す */
function appendLibraryCards()
{
	const books = visibleLibraryBooks();
	const upto = Math.min(books.length, libraryShown + LIBRARY_PAGE_SIZE);
	const fragment = document.createDocumentFragment();
	for (let i = libraryShown; i < upto; i++) fragment.appendChild(libraryCard(books[i]));
	el.libraryGrid.appendChild(fragment);
	libraryShown = upto;

	const total = (state.library && state.library.books) ? state.library.books.length : 0;
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
	more.className = 'icon';
	more.textContent = 'さらに表示 (残り ' + remaining + ' 冊)';
	more.addEventListener('click', () => appendLibraryCards());
	el.libraryStatus.append(' ', more);
}

function libraryCard(book)
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
		// キャッシュ避けを付けない。no-cache + ETag で毎回再検証しており、
		// パラメータを足すと 304 が使えず毎回作り直しになる
		image.src = 'api/library/cover/' + encodeURIComponent(book.id);
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
 */
async function openLibraryBook(bookId)
{
	if (!bookId) return;
	if (bookId === state.bookId) {
		closeLibrary();
		return;
	}
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
