package com.innercosmos.controller;

import com.innercosmos.ai.client.LlmClient;
import com.innercosmos.ai.client.LlmRequest;
import com.innercosmos.asr.AsrClient;
import com.innercosmos.asr.AsrResult;
import com.innercosmos.common.ApiResponse;
import com.innercosmos.common.ErrorCode;
import com.innercosmos.entity.VoiceTranscription;
import com.innercosmos.exception.BusinessException;
import com.innercosmos.service.VoiceTranscriptionService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;

@RestController
@RequestMapping("/api/diary")
public class DiaryController extends BaseController {
    private final AsrClient asrClient;
    private final VoiceTranscriptionService transcriptionService;
    private final LlmClient llmClient;

    public DiaryController(AsrClient asrClient,
                           VoiceTranscriptionService transcriptionService,
                           LlmClient llmClient) {
        this.asrClient = asrClient;
        this.transcriptionService = transcriptionService;
        this.llmClient = llmClient;
    }

    @PostMapping("/transcribe")
    public ApiResponse<VoiceTranscription> transcribe(@RequestBody Map<String, String> body, HttpSession session) {
        Long userId = currentUserId(session);
        String text = body.getOrDefault("text", "");
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日记原文不能为空");
        }
        AsrResult asr = asrClient.transcribe(text.getBytes(StandardCharsets.UTF_8), text);
        return ApiResponse.ok(transcriptionService.create(userId, text, asr, "DIARY"));
    }

    @PostMapping("/transcribe-audio")
    public ApiResponse<VoiceTranscription> transcribeAudio(@org.springframework.web.bind.annotation.RequestParam("file") MultipartFile file,
                                                           HttpSession session) throws IOException {
        Long userId = currentUserId(session);
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "音频文件不能为空");
        }
        if (file.getSize() > 25L * 1024L * 1024L) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "音频文件不能超过 25MB");
        }

        AsrResult asr = asrClient.transcribe(file.getBytes(), "");
        String text = asr.text == null ? "" : asr.text.trim();
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "没有识别到可用文字，请靠近麦克风再试一次");
        }
        return ApiResponse.ok(transcriptionService.create(userId, text, asr, "DIARY_AUDIO"));
    }

    @PostMapping("/polish")
    public ApiResponse<Map<String, String>> polish(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = currentUserId(session);
        String text = body.get("text") == null ? "" : body.get("text").toString();
        int level = body.get("level") == null ? 2 : Integer.parseInt(body.get("level").toString());
        if (text.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日记内容不能为空");
        }
        if (level < 1 || level > 3) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "润色档位只能是 1、2 或 3");
        }

        LlmRequest request = new LlmRequest(userId, "DIARY_POLISH_LEVEL_" + level, polishPrompt(level, text));
        String polished = llmClient.chat(request);
        if (polished == null || polished.isBlank()) {
            polished = fallbackPolish(level, text);
        }
        polished = cleanPolishOutput(polished, text);
        if (polished.isBlank()) {
            polished = fallbackPolish(level, text);
        }
        return ApiResponse.ok(Map.of("polishedText", polished.trim()));
    }

    @PostMapping("/{id}/analyze")
    public ApiResponse<Map<String, String>> analyze(@PathVariable Long id, HttpSession session) {
        Long userId = currentUserId(session);
        VoiceTranscription vt = transcriptionService.getOwned(id, userId);
        String text = vt.editedText == null || vt.editedText.isBlank() ? vt.originalText : vt.editedText;
        String raw = llmClient.chat(new LlmRequest(userId, "DIARY_ANALYZE", analyzePrompt(text)));
        return ApiResponse.ok(Map.of(
                "analysis", raw == null ? "" : raw.trim(),
                "status", vt.status == null ? "RAW" : vt.status,
                "source", "MiniMax"
        ));
    }

    @PostMapping("/submit")
    public ApiResponse<Boolean> submit(@RequestBody Map<String, Object> body, HttpSession session) {
        Long userId = currentUserId(session);
        if (body.get("id") == null) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "缺少日记转写 ID");
        }
        Long id = Long.valueOf(body.get("id").toString());
        String finalContent = body.get("content") == null ? "" : body.get("content").toString();
        if (finalContent.isBlank()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "日记内容不能为空");
        }

        transcriptionService.submitFinal(id, userId, finalContent);
        return ApiResponse.ok(true);
    }

    private String polishPrompt(int level, String text) {
        String mode = switch (level) {
            case 1 -> "轻度纠偏：尽可能保留用户原话、语气、顺序和表达习惯，只去掉明显口水词、重复停顿和语音识别错字。";
            case 2 -> "均衡整理：尊重原意，同时让表达更有逻辑、更清楚、更适合回看；可以调整句序和合并重复表达。";
            case 3 -> "深度整理：在尊重原意的前提下，大幅提升可读性和结构感；可以重新分段、精简口水词、重组表达顺序。";
            default -> "";
        };
        return """
                你是 Inner Cosmos 的心声日记润色助手。
                %s
                不要添加用户没有说过的新事实，不要诊断，不要鸡汤化。
                只返回润色后的正文，不要解释。

                原文：
                %s
                """.formatted(mode, text);
    }

    private String analyzePrompt(String text) {
        return """
                你是 Inner Cosmos 的日记理解助手。请基于下面这段心声日记，输出简洁中文 JSON：
                {
                  "emotionWeather": "今日情绪天气",
                  "dailyTheme": "今日主题",
                  "auroraObservation": "Aurora 像朋友一样注意到的观察",
                  "memoryLens": "值得沉淀成记忆的线索",
                  "todoClue": "可能的一步行动，可留空"
                }
                不要诊断，不要夸大，不要添加新事实。

                日记：
                %s
                """.formatted(text);
    }

    private String fallbackPolish(int level, String text) {
        String cleaned = text.replaceAll("\\s+", " ").trim();
        if (level == 1) return cleaned;
        if (level == 2) return cleaned.replace("。", "。\n");
        return "今天的心声：\n\n" + cleaned.replace("。", "。\n\n");
    }

    private String cleanPolishOutput(String raw, String originalText) {
        if (raw == null) return "";
        String text = raw.trim();
        text = text.replaceAll("(?is)<think>.*?</think>", "").trim();
        text = text.replaceAll("(?is)<analysis>.*?</analysis>", "").trim();
        text = text.replaceAll("(?is)^```(?:json|markdown|text)?\\s*", "")
                .replaceAll("(?is)```\\s*$", "")
                .trim();

        java.util.regex.Matcher jsonMatcher = java.util.regex.Pattern
                .compile("(?is)\"(?:polishedText|polished|result|content|text)\"\\s*:\\s*\"((?:\\\\.|[^\"\\\\])*)\"")
                .matcher(text);
        String lastJsonValue = null;
        while (jsonMatcher.find()) {
            lastJsonValue = jsonMatcher.group(1);
        }
        if (lastJsonValue != null) {
            text = lastJsonValue
                    .replace("\\n", "\n")
                    .replace("\\\"", "\"")
                    .replace("\\\\", "\\")
                    .trim();
        }

        for (String marker : new String[]{
                "润色后的正文：", "润色后正文：", "最终正文：", "最终结果：", "输出：", "正文：",
                "polishedText:", "result:", "content:"
        }) {
            int index = text.lastIndexOf(marker);
            if (index >= 0) {
                text = text.substring(index + marker.length()).trim();
            }
        }

        StringBuilder cleaned = new StringBuilder();
        for (String line : text.split("\\R")) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                if (!cleaned.isEmpty() && cleaned.charAt(cleaned.length() - 1) != '\n') cleaned.append('\n');
                continue;
            }
            if (looksLikePromptLeak(trimmed, originalText)) continue;
            cleaned.append(line.stripTrailing()).append('\n');
        }
        return cleaned.toString()
                .replaceAll("(?m)^\\s*[-*]\\s*(只返回|不要|原文|任务|要求).*$", "")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private boolean looksLikePromptLeak(String line, String originalText) {
        String original = originalText == null ? "" : originalText.trim();
        if (line.startsWith("你是 Inner Cosmos") || line.startsWith("你是Inner Cosmos")) return true;
        if (line.startsWith("只返回") || line.startsWith("不要解释") || line.startsWith("不要添加")) return true;
        if (line.startsWith("原文：") || line.startsWith("用户原文：") || (!original.isBlank() && line.equals(original))) return true;
        if (line.contains("润色助手") || line.contains("档位") || line.toLowerCase().contains("prompt")) return true;
        return line.startsWith("{") || line.endsWith("}");
    }
}
