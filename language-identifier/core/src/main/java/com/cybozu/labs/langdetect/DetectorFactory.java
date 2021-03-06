/**
 * Per the Apache license changes made to this file should be noted below.
 *
 * Changes:
 *
 * * Windows newlines have been converted to Unix newlines.
 * * Static methods and attributes have been made non static.
 * * The private constructor has been removed, allowing one to create instances
 *   of the DetectorFactory class.
 * * Language profiles are cached in a static attribute based on the directory
 *   they are located in, access to this cache is synchronized.
 */

package com.cybozu.labs.langdetect;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import net.arnx.jsonic.JSON;
import net.arnx.jsonic.JSONException;

import com.cybozu.labs.langdetect.util.LangProfile;

/**
 * Language Detector Factory Class
 *
 * This class manages an initialization and constructions of {@link Detector}.
 *
 * Before using language detection library,
 * load profiles with {@link DetectorFactory#loadProfile(String)} method
 * and set initialization parameters.
 *
 * When the language detection,
 * construct Detector instance via {@link DetectorFactory#create()}.
 * See also {@link Detector}'s sample code.
 *
 * <ul>
 * <li>4x faster improvement based on Elmer Garduno's code. Thanks!</li>
 * </ul>
 *
 * @see Detector
 * @author Nakatani Shuyo
 */
public class DetectorFactory {
    public HashMap<String, double[]> wordLangProbMap = new HashMap<String, double[]>();
    public ArrayList<String> langlist = new ArrayList<String>();
    public Long seed = null;

    /**
     * Hash used for caching language profiles per profile directory.
     */
    public static HashMap<String, ArrayList<LangProfile>> profileCache =
        new HashMap<String, ArrayList<LangProfile>>();

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public void loadProfile(String profileDirectory) throws LangDetectException {
        loadProfile(new File(profileDirectory));
    }

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public void loadProfile(File profileDirectory) throws LangDetectException {
        String profilePath = profileDirectory.toString();

        synchronized(DetectorFactory.profileCache) {
            if ( !DetectorFactory.profileCache.containsKey(profilePath) ) {
                ArrayList<LangProfile> newProfiles = new ArrayList<LangProfile>();

                File[] listFiles = profileDirectory.listFiles();

                if (listFiles == null)
                    throw new LangDetectException(ErrorCode.NeedLoadProfileError, "Not found profile: " + profileDirectory);

                for (File file: listFiles) {
                    if (file.getName().startsWith(".") || !file.isFile()) continue;

                    FileInputStream is = null;

                    try {
                        is = new FileInputStream(file);
                        LangProfile profile = JSON.decode(is, LangProfile.class);

                        newProfiles.add(profile);
                    } catch (JSONException e) {
                        throw new LangDetectException(ErrorCode.FormatError, "profile format error in '" + file.getName() + "'");
                    } catch (IOException e) {
                        throw new LangDetectException(ErrorCode.FileLoadError, "can't open '" + file.getName() + "'");
                    } finally {
                        try {
                            if (is!=null) is.close();
                        } catch (IOException e) {}
                    }
                }

                DetectorFactory.profileCache.put(profilePath, newProfiles);
            }
        }

        int index = 0;

        ArrayList<LangProfile> profiles = DetectorFactory.profileCache
            .get(profilePath);

        for ( LangProfile profile: profiles ) {
            addProfile(profile, index, profiles.size());

            index++;
        }
    }

    /**
     * Load profiles from specified directory.
     * This method must be called once before language detection.
     *
     * @param profileDirectory profile directory path
     * @throws LangDetectException  Can't open profiles(error code = {@link ErrorCode#FileLoadError})
     *                              or profile's format is wrong (error code = {@link ErrorCode#FormatError})
     */
    public void loadProfile(List<String> json_profiles) throws LangDetectException {
        int index = 0;
        int langsize = json_profiles.size();
        if (langsize < 2)
            throw new LangDetectException(ErrorCode.NeedLoadProfileError, "Need more than 2 profiles");

        for (String json: json_profiles) {
            try {
                LangProfile profile = JSON.decode(json, LangProfile.class);
                addProfile(profile, index, langsize);
                ++index;
            } catch (JSONException e) {
                throw new LangDetectException(ErrorCode.FormatError, "profile format error");
            }
        }
    }

    /**
     * @param profile
     * @param langsize
     * @param index
     * @throws LangDetectException
     */
    public void addProfile(LangProfile profile, int index, int langsize) throws LangDetectException {
        String lang = profile.name;
        if (this.langlist.contains(lang)) {
            throw new LangDetectException(ErrorCode.DuplicateLangError, "duplicate the same language profile");
        }
        this.langlist.add(lang);
        for (String word: profile.freq.keySet()) {
            if (!this.wordLangProbMap.containsKey(word)) {
                this.wordLangProbMap.put(word, new double[langsize]);
            }
            int length = word.length();
            if (length >= 1 && length <= 3) {
                double prob = profile.freq.get(word).doubleValue() / profile.n_words[length - 1];
                this.wordLangProbMap.get(word)[index] = prob;
            }
        }
    }

    /**
     * Clear loaded language profiles (reinitialization to be available)
     */
    public void clear() {
        this.langlist.clear();
        this.wordLangProbMap.clear();
    }

    public void setSeed(long seed) {
        this.seed = seed;
    }

    public final List<String> getLangList() {
        return Collections.unmodifiableList(this.langlist);
    }
}
