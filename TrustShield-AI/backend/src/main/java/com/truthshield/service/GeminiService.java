package com.truthshield.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.*;

@Service
public class GeminiService {

    @Value("${gemini.api.key:}")
    private String apiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> analyzeContent(String headline, String content, String sourceUrl) {
        System.out.println("STEP 1: analyzeContent started");
        String effectiveKey = apiKey;
        if (effectiveKey == null || effectiveKey.isEmpty() || effectiveKey.equals("MY_GEMINI_API_KEY")) {
            effectiveKey = System.getenv("GEMINI_API_KEY");
        }
        System.out.println("STEP 2: API KEY FOUND = " +
    (effectiveKey != null && !effectiveKey.isEmpty()));

        if (effectiveKey == null || effectiveKey.isEmpty() || effectiveKey.equals("MY_GEMINI_API_KEY")) {
            // Return fallback heuristic map if api key is missing to keep service robust
            return getHeuristicFallback(headline, content, sourceUrl);
        }

        try {
            String url =
"https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent?key="
+ effectiveKey;
System.out.println("STEP 3: Sending request to Gemini");
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            String systemPrompt = """
You are Truth Shield AI.

Analyze misinformation, manipulation, clickbait,
medical misinformation, fake claims and bias.

You MUST return ONLY valid JSON.

Required JSON format:

{
  "fakeProbabilityScore": 0,
  "trustLevel": "Trustworthy",
  "explanation": [
    "point 1",
    "point 2",
    "point 3"
  ],
  "riskIndicators": [
    "Clickbait",
    "Emotional Manipulation"
  ],
  "suspiciousPhrases": [
    {
      "phrase": "shocking",
      "reason": "Uses emotional manipulation and sensationalism."
    },
    {
      "phrase": "share immediately",
      "reason": "Creates urgency without evidence."
    }
  ],
  "recommendedAction": "Verify using trusted scientific sources.",
  "referenceLinks": [
    {
      "title": "Snopes",
      "url": "https://www.snopes.com"
    },
    {
      "title": "FactCheck",
      "url": "https://www.factcheck.org"
    }
  ],
  "categoryPercentages": {
    "clickbait": 0,
    "emotionalManipulation": 0,
    "bias": 0,
    "exaggeration": 0
  }
}

IMPORTANT:
1. explanation MUST be array points.
2. suspiciousPhrases MUST contain exact phrases from article text.
3. include referenceLinks.
4. return ONLY JSON.
5. no markdown.
6. no paragraph explanation.
""";

            String userPrompt = String.format("Headline: %s\nText: %s\nSource URL: %s", headline, content, sourceUrl != null ? sourceUrl : "None");

            // Build request matches the official Google Gemini endpoint schema: { contents: [{ parts: [{ text: "..." }] }], generationConfig: { responseMimeType: "application/json" } }
            Map<String, Object> requestBody = new HashMap<>();
            
            Map<String, Object> part = new HashMap<>();
            part.put("text", systemPrompt + "\n\nUser Input to evaluate:\n" + userPrompt);
            
            Map<String, Object> contentMap = new HashMap<>();
            contentMap.put("parts", Collections.singletonList(part));
            
            requestBody.put("contents", Collections.singletonList(contentMap));

            Map<String, Object> generationConfig = new HashMap<>();
            generationConfig.put("responseMimeType", "application/json");
            generationConfig.put("temperature", 0.1);
            requestBody.put("generationConfig", generationConfig);

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);
            System.out.println("GEMINI RESPONSE:");
System.out.println(response.getBody());
            System.out.println("STEP 4: Gemini responded");

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                System.out.println("STEP 5: Parsing response");
                Map body = response.getBody();
                List candidates = (List) body.get("candidates");
                if (candidates != null && !candidates.isEmpty()) {
                    Map firstCandidate = (Map) candidates.get(0);
                    Map contentObj = (Map) firstCandidate.get("content");
                    if (contentObj != null) {
                        List parts = (List) contentObj.get("parts");
                        if (parts != null && !parts.isEmpty()) {
                            Map firstPart = (Map) parts.get(0);
                            String textResponse = (String) firstPart.get("text");
                            if (textResponse != null) {
                                com.fasterxml.jackson.databind.ObjectMapper mapper =
        new com.fasterxml.jackson.databind.ObjectMapper();

Map<String, Object> aiResult =
        mapper.readValue(textResponse.trim(), Map.class);

// Create final result FIRST
Map<String, Object> finalResult =
        new HashMap<>();

// Fake Score
Integer fakeScore = 50;

Object scoreObj =
        aiResult.get("fakeProbabilityScore");

if (scoreObj instanceof Integer) {
    fakeScore = (Integer) scoreObj;

} else if (scoreObj instanceof Double) {
    fakeScore =
            ((Double) scoreObj).intValue();
}

finalResult.put(
        "fakeProbabilityScore",
        fakeScore
);

// Trust Level
String trustLevel =
        (String) aiResult.getOrDefault(
                "trustLevel",
                fakeScore <= 35
                        ? "Trustworthy"
                        : fakeScore <= 75
                        ? "Suspicious"
                        : "Dangerous"
        );

finalResult.put(
        "trustLevel",
        trustLevel
);

// Explanation points
Object explanationObj =
        aiResult.get("explanation");

List<String> explanationList =
        new ArrayList<>();

if (explanationObj instanceof List<?>) {

    for (Object item :
            (List<?>) explanationObj) {

        explanationList.add(
                String.valueOf(item)
        );
    }

} else if (
        explanationObj instanceof String
) {

    explanationList.add(
            explanationObj.toString()
    );
}

finalResult.put(
        "explanation",
        explanationList
);

// Risk Indicators
finalResult.put(
        "riskIndicators",
        aiResult.getOrDefault(
                "riskIndicators",
                new ArrayList<>()
        )
);

// Suspicious Phrases
finalResult.put(
        "suspiciousPhrases",
        aiResult.getOrDefault(
                "suspiciousPhrases",
                new ArrayList<>()
        )
);

// Recommended Action
finalResult.put(
        "recommendedAction",
        aiResult.getOrDefault(
                "recommendedAction",
                "Verify using trusted sources."
        )
);

// Reference Links
finalResult.put(
        "referenceLinks",
        aiResult.getOrDefault(
                "referenceLinks",
                new ArrayList<>()
        )
);

// Graph Categories
Object categoryObj =
        aiResult.get("categoryPercentages");

Map<String, Integer> categories =
        new HashMap<>();

if (categoryObj instanceof Map<?, ?> rawMap) {

    categories.put(
            "clickbait",
            parseInt(rawMap.get("clickbait"), 50)
    );

    categories.put(
            "emotionalManipulation",
            parseInt(rawMap.get(
                    "emotionalManipulation"), 50)
    );

    categories.put(
            "bias",
            parseInt(rawMap.get("bias"), 50)
    );

    categories.put(
            "exaggeration",
            parseInt(rawMap.get("exaggeration"), 50)
    );
} else {

    categories.put("clickbait", 50);
    categories.put("emotionalManipulation", 50);
    categories.put("bias", 50);
    categories.put("exaggeration", 50);
}

finalResult.put(
        "categoryPercentages",
        categories
);

return finalResult;
}
                        }
                    }
                }
            }
        } catch (Exception e) {
            System.out.println("GEMINI FAILED:");
e.printStackTrace();
        }

        return getHeuristicFallback(headline, content, sourceUrl);
    }

    private Map<String, Object> getHeuristicFallback(String headline, String content, String sourceUrl) {
        int score = 25;
        String combined = (headline + " " + content).toLowerCase();
        
        List<Map<String, String>> suspiciousPhrases = new ArrayList<>();
        if (combined.contains("breaking") || combined.contains("shocking")) {
            score += 30;
            Map<String, String> phrase = new HashMap<>();
            phrase.put("phrase", combined.contains("breaking") ? "BREAKING" : "SHOCKING");
            phrase.put("reason", "Highly sensationalized attention triggers designed to spark immediate panic.");
            suspiciousPhrases.add(phrase);
        }
        
        if (combined.contains("must share") || combined.contains("urgent")) {
            score += 25;
            Map<String, String> phrase = new HashMap<>();
            phrase.put("phrase", combined.contains("urgent") ? "URGENT" : "MUST SHARE");
            phrase.put("reason", "Pressures readers to react immediately, blocking critical evaluation.");
            suspiciousPhrases.add(phrase);
        }

        int finalScore = Math.min(95, Math.max(8, score));
        String trustLevel = finalScore < 35 ? "Trustworthy" : finalScore < 75 ? "Suspicious" : "Dangerous";

        Map<String, Object> fallback = new HashMap<>();
        fallback.put("fakeProbabilityScore", finalScore);
        fallback.put("trustLevel", trustLevel);
        fallback.put(
    "explanation",
    Arrays.asList(
        "Heuristic warning: clickbait or emotionally manipulative wording detected.",
        "Urgency-based language may influence reader judgement.",
        "Cross-check this claim using trusted fact-checking websites."
    )
);
        fallback.put("riskIndicators", Arrays.asList("Clickbait Headline", "Emotional Manipulation"));
        fallback.put("suspiciousPhrases", suspiciousPhrases);
        fallback.put("recommendedAction", "Verify from regional global journals before sharing with household peers.");

        Map<String, Integer> categoryPercentages = new HashMap<>();
        categoryPercentages.put("clickbait", Math.min(100, finalScore + 10));
        categoryPercentages.put("emotionalManipulation", Math.min(100, finalScore));
        categoryPercentages.put("bias", Math.min(100, finalScore - 10));
        categoryPercentages.put("exaggeration", Math.min(100, finalScore + 5));
        fallback.put("categoryPercentages", categoryPercentages);

       return fallback;
    }

    private Integer parseInt(
            Object value,
            Integer fallback
    ) {

        try {

            if (value instanceof Integer i) {
                return i;
            }

            if (value instanceof Double d) {
                return d.intValue();
            }

            if (value instanceof String s) {
                return Integer.parseInt(s);
            }

        } catch (Exception ignored) {}

        return fallback;
    }
}