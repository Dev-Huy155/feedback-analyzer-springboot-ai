package com.huy.feedbackai.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huy.feedbackai.dto.FeedbackRequest;
import com.huy.feedbackai.model.Feedback;
import com.huy.feedbackai.repository.FeedbackRepository;

@Service
public class FeedbackService {

	private final FeedbackRepository repository;
	private final GeminiService geminiService;
	private final ObjectMapper objectMapper = new ObjectMapper();

	// Spring tự "tiêm" (inject) các dependency qua constructor
	public FeedbackService(FeedbackRepository repository, GeminiService geminiService) {
		this.repository = repository;
		this.geminiService = geminiService;
	}

	public Feedback analyzeAndSave(FeedbackRequest req) {
		// 1. Yêu cầu AI trả về JSON có cấu trúc (structured output)
		String prompt = """
				Bạn là công cụ phân tích phản hồi khách hàng.
				Phân tích đoạn phản hồi dưới đây và CHỈ trả về JSON đúng định dạng,
				không thêm giải thích, không thêm dấu ```:
				{"sentiment": "POSITIVE hoặc NEGATIVE hoặc NEUTRAL", "summary": "một câu tóm tắt ngắn"}

				Phản hồi: "%s"
				""".formatted(req.getContent());

		String aiRaw = geminiService.generate(prompt);

		// 2. Dọn dẹp (phòng khi AI lỡ bọc trong ```json ... ```)
		String cleaned = aiRaw.replace("```json", "").replace("```", "").trim();

		String sentiment = "UNKNOWN";
		String summary = "";
		try {
			JsonNode node = objectMapper.readTree(cleaned);
			sentiment = node.path("sentiment").asText("UNKNOWN");
			summary = node.path("summary").asText("");
		} catch (Exception e) {
			summary = "Không parse được kết quả AI: " + aiRaw;
		}

		// 3. Lưu vào DB
		Feedback fb = new Feedback();
		fb.setCustomerName(req.getCustomerName());
		fb.setContent(req.getContent());
		fb.setSentiment(sentiment);
		fb.setSummary(summary);
		return repository.save(fb);
	}

	public List<Feedback> getAll() {
		return repository.findAll();
	}

	public Feedback getById(Long id) {
		return repository.findById(id).orElse(null);
	}

	public void delete(Long id) {
		repository.deleteById(id);
	}

	// Thống kê số lượng theo từng sentiment
	public Map<String, Long> stats() {
		return repository.findAll().stream()
				.collect(Collectors.groupingBy(Feedback::getSentiment, Collectors.counting()));
	}

	// Nâng cao: hỏi AI dựa trên toàn bộ feedback đã lưu
	public String ask(String question) {
		String allFeedback = repository.findAll().stream().map(f -> "- " + f.getContent())
				.collect(Collectors.joining("\n"));

		String prompt = """
				Dưới đây là danh sách phản hồi khách hàng:
				%s

				Dựa trên các phản hồi trên, hãy trả lời câu hỏi sau bằng tiếng Việt, ngắn gọn:
				%s
				""".formatted(allFeedback, question);

		return geminiService.generate(prompt);
	}
}