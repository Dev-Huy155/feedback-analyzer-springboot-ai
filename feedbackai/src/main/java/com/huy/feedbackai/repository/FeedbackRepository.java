package com.huy.feedbackai.repository;

import com.huy.feedbackai.model.Feedback;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    List<Feedback> findBySentiment(String sentiment);
}
