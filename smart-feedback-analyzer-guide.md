# Smart Feedback Analyzer — Hướng dẫn xây dựng (Java + Spring Boot + AI)

> Dự án nhỏ để chứng minh **Java / Spring Boot** + **kỹ năng dùng AI** cho hồ sơ thực tập.
> Thời gian ước tính: **1–2 tuần** (khoảng 2–3 tiếng/ngày).

---

## 1. Dự án này làm gì?

Một REST API backend cho phép:
- Gửi lên một **phản hồi khách hàng** (feedback / review).
- AI **tự động phân tích**: gán *sentiment* (POSITIVE / NEGATIVE / NEUTRAL) và viết một câu **tóm tắt**.
- Lưu vào database, cho phép xem danh sách, xem chi tiết, xóa.
- (Nâng cao) Thống kê số lượng theo sentiment.
- (Nâng cao) Hỏi một câu và AI trả lời dựa trên **toàn bộ** feedback đã lưu.

**Luồng cốt lõi:** `Client gửi review → Backend gọi AI → AI trả về sentiment + summary → Lưu DB → Trả kết quả`

---

## 2. Bạn sẽ học được gì (và kể được gì khi phỏng vấn)

| Kỹ năng | Xuất hiện ở đâu trong dự án |
|---|---|
| REST API design | Các endpoint `/api/feedbacks` |
| Kiến trúc phân tầng | Controller → Service → Repository |
| Database với JPA/Hibernate | Entity `Feedback`, `JpaRepository` |
| Gọi API bên ngoài từ Java | `GeminiService` gọi HTTP tới Gemini |
| Xử lý JSON | Parse response của AI bằng Jackson |
| Dùng AI có mục đích | Prompt engineering để lấy output có cấu trúc |
| Cấu hình & bảo mật key | API key để trong biến môi trường, không hard-code |

**Câu chốt để kể:** *"Em xây một backend nhận phản hồi khách hàng và dùng AI để tự động phân loại cảm xúc và tóm tắt, giúp team đọc hàng nghìn review mà không phải đọc thủ công."*

---

## 3. Công nghệ

- **Java 17** (hoặc mới hơn)
- **Spring Boot 3.x** — Web, Data JPA
- **H2 Database** (database in-memory, KHÔNG cần cài đặt gì — hoàn hảo để bắt đầu nhanh)
- **Google Gemini API** (free tier)
- **Maven** để quản lý thư viện
- **Postman** để test API

> Sau khi chạy được với H2, bạn có thể nâng cấp sang **MySQL** để CV "mạnh" hơn (mình có ghi cách ở cuối).

---

## 4. Chuẩn bị

### 4.1. Cài đặt
- **JDK 17+**: kiểm tra bằng `java -version`
- **IntelliJ IDEA Community** (miễn phí) hoặc Spring Tool Suite / VS Code
- **Postman** (test API)

### 4.2. Lấy Gemini API Key (miễn phí, không cần thẻ)
1. Vào **Google AI Studio**: https://aistudio.google.com
2. Đăng nhập bằng tài khoản Google.
3. Bấm **"Get API key"** → **Create API key**.
4. Copy key, lưu lại (dạng `AIza...`).

> ⚠️ **Không bao giờ** commit API key lên GitHub. Ta sẽ để nó trong biến môi trường.

### 4.3. Tạo project Spring Boot
Vào **https://start.spring.io** và chọn:
- Project: **Maven**
- Language: **Java**
- Spring Boot: **3.x** (bản mới nhất ổn định)
- Group: `com.huy` — Artifact: `feedbackai`
- Java: **17**
- Dependencies (bấm ADD DEPENDENCIES): **Spring Web**, **Spring Data JPA**, **H2 Database**, **Lombok**

Bấm **GENERATE**, giải nén, mở bằng IntelliJ.

---

## 5. Cấu trúc thư mục

```
src/main/java/com/huy/feedbackai/
├── FeedbackaiApplication.java      (đã có sẵn — điểm khởi động)
├── model/
│   └── Feedback.java               (Entity — bảng trong DB)
├── repository/
│   └── FeedbackRepository.java     (truy vấn DB)
├── service/
│   ├── GeminiService.java          (gọi AI)
│   └── FeedbackService.java        (logic nghiệp vụ)
├── controller/
│   └── FeedbackController.java     (nhận HTTP request)
└── dto/
    ├── FeedbackRequest.java        (dữ liệu client gửi vào)
    └── AskRequest.java             (câu hỏi ở tính năng nâng cao)
```

Tạo các package (`model`, `repository`, `service`, `controller`, `dto`) bằng cách chuột phải vào `com.huy.feedbackai` → New → Package.

---

## 6. Cấu hình

### `src/main/resources/application.properties`
```properties
# H2 in-memory database
spring.datasource.url=jdbc:h2:mem:feedbackdb
spring.datasource.driverClassName=org.h2.Driver
spring.datasource.username=sa
spring.datasource.password=
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true

# Bật H2 console để xem dữ liệu qua trình duyệt: http://localhost:8080/h2-console
spring.h2.console.enabled=true

# Gemini API key — đọc từ biến môi trường (an toàn)
gemini.api.key=${GEMINI_API_KEY}
gemini.api.url=https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent
```

### Đặt biến môi trường `GEMINI_API_KEY`
**Trong IntelliJ:** Run → Edit Configurations → chọn app → mục *Environment variables* → thêm:
```
GEMINI_API_KEY=AIza...(key của bạn)
```

---

## 7. Viết code từng lớp

### 7.1. Entity — `model/Feedback.java`
Đây là một dòng dữ liệu trong bảng `feedback`.

```java
package com.huy.feedbackai.model;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Entity
@Data                       // Lombok tự sinh getter/setter/toString
public class Feedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String customerName;

    @Column(length = 2000)
    private String content;         // nội dung review gốc

    private String sentiment;       // POSITIVE / NEGATIVE / NEUTRAL (do AI gán)

    @Column(length = 1000)
    private String summary;         // tóm tắt (do AI viết)

    private LocalDateTime createdAt = LocalDateTime.now();
}
```

> **Hỏi phỏng vấn có thể gặp:** "@Entity, @Id, @GeneratedValue để làm gì?" → Entity ánh xạ class thành bảng; @Id là khóa chính; @GeneratedValue để DB tự tăng id.

### 7.2. Repository — `repository/FeedbackRepository.java`
Chỉ cần kế thừa `JpaRepository`, Spring tự sinh sẵn các hàm CRUD.

```java
package com.huy.feedbackai.repository;

import com.huy.feedbackai.model.Feedback;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface FeedbackRepository extends JpaRepository<Feedback, Long> {
    // Spring tự hiểu tên hàm và sinh query cho tính năng thống kê
    List<Feedback> findBySentiment(String sentiment);
}
```

### 7.3. DTO — dữ liệu client gửi lên
`dto/FeedbackRequest.java`
```java
package com.huy.feedbackai.dto;

import lombok.Data;

@Data
public class FeedbackRequest {
    private String customerName;
    private String content;
}
```

`dto/AskRequest.java` (dùng cho tính năng nâng cao)
```java
package com.huy.feedbackai.dto;

import lombok.Data;

@Data
public class AskRequest {
    private String question;
}
```

### 7.4. ⭐ GeminiService — TRÁI TIM AI của dự án
Đây là lớp gọi tới Gemini. Đây cũng là phần bạn nên hiểu kỹ nhất để nói khi phỏng vấn.

`service/GeminiService.java`
```java
package com.huy.feedbackai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

@Service
public class GeminiService {

    @Value("${gemini.api.key}")
    private String apiKey;

    @Value("${gemini.api.url}")
    private String apiUrl;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Gửi một prompt tới Gemini và trả về phần text mà AI sinh ra.
     */
    public String generate(String prompt) {
        // 1. Xây dựng body request đúng định dạng Gemini yêu cầu
        Map<String, Object> body = Map.of(
            "contents", List.of(
                Map.of("parts", List.of(
                    Map.of("text", prompt)
                ))
            )
        );

        // 2. Set header (Content-Type + API key)
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("x-goog-api-key", apiKey);

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // 3. Gọi API và lấy text từ response JSON
        try {
            ResponseEntity<String> response =
                restTemplate.postForEntity(apiUrl, request, String.class);

            JsonNode root = objectMapper.readTree(response.getBody());
            // Đường dẫn tới text: candidates[0].content.parts[0].text
            return root.path("candidates").get(0)
                       .path("content").path("parts").get(0)
                       .path("text").asText().trim();
        } catch (Exception e) {
            return "AI_ERROR: " + e.getMessage();
        }
    }
}
```

**Giải thích để bạn kể được:** Gemini nhận JSON dạng `{ "contents": [ { "parts": [ { "text": "..." } ] } ] }`, và trả JSON có `candidates[0].content.parts[0].text`. Ta dùng `RestTemplate` để gửi POST, và `Jackson (ObjectMapper)` để bóc phần text ra.

### 7.5. FeedbackService — logic nghiệp vụ
Lớp này quyết định: khi có feedback mới thì hỏi AI thế nào, parse ra sao.

`service/FeedbackService.java`
```java
package com.huy.feedbackai.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.huy.feedbackai.dto.FeedbackRequest;
import com.huy.feedbackai.model.Feedback;
import com.huy.feedbackai.repository.FeedbackRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
        String allFeedback = repository.findAll().stream()
            .map(f -> "- " + f.getContent())
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
```

> **Điểm ăn tiền khi phỏng vấn:** bạn ép AI trả về JSON để dùng được trong code (structured output) thay vì trả văn xuôi. Đây là kỹ thuật "prompt engineering cho lập trình" thật sự.

### 7.6. Controller — cổng nhận HTTP request
`controller/FeedbackController.java`
```java
package com.huy.feedbackai.controller;

import com.huy.feedbackai.dto.AskRequest;
import com.huy.feedbackai.dto.FeedbackRequest;
import com.huy.feedbackai.model.Feedback;
import com.huy.feedbackai.service.FeedbackService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

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
```

---

## 8. Chạy & test

1. Chạy `FeedbackaiApplication` (nút Run màu xanh trong IntelliJ).
2. Mở **Postman**, test lần lượt:

**Tạo feedback (POST):**c
- URL: `POST http://localhost:8080/api/feedbacks`
- Body → raw → JSON:
```json
{
  "customerName": "Huy",
  "content": "Giao hàng nhanh nhưng đóng gói hơi sơ sài, sản phẩm vẫn ổn."
}
```
- Kết quả mong đợi: trả về object có `sentiment` và `summary` do AI điền.

**Xem tất cả:** `GET http://localhost:8080/api/feedbacks`

**Thống kê:** `GET http://localhost:8080/api/feedbacks/stats`

**Hỏi AI (POST):** `POST http://localhost:8080/api/feedbacks/ask`
```json
{ "question": "Khách hàng phàn nàn nhiều nhất về điều gì?" }
```

**Xem DB trực quan:** mở trình duyệt `http://localhost:8080/h2-console`, JDBC URL điền `jdbc:h2:mem:feedbackdb`, bấm Connect.

---

## 9. Lộ trình làm trong 1–2 tuần

| Ngày | Việc |
|---|---|
| 1 | Cài đặt, tạo project, lấy Gemini key, chạy app rỗng thành công |
| 2 | Viết Entity + Repository + application.properties, xem H2 console |
| 3–4 | Viết `GeminiService`, test gọi AI với 1 prompt đơn giản |
| 5–6 | Viết `FeedbackService` + `Controller`, test POST/GET bằng Postman |
| 7 | Thêm `/stats` và `/ask` (nâng cao) |
| 8–9 | Xử lý lỗi, làm sạch code, viết README |
| 10 | Đẩy lên GitHub, quay video demo ngắn / chụp màn hình Postman |

---

## 10. README cho GitHub (bắt buộc — nhà tuyển dụng sẽ đọc cái này)

Tạo file `README.md` ở gốc repo, gồm:
- **Mô tả 1 dòng:** "AI-powered customer feedback analyzer built with Spring Boot and Google Gemini API."
- **Tính năng chính** (bullet)
- **Tech stack**
- **Cách chạy** (yêu cầu JDK 17, đặt biến `GEMINI_API_KEY`, `./mvnw spring-boot:run`)
- **Ảnh chụp Postman** minh họa 1 request/response
- **Kiến trúc** (Controller → Service → Repository → DB, và luồng gọi AI)

Viết README bằng **tiếng Anh** — đây là cơ hội thể hiện tiếng Anh viết của bạn cho nhà tuyển dụng.

---

## 11. Nâng cấp để "ghi điểm" thêm (tùy sức)

- **Đổi H2 → MySQL:** thêm dependency `mysql-connector-j`, đổi `application.properties`. Ghi "MySQL" vào CV sẽ mạnh hơn H2.
- **Viết Unit Test** với JUnit + Mockito cho `FeedbackService` (mock `GeminiService`). CV bạn đã ghi JUnit — có test thật sẽ rất khớp.
- **Validation:** thêm `@NotBlank` cho `content` (dependency `spring-boot-starter-validation`).
- **Swagger UI** (springdoc-openapi): tự sinh trang tài liệu API đẹp — trông rất chuyên nghiệp.
- **Một trang HTML nhỏ** gọi API để demo trực quan (bạn đã biết Next.js/HTML).

---

## 12. Câu hỏi phỏng vấn có thể gặp về dự án này (chuẩn bị trước)

1. *Tại sao chia thành Controller / Service / Repository?* → Tách trách nhiệm (separation of concerns), dễ test và bảo trì.
2. *Dependency Injection là gì, dùng ở đâu?* → Spring tự tiêm object qua constructor; thấy ở `FeedbackService` và `Controller`.
3. *Bạn gọi AI như thế nào?* → HTTP POST tới Gemini bằng RestTemplate, gửi JSON, parse response bằng Jackson.
4. *Làm sao AI trả về đúng định dạng để code dùng được?* → Ép model trả JSON qua prompt, rồi parse; có xử lý trường hợp lỗi.
5. *Nếu API AI lỗi/timeout thì sao?* → Có try-catch, trả thông báo lỗi thay vì để app crash (và có thể nói hướng cải tiến: retry, fallback).
6. *Làm sao bảo mật API key?* → Để trong biến môi trường, không commit lên Git.

---

Chúc bạn làm tốt! Nếu kẹt ở bước nào, cứ nói rõ lỗi (copy đoạn error) là gỡ được.
