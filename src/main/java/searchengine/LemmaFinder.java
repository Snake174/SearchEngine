package searchengine;

import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;

import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private final String[] particlesNames = new String[] { "МЕЖД", "СОЮЗ", "ПРЕДЛ", "ЧАСТ" };
    private final LuceneMorphology luceneMorph;

    public LemmaFinder() {
        try {
            luceneMorph = new RussianLuceneMorphology();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public HashMap<String, Integer> textToLemmas(String text) {
        HashMap<String, Integer> result = new HashMap<>();
        String[] words = arrayContainsRussianWords(text);

        for (String word : words) {
            if (word.isBlank()) {
                continue;
            }

            List<String> wordBaseForms = luceneMorph.getMorphInfo(word);

            if (anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }

            List<String> normalForms = luceneMorph.getNormalForms(word);

            if (normalForms.isEmpty()) {
                continue;
            }

            String normalWord = normalForms.get(0);

            if (result.containsKey(normalWord)) {
                result.put(normalWord, result.get(normalWord) + 1);
            } else {
                result.put(normalWord, 1);
            }
        }

        return result;
    }

    private String[] arrayContainsRussianWords(String text) {
        return text.toLowerCase(Locale.ROOT)
            .replaceAll("([^а-я\\s])", " ")
            .trim()
            .split("\\s+");
    }

    private boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(this::hasParticleProperty);
    }

    private boolean hasParticleProperty(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }

        return false;
    }
}
