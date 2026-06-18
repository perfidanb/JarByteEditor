package vn.perfidanb.jarbe.service;

import java.io.IOException;
import java.util.List;

public interface TranslationClient {
    List<String> translate(List<String> texts, String sourceLanguage, String targetLanguage) throws IOException, InterruptedException;
}
