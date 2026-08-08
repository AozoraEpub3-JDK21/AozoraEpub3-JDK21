'use strict';

// ------------------------------------------------------------------
// セクション / 目次
// ------------------------------------------------------------------

function buildSectionSelect()
{
	el.sectionSelect.textContent = '';
	const labels = sectionLabels();
	state.book.spine.forEach((item, index) => {
		const option = document.createElement('option');
		option.value = String(index);
		option.textContent = (index + 1) + '. ' + (labels[index] || fileNameOf(item.path));
		el.sectionSelect.appendChild(option);
	});
}

/** 目次のラベルを spine インデックスへ割り当てる (最初に見つかったものを採用) */
function sectionLabels()
{
	const labels = {};
	walkToc(state.book.toc, entry => {
		if (entry.spineIndex >= 0 && labels[entry.spineIndex] === undefined) {
			labels[entry.spineIndex] = entry.label;
		}
	});
	return labels;
}

function walkToc(entries, callback)
{
	for (const entry of entries || []) {
		callback(entry);
		walkToc(entry.children, callback);
	}
}

function buildToc()
{
	el.tocTree.textContent = '';
	const root = renderTocList(state.book.toc);
	if (root) el.tocTree.appendChild(root);
	highlightToc();
}

function renderTocList(entries)
{
	if (!entries || entries.length === 0) return null;
	const list = document.createElement('ul');
	for (const entry of entries) {
		const item = document.createElement('li');
		const anchor = document.createElement('a');
		anchor.textContent = entry.label;
		if (entry.spineIndex >= 0) {
			anchor.href = '#';
			anchor.dataset.spineIndex = String(entry.spineIndex);
			if (entry.fragment) anchor.dataset.fragment = entry.fragment;
			anchor.addEventListener('click', event => {
				event.preventDefault();
				gotoSection(entry.spineIndex, entry.fragment);
			});
		} else {
			anchor.className = 'disabled';
		}
		item.appendChild(anchor);
		const children = renderTocList(entry.children);
		if (children) item.appendChild(children);
		list.appendChild(item);
	}
	return list;
}

function highlightToc()
{
	for (const anchor of el.tocTree.querySelectorAll('a')) {
		anchor.classList.toggle('current', anchor.dataset.spineIndex === String(state.spineIndex));
	}
}

/**
 * セクションを表示する。
 * @param {number} index spine インデックス
 * @param {?string} fragment 遷移先の id (本プロジェクトの目次は #chapterId 付き)
 */
function gotoSection(index, fragment, atEnd)
{
	if (!state.book || state.book.spine.length === 0) return;
	const clamped = Math.max(0, Math.min(index, state.book.spine.length - 1));
	const path = state.book.spine[clamped].path;
	const url = 'book/' + encodeURIComponent(state.bookId) + '/' + encodePath(path);

	state.spineIndex = clamped;
	state.pendingFragment = fragment || null;
	// 読み戻しで前のセクションへ移るときは、その末尾に着地させないと後ろ向きに読み進められない
	state.pendingAtEnd = !!atEnd;
	el.sectionSelect.value = String(clamped);
	highlightToc();

	const currentSrc = el.frame.getAttribute('data-path');
	if (currentSrc === path) {
		// 同じセクション内の移動はリロードせずスクロールだけ行う
		applyPendingFragment();
	} else {
		el.frame.setAttribute('data-path', path);
		el.frame.src = url;
	}
	updateHint();
}

function stepSection(delta, atEnd)
{
	gotoSection(state.spineIndex + delta, null, atEnd);
}

