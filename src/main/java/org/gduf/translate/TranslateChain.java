package org.gduf.translate;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class TranslateChain {

    private final List<Translator> translators;

    public TranslateChain(List<Translator> translators) {
        this.translators = translators.stream()
                .sorted(Comparator.comparingInt(Translator::getPriority))
                .toList();
    }

    public String translate(String text, String from, String to) {
        for (Translator t : translators) {
            try {
                String result = t.translate(text, from, to);
                if (result != null && !result.isBlank()) {
                    return result;
                }
            } catch (Exception e) {
                System.err.println("[TranslateChain] " + t.getName() + " failed: " + e.getMessage());
            }
        }
        return null;
    }
}
