package com.github.hmdev.writer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.github.hmdev.converter.AozoraEpub3Converter;
import com.github.hmdev.info.FixedPageSize;
import com.github.hmdev.info.ImageInfo;
import com.github.hmdev.info.SectionInfo;
import com.github.hmdev.util.LogAppender;
import com.github.junrar.Archive;
import com.github.junrar.exception.RarException;
import com.github.junrar.rarfile.FileHeader;

/** ePub3用のファイル一式をZipで固めたファイルを生成.
 * 画像のみのZipの場合こちらで画像専用の処理を行う
 */
public class Epub3ImageWriter extends Epub3Writer
{
	private static final Logger logger = LoggerFactory.getLogger(Epub3ImageWriter.class);

	/** コピーのみのファイル */
	final static String[] TEMPLATE_FILE_NAMES_VERTICAL_IMAGE = new String[]{
		"META-INF/container.xml",
		OPS_PATH+CSS_PATH+"vertical_image.css"
	};
	final static String[] TEMPLATE_FILE_NAMES_HORIZONTAL_IMAGE = new String[]{
		"META-INF/container.xml",
		OPS_PATH+CSS_PATH+"horizontal_image.css"
	};
	final static String[] TEMPLATE_FILE_NAMES_KINDLE_IMAGE = new String[]{
		"META-INF/container.xml",
		OPS_PATH+CSS_PATH+"kindle_image.css"
	};
	final static String[] TEMPLATE_FILE_NAMES_SVG_IMAGE = new String[]{
		"META-INF/container.xml",
		OPS_PATH+CSS_PATH+"svg_image.css"
	};
	String[] getTemplateFiles()
	{
		//SVG のページは svg_image.css を指す（svg_image.vm・cover.vm）。Kindle でも SVG ならそれを入れる。前は kindle_image.css だけ入り、ページが本に無い CSS を指した（internal #81）
		if (this.isSvgImage) return TEMPLATE_FILE_NAMES_SVG_IMAGE;
		if (this.isKindle) return TEMPLATE_FILE_NAMES_KINDLE_IMAGE;
		if (this.bookInfo != null && this.bookInfo.vertical) return TEMPLATE_FILE_NAMES_VERTICAL_IMAGE;
		return TEMPLATE_FILE_NAMES_HORIZONTAL_IMAGE;
	}
	
	/** 出力先ePubのZipストリーム */
	ZipArchiveOutputStream zos;
	
	/** コンストラクタ
	 * @param templatePath epubテンプレート格納パス文字列 最後は"/"
	 */
	public Epub3ImageWriter(String jarPath)
	{
		super(jarPath);
	}
	
	/** 本文を出力する
	 * setFileNamesで sortedFileNames が設定されている必要がある 
	 * @throws RarException */
	/**
	 * 書庫の項目が、画像として書き出す物か（writeArchiveImage が書く物＝id の付いた登録済みの画像）。開く前に見る。
	 * 項目の名前そのものが画像の拡張子であることも見る（01.jpg と同じ名前の 01.txt を correctExt が 01.jpg に読み替えて開くと、
	 * 壊れたテキストで 1 冊が失敗し、読めてもテキストを画像として書いてしまう。internal #36）
	 */
	private boolean isWrittenImage(String srcImageFileName) throws java.io.IOException
	{
		String lower = srcImageFileName.toLowerCase();
		if (!(lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".png") || lower.endsWith(".gif") || lower.endsWith(".bmp"))) return false;
		ImageInfo imageInfo = this.imageInfoReader.getImageInfo(this.imageInfoReader.correctExt(srcImageFileName));
		return imageInfo != null && imageInfo.getId() != null;
	}

	/** 書く段を経て本に入った画像か（読めずに外した画像は id を外してある） */
	private boolean isOnPage(String srcFilePath) throws IOException
	{
		ImageInfo written = this.imageInfoReader.getImageInfo(srcFilePath.trim());
		return written != null && written.getId() != null;
	}

	@Override
	void writeSections(AozoraEpub3Converter converter, BufferedReader src, BufferedWriter bw, File srcFile, String srcExt, ZipArchiveOutputStream zos) throws IOException, RarException
	{
		ArrayList<String> vecFileName = new ArrayList<>();
		//ファイル名取得してImageInfoのIDを設定
		int pageNum = 0;
		for (String srcFilePath : this.imageInfoReader.getImageFileNames()) {
			if (this.canceled) return;
			pageNum++;
			vecFileName.add(this.getImageFilePath(srcFilePath.trim(), pageNum));
		}
		
		//画像を出力して出力サイズを取得
		zos.setLevel(0);
		//サブパスの文字長
		int archivePathLength = 0;
		if (this.bookInfo.textEntryName != null) archivePathLength = this.bookInfo.textEntryName.indexOf('/')+1;
		
		if ("rar".equals(srcExt)) {
			Archive archive = new Archive(srcFile);
			try {
			for (FileHeader fileHeader : archive.getFileHeaders()) {
				if (!fileHeader.isDirectory()) {
					String entryName = fileHeader.getFileName();
					entryName = entryName.replace('\\', '/');
					//アーカイブ内のサブフォルダは除外
					String srcImageFileName = entryName.substring(archivePathLength);
					//使う画像だけ開く（zip と同じ。壊れたテキストを画像として開かない）
					if (this.isWrittenImage(srcImageFileName)) {
						InputStream is = archive.getInputStream(fileHeader);
						try {
							this.writeArchiveImage(srcImageFileName, is);
						} finally {
							is.close();
						}
					}
				}
				if (this.canceled) return;
			}
			} finally { archive.close(); }
		} else {
			//目次から 1 つずつ開く（壊れた項目があっても、ほかの画像を読める。internal #36）
			final int pathLength = archivePathLength;
			com.github.hmdev.io.ZipEntries.forEach(srcFile.toPath(), false, (rawName, opener) -> {
				try {
					//アーカイブ内のサブフォルダは除外
					String entryName = Epub3Writer.sanitizeArchiveEntryName(rawName);
					String srcImageFileName = entryName.substring(pathLength);
					//使う画像（writeArchiveImage が書くもの）だけ開く。ほかの項目は展開しない
					//（暗号化や未対応の圧縮の項目を開くと、前は素通りしていたのに失敗する）
					if (!this.isWrittenImage(srcImageFileName)) return !this.canceled;
					try (java.io.InputStream is = opener.open()) {
						this.writeArchiveImage(srcImageFileName, is);
					}
				} catch (IllegalArgumentException e) {
					System.err.println("Skipping suspicious archive entry: " + e.getMessage());
					// 疑わしいエントリはスキップ
				}
				return !this.canceled;
			});
			if (this.canceled) return;
		}
		
		//ページにできる画像が 1 枚も無い（読めない BMP だけも含む）と、本文の無い壊れた EPUB になる。書かずに失敗にする（internal #64・#77）
		//画像を書いた後に見る。読めずに外した画像は imageInfos から抜けている
		if (this.imageInfos.isEmpty()) {
			throw new NotConvertibleException("画像がありませんでした");
		}
		
		//表紙に選んだ画像が読めずに外れたら、その後ろで最初に書けた画像（無ければ前で最初の画像）に表紙の印だけ付ける。前は表紙が無くなった（internal #77）
		//本文のページは動かさない。表紙のページへ移すと後ろのページが 1 つずつずれ、Kindle の見開きの組（page-spread）が崩れる。
		//表紙のページは作らない（write が、外れた画像を指す表紙を見て insertCoverPage を下ろす）＝本棚の表紙になる
		int coverIndex = bookInfo.coverImageIndex;
		ArrayList<String> names = this.imageInfoReader.getImageFileNames();
		if (coverIndex >= 0 && coverIndex < names.size() && bookInfo.coverImage == null && !this.isOnPage(names.get(coverIndex))) {
			for (int k = 0; k < names.size(); k++) {
				int i = (coverIndex + k) % names.size();
				if (!this.isOnPage(names.get(i))) continue;
				this.imageInfoReader.getImageInfo(names.get(i).trim()).setIsCover(true);
				LogAppender.println("[INFO] 表紙の画像を書けなかったので、"+names.get(i).trim()+" を本棚の表紙にします");
				break;
			}
		}
		
		//画像xhtmlを出力
		zos.setLevel(9);
		//ページの大きさは本全体で 1 つ。imageInfos はページにできる画像（先頭へ移す表紙を含む）で、別ファイルの表紙はまだ入っていない
		FixedPageSize pageSize = FixedPageSize.ofMedianAspect(this.imageInfos);
		if (pageSize != null) this.velocityContext.put("pageSize", pageSize);
		pageNum = 0;
		for (String srcFilePath : this.imageInfoReader.getImageFileNames()) {
			if (this.canceled) return;
			String fileName = vecFileName.get(pageNum++);
			//書く段で読めずに外した画像（id を外してある）はページにしない
			if (fileName != null && !this.isOnPage(srcFilePath)) fileName = null;
			if (fileName != null) {
				//見開きの組は元の画像の並びで決める。本文から抜けたページ（表紙のページへ移した画像・読めずに外した画像）で後ろがずれない
				boolean oddImage = (pageNum-1) % 2 == 1;
				if (isSvgImage) {
					this.printSvgImageSection(srcFilePath, oddImage);
				} else {
					this.startImageSection(srcFilePath, oddImage);
					bw.write(String.format(converter.getChukiValue("画像")[0], fileName));
					bw.write(converter.getChukiValue("画像終わり")[0]);
					bw.flush();
					this.endSection();
				}
			}
			if (this.jProgressBar != null) this.jProgressBar.setValue(this.jProgressBar.getValue()+1);
			if (this.canceled) return;
		}
	}
	
	/** セクション開始. 
	 * @throws IOException */
	private void startImageSection(String srcImageFilePath, boolean oddImage) throws IOException
	{
		this.sectionIndex++;
		String sectionId = decimalFormat.format(this.sectionIndex);
		//package.opf用にファイル名
		SectionInfo sectionInfo = new SectionInfo(sectionId);
		sectionInfo.oddImage = oddImage;
		
		//画像専用指定
		sectionInfo.setImagePage(true);
		ImageInfo imageInfo = this.imageInfoReader.getImageInfo(srcImageFilePath);
		
		this.sectionInfos.add(sectionInfo);
		if (this.sectionIndex ==1 || this.sectionIndex % 5 == 0) this.addChapter(null, ""+this.sectionIndex, 0); //目次追加
		super.zos.putArchiveEntry(new ZipArchiveEntry(OPS_PATH+XHTML_PATH+sectionId+".xhtml"));
		//ヘッダ出力
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(super.zos, "UTF-8"));
		//出力開始するセクションに対応したSectionInfoを設定
		this.velocityContext.put("sectionInfo", sectionInfo);
		//固定レイアウトのページの中で画像を置く位置に使う
		this.velocityContext.put("imageInfo", imageInfo);
		//テキストの本と同じ mergeTemplate を通す。jar の中のテンプレートも探せ、改行も LF にそろう（監査項目 43・44）
		mergeTemplate(this.templatePath+OPS_PATH+XHTML_PATH+XHTML_HEADER_VM, bw);
		bw.flush();
	}
	
	/** SVGでセクション出力 */
	private void printSvgImageSection(String srcImageFilePath, boolean oddImage) throws IOException
	{
		this.sectionIndex++;
		String sectionId = decimalFormat.format(this.sectionIndex);
		//package.opf用にファイル名
		SectionInfo sectionInfo = new SectionInfo(sectionId);
		sectionInfo.oddImage = oddImage;
		ImageInfo imageInfo = this.imageInfoReader.getImageInfo(srcImageFilePath);
		
		//画像専用指定
		sectionInfo.setImagePage(true);
		this.sectionInfos.add(sectionInfo);
		if (this.sectionIndex ==1 || this.sectionIndex % 5 == 0) this.addChapter(null, ""+this.sectionIndex, 0); //目次追加
		super.zos.putArchiveEntry(new ZipArchiveEntry(OPS_PATH+XHTML_PATH+sectionId+".xhtml"));
		//ヘッダ出力
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(super.zos, "UTF-8"));
		//出力開始するセクションに対応したSectionInfoを設定
		this.velocityContext.put("sectionInfo", sectionInfo);
		this.velocityContext.put("imageInfo", imageInfo);
		mergeTemplate(this.templatePath+OPS_PATH+XHTML_PATH+SVG_IMAGE_VM, bw);
		bw.flush();
	}
	
	@Override
	public String getImageFilePath(String srcImageFileName, int lineNum)
	{
		boolean isCover = false;
		this.imageIndex++; //xhtmlと画像ファイル名の番号を合わせるため先に++
		String imageId = decimalFormat.format(this.imageIndex);
		String imageFileName;
		ImageInfo imageInfo;
		try {
			imageInfo = this.imageInfoReader.getImageInfo(srcImageFileName);
			//EPUB に書けない形式（BMP）のページは、捨てずに PNG で書く。前は「画像フォーマットエラー」で本から外していた（internal #77）
			//読めないときは、書く段（writeArchiveImage）で外す。画像はページと OPF より先に書くので、そこで外せば食い違わない
			//出力の名前の拡張子は、書く形式に合わせる（名前の拡張子でなく。.bmp の名前の PNG を 0001.bmp にしない）
			String ext = imageInfo.settleOutFormat(false);
			imageFileName = IMAGES_PATH+imageId+"."+ext;
			imageInfo.setId(imageId);
			imageInfo.setOutFileName(imageId+"."+ext);
			if (this.imageIndex-1 == bookInfo.coverImageIndex) {
				imageInfo.setIsCover(true);
				isCover = true;
			}
			this.imageInfos.add(imageInfo);
			
		} catch (IOException e) {
			logger.error("画像ファイルの読み込みに失敗: {}", srcImageFileName, e);
			return null;
		}
		//先頭に表紙ページ移動の場合でカバーページならnullを返して本文中から削除
		if (bookInfo.insertCoverPage && isCover) return null;
		return "../"+imageFileName;
	}
}
