package models;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Models the statistics used in the Website.
 * Handles the treatment of cache Memory.
 * @author Karim BG
 */
public class Statistics {
    QueryResult cachedValue;

    /**
     * @param query the result of the query in memory (in the cache)
     * @return A Statistics object.
     * @author Karim BG
     */
    public Statistics(QueryResult query){
        this.cachedValue = query;
    }

    /**
     * Gets the titles from the articles in the cache.
     * @return A List<String> of the titles.
     * @author Karim BG
     */
    public List<String> getTitles(){
        return this.cachedValue.getArticles().stream()
                .map(a -> a.getTitle() != null ? a.getTitle() : "")
                .collect(Collectors.toList());
    }

    /**
     * Gets the titles from the description in the cache.
     * @return A List<String> of the description.
     * @author Karim BG
     */
    public List<String> getDescriptions(){
        return this.cachedValue.getArticles().stream()
                .map(a -> a.getDescription() != null ? a.getDescription() : "")
                .collect(Collectors.toList());
    }

    /**
     * Gets the words in a given List<String> of sentences.
     * @return A List<String> of the words.
     * @author Karim BG
     */
    public static List<String> getWords(List<String> text){
        return text.stream()
                .flatMap(sentence-> Arrays.stream(sentence.split(" ")))
                .map(word -> word.replaceAll("[^\\p{L}]", "").toLowerCase())
                //.reduce("", (a,b)->(a+b+" "));
                .collect(Collectors.toList());
    }

    /**
     * Gets the counter for a List of words.
     * @return A Map linking each String word with the number of occurrences.
     * @author Karim BG
     */
    public static Map<String, Long> getCounter(List<String> words){
        return words.stream()
                .collect(Collectors.groupingBy(w -> w, Collectors.counting()));
    }

    /**
     * Transforms a Map<String,Long> into a readable string.
     * @return A String of the counter.
     * @author Karim BG
     */
    public static String getString(Map<String, Long> counter){
        return counter.entrySet().stream()
                .sorted((e1, e2) -> e2.getValue().compareTo(e1.getValue()))
                .map(e -> e.getKey() + ":" + e.getValue())
                .collect(Collectors.joining("\n"));
    }

    /**
     * Filters the words to get rid of the two letters words that give often no information
     * @return A List<String> of the titles.
     * @author Karim BG
     */
    public static List<String> filtering(List<String> words){
        return words.stream()
                .filter(t->t.length()>2)
                .collect(Collectors.toList());
    }
}
