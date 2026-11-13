package com.example;

import com.example.assistant.CommandProcessor;
import com.example.assistant.ConversationMemory;
import com.example.assistant.LanguageUtils;
import com.example.assistant.MathAndUnitEvaluator;
import com.example.model.ChatMessage;

import org.junit.Test;

import java.util.Arrays;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SubhAiLogicTest {

    @Test
    public void detectsHindiHinglishAndEnglish() {
        assertEquals(LanguageUtils.LANG_HINDI, LanguageUtils.detectLanguage("नमस्ते शुभ, समय क्या हुआ है?"));
        assertEquals(LanguageUtils.LANG_HINGLISH, LanguageUtils.detectLanguage("bhai koi accha joke sunao"));
        assertEquals(LanguageUtils.LANG_ENGLISH, LanguageUtils.detectLanguage("Open YouTube and search space"));
    }

    @Test
    public void detectsWakeAndSleepPhrases() {
        assertTrue(LanguageUtils.containsWakeWord("Hey SUBH open YouTube"));
        assertEquals("open YouTube", LanguageUtils.stripWakeWord("Hey SUBH open YouTube"));
        assertTrue(LanguageUtils.isSleepCommand("Good Night SUBH"));
        assertFalse(LanguageUtils.isSleepCommand("Tell me a joke"));
    }

    @Test
    public void conversationMemoryAvoidsImmediateRepetitionAndTracksTopics() {
        ConversationMemory memory = new ConversationMemory();
        String[] options = new String[]{"Reply A", "Reply B"};
        String first = memory.pickNonRepeating(options);
        memory.recordTurn("tell me a joke", first, "humor");
        String second = memory.pickNonRepeating(options);
        assertEquals("Reply A", first);
        assertEquals("Reply B", second);

        memory.recordTurn("tell me about quantum physics", "Quantum physics reply", "science & tech");
        assertEquals("science & tech", memory.getCurrentTopic());
        assertEquals("humor", memory.getPreviousTopic());
    }

    @Test
    public void hydratesMemoryFromSavedHistory() {
        ConversationMemory memory = new ConversationMemory();
        memory.hydrateFromHistory(Arrays.asList(
                new ChatMessage(1L, "Tell me a joke", true, "en", 100L, false, ""),
                new ChatMessage(2L, "Joke reply", false, "en", 200L, true, "SAMPLE AI"),
                new ChatMessage(3L, "Explain Java code", true, "en", 300L, false, ""),
                new ChatMessage(4L, "Java reply", false, "en", 400L, true, "SAMPLE AI")
        ));
        assertEquals("programming", memory.getCurrentTopic());
        assertEquals("humor", memory.getPreviousTopic());
        assertTrue(memory.wasRecentlySaid("Java reply"));
    }

    @Test
    public void evaluatesMathExpressionsAndUnitConversions() {
        MathAndUnitEvaluator.EvalResult arithmetic = MathAndUnitEvaluator.tryEvaluate("25 * 48 + 120");
        assertTrue(arithmetic.handled);
        assertTrue(arithmetic.formattedResponse.contains("1320"));

        MathAndUnitEvaluator.EvalResult conv = MathAndUnitEvaluator.tryEvaluate("Convert 5 km to m");
        assertTrue(conv.handled);
        assertTrue(conv.formattedResponse.contains("5000"));
    }

    @Test
    public void requiresConfirmationForSensitiveVoiceCommands() {
        CommandProcessor.CommandResult contactsRes = CommandProcessor.tryExecute(null, "Open contacts", "");
        assertTrue(contactsRes.handled);
        assertTrue(contactsRes.requiresConfirmation);
        assertEquals(CommandProcessor.SensitiveActionType.OPEN_CONTACTS, contactsRes.sensitiveAction);

        CommandProcessor.CommandResult noteRes = CommandProcessor.tryExecute(null, "Save a note: Buy milk", "");
        assertTrue(noteRes.handled);
        assertTrue(noteRes.requiresConfirmation);
        assertEquals(CommandProcessor.SensitiveActionType.SAVE_NOTE, noteRes.sensitiveAction);
        assertEquals("Buy milk", noteRes.actionPayload);
    }
}
