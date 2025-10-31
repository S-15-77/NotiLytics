package models;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class ReadabilityCalculator {
    // Regex patterns for sentence and word splitting
    private static final Pattern SENTENCE_PATTERN = Pattern.compile("[.!?]+\s*");
    private static final Pattern WORD_PATTERN = Pattern.compile("\\b\\w+\\b");

    // Calculate Flesch-Kincaid Grade Level
    public static double calculateFleschKincaidGrade(String text) {
        int sentences = countSentences(text);
        int words = countWords(text);
        int syllables = countSyllables(text);
        if (sentences == 0 || words == 0) return 0.0;
        return 0.39 * ((double) words / sentences) + 11.8 * ((double) syllables / words) - 15.59;
    }

    // Calculate Flesch Reading Score
    public static double calculateFleschReadingScore(String text) {
        int sentences = countSentences(text);
        int words = countWords(text);
        int syllables = countSyllables(text);
        if (sentences == 0 || words == 0) return 0.0;
        return 206.835 - 1.015 * ((double) words / sentences) - 84.6 * ((double) syllables / words);
    }

    // Average Flesch-Kincaid Grade Level for a list of descriptions
    public static double averageGrade(List<String> descriptions) {
        return descriptions.stream()
                .mapToDouble(ReadabilityCalculator::calculateFleschKincaidGrade)
                .average()
                .orElse(0.0);
    }

    // Average Flesch Reading Score for a list of descriptions
    public static double averageScore(List<String> descriptions) {
        return descriptions.stream()
                .mapToDouble(ReadabilityCalculator::calculateFleschReadingScore)
                .average()
                .orElse(0.0);
    }

    // Count sentences using regex
    private static int countSentences(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        String[] sentences = SENTENCE_PATTERN.split(text.trim());
        return sentences.length;
    }

    // Count words using regex
    private static int countWords(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return (int) WORD_PATTERN.matcher(text).results().count();
    }

    // Count syllables in all words
    private static int countSyllables(String text) {
        if (text == null || text.trim().isEmpty()) return 0;
        return WORD_PATTERN.matcher(text)
                .results()
                .map(match -> match.group())
                .mapToInt(ReadabilityCalculator::countSyllablesInWord)
                .sum();
    }

    // Simple syllable counting for English words
    private static int countSyllablesInWord(String word) {
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
        if (word.endsWith("e") && count > 1) count--;
        return Math.max(count, 1);
    }
}

