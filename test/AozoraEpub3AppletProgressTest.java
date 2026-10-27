import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import javax.swing.JProgressBar;

import org.junit.Test;

/**
 * 進み具合のバーの最大値（internal #63）。短い本では行数/10 と画像の数から作る最大値が 0 になり、
 * 割合が 0÷0 になってバーに NaN と出ていた
 */
public class AozoraEpub3AppletProgressTest {

	/** 最大値の元が 0 でも、割合は数で、文字に NaN が出ない。終わりに最大値まで進めれば 100% */
	@Test
	public void aShortBookShowsAPercentNotNaN() {
		JProgressBar bar = new JProgressBar(0, 100);
		AozoraEpub3Applet.startProgress(bar, 0);
		assertFalse(Double.isNaN(bar.getPercentComplete()));
		assertFalse(bar.getString(), bar.getString().contains("NaN"));
		assertEquals(0.0, bar.getPercentComplete(), 0.0);
		bar.setValue(bar.getMaximum());
		assertEquals(1.0, bar.getPercentComplete(), 0.0);
	}

	/** 最大値の元が 1 以上なら、そのまま最大値にする（今までと同じ） */
	@Test
	public void aLongerBookKeepsItsMaximum() {
		JProgressBar bar = new JProgressBar(0, 100);
		bar.setValue(40);
		AozoraEpub3Applet.startProgress(bar, 37);
		assertEquals(37, bar.getMaximum());
		assertEquals(0, bar.getValue());
		assertEquals(true, bar.isStringPainted());
	}
}
