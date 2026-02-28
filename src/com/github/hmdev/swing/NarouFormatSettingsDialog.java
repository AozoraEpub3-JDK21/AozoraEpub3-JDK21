package com.github.hmdev.swing;

import java.awt.BorderLayout;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTextField;
import javax.swing.SpinnerNumberModel;
import javax.swing.border.TitledBorder;

import com.github.hmdev.web.NarouFormatSettings;

/**
 * narou.rb互換フォーマット設定ダイアログ。
 * setting_narourb.ini の全設定項目をGUIで編集可能にする。
 */
public class NarouFormatSettingsDialog extends JDialog {

	private static final long serialVersionUID = 1L;

	private NarouFormatSettings settings;
	private File settingsFile;
	private boolean saved = false;

	// === テキスト変換設定 ===
	private JCheckBox chkHalfIndentBracket;
	private JCheckBox chkConvertNumToKanji;
	private JCheckBox chkKanjiNumWithUnits;
	private JSpinner spnKanjiNumUnitsDigit;
	private JCheckBox chkConvertSymbolsToZenkaku;
	private JCheckBox chkAutoJoinInBrackets;
	private JCheckBox chkAutoJoinLine;
	private JCheckBox chkTransformFraction;
	private JCheckBox chkTransformDate;
	private JTextField txtDateFormat;
	private JCheckBox chkConvertHorizontalEllipsis;
	private JCheckBox chkDakutenFont;
	private JCheckBox chkProlongedSoundMarkToDash;
	private JCheckBox chkNarouTag;

	// === ファイナライズ処理設定 ===
	private JCheckBox chkAuthorComments;
	private JComboBox<String> cmbAuthorCommentStyle;
	private JCheckBox chkAutoIndent;
	private JCheckBox chkEnchantMidashi;
	private JCheckBox chkInspectBrackets;

	// === 表示・メタデータ設定 ===
	private JCheckBox chkIncludeStory;
	private JCheckBox chkIncludeTocUrl;
	private JCheckBox chkShowPostDate;
	private JCheckBox chkShowPublishDate;
	private JCheckBox chkDisplayEndOfBook;

	public NarouFormatSettingsDialog(Image iconImage, NarouFormatSettings settings, File settingsFile) {
		this.settings = settings;
		this.settingsFile = settingsFile;

		setIconImage(iconImage);
		setModalityType(Dialog.ModalityType.APPLICATION_MODAL);
		setTitle("narou.rb互換 フォーマット設定");
		setSize(new Dimension(520, 620));
		setResizable(true);
		setLocationRelativeTo(null);
		setLayout(new BorderLayout());

		// メインパネル（スクロール可能）
		JPanel mainPanel = new JPanel();
		mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
		mainPanel.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));

		// --- テキスト変換 セクション ---
		mainPanel.add(createTextConversionPanel());
		mainPanel.add(Box.createVerticalStrut(8));

		// --- ファイナライズ処理 セクション ---
		mainPanel.add(createFinalizePanel());
		mainPanel.add(Box.createVerticalStrut(8));

		// --- 表示・メタデータ セクション ---
		mainPanel.add(createDisplayPanel());
		mainPanel.add(Box.createVerticalGlue());

		JScrollPane scrollPane = new JScrollPane(mainPanel);
		scrollPane.getVerticalScrollBar().setUnitIncrement(16);
		add(scrollPane, BorderLayout.CENTER);

		// --- ボタンパネル ---
		add(createButtonPanel(), BorderLayout.SOUTH);

		// 設定値をUIに反映
		loadSettingsToUI();
	}

	private JPanel createTextConversionPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(), "テキスト変換",
			TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

		chkHalfIndentBracket = addCheckBox(panel, gbc, "行頭かぎ括弧に二分アキを挿入",
			"縦書き時の見た目を改善します（「『〔 等の前に二分アキ）");
		chkConvertNumToKanji = addCheckBox(panel, gbc, "数字を漢数字に変換",
			"アラビア数字を漢数字に変換します（123 → 一二三）");

		gbc.gridwidth = 1; gbc.insets = new Insets(2, 28, 2, 4);
		chkKanjiNumWithUnits = addCheckBox(panel, gbc, "漢数字に単位を付与",
			"千・万 等の単位を付与します（1000 → 千）");
		gbc.gridx = 1;
		JPanel unitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		unitPanel.add(new JLabel("下位桁ゼロ数:"));
		spnKanjiNumUnitsDigit = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
		spnKanjiNumUnitsDigit.setPreferredSize(new Dimension(50, 22));
		unitPanel.add(spnKanjiNumUnitsDigit);
		panel.add(unitPanel, gbc);
		gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
		gbc.insets = new Insets(2, 4, 2, 4);

		chkConvertSymbolsToZenkaku = addCheckBox(panel, gbc, "半角記号を全角に変換",
			"- → －、< → 〈 等の記号を全角化します");
		chkTransformFraction = addCheckBox(panel, gbc, "分数を変換",
			"1/2 → 2分の1 のように変換します");
		chkTransformDate = addCheckBox(panel, gbc, "日付を変換",
			"2024/1/1 → 2024年1月1日 のように変換します");

		gbc.gridwidth = 1; gbc.insets = new Insets(2, 28, 2, 4);
		gbc.gridy++;
		JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		datePanel.add(new JLabel("日付フォーマット:"));
		txtDateFormat = new JTextField("%Y年%m月%d日", 16);
		txtDateFormat.setToolTipText("%Y=年, %m=月, %d=日");
		datePanel.add(txtDateFormat);
		gbc.gridwidth = 2;
		panel.add(datePanel, gbc);
		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);

		chkConvertHorizontalEllipsis = addCheckBox(panel, gbc, "中黒を三点リーダーに変換",
			"・・・ → … のように変換します");
		chkDakutenFont = addCheckBox(panel, gbc, "濁点フォント処理",
			"か゛ → ［＃濁点］か［＃濁点終わり］");
		chkProlongedSoundMarkToDash = addCheckBox(panel, gbc, "長音記号をダッシュに変換",
			"ーー → ―― のように変換します（2個以上連続時）");
		chkNarouTag = addCheckBox(panel, gbc, "なろう独自タグを処理",
			"[newpage]→改ページ、[chapter:...]→見出し");
		chkAutoJoinInBrackets = addCheckBox(panel, gbc, "かぎ括弧内の改行を連結",
			"「の中の改行を全角スペースに置換します");
		chkAutoJoinLine = addCheckBox(panel, gbc, "行末読点で次行と連結",
			"、で終わる行を次の行と連結します");

		return panel;
	}

	private JPanel createFinalizePanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(), "ファイナライズ処理",
			TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

		chkAuthorComments = addCheckBox(panel, gbc, "前書き・後書きを自動検出",
			"*44個→前書き、*48個→後書きとして検出します");

		gbc.gridwidth = 1; gbc.insets = new Insets(2, 28, 2, 4);
		gbc.gridy++;
		JPanel stylePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		stylePanel.add(new JLabel("スタイル:"));
		cmbAuthorCommentStyle = new JComboBox<>(new String[]{"css", "simple", "plain"});
		cmbAuthorCommentStyle.setToolTipText("css=CSSで装飾, simple=字下げ+小さい文字, plain=区切り線のみ");
		stylePanel.add(cmbAuthorCommentStyle);
		gbc.gridwidth = 2;
		panel.add(stylePanel, gbc);
		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);

		chkAutoIndent = addCheckBox(panel, gbc, "自動行頭字下げ",
			"段落開始を検出し全角スペースで字下げします");
		chkEnchantMidashi = addCheckBox(panel, gbc, "改ページ直後を見出し化",
			"［＃改ページ］直後の行を中見出しに変換します");
		chkInspectBrackets = addCheckBox(panel, gbc, "かぎ括弧の開閉チェック",
			"括弧の対応が正しいかチェックし、警告を出力します");

		return panel;
	}

	private JPanel createDisplayPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(), "表示・メタデータ",
			TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

		chkIncludeStory = addCheckBox(panel, gbc, "あらすじを表紙に含める",
			"なろうAPIから取得したあらすじを表紙ページに表示します");
		chkIncludeTocUrl = addCheckBox(panel, gbc, "掲載URLを表紙に含める",
			"小説の掲載URLを表紙ページに表示します");
		chkShowPostDate = addCheckBox(panel, gbc, "更新日時を各話に表示",
			"各話の最終更新日時を表示します");
		chkShowPublishDate = addCheckBox(panel, gbc, "初回公開日を各話に表示",
			"各話の初回公開日を表示します（改稿済の話のみ更新日時と別に表示）");
		chkDisplayEndOfBook = addCheckBox(panel, gbc, "本の終了マーカーを表示",
			"最終ページに「この本はここで終わりです」を表示します");

		return panel;
	}

	private JCheckBox addCheckBox(JPanel panel, GridBagConstraints gbc, String label, String tooltip) {
		JCheckBox cb = new JCheckBox(label);
		cb.setToolTipText(tooltip);
		panel.add(cb, gbc);
		gbc.gridy++;
		return cb;
	}

	private JPanel createButtonPanel() {
		JPanel panel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));

		JButton btnDefault = new JButton("デフォルトに戻す");
		btnDefault.setToolTipText("全ての設定をデフォルト値に戻します");
		btnDefault.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(this,
				"全ての設定をデフォルト値に戻しますか？", "確認",
				JOptionPane.YES_NO_OPTION);
			if (result == JOptionPane.YES_OPTION) {
				settings = new NarouFormatSettings();
				loadSettingsToUI();
			}
		});

		JButton btnCancel = new JButton("キャンセル");
		btnCancel.addActionListener(e -> dispose());

		JButton btnSave = new JButton("保存");
		btnSave.addActionListener(e -> {
			saveUIToSettings();
			try {
				settings.save(settingsFile);
				saved = true;
				dispose();
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
					"設定の保存に失敗しました: " + ex.getMessage(),
					"エラー", JOptionPane.ERROR_MESSAGE);
			}
		});

		panel.add(btnDefault);
		panel.add(btnCancel);
		panel.add(btnSave);
		return panel;
	}

	/** 設定値をUIコンポーネントに反映 */
	private void loadSettingsToUI() {
		chkHalfIndentBracket.setSelected(settings.isEnableHalfIndentBracket());
		chkConvertNumToKanji.setSelected(settings.isEnableConvertNumToKanji());
		chkKanjiNumWithUnits.setSelected(settings.isEnableKanjiNumWithUnits());
		spnKanjiNumUnitsDigit.setValue(settings.getKanjiNumWithUnitsLowerDigitZero());
		chkConvertSymbolsToZenkaku.setSelected(settings.isEnableConvertSymbolsToZenkaku());
		chkAutoJoinInBrackets.setSelected(settings.isEnableAutoJoinInBrackets());
		chkAutoJoinLine.setSelected(settings.isEnableAutoJoinLine());
		chkTransformFraction.setSelected(settings.isEnableTransformFraction());
		chkTransformDate.setSelected(settings.isEnableTransformDate());
		txtDateFormat.setText(settings.getDateFormat());
		chkConvertHorizontalEllipsis.setSelected(settings.isEnableConvertHorizontalEllipsis());
		chkDakutenFont.setSelected(settings.isEnableDakutenFont());
		chkProlongedSoundMarkToDash.setSelected(settings.isEnableProlongedSoundMarkToDash());
		chkNarouTag.setSelected(settings.isEnableNarouTag());
		chkAuthorComments.setSelected(settings.isEnableAuthorComments());
		cmbAuthorCommentStyle.setSelectedItem(settings.getAuthorCommentStyle());
		chkAutoIndent.setSelected(settings.isEnableAutoIndent());
		chkEnchantMidashi.setSelected(settings.isEnableEnchantMidashi());
		chkInspectBrackets.setSelected(settings.isEnableInspectInvalidOpenCloseBrackets());
		chkIncludeStory.setSelected(settings.isIncludeStory());
		chkIncludeTocUrl.setSelected(settings.isIncludeTocUrl());
		chkShowPostDate.setSelected(settings.isShowPostDate());
		chkShowPublishDate.setSelected(settings.isShowPublishDate());
		chkDisplayEndOfBook.setSelected(settings.isEnableDisplayEndOfBook());
	}

	/** UIコンポーネントの値を設定に反映 */
	private void saveUIToSettings() {
		settings.setEnableHalfIndentBracket(chkHalfIndentBracket.isSelected());
		settings.setEnableConvertNumToKanji(chkConvertNumToKanji.isSelected());
		settings.setEnableKanjiNumWithUnits(chkKanjiNumWithUnits.isSelected());
		settings.setKanjiNumWithUnitsLowerDigitZero((Integer) spnKanjiNumUnitsDigit.getValue());
		settings.setEnableConvertSymbolsToZenkaku(chkConvertSymbolsToZenkaku.isSelected());
		settings.setEnableAutoJoinInBrackets(chkAutoJoinInBrackets.isSelected());
		settings.setEnableAutoJoinLine(chkAutoJoinLine.isSelected());
		settings.setEnableTransformFraction(chkTransformFraction.isSelected());
		settings.setEnableTransformDate(chkTransformDate.isSelected());
		settings.setDateFormat(txtDateFormat.getText());
		settings.setEnableConvertHorizontalEllipsis(chkConvertHorizontalEllipsis.isSelected());
		settings.setEnableDakutenFont(chkDakutenFont.isSelected());
		settings.setEnableProlongedSoundMarkToDash(chkProlongedSoundMarkToDash.isSelected());
		settings.setEnableNarouTag(chkNarouTag.isSelected());
		settings.setEnableAuthorComments(chkAuthorComments.isSelected());
		settings.setAuthorCommentStyle((String) cmbAuthorCommentStyle.getSelectedItem());
		settings.setEnableAutoIndent(chkAutoIndent.isSelected());
		settings.setEnableEnchantMidashi(chkEnchantMidashi.isSelected());
		settings.setEnableInspectInvalidOpenCloseBrackets(chkInspectBrackets.isSelected());
		settings.setIncludeStory(chkIncludeStory.isSelected());
		settings.setIncludeTocUrl(chkIncludeTocUrl.isSelected());
		settings.setShowPostDate(chkShowPostDate.isSelected());
		settings.setShowPublishDate(chkShowPublishDate.isSelected());
		settings.setEnableDisplayEndOfBook(chkDisplayEndOfBook.isSelected());
	}

	/** ダイアログが保存で閉じられたかどうか */
	public boolean isSaved() {
		return saved;
	}

	/** 編集後の設定を取得 */
	public NarouFormatSettings getSettings() {
		return settings;
	}
}
