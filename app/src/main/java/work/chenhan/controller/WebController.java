package work.chenhan.controller;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import work.chenhan.entity.WebhookConfig;
import work.chenhan.repository.ScaProcessRecordRepository;

import work.chenhan.repository.WebhookPushLogRepository;

@Controller
public class WebController {

    private final work.chenhan.service.WebhookConfigService webhookConfigService;
    private final ScaProcessRecordRepository processRecordRepository;
    private final WebhookPushLogRepository pushLogRepository;

    public WebController(work.chenhan.service.WebhookConfigService webhookConfigService,
            ScaProcessRecordRepository processRecordRepository,
            WebhookPushLogRepository pushLogRepository) {
        this.webhookConfigService = webhookConfigService;
        this.processRecordRepository = processRecordRepository;
        this.pushLogRepository = pushLogRepository;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    // --- Webhook 管理 ---

    @GetMapping("/webhooks")
    public String listWebhooks(Model model) {
        model.addAttribute("webhooks", webhookConfigService.getAllConfigs());
        model.addAttribute("newWebhook", new WebhookConfig());
        return "webhooks";
    }

    @PostMapping("/webhooks")
    public String addWebhook(@ModelAttribute WebhookConfig webhookConfig,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {
        if (webhookConfig.getUrl() != null && !webhookConfig.getUrl().isBlank()) {
            try {
                webhookConfigService.save(webhookConfig);
            } catch (RuntimeException e) {
                redirectAttributes.addFlashAttribute("errorMessage", e.getMessage());
            }
        }
        return "redirect:/webhooks";
    }

    @GetMapping("/webhooks/delete/{id}")
    public String deleteWebhook(@PathVariable Long id) {
        webhookConfigService.deleteById(id);
        return "redirect:/webhooks";
    }

    @GetMapping("/webhooks/toggle/{id}")
    public String toggleWebhook(@PathVariable Long id) {
        webhookConfigService.toggleEnabled(id);
        return "redirect:/webhooks";
    }

    // --- 日志查看 ---

    @GetMapping("/logs/push")
    public String listPushLogs(@RequestParam(required = false) String projectName,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String webhookUrl,
            Model model) {

        org.springframework.data.jpa.domain.Specification<work.chenhan.entity.WebhookPushLog> spec = (root, query,
                cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (projectName != null && !projectName.isBlank()) {
                predicates.add(cb.like(root.get("scaProjectName"), "%" + projectName + "%"));
            }
            if (applicationName != null && !applicationName.isBlank()) {
                predicates.add(cb.like(root.get("scaApplicationName"), "%" + applicationName + "%"));
            }
            if (taskId != null && !taskId.isBlank()) {
                predicates.add(cb.equal(root.get("scaTaskId"), taskId));
            }
            if (status != null && !status.isBlank()) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (webhookUrl != null && !webhookUrl.isBlank()) {
                predicates.add(cb.like(root.get("webhookUrl"), "%" + webhookUrl + "%"));
            }

            query.orderBy(cb.desc(root.get("id")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        model.addAttribute("logs", pushLogRepository.findAll(spec));

        // 将筛选参数返回给视图以回填表单
        model.addAttribute("paramProjectName", projectName);
        model.addAttribute("paramApplicationName", applicationName);
        model.addAttribute("paramTaskId", taskId);
        model.addAttribute("paramStatus", status);
        model.addAttribute("paramWebhookUrl", webhookUrl);

        return "push_logs";
    }

    @GetMapping("/logs/process")
    public String listProcessRecords(@RequestParam(required = false) String projectName,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) Boolean isAllowed,
            Model model) {

        org.springframework.data.jpa.domain.Specification<work.chenhan.entity.ScaProcessRecord> spec = (root, query,
                cb) -> {
            java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();

            if (projectName != null && !projectName.isBlank()) {
                predicates.add(cb.like(root.get("scaProjectName"), "%" + projectName + "%"));
            }
            if (applicationName != null && !applicationName.isBlank()) {
                predicates.add(cb.like(root.get("scaApplicationName"), "%" + applicationName + "%"));
            }
            if (taskId != null && !taskId.isBlank()) {
                predicates.add(cb.equal(root.get("scaTaskId"), taskId));
            }
            if (isAllowed != null) {
                predicates.add(cb.equal(root.get("isAllowed"), isAllowed));
            }

            query.orderBy(cb.desc(root.get("id")));
            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        model.addAttribute("records", processRecordRepository.findAll(spec));

        // 将筛选参数返回给视图以回填表单
        model.addAttribute("paramProjectName", projectName);
        model.addAttribute("paramApplicationName", applicationName);
        model.addAttribute("paramTaskId", taskId);
        model.addAttribute("paramIsAllowed", isAllowed);

        return "process_records";
    }
}
