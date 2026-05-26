package com.innercosmos.event;

import com.innercosmos.entity.DialogMessage;
import com.innercosmos.entity.TodoItem;
import com.innercosmos.mapper.DialogMessageMapper;
import com.innercosmos.mapper.TodoItemMapper;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class TodoExtractListener {
    private final DialogMessageMapper messageMapper;
    private final TodoItemMapper todoItemMapper;

    public TodoExtractListener(DialogMessageMapper messageMapper, TodoItemMapper todoItemMapper) {
        this.messageMapper = messageMapper;
        this.todoItemMapper = todoItemMapper;
    }

    private static final String[] TODO_KEYWORDS = {"作业", "考试", "截止", "提交", "复习", "完成", "计划", "打算", "准备"};

    @EventListener
    @Async("taskExecutor")
    public void onDialogFinished(DialogFinishedEvent event) {
        try {
            List<DialogMessage> messages = messageMapper.selectList(
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<DialogMessage>()
                            .eq("session_id", event.sessionId)
                            .eq("speaker", "USER")
                            .isNotNull("text_content"));
            for (DialogMessage msg : messages) {
                if (msg.textContent == null) continue;
                for (String keyword : TODO_KEYWORDS) {
                    if (msg.textContent.contains(keyword)) {
                        String sentence = extractSentence(msg.textContent, keyword);
                        if (sentence != null && !sentence.isBlank()) {
                            TodoItem todo = new TodoItem();
                            todo.userId = event.userId;
                            todo.taskName = sentence.length() > 60 ? sentence.substring(0, 60) + "..." : sentence;
                            todo.description = "从对话中自动提取";
                            todo.priority = "MEDIUM";
                            todo.status = "TODO";
                            todo.createdAt = LocalDateTime.now();
                            todo.updatedAt = LocalDateTime.now();
                            todoItemMapper.insert(todo);
                            break;
                        }
                    }
                }
            }
        } catch (Exception ignored) {
        }
    }

    private String extractSentence(String text, String keyword) {
        int idx = text.indexOf(keyword);
        if (idx < 0) return null;
        int start = Math.max(0, text.lastIndexOf('。', idx) + 1);
        int end = text.indexOf('。', idx);
        if (end < 0) end = text.indexOf('，', idx);
        if (end < 0) end = Math.min(text.length(), idx + 40);
        return text.substring(start, end).trim();
    }
}
