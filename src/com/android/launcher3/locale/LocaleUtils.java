/*
 * Copyright (C) 2010 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.launcher3.locale;

import android.provider.ContactsContract.FullNameStyle;
import android.provider.ContactsContract.PhoneticNameStyle;
import android.text.TextUtils;
import android.util.Log;

import com.android.launcher3.locale.HanziToPinyin.Token;

import com.google.common.annotations.VisibleForTesting;

import java.lang.Character.UnicodeBlock;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;

import libcore.icu.AlphabeticIndex;
import libcore.icu.AlphabeticIndex.ImmutableIndex;
import libcore.icu.Transliterator;

/**
 * This utility class provides specialized handling for locale specific
 * information: labels, name lookup keys.
 *
 * This class has been modified from ContactLocaleUtils.java for now to rip out
 * Chinese/Japanese specific Alphabetic Indexers because the MediaProvider's sort
 * is using a Collator sort which can result in confusing behavior, so for now we will
 * simplify and batch up those results until we later support our own internal databases
 * An example of what This is, if we have songs "Able", "Xylophone" and "上" in
 * simplified chinese language The media provider would give it to us in that order sorted,
 * but the ICU lib would return "A", "X", "S".  Unless we write our own db or do our own sort
 * there is no good easy solution
 */
public class LocaleUtils {
    public static final String TAG = "LauncherLocale";

    public static final Locale LOCALE_ARABIC = new Locale("ar");
    public static final Locale LOCALE_GREEK = new Locale("el");
    public static final Locale LOCALE_HEBREW = new Locale("he");
    // Serbian and Ukrainian labels are complementary supersets of Russian
    public static final Locale LOCALE_SERBIAN = new Locale("sr");
    public static final Locale LOCALE_UKRAINIAN = new Locale("uk");
    public static final Locale LOCALE_THAI = new Locale("th");

    /**
     * This class is the default implementation and should be the base class
     * for other locales.
     *
     * sortKey: same as name
     * nameLookupKeys: none
     * labels: uses ICU AlphabeticIndex for labels and extends by labeling
     *     phone numbers "#".  Eg English labels are: [A-Z], #, " "
     */
    private static class LocaleUtilsBase {
        private static final String EMPTY_STRING = "";
        private static final String NUMBER_STRING = "#";

        protected final ImmutableIndex mAlphabeticIndex;
        private final int mAlphabeticIndexBucketCount;
        private final int mNumberBucketIndex;
        private final boolean mEnableSecondaryLocalePinyin;

        public LocaleUtilsBase(LocaleSet locales) {
            // AlphabeticIndex.getBucketLabel() uses a binary search across
            // the entire label set so care should be taken about growing this
            // set too large. The following set determines for which locales
            // we will show labels other than your primary locale. General rules
            // of thumb for adding a locale: should be a supported locale; and
            // should not be included if from a name it is not deterministic
            // which way to label it (so eg Chinese cannot be added because
            // the labeling of a Chinese character varies between Simplified,
            // Traditional, and Japanese locales). Use English only for all
            // Latin based alphabets. Ukrainian and Serbian are chosen for
            // Cyrillic because their alphabets are complementary supersets
            // of Russian.
            final Locale secondaryLocale = locales.getSecondaryLocale();
            mEnableSecondaryLocalePinyin = locales.isSecondaryLocaleSimplifiedChinese();
            AlphabeticIndex ai = new AlphabeticIndex(locales.getPrimaryLocale())
                    .setMaxLabelCount(300);
            if (secondaryLocale != null) {
                ai.addLabels(secondaryLocale);
            }
            mAlphabeticIndex = ai.addLabels(Locale.ENGLISH)
                    .addLabels(Locale.JAPANESE)
                    .addLabels(Locale.KOREAN)
                    .addLabels(LOCALE_THAI)
                    .addLabels(LOCALE_ARABIC)
                    .addLabels(LOCALE_HEBREW)
                    .addLabels(LOCALE_GREEK)
                    .addLabels(LOCALE_UKRAINIAN)
                    .addLabels(LOCALE_SERBIAN)
                    .getImmutableIndex();
            mAlphabeticIndexBucketCount = mAlphabeticIndex.getBucketCount();
            mNumberBucketIndex = mAlphabeticIndexBucketCount - 1;
        }

        public String getSortKey(String name) {
            return name;
        }

        /**
         * Returns the bucket index for the specified string. AlphabeticIndex
         * sorts strings into buckets numbered in order from 0 to N, where the
         * exact value of N depends on how many representative index labels are
         * used in a particular locale. This routine adds one additional bucket
         * for phone numbers. It attempts to detect phone numbers and shifts
         * the bucket indexes returned by AlphabeticIndex in order to make room
         * for the new # bucket, so the returned range becomes 0 to N+1.
         */
        public int getBucketIndex(String name) {
            boolean prefixIsNumeric = false;
            final int length = name.length();
            int offset = 0;
            while (offset < length) {
                int codePoint = Character.codePointAt(name, offset);
                // Ignore standard phone number separators and identify any
                // string that otherwise starts with a number.
                if (Character.isDigit(codePoint)) {
                    prefixIsNumeric = true;
                    break;
                } else if (!Character.isSpaceChar(codePoint) &&
                        codePoint != '+' && codePoint != '(' &&
                        codePoint != ')' && codePoint != '.' &&
                        codePoint != '-' && codePoint != '#') {
                    break;
                }
                offset += Character.charCount(codePoint);
            }
            if (prefixIsNumeric) {
                return mNumberBucketIndex;
            }

            /**
             * TODO: ICU 52 AlphabeticIndex doesn't support Simplified Chinese
             * as a secondary locale. Remove the following if that is added.
             */
            if (mEnableSecondaryLocalePinyin) {
                name = HanziToPinyin.getInstance().transliterate(name);
            }
            final int bucket = mAlphabeticIndex.getBucketIndex(name);
            if (bucket < 0) {
                return -1;
            }
            if (bucket >= mNumberBucketIndex) {
                return bucket + 1;
            }
            return bucket;
        }

        /**
         * Returns the number of buckets in use (one more than AlphabeticIndex
         * uses, because this class adds a bucket for phone numbers).
         */
        public int getBucketCount() {
            return mAlphabeticIndexBucketCount + 1;
        }

        /**
         * Returns the label for the specified bucket index if a valid index,
         * otherwise returns an empty string. '#' is returned for the phone
         * number bucket; for all others, the AlphabeticIndex label is returned.
         */
        public String getBucketLabel(int bucketIndex) {
            if (bucketIndex < 0 || bucketIndex >= getBucketCount()) {
                return EMPTY_STRING;
            } else if (bucketIndex == mNumberBucketIndex) {
                return NUMBER_STRING;
            } else if (bucketIndex > mNumberBucketIndex) {
                --bucketIndex;
            }
            return mAlphabeticIndex.getBucketLabel(bucketIndex);
        }

        @SuppressWarnings("unused")
        public Iterator<String> getNameLookupKeys(String name, int nameStyle) {
            return null;
        }

        public ArrayList<String> getLabels() {
            final int bucketCount = getBucketCount();
            final ArrayList<String> labels = new ArrayList<String>(bucketCount);
            for(int i = 0; i < bucketCount; ++i) {
                labels.add(getBucketLabel(i));
            }
            return labels;
        }
    }

    /**
     * Japanese specific locale overrides.
     *
     * sortKey: unchanged (same as name)
     * nameLookupKeys: unchanged (none)
     * labels: extends default labels by labeling unlabeled CJ characters
     *     with the Japanese character 他 ("misc"). Japanese labels are:
     *     あ, か, さ, た, な, は, ま, や, ら, わ, 他, [A-Z], #, " "
     */
    private static class JapaneseContactUtils extends LocaleUtilsBase {
        // \u4ed6 is Japanese character 他 ("misc")
        private static final String JAPANESE_MISC_LABEL = "\u4ed6";
        private final int mMiscBucketIndex;

        public JapaneseContactUtils(LocaleSet locales) {
            super(locales);
            // Determine which bucket AlphabeticIndex is lumping unclassified
            // Japanese characters into by looking up the bucket index for
            // a representative Kanji/CJK unified ideograph (\u65e5 is the
            // character '日').
            mMiscBucketIndex = super.getBucketIndex("\u65e5");
        }

        // Set of UnicodeBlocks for unified CJK (Chinese) characters and
        // Japanese characters. This includes all code blocks that might
        // contain a character used in Japanese (which is why unified CJK
        // blocks are included but Korean Hangul and jamo are not).
        private static final Set<Character.UnicodeBlock> CJ_BLOCKS;
        static {
            Set<UnicodeBlock> set = new HashSet<UnicodeBlock>();
            set.add(UnicodeBlock.HIRAGANA);
            set.add(UnicodeBlock.KATAKANA);
            set.add(UnicodeBlock.KATAKANA_PHONETIC_EXTENSIONS);
            set.add(UnicodeBlock.HALFWIDTH_AND_FULLWIDTH_FORMS);
            set.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS);
            set.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A);
            set.add(UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B);
            set.add(UnicodeBlock.CJK_SYMBOLS_AND_PUNCTUATION);
            set.add(UnicodeBlock.CJK_RADICALS_SUPPLEMENT);
            set.add(UnicodeBlock.CJK_COMPATIBILITY);
            set.add(UnicodeBlock.CJK_COMPATIBILITY_FORMS);
            set.add(UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS);
            set.add(UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS_SUPPLEMENT);
            CJ_BLOCKS = Collections.unmodifiableSet(set);
        }

        /**
         * Helper routine to identify unlabeled Chinese or Japanese characters
         * to put in a 'misc' bucket.
         *
         * @return true if the specified Unicode code point is Chinese or
         *              Japanese
         */
        private static boolean isChineseOrJapanese(int codePoint) {
            return CJ_BLOCKS.contains(UnicodeBlock.of(codePoint));
        }

        /**
         * Returns the bucket index for the specified string. Adds an
         * additional 'misc' bucket for Kanji characters to the base class set.
         */
        @Override
        public int getBucketIndex(String name) {
            final int bucketIndex = super.getBucketIndex(name);
            if ((bucketIndex == mMiscBucketIndex &&
                    !isChineseOrJapanese(Character.codePointAt(name, 0))) ||
                    bucketIndex > mMiscBucketIndex) {
                return bucketIndex + 1;
            }
            return bucketIndex;
        }

        /**
         * Returns the number of buckets in use (one more than the base class
         * uses, because this class adds a bucket for Kanji).
         */
        @Override
        public int getBucketCount() {
            return super.getBucketCount() + 1;
        }

        /**
         * Returns the label for the specified bucket index if a valid index,
         * otherwise returns an empty string. '他' is returned for unclassified
         * Kanji; for all others, the label determined by the base class is
         * returned.
         */
        @Override
        public String getBucketLabel(int bucketIndex) {
            if (bucketIndex == mMiscBucketIndex) {
                return JAPANESE_MISC_LABEL;
            } else if (bucketIndex > mMiscBucketIndex) {
                --bucketIndex;
            }
            return super.getBucketLabel(bucketIndex);
        }

        @Override
        public Iterator<String> getNameLookupKeys(String name, int nameStyle) {
            // Hiragana and Katakana will be positively identified as Japanese.
            if (nameStyle == PhoneticNameStyle.JAPANESE) {
                return getRomajiNameLookupKeys(name);
            }
            return null;
        }

        private static boolean mInitializedTransliterator;
        private static Transliterator mJapaneseTransliterator;

        private static Transliterator getJapaneseTransliterator() {
            synchronized(JapaneseContactUtils.class) {
                if (!mInitializedTransliterator) {
                    mInitializedTransliterator = true;
                    Transliterator t = null;
                    try {
                        t = new Transliterator("Hiragana-Latin; Katakana-Latin;"
                                + " Latin-Ascii");
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Hiragana/Katakana-Latin transliterator data"
                                + " is missing");
                    }
                    mJapaneseTransliterator = t;
                }
                return mJapaneseTransliterator;
            }
        }

        public static Iterator<String> getRomajiNameLookupKeys(String name) {
            final Transliterator t = getJapaneseTransliterator();
            if (t == null) {
                return null;
            }
            final String romajiName = t.transliterate(name);
            if (TextUtils.isEmpty(romajiName) ||
                    TextUtils.equals(name, romajiName)) {
                return null;
            }
            final HashSet<String> keys = new HashSet<String>();
            keys.add(romajiName);
            return keys.iterator();
        }
    }

    /**
     * Simplified Chinese specific locale overrides. Uses ICU Transliterator
     * for generating pinyin transliteration.
     *
     * sortKey: unchanged (same as name)
     * nameLookupKeys: adds additional name lookup keys
     *     - Chinese character's pinyin and pinyin's initial character.
     *     - Latin word and initial character.
     * labels: unchanged
     *     Simplified Chinese labels are the same as English: [A-Z], #, " "
     */
    private static class SimplifiedChineseContactUtils
            extends LocaleUtilsBase {
        public SimplifiedChineseContactUtils(LocaleSet locales) {
            super(locales);
        }

        @Override
        public Iterator<String> getNameLookupKeys(String name, int nameStyle) {
            if (nameStyle != FullNameStyle.JAPANESE &&
                    nameStyle != FullNameStyle.KOREAN) {
                return getPinyinNameLookupKeys(name);
            }
            return null;
        }

        public static Iterator<String> getPinyinNameLookupKeys(String name) {
            // TODO : Reduce the object allocation.
            HashSet<String> keys = new HashSet<String>();
            ArrayList<Token> tokens = HanziToPinyin.getInstance().getTokens(name);
            final int tokenCount = tokens.size();
            final StringBuilder keyPinyin = new StringBuilder();
            final StringBuilder keyInitial = new StringBuilder();
            // There is no space among the Chinese Characters, the variant name
            // lookup key wouldn't work for Chinese. The keyOriginal is used to
            // build the lookup keys for itself.
            final StringBuilder keyOriginal = new StringBuilder();
            for (int i = tokenCount - 1; i >= 0; i--) {
                final Token token = tokens.get(i);
                if (Token.UNKNOWN == token.type) {
                    continue;
                }
                if (Token.PINYIN == token.type) {
                    keyPinyin.insert(0, token.target);
                    keyInitial.insert(0, token.target.charAt(0));
                } else if (Token.LATIN == token.type) {
                    // Avoid adding space at the end of String.
                    if (keyPinyin.length() > 0) {
                        keyPinyin.insert(0, ' ');
                    }
                    if (keyOriginal.length() > 0) {
                        keyOriginal.insert(0, ' ');
                    }
                    keyPinyin.insert(0, token.source);
                    keyInitial.insert(0, token.source.charAt(0));
                }
                keyOriginal.insert(0, token.source);
                keys.add(keyOriginal.toString());
                keys.add(keyPinyin.toString());
                keys.add(keyInitial.toString());
            }
            return keys.iterator();
        }
    }

    private static final String JAPANESE_LANGUAGE = Locale.JAPANESE.getLanguage().toLowerCase();
    private static LocaleUtils sSingleton;

    private final LocaleSet mLocales;
    private final LocaleUtilsBase mUtils;

    private LocaleUtils(LocaleSet locales) {
        if (locales == null) {
            mLocales = LocaleSet.getDefault();
        } else {
            mLocales = locales;
        }
        if (mLocales.isPrimaryLanguage(JAPANESE_LANGUAGE)) {
            mUtils = new JapaneseContactUtils(mLocales);
        } else if (mLocales.isPrimaryLocaleSimplifiedChinese()) {
            mUtils = new SimplifiedChineseContactUtils(mLocales);
        } else {
            mUtils = new LocaleUtilsBase(mLocales);
        }
        Log.i(TAG, "AddressBook Labels [" + mLocales.toString() + "]: "
                + getLabels().toString());
    }

    public boolean isLocale(LocaleSet locales) {
        return mLocales.equals(locales);
    }

    public static synchronized LocaleUtils getInstance() {
        if (sSingleton == null) {
            sSingleton = new LocaleUtils(LocaleSet.getDefault());
        }
        return sSingleton;
    }

    @VisibleForTesting
    public static synchronized void setLocale(Locale locale) {
        setLocales(new LocaleSet(locale));
    }

    public static synchronized void setLocales(LocaleSet locales) {
        if (sSingleton == null || !sSingleton.isLocale(locales)) {
            sSingleton = new LocaleUtils(locales);
        }
    }

    public String getSortKey(String name, int nameStyle) {
        return mUtils.getSortKey(name);
    }

    public int getBucketIndex(String name) {
        return mUtils.getBucketIndex(name);
    }

    public int getBucketCount() {
        return mUtils.getBucketCount();
    }

    public String getBucketLabel(int bucketIndex) {
        return mUtils.getBucketLabel(bucketIndex);
    }

    public String getLabel(String name) {
        return getBucketLabel(getBucketIndex(name));
    }

    public ArrayList<String> getLabels() {
        return mUtils.getLabels();
    }
}
