package org.gduf.translate;

public interface Translator {
    String translate(String text, String from, String to);
    String getName();
    int getPriority();
}
