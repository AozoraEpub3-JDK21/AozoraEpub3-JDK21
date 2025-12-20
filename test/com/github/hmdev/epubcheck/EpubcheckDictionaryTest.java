package com.github.hmdev.epubcheck;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import java.util.Locale;

import org.junit.Test;

import com.github.hmdev.util.I18n;

public class EpubcheckDictionaryTest {

    @Test
    public void returnsJapaneseMessageWhenDefined() {
        I18n.init(Locale.JAPAN, null);
        String msg = EpubcheckDictionary.messageFor("RSC-005");
        assertThat(msg, containsString("要素"));
    }

    @Test
    public void returnsEnglishMessageWhenDefined() {
        I18n.init(Locale.ENGLISH, null);
        String msg = EpubcheckDictionary.messageFor("RSC-005");
        assertThat(msg, containsString("Element"));
    }

    @Test
    public void returnsNullWhenCodeUnknown() {
        I18n.init(Locale.ENGLISH, null);
        assertThat(EpubcheckDictionary.messageFor("UNKNOWN-CODE"), nullValue());
    }

    @Test
    public void classifiesCodeByDictionaryThenPrefix() {
        assertEquals(EpubErrorType.RSC, EpubcheckDictionary.typeFor("RSC-005"));
        // Unknown code should fall back to prefix detection
        assertEquals(EpubErrorType.OPF, EpubcheckDictionary.typeFor("OPF-999"));
    }
}
