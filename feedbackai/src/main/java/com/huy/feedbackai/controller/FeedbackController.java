package com.huy.feedbackai.controller;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.huy.feedbackai.dto.AskRequest;
import com.huy.feedbackai.dto.FeedbackRequest;
import com.huy.feedbackai.model.Feedback;
import com.huy.feedbackai.service.FeedbackService;

@RestController
@RequestMapping("/api/feedbacks")
@CrossOrigin // cho phép frontend gọi (nếu sau này bạn thêm giao diện)
public class FeedbackController {

	private final FeedbackService service;

	public FeedbackController(FeedbackService service) {
		this.service = service;
	}

	// Tạo feedback mới + AI phân tích
	@PostMapping
	public Feedback create(@RequestBody FeedbackRequest req) {
		return service.analyzeAndSave(req);
	}

	@GetMapping
	public List<Feedback> getAll() {
		return service.getAll();
	}

	@GetMapping("/{id}")
	public Feedback getById(@PathVariable Long id) {
		return service.getById(id);
	}

	@DeleteMapping("/{id}")
	public String delete(@PathVariable Long id) {
		service.delete(id);
		return "Đã xóa feedback id=" + id;
	}

	@GetMapping("/stats")
	public Map<String, Long> stats() {
		return service.stats();
	}

	@PostMapping("/ask")
	public Map<String, String> ask(@RequestBody AskRequest req) {
		return Map.of("answer", service.ask(req.getQuestion()));
	}
}