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
import com.github.hmdev.util.I18n;

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
		setTitle(I18n.t("ui.narou.title"));
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
			BorderFactory.createEtchedBorder(), I18n.t("ui.narou.border.textConversion"),
			TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

		chkHalfIndentBracket = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.halfIndentBracket"),
			I18n.t("ui.narou.tooltip.halfIndentBracket"));
		chkConvertNumToKanji = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.convertNumToKanji"),
			I18n.t("ui.narou.tooltip.convertNumToKanji"));

		gbc.gridwidth = 1; gbc.insets = new Insets(2, 28, 2, 4);
		chkKanjiNumWithUnits = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.kanjiNumWithUnits"),
			I18n.t("ui.narou.tooltip.kanjiNumWithUnits"));
		gbc.gridx = 1;
		JPanel unitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		unitPanel.add(new JLabel(I18n.t("ui.narou.label.lowerDigitZero")));
		spnKanjiNumUnitsDigit = new JSpinner(new SpinnerNumberModel(2, 1, 10, 1));
		spnKanjiNumUnitsDigit.setPreferredSize(new Dimension(50, 22));
		unitPanel.add(spnKanjiNumUnitsDigit);
		panel.add(unitPanel, gbc);
		gbc.gridx = 0; gbc.gridy++; gbc.gridwidth = 2;
		gbc.insets = new Insets(2, 4, 2, 4);

		chkConvertSymbolsToZenkaku = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.convertSymbolsToZenkaku"),
			I18n.t("ui.narou.tooltip.convertSymbolsToZenkaku"));
		chkTransformFraction = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.transformFraction"),
			I18n.t("ui.narou.tooltip.transformFraction"));
		chkTransformDate = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.transformDate"),
			I18n.t("ui.narou.tooltip.transformDate"));

		gbc.gridwidth = 1; gbc.insets = new Insets(2, 28, 2, 4);
		gbc.gridy++;
		JPanel datePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		datePanel.add(new JLabel(I18n.t("ui.narou.label.dateFormat")));
		txtDateFormat = new JTextField("%Y年%m月%d日", 16);
		txtDateFormat.setToolTipText("%Y=年, %m=月, %d=日");
		datePanel.add(txtDateFormat);
		gbc.gridwidth = 2;
		panel.add(datePanel, gbc);
		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);

		chkConvertHorizontalEllipsis = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.convertEllipsis"),
			I18n.t("ui.narou.tooltip.convertEllipsis"));
		chkDakutenFont = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.dakutenFont"),
			I18n.t("ui.narou.tooltip.dakutenFont"));
		chkProlongedSoundMarkToDash = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.prolongedToDash"),
			I18n.t("ui.narou.tooltip.prolongedToDash"));
		chkNarouTag = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.narouTag"),
			I18n.t("ui.narou.tooltip.narouTag"));
		chkAutoJoinInBrackets = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.autoJoinInBrackets"),
			I18n.t("ui.narou.tooltip.autoJoinInBrackets"));
		chkAutoJoinLine = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.autoJoinLine"),
			I18n.t("ui.narou.tooltip.autoJoinLine"));

		return panel;
	}

	private JPanel createFinalizePanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(), I18n.t("ui.narou.border.finalize"),
			TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

		chkAuthorComments = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.authorComments"),
			I18n.t("ui.narou.tooltip.authorComments"));

		gbc.gridwidth = 1; gbc.insets = new Insets(2, 28, 2, 4);
		gbc.gridy++;
		JPanel stylePanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 4, 0));
		stylePanel.add(new JLabel(I18n.t("ui.narou.label.style")));
		cmbAuthorCommentStyle = new JComboBox<>(new String[]{"css", "simple", "plain"});
		cmbAuthorCommentStyle.setToolTipText("css=CSSで装飾, simple=字下げ+小さい文字, plain=区切り線のみ");
		stylePanel.add(cmbAuthorCommentStyle);
		gbc.gridwidth = 2;
		panel.add(stylePanel, gbc);
		gbc.gridy++;
		gbc.insets = new Insets(2, 4, 2, 4);

		chkAutoIndent = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.autoIndent"),
			I18n.t("ui.narou.tooltip.autoIndent"));
		chkEnchantMidashi = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.enchantMidashi"),
			I18n.t("ui.narou.tooltip.enchantMidashi"));
		chkInspectBrackets = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.inspectBrackets"),
			I18n.t("ui.narou.tooltip.inspectBrackets"));

		return panel;
	}

	private JPanel createDisplayPanel() {
		JPanel panel = new JPanel();
		panel.setLayout(new GridBagLayout());
		panel.setBorder(BorderFactory.createTitledBorder(
			BorderFactory.createEtchedBorder(), I18n.t("ui.narou.border.display"),
			TitledBorder.LEFT, TitledBorder.TOP));

		GridBagConstraints gbc = new GridBagConstraints();
		gbc.anchor = GridBagConstraints.WEST;
		gbc.insets = new Insets(2, 4, 2, 4);
		gbc.gridx = 0; gbc.gridy = 0; gbc.gridwidth = 2;

		chkIncludeStory = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.includeStory"),
			I18n.t("ui.narou.tooltip.includeStory"));
		chkIncludeTocUrl = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.includeTocUrl"),
			I18n.t("ui.narou.tooltip.includeTocUrl"));
		chkShowPostDate = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.showPostDate"),
			I18n.t("ui.narou.tooltip.showPostDate"));
		chkShowPublishDate = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.showPublishDate"),
			I18n.t("ui.narou.tooltip.showPublishDate"));
		chkDisplayEndOfBook = addCheckBox(panel, gbc, I18n.t("ui.narou.chk.displayEndOfBook"),
			I18n.t("ui.narou.tooltip.displayEndOfBook"));

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

		JButton btnDefault = new JButton(I18n.t("ui.narou.btn.default"));
		btnDefault.setToolTipText(I18n.t("ui.narou.tooltip.default"));
		btnDefault.addActionListener(e -> {
			int result = JOptionPane.showConfirmDialog(this,
				I18n.t("ui.narou.confirm.reset"), I18n.t("ui.narou.confirm.resetTitle"),
				JOptionPane.YES_NO_OPTION);
			if (result == JOptionPane.YES_OPTION) {
				settings = new NarouFormatSettings();
				loadSettingsToUI();
			}
		});

		JButton btnCancel = new JButton(I18n.t("ui.narou.btn.cancel"));
		btnCancel.addActionListener(e -> dispose());

		JButton btnSave = new JButton(I18n.t("ui.narou.btn.save"));
		btnSave.addActionListener(e -> {
			saveUIToSettings();
			try {
				settings.save(settingsFile);
				saved = true;
				dispose();
			} catch (Exception ex) {
				JOptionPane.showMessageDialog(this,
					I18n.t("ui.narou.error.save") + ex.getMessage(),
					I18n.t("ui.error"), JOptionPane.ERROR_MESSAGE);
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
