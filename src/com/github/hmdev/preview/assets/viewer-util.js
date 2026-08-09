'use strict';

// ------------------------------------------------------------------
// ユーティリティ
// ------------------------------------------------------------------

function kvTable(title, rows)
{
	const table = document.createElement('table');
	table.className = 'kv';
	if (title) {
		const caption = document.createElement('caption');
		caption.className = 'section-title';
		caption.style.captionSide = 'top';
		caption.textContent = title;
		table.appendChild(caption);
	}
	for (const [key, value] of rows) {
		const tr = document.createElement('tr');
		const th = document.createElement('th');
		th.textContent = key;
		const td = document.createElement('td');
		td.textContent = (value === null || value === undefined || value === '') ? '—' : value;
		tr.append(th, td);
		table.appendChild(tr);
	}
	return table;
}

function formatBytes(bytes)
{
	const value = Number(bytes) || 0;
	if (value < 1024) return value + ' B';
	if (value < 1024 * 1024) return (value / 1024).toFixed(1) + ' KB';
	return (value / 1024 / 1024).toFixed(2) + ' MB';
}

/**
 * エポックミリ秒を「YYYY-MM-DD HH:mm」(閲覧環境のローカル時刻) にする。
 * 表示揺れを避けるため toLocaleString は使わない。
 */
function formatDateTime(millis)
{
	const value = Number(millis);
	if (!value) return '—';
	const date = new Date(value);
	const pad = n => String(n).padStart(2, '0');
	return date.getFullYear() + '-' + pad(date.getMonth() + 1) + '-' + pad(date.getDate())
		+ ' ' + pad(date.getHours()) + ':' + pad(date.getMinutes());
}

function fileNameOf(path)
{
	const slash = String(path).lastIndexOf('/');
	return (slash < 0) ? path : path.substring(slash + 1);
}

/** パスの各セグメントを URL エンコードする (スラッシュは残す) */
function encodePath(path)
{
	return String(path).split('/').map(encodeURIComponent).join('/');
}

async function getJson(path)
{
	const response = await fetch(path, {cache: 'no-store'});
	if (!response.ok) throw new Error('HTTP ' + response.status);
	return response.json();
}

async function getText(path)
{
	const response = await fetch(path, {cache: 'no-store'});
	if (!response.ok) throw new Error('HTTP ' + response.status);
	return response.text();
}

function showFatal(error)
{
	el.bookTitle.textContent = 'エラー';
	el.bookCreator.textContent = String(error && error.message ? error.message : error);
}
