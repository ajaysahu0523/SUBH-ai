package com.example.ai;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.assistant.ConversationMemory;
import com.example.assistant.LanguageUtils;
import com.example.assistant.NetworkUtils;
import com.example.data.UserPreferencesManager;
import com.example.model.ChatMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Sample / placeholder implementation of AiRepository used while Firebase AI Logic is not enabled.
 * Covers daily conversation, jokes, education, coding help, translation, summarization, ideas,
 * English/Hindi learning, and topic-shift follow-ups, while keeping sample output clearly labeled
 * and never exposing API keys or pretending to run unsupported hardware actions.
 */
public class PlaceholderAiRepository implements AiRepository {
    private final Context appContext;
    private final UserPreferencesManager preferencesManager;
    private final ConversationMemory conversationMemory;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public PlaceholderAiRepository(
            Context context,
            UserPreferencesManager preferencesManager,
            ConversationMemory conversationMemory
    ) {
        this.appContext = context.getApplicationContext();
        this.preferencesManager = preferencesManager;
        this.conversationMemory = conversationMemory;
    }

    @Override
    public void generateResponse(
            String userMessage,
            String detectedLanguage,
            List<ChatMessage> conversationHistory,
            AiCallback callback
    ) {
        executor.execute(() -> {
            try {
                Thread.sleep(480);
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            }

            if (!NetworkUtils.isNetworkAvailable(appContext)) {
                String offlineMsg = buildOfflineError(detectedLanguage);
                mainHandler.post(() -> callback.onError(offlineMsg, detectedLanguage));
                return;
            }

            String reply = buildSmartSampleReply(userMessage, detectedLanguage);
            String topic = inferEffectiveTopic(userMessage);
            conversationMemory.recordTurn(userMessage, reply, topic);

            mainHandler.post(() -> callback.onSuccess(reply, detectedLanguage, true));
        });
    }

    @Override
    public void shutdown() {
        executor.shutdownNow();
    }

    private String inferEffectiveTopic(String userMessage) {
        String lower = userMessage == null ? "" : userMessage.toLowerCase(Locale.ROOT).trim();
        if (isFollowUpPhrase(lower) && !conversationMemory.getCurrentTopic().isEmpty()) {
            return conversationMemory.getCurrentTopic();
        }
        String inferred = ConversationMemory.inferTopicFromText(userMessage);
        return inferred.isEmpty() ? "general" : inferred;
    }

    private boolean isFollowUpPhrase(String lower) {
        return lower.contains("another") || lower.contains("one more") || lower.contains("ek aur")
                || lower.contains("और सुनाओ") || lower.contains("tell me more") || lower.contains("aur batao")
                || lower.contains("और बताओ") || lower.equals("why") || lower.equals("kyun")
                || lower.equals("क्यों") || lower.contains("what about that") || lower.contains("previous topic");
    }

    private String buildOfflineError(String lang) {
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return "नेटवर्क कनेक्शन नहीं है। कृपया इंटरनेट चालू करें और फिर कोशिश करें।";
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return "Network connection nahi mil raha hai. Please internet check karke dubara try karein.";
        }
        return "Network unavailable. Please check your internet connection and try again.";
    }

    private String buildSmartSampleReply(String rawMessage, String lang) {
        String lower = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.ROOT).trim();
        String name = preferencesManager.getPreferredName();
        String callName = name.isEmpty() ? "" : (" " + name);
        String prefix = "[Sample Mode — Firebase Required]\n";

        // Time / Date (real device clock)
        if (lower.contains("time") || lower.contains("date") || lower.contains("samay")
                || lower.contains("waqt") || lower.contains("समय") || lower.contains("तारीख")) {
            String nowTime = new SimpleDateFormat("hh:mm a", Locale.getDefault()).format(new Date());
            String nowDate = new SimpleDateFormat("EEEE, dd MMM yyyy", Locale.getDefault()).format(new Date());
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + "अभी का समय " + nowTime + " है और आज " + nowDate + " है।";
            } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                return prefix + "Abhi time " + nowTime + " ho raha hai, aur aaj " + nowDate + " hai.";
            }
            return prefix + "It is currently " + nowTime + " on " + nowDate + ".";
        }

        // Jokes & Humor
        boolean followUpHumor = isFollowUpPhrase(lower) && "humor".equals(conversationMemory.getCurrentTopic());
        if (followUpHumor || lower.contains("joke") || lower.contains("funny") || lower.contains("हंसाओ")
                || lower.contains("चुटकुला") || lower.contains("hasao") || lower.contains("mazak")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + conversationMemory.pickNonRepeating(new String[]{
                        "एक मज़ेदार बात सुनिए: कंप्यूटर को कभी ठंड क्यों नहीं लगती? क्योंकि उसमें हमेशा Windows खुले रहते हैं!",
                        "टीचर: बताओ, चाँद पर पहला कदम किसने रखा? छात्र: सर, नील आर्मस्ट्रांग ने। टीचर: और दूसरा कदम? छात्र: सर, वो भी उन्हीं ने रखा होगा, एक पैर पर थोड़ी नाचते!",
                        "दोस्त: यार मेरा फ़ोन बहुत हैंग होता है। दूसरा दोस्त: तो उसे थोड़ी देर धूप में सुखा ले, शायद थक गया होगा!"
                });
            } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                return prefix + conversationMemory.pickNonRepeating(new String[]{
                        "Sunao ek tech joke: Programmer ne market se 1 litre doodh laya aur bola — ande mile toh 6 le aana. Ande dikhe toh 6 litre doodh le aaya!",
                        "Ek mast joke suno: Wi-Fi aur dost mein kya farq hai? Wi-Fi signal kamzor ho toh gussa aata hai, dost gayab ho toh Wi-Fi ka password maangne zaroor wapas aata hai!",
                        "Ek aur funny line: Computer ko sardi kyun nahi lagti? Kyunki usmein already bahut saare Windows khule hote hain!"
                });
            }
            return prefix + conversationMemory.pickNonRepeating(new String[]{
                    "Why do Java developers wear glasses? Because they don't C sharp. Would you like another one?",
                    "I told my smart speaker to play something relaxing, and it whispered 'Battery at 99%'. Want a tech riddle next?",
                    "Why did the neural network go to therapy? It had too many deep issues and couldn't stop overfitting."
            });
        }

        // Summarization
        if (lower.startsWith("summarize") || lower.contains("summary of") || lower.contains("सारांश")) {
            String body = rawMessage.replaceFirst("(?i)^summarize\\s*:?\\s*", "").trim();
            if (body.length() > 140) {
                body = body.substring(0, 140) + "…";
            }
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + "सारांश (सैंपल): मुख्य बिंदु यह है कि \"" + body + "\"। गहन AI सारांश के लिए Firebase AI Logic सक्षम करें।";
            }
            return prefix + "Sample summary focus: \"" + body + "\". Enable Firebase AI Logic for full multi-paragraph Gemini summarization.";
        }

        // Translation & Language Learning (Hindi / English)
        if (lower.contains("translate") || lower.contains("learn english") || lower.contains("learn hindi")
                || lower.contains("अनुवाद") || lower.contains("अंग्रेजी सीख")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + "अंग्रेज़ी सीखने की टिप: रोज़ 5 छोटे वाक्य बोलकर अभ्यास करें — जैसे 'How is your day going?' (आपका दिन कैसा जा रहा है?)। लाइव अनुवाद के लिए Firebase AI Logic जोड़ें।";
            } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                return prefix + "Language tip: Roz Hindi aur English ke 5 daily phrases practice karein, jaise 'Could you clarify that?' = 'Kya aap ise thoda aur samjha sakte hain?'. Live Gemini translation ke liye Firebase enable karein.";
            }
            return prefix + "Language tip: Try pairing daily English phrases with Hindi equivalents, e.g., 'Good morning' = 'सुप्रभात'. Enable Firebase AI Logic for live full-sentence Gemini translation.";
        }

        // Coding & Programming assistance
        if (lower.contains("code") || lower.contains("java") || lower.contains("python")
                || lower.contains("programming") || lower.contains("bug") || lower.contains("algorithm")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + "कोडिंग सहायता" + callName + ": Java या किसी भी भाषा में साफ कोड लिखने के लिए छोटे फंक्शन बनाएँ, null checks रखें, और लॉजिक को अलग लेयर में रखें। लाइव Gemini कोडिंग उत्तर के लिए Firebase जोड़ें।";
            }
            return prefix + "Coding tip" + callName + ": Keep functions focused on one responsibility, validate null/edge inputs early, and separate UI from data logic. Connect Firebase AI Logic for live Gemini code generation and debugging.";
        }

        // Educational & Ideas
        if (lower.contains("idea") || lower.contains("suggest") || lower.contains("explain")
                || lower.contains("study") || lower.contains("physics") || lower.contains("history")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + "शैक्षिक / विचार सहायता" + callName + ": किसी भी विषय को 3 चरणों में समझें — मूल परिभाषा, रोज़मर्रा का उदाहरण, और अभ्यास प्रश्न। विस्तृत Gemini उत्तरों के लिए AI Studio में Firebase सक्रिय करें।";
            }
            return prefix + "Study & Idea framework" + callName + ": Break \"" + rawMessage + "\" into core principle, real-world analogy, and one actionable next step. Enable Firebase AI Logic for full Gemini reasoning.";
        }

        // Thank you / Casual appreciation
        if (lower.matches(".*\\b(thank you|thanks|shukriya|dhanyavaad|dhanyawad|nice|awesome|great)\\b.*")
                || lower.contains("धन्यवाद") || lower.contains("शुक्रिया")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + conversationMemory.pickNonRepeating(new String[]{
                        "आपका स्वागत है" + callName + "! जब भी कोई मदद चाहिए, मुझे बताइए।",
                        "यह तो मेरा काम है" + callName + "। बताइए, आगे मैं आपकी क्या मदद कर सकती हूँ?"
                });
            } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                return prefix + conversationMemory.pickNonRepeating(new String[]{
                        "Most welcome" + callName + "! Bataiye aur kya madad kar sakti hoon?",
                        "Arre koi baat nahi" + callName + "! Main hamesha aapki help ke liye ready hoon."
                });
            }
            return prefix + conversationMemory.pickNonRepeating(new String[]{
                    "Always at your service" + callName + ". What shall we work on next?",
                    "You're very welcome" + callName + ". Standing by whenever you need me."
            });
        }

        // Identity / Capabilities
        if (lower.contains("who are you") || lower.contains("what can you do")
                || lower.contains("कौन हो") || lower.contains("क्या कर सकते") || lower.contains("क्या कर सकती")
                || lower.contains("kaun ho") || lower.contains("kya kar sakte") || lower.contains("kya kar sakti")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + "मैं SUBH AI हूँ, आपकी JARVIS-प्रेरित निजी असिस्टेंट। मैं मौसम, समाचार, गणित, यूनिट कन्वर्ज़न, नोट्स, रिमाइंडर, YouTube, Google सर्च और फ़ोन ऐप्स चलाने में मदद कर सकती हूँ। लाइव Gemini AI के लिए AI Studio में Firebase सक्षम करें।";
            } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                return prefix + "Main SUBH AI hoon — aapki JARVIS-style personal assistant! Main live weather, news, math, notes, reminders, apps, YouTube aur Google search chala sakti hoon. Live Gemini AI ke liye Firebase enable karein.";
            }
            return prefix + "I am SUBH AI, your JARVIS-inspired personal assistant" + callName
                    + ". I handle live weather, news headlines, math & unit conversions, notes, reminders, installed apps, YouTube, Google Search, and Hindi/English/Hinglish voice chats. Enable Firebase in AI Studio to connect live Gemini AI.";
        }

        // Greetings / Casual daily conversation
        if (lower.matches(".*\\b(hi|hello|hey|namaste|namaskar|kaise ho|kaisi ho|how are you|kya haal)\\b.*")
                || lower.contains("नमस्ते") || lower.contains("कैसे हो") || lower.contains("कैसी हो")) {
            if (LanguageUtils.LANG_HINDI.equals(lang)) {
                return prefix + conversationMemory.pickNonRepeating(new String[]{
                        "नमस्ते" + callName + "! मैं बिल्कुल ठीक और तैयार हूँ। बताइए, आज मैं आपकी क्या मदद कर सकती हूँ?",
                        "बहुत बढ़िया हूँ" + callName + "! आज मौसम, समाचार, गणित या कोई ऐप खोलने में मदद करूँ?"
                });
            } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                return prefix + conversationMemory.pickNonRepeating(new String[]{
                        "Hey" + callName + "! Main bilkul mast hoon. Boliye aaj kya kaam karna hai?",
                        "Namaste" + callName + "! Sab systems ready hain. Weather check karein ya koi app kholein?"
                });
            }
            return prefix + conversationMemory.pickNonRepeating(new String[]{
                    "At your service" + callName + ". All voice and assistant systems are online. How can I assist you today?",
                    "Hello" + callName + "! Standing by and ready. Would you like live weather, news, math help, or a voice command?"
            });
        }

        // Follow-up / Previous topic recall across topic changes
        if (isFollowUpPhrase(lower) || lower.contains("earlier") || lower.contains("before that")) {
            String topic = lower.contains("previous") || lower.contains("earlier") || lower.contains("before")
                    ? (!conversationMemory.getPreviousTopic().isEmpty()
                            ? conversationMemory.getPreviousTopic()
                            : conversationMemory.getCurrentTopic())
                    : conversationMemory.getCurrentTopic();
            if (!topic.isEmpty()) {
                if (LanguageUtils.LANG_HINDI.equals(lang)) {
                    return prefix + "हम अभी \"" + topic + "\" पर बात कर रहे थे। लाइव Gemini AI जुड़ने पर मैं इस विषय पर गहराई से विस्तार दे सकूँगी।";
                } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
                    return prefix + "Hum \"" + topic + "\" ke baare mein baat kar rahe the. Firebase AI Logic connect hone par main is par detailed Gemini jawab de paungi.";
                }
                return prefix + "We were discussing \"" + topic + "\". Once Firebase AI Logic is enabled, I can explore that follow-up in full depth with Gemini.";
            }
        }

        // General intelligent sample response
        if (LanguageUtils.LANG_HINDI.equals(lang)) {
            return prefix + conversationMemory.pickNonRepeating(new String[]{
                    "आपका प्रश्न \"" + rawMessage + "\" समझ लिया है" + callName + "। यह सैंपल रिस्पॉन्स है — लाइव Gemini AI से उत्तर पाने के लिए AI Studio में Firebase जोड़ें। क्या आप चाहेंगे कि मैं इसे Google पर खोजूँ?",
                    "बहुत अच्छा सवाल है" + callName + "! फिलहाल मैं सैंपल AI मोड में हूँ, लेकिन मौसम, समाचार, गणित, नोट्स और ऐप कमांड पूरी तरह सक्रिय हैं।"
            });
        } else if (LanguageUtils.LANG_HINGLISH.equals(lang)) {
            return prefix + conversationMemory.pickNonRepeating(new String[]{
                    "Samajh gayi" + callName + "! Aapne pucha: \"" + rawMessage + "\". Abhi main Sample AI Mode mein hoon — live Gemini AI ke liye AI Studio mein Firebase enable karein. Kya main ise Google par search karun?",
                    "Badhiya sawaal hai" + callName + "! Live Gemini AI ke liye Firebase chahiye, tab tak aap weather, news, math, notes aur app commands use kar sakte hain."
            });
        }
        return prefix + conversationMemory.pickNonRepeating(new String[]{
                "I heard \"" + rawMessage + "\"" + callName + ". Live Gemini responses require enabling Firebase AI Logic in AI Studio. Would you like me to search Google for this instead?",
                "Understood" + callName + ". I am operating in Sample AI Mode while Firebase is disabled, though weather, news, math, notes, and voice commands are fully active."
        });
    }
}
