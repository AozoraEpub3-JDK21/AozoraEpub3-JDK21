import org.junit.Test;

public class AozoraEpub3SmokeTest {
    @Test
    public void runHelpDoesNotThrow() {
        try {
            AozoraEpub3.main(new String[]{"-h"});
        } catch (Throwable t) {
            org.junit.Assert.fail("CLI -h raised exception: " + t);
        }
    }
}
