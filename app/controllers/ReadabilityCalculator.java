package controllers;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Utility class for calculating readability metrics (Flesch-Kincaid Grade Level and Flesch Reading Score)
 * for article descriptions using Java 8 Streams.
 * @author Santhosh
 */
public class ReadabilityCalculator {
    /**
     * Regex pattern for sentence splitting.
     */
    public static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\\s*");
    /**
     * Regex pattern for word splitting.
     */
    public static final Pattern WORD_PATTERN = Pattern.compile("[A-Za-z]+(?:'[A-Za-z]+)?");

    /**
     * Calculates the Flesch-Kincaid Grade Level for a given text.
     * @param text The input text to analyze.
     * @return The grade level required to understand the text.
     * @author Santhosh
     */
    public static double calculateFleschKincaidGrade(String text) {
        int sentences = countSentences(text);
        int words = countWords(text);
        int syllables = countSyllables(text);
        if (sentences == 0 || words == 0) return 0.0;
        return 0.39 * ((double) words / sentences) + 11.8 * ((double) syllables / words) - 15.59;
    }

    /**
     * Calculates the Flesch Reading Score for a given text.
     * @param text The input text to analyze.
     * @return The Flesch Reading Score (higher is easier).
     * @author Santhosh
     */
    public static double calculateFleschReadingScore(String text) {
        int sentences = countSentences(text);
        int words = countWords(text);
        int syllables = countSyllables(text);
        if (sentences == 0 || words == 0) return 0.0;
        return 206.835 - 1.015 * ((double) words / sentences) - 84.6 * ((double) syllables / words);
    }

    /**
     * Calculates the average Flesch-Kincaid Grade Level for a list of descriptions.
     * @param descriptions List of article descriptions.
     * @return The average grade level.
     * @author Santhosh
     */
    public static double averageGrade(List<String> descriptions) {
        return descriptions.stream()
                .mapToDouble(ReadabilityCalculator::calculateFleschKincaidGrade)
                .average()
                .orElse(0.0);
    }

    /**
     * Calculates the average Flesch Reading Score for a list of descriptions.
     * @param descriptions List of article descriptions.
     * @return The average reading score.
     * @author Santhosh
     */
    public static double averageScore(List<String> descriptions) {
        return descriptions.stream()
                .mapToDouble(ReadabilityCalculator::calculateFleschReadingScore)
                .average()
                .orElse(0.0);
    }

    /**
     * Counts the number of sentences in the text.
     * @param text The input text.
     * @return Number of sentences.
     * @author Santhosh
     */
    public static int countSentences(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String[] sentences = SENTENCE_PATTERN.split(text.trim());
        return sentences.length;
    }

    /**
     * Counts the number of words in the text.
     * @param text The input text.
     * @return Number of words.
     * @author Santhosh
     */
    public static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return (int) WORD_PATTERN.matcher(text).results().count();
    }

    /**
     * Counts the total number of syllables in the text.
     * @param text The input text.
     * @return Number of syllables.
     * @author Santhosh
     */
    public static int countSyllables(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return WORD_PATTERN.matcher(text)
                .results()
                .map(match -> match.group())
                .mapToInt(ReadabilityCalculator::countSyllablesInWord)
                .sum();
    }

    /**
     * Estimates the number of syllables in a single word using a simple algorithm.
     * @param word The word to analyze.
     * @return Number of syllables .
     * @author Santhosh
     */
    public static int countSyllablesInWord(String word) {
        if (word == null || word.isEmpty()) return 0;
        word = word.toLowerCase().replaceAll("[^a-z]", "");
        if (word.isEmpty()) return 0;
        int count = 0;
        boolean prevVowel = false;
        String vowels = "aeiouy";
        for (char c : word.toCharArray()) {
            boolean isVowel = vowels.indexOf(c) >= 0;
            if (isVowel && !prevVowel) {
                count++;
            }
            prevVowel = isVowel;
        }
        // Remove silent 'e'
        if (word.endsWith("e") && count > 1) {
            int len = word.length();
            boolean consonantPlusLe = len >= 3 && word.endsWith("le")
                    && "aeiouy".indexOf(word.charAt(len - 3)) == -1;
            if (!consonantPlusLe) count--;
        }
        return Math.max(count, 1);
    }
}
