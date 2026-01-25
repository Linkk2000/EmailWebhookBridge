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
    private final org.springframework.web.client.RestClient.Builder restClientBuilder;

    public WebController(work.chenhan.service.WebhookConfigService webhookConfigService,
            ScaProcessRecordRepository processRecordRepository,
            WebhookPushLogRepository pushLogRepository,
            org.springframework.web.client.RestClient.Builder restClientBuilder) {
        this.webhookConfigService = webhookConfigService;
        this.processRecordRepository = processRecordRepository;
        this.pushLogRepository = pushLogRepository;
        this.restClientBuilder = restClientBuilder;
    }

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
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

    @GetMapping("/webhooks/test/{id}")
    public String testWebhook(@PathVariable Long id,
            org.springframework.web.servlet.mvc.support.RedirectAttributes redirectAttributes) {

        webhookConfigService.testConnection(id, restClientBuilder.build());
        webhookConfigService.findById(id).ifPresent(config -> {
            if ("UP".equals(config.getLastStatus())) {
                redirectAttributes.addFlashAttribute("successMessage", "测试成功！Webhook 连接正常。");
            } else {
                redirectAttributes.addFlashAttribute("errorMessage", "测试失败: " + config.getErrorMessage());
            }
        });
        return "redirect:/webhooks";
    }

    @GetMapping("/webhooks/detail/{id}")
    public String webhookDetail(@PathVariable Long id,
            @RequestParam(required = false) String projectName,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        webhookConfigService.findById(id).ifPresent(config -> {
            // 使用 Specification 进行过滤
            org.springframework.data.jpa.domain.Specification<work.chenhan.entity.WebhookPushLog> spec = (root, query,
                    cb) -> {
                java.util.List<jakarta.persistence.criteria.Predicate> predicates = new java.util.ArrayList<>();
                predicates.add(cb.equal(root.get("webhookUrl"), config.getUrl()));
                if (projectName != null && !projectName.isBlank()) {
                    predicates.add(cb.like(root.get("scaProjectName"), "%" + projectName + "%"));
                }
                if (applicationName != null && !applicationName.isBlank()) {
                    predicates.add(cb.like(root.get("scaApplicationName"), "%" + applicationName + "%"));
                }
                if (status != null && !status.isBlank()) {
                    predicates.add(cb.equal(root.get("status"), status));
                }
                return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
            };

            org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page,
                    size,
                    org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
            org.springframework.data.domain.Page<work.chenhan.entity.WebhookPushLog> logPage = pushLogRepository
                    .findAll(spec, pageable);

            model.addAttribute("webhook", config);
            model.addAttribute("logs", logPage.getContent());
            model.addAttribute("page", logPage);

            // 全局基础统计
            long total = pushLogRepository
                    .count((root, query, cb) -> cb.equal(root.get("webhookUrl"), config.getUrl()));
            long success = pushLogRepository.count((root, query, cb) -> cb.and(
                    cb.equal(root.get("webhookUrl"), config.getUrl()),
                    cb.equal(root.get("status"), "SUCCESS")));
            long failed = total - success;
            double successRate = total > 0 ? (success * 100.0 / total) : 0;

            model.addAttribute("statsTotal", total);
            model.addAttribute("statsSuccess", success);
            model.addAttribute("statsFailed", failed);
            model.addAttribute("statsSuccessRate", String.format("%.1f", successRate));

            // 构建趋势图统计 (最近 24 小时)
            java.time.LocalDateTime now = java.time.LocalDateTime.now();
            java.util.List<String> chartLabels = new java.util.ArrayList<>();
            java.util.List<Long> chartSuccessData = new java.util.ArrayList<>();
            java.util.List<Long> chartFailedData = new java.util.ArrayList<>();

            // 预先查出最近 24 小时的所有日志
            java.util.List<work.chenhan.entity.WebhookPushLog> recentLogs = pushLogRepository
                    .findAll((root, query, cb) -> cb.and(
                            cb.equal(root.get("webhookUrl"), config.getUrl()),
                            cb.greaterThanOrEqualTo(root.get("pushedAt"), now.minusHours(24))));

            for (int i = 23; i >= 0; i--) {
                java.time.LocalDateTime hourStart = now.minusHours(i).withMinute(0).withSecond(0).withNano(0);
                java.time.LocalDateTime hourEnd = hourStart.plusHours(1);

                chartLabels.add(hourStart.format(java.time.format.DateTimeFormatter.ofPattern("HH:00")));

                long hSuccess = recentLogs.stream()
                        .filter(l -> "SUCCESS".equals(l.getStatus()) && !l.getPushedAt().isBefore(hourStart)
                                && l.getPushedAt().isBefore(hourEnd))
                        .count();
                long hFailed = recentLogs.stream()
                        .filter(l -> "FAILED".equals(l.getStatus()) && !l.getPushedAt().isBefore(hourStart)
                                && l.getPushedAt().isBefore(hourEnd))
                        .count();

                chartSuccessData.add(hSuccess);
                chartFailedData.add(hFailed);
            }

            model.addAttribute("chartLabels", chartLabels);
            model.addAttribute("chartSuccessData", chartSuccessData);
            model.addAttribute("chartFailedData", chartFailedData);

            // 回显参数
            model.addAttribute("paramProjectName", projectName);
            model.addAttribute("paramApplicationName", applicationName);
            model.addAttribute("paramStatus", status);
        });
        return "webhook_detail";
    }

    // --- 日志查看 ---

    @GetMapping("/logs/push")
    public String listPushLogs(@RequestParam(required = false) String projectName,
            @RequestParam(required = false) String applicationName,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String webhookUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        org.springframework.data.domain.Page<work.chenhan.entity.WebhookPushLog> logPage = pushLogRepository
                .findAll(spec, pageable);

        model.addAttribute("logs", logPage.getContent());
        model.addAttribute("page", logPage);

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
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
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

            return cb.and(predicates.toArray(new jakarta.persistence.criteria.Predicate[0]));
        };

        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(page, size,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "id"));
        org.springframework.data.domain.Page<work.chenhan.entity.ScaProcessRecord> recordPage = processRecordRepository
                .findAll(spec, pageable);

        model.addAttribute("records", recordPage.getContent());
        model.addAttribute("page", recordPage);

        // 将筛选参数返回给视图以回填表单
        model.addAttribute("paramProjectName", projectName);
        model.addAttribute("paramApplicationName", applicationName);
        model.addAttribute("paramTaskId", taskId);
        model.addAttribute("paramIsAllowed", isAllowed);

        return "process_records";
    }
}
