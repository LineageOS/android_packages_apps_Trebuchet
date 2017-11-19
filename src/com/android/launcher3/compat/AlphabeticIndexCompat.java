package com.android.launcher3.compat;

import android.content.Context;
import android.icu.text.AlphabeticIndex;

import com.android.launcher3.Utilities;

import java.util.Locale;

public class AlphabeticIndexCompat {

    private static final String MID_DOT = "\u2219";

    private AlphabeticIndex.ImmutableIndex mAlphabeticIndex;
    private String mDefaultMiscLabel;

    public AlphabeticIndexCompat(Context context) {
        Locale curLocale = context.getResources().getConfiguration().getLocales().get(0);
        mAlphabeticIndex = new AlphabeticIndex(curLocale).buildImmutableIndex();
        if (curLocale.getLanguage().equals(Locale.JAPANESE.getLanguage())) {
            // Japanese character 他 ("misc")
            mDefaultMiscLabel = "\u4ed6";
            // TODO(winsonc, omakoto): We need to handle Japanese sections better, especially the kanji
        } else {
            // Dot
            mDefaultMiscLabel = MID_DOT;
        }
    }

    /**
     * Computes the section name for an given string {@param s}.
     */
    public String computeSectionName(CharSequence cs) {
        String s = Utilities.trim(cs);
        int bucketIndex = mAlphabeticIndex.getBucketIndex(s);
        String sectionName = mAlphabeticIndex.getBucket(bucketIndex).getLabel();
        if (Utilities.trim(sectionName).isEmpty()) {
            if (s.length() > 0) {
                int c = s.codePointAt(0);
                boolean startsWithDigit = Character.isDigit(c);
                if (startsWithDigit) {
                    // Digit section
                    return "#";
                } else {
                    boolean startsWithLetter = Character.isLetter(c);
                    if (startsWithLetter) {
                        return mDefaultMiscLabel;
                    } else {
                        // In languages where these differ, this ensures that we differentiate
                        // between the misc section in the native language and a misc section
                        // for everything else.
                        return MID_DOT;
                    }
                }
            } else {
                // Somehow this app's name is all white/java space characters.
                // Put it in the bucket with all the other crazies.
                return MID_DOT;
            }
        }
        return sectionName;
    }
}
